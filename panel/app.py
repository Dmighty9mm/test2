#!/usr/bin/env python3
"""
PlebMc Panel: accounts, coins and cosmetics for a PlebMc (Eaglercraft) network.

One file, Python standard library only (SQLite for storage), so it runs anywhere
with `python3 app.py` or in the Docker image next to it.

What it does
  * Players create a PlebMc account (name + password). On an offline-mode server
    a name proves nothing, so the proxy plugin (PlebioAPI) only lets a player
    spend coins, buy, equip or use the admin menu after they sign in to the
    account that owns their name, in game or with a remembered client token.
  * Players sign in here to see their coins and cosmetics, change their
    password and sign out every game client.
  * Admins search players, change coins and cosmetics, kick, lock accounts and
    reset passwords. Changes to coins and cosmetics are queued and applied by
    PlebioAPI on the proxy within a few seconds (PlebioAPI stays the one place
    coins are kept, so nothing can be spent twice).

The proxy talks to /api/server/* with the server key (PANEL_SERVER_KEY).
Everything else is the website.

Commands
  python3 app.py                         run the panel
  python3 app.py create-account NAME [owner|admin|player]
  python3 app.py set-role NAME owner|admin|player
  python3 app.py reset-password NAME
  python3 app.py unlock NAME
"""

import base64
import getpass
import hashlib
import hmac
import json
import mimetypes
import os
import re
import secrets
import sqlite3
import sys
import threading
import time
from http import HTTPStatus
from http.cookies import SimpleCookie
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

HERE = os.path.dirname(os.path.abspath(__file__))
STATIC = os.path.join(HERE, "static")

NAME_RE = re.compile(r"^[A-Za-z0-9_]{3,16}$")
MIN_PASSWORD = 8
MAX_PASSWORD = 128
ROLES = ("player", "admin", "owner")
SESSION_DAYS = 14
GAME_TOKEN_DAYS = 60
MAX_GAME_TOKENS = 10
CLAIM_MINUTES = 15
CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
# wrong passwords before a name or address has to wait
MAX_FAILS = 5
FAIL_WINDOW = 10 * 60
COMMAND_ACTIONS = ("coins", "give", "remove", "equip", "kick", "signout")
CATEGORIES = ("hat", "trail", "cloak")


def env(name, default=None):
    v = os.environ.get(name)
    return v if v not in (None, "") else default


class Config:
    def __init__(self):
        self.db = env("PANEL_DB", os.path.join(HERE, "data", "panel.db"))
        self.host = env("PANEL_HOST", "0.0.0.0")
        self.port = int(env("PANEL_PORT", "8080"))
        self.server_key = env("PANEL_SERVER_KEY", "")
        self.public_url = env("PANEL_PUBLIC_URL", "http://localhost:8080").rstrip("/")
        self.site_name = env("PANEL_SITE_NAME", "PlebMc")
        self.trust_proxy = env("PANEL_TRUST_PROXY", "0") == "1"
        self.open_registration = env("PANEL_OPEN_REGISTRATION", "0") == "1"
        # the PlebMc client's start screen can make accounts (it signs in before it can play)
        self.client_registration = env("PANEL_CLIENT_REGISTRATION", "1") == "1"
        # accounts one address may make from the client per hour
        self.client_registrations_per_hour = int(env("PANEL_CLIENT_REGISTRATIONS_PER_HOUR", "3"))
        secure = env("PANEL_SECURE_COOKIES")
        self.secure_cookies = (secure == "1") if secure is not None else self.public_url.startswith("https://")
        # scrypt cost; tests turn it down
        self.scrypt_n = int(env("PANEL_SCRYPT_N", str(2 ** 14)))


CFG = Config()


# ==================== passwords and tokens ====================

def hash_password(password, n=None):
    n = n or CFG.scrypt_n
    salt = secrets.token_bytes(16)
    h = hashlib.scrypt(password.encode("utf-8"), salt=salt, n=n, r=8, p=1, dklen=32, maxmem=64 * 1024 * 1024)
    return "scrypt$%d$8$1$%s$%s" % (n, b64(salt), b64(h))


def check_password(password, stored):
    try:
        kind, n, r, p, salt, h = stored.split("$")
        if kind != "scrypt":
            return False
        got = hashlib.scrypt(password.encode("utf-8"), salt=unb64(salt), n=int(n), r=int(r), p=int(p), dklen=32,
                             maxmem=64 * 1024 * 1024)
        return hmac.compare_digest(got, unb64(h))
    except (ValueError, TypeError):
        return False


def b64(b):
    return base64.urlsafe_b64encode(b).decode("ascii").rstrip("=")


def unb64(s):
    return base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))


def token_hash(token):
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def new_code():
    raw = "".join(secrets.choice(CODE_ALPHABET) for _ in range(8))
    return raw[:4] + "-" + raw[4:]


def norm_code(code):
    return re.sub(r"[^A-Z0-9]", "", str(code or "").upper())


def password_problem(password):
    if not isinstance(password, str) or len(password) < MIN_PASSWORD:
        return "Use a password of at least %d characters." % MIN_PASSWORD
    if len(password) > MAX_PASSWORD:
        return "Use a password of at most %d characters." % MAX_PASSWORD
    return None


def name_problem(name):
    if not isinstance(name, str) or not NAME_RE.match(name):
        return "Names are 3 to 16 letters, digits or _."
    return None


# ==================== storage ====================

SCHEMA = """
CREATE TABLE IF NOT EXISTS accounts (
  id INTEGER PRIMARY KEY,
  name TEXT NOT NULL,
  name_key TEXT NOT NULL UNIQUE,
  password TEXT NOT NULL,
  role TEXT NOT NULL DEFAULT 'player',
  locked INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  password_at INTEGER NOT NULL,
  last_login INTEGER
);
CREATE TABLE IF NOT EXISTS web_sessions (
  token_hash TEXT PRIMARY KEY,
  account_id INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS game_tokens (
  token_hash TEXT PRIMARY KEY,
  account_id INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  last_used INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS claim_codes (
  name_key TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  code TEXT NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS players (
  uuid TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  name_key TEXT NOT NULL,
  coins INTEGER NOT NULL DEFAULT 0,
  owned TEXT NOT NULL DEFAULT '[]',
  equipped TEXT NOT NULL DEFAULT '{}',
  streak INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS players_name ON players(name_key);
CREATE TABLE IF NOT EXISTS commands (
  id INTEGER PRIMARY KEY,
  created_at INTEGER NOT NULL,
  by_name TEXT NOT NULL,
  target TEXT NOT NULL,
  action TEXT NOT NULL,
  args TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending',
  result TEXT,
  done_at INTEGER
);
CREATE TABLE IF NOT EXISTS audit (
  id INTEGER PRIMARY KEY,
  at INTEGER NOT NULL,
  actor TEXT NOT NULL,
  action TEXT NOT NULL,
  detail TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS kv (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);
"""


class Db:
    """One SQLite connection behind a lock (the panel is small; this keeps it simple and safe)."""

    def __init__(self, path):
        if path != ":memory:":
            os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
        self.conn = sqlite3.connect(path, check_same_thread=False, isolation_level=None)
        self.conn.row_factory = sqlite3.Row
        self.lock = threading.RLock()
        with self.lock:
            if path != ":memory:":
                self.conn.execute("PRAGMA journal_mode=WAL")
            self.conn.execute("PRAGMA foreign_keys=ON")
            self.conn.executescript(SCHEMA)

    def all(self, sql, *args):
        with self.lock:
            return [dict(r) for r in self.conn.execute(sql, args).fetchall()]

    def one(self, sql, *args):
        with self.lock:
            r = self.conn.execute(sql, args).fetchone()
            return dict(r) if r else None

    def run(self, sql, *args):
        with self.lock:
            return self.conn.execute(sql, args)

    def tx(self):
        return Tx(self)

    def get_kv(self, key, default=None):
        r = self.one("SELECT value FROM kv WHERE key=?", key)
        return json.loads(r["value"]) if r else default

    def set_kv(self, key, value):
        self.run("INSERT INTO kv(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", key,
                 json.dumps(value))


class Tx:
    def __init__(self, db):
        self.db = db

    def __enter__(self):
        self.db.lock.acquire()
        self.db.conn.execute("BEGIN IMMEDIATE")
        return self.db.conn

    def __exit__(self, kind, value, tb):
        try:
            self.db.conn.execute("COMMIT" if kind is None else "ROLLBACK")
        finally:
            self.db.lock.release()
        return False


# ==================== throttling ====================

class Throttle:
    def __init__(self, max_fails=MAX_FAILS, window=FAIL_WINDOW, clock=time.time):
        self.max = max_fails
        self.window = window
        self.clock = clock
        self.fails = {}
        self.lock = threading.Lock()

    def blocked(self, *keys):
        now = self.clock()
        with self.lock:
            for k in keys:
                if k is None:
                    continue
                f = [t for t in self.fails.get(k, []) if now - t < self.window]
                self.fails[k] = f
                if len(f) >= self.max:
                    return True
        return False

    def fail(self, *keys):
        now = self.clock()
        with self.lock:
            for k in keys:
                if k is not None:
                    self.fails.setdefault(k, []).append(now)

    def clear(self, *keys):
        with self.lock:
            for k in keys:
                self.fails.pop(k, None)


# ==================== the panel ====================

class ApiError(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status
        self.message = message


class Panel:
    def __init__(self, db, cfg=CFG, clock=time.time):
        self.db = db
        self.cfg = cfg
        self.clock = clock
        self.throttle = Throttle(clock=clock)
        # registration codes tried per address
        self.code_throttle = Throttle(max_fails=10, window=FAIL_WINDOW, clock=clock)
        # accounts made from the client per address
        self.signup_throttle = Throttle(max_fails=cfg.client_registrations_per_hour, window=3600, clock=clock)

    def now(self):
        return int(self.clock())

    # ---------- accounts ----------

    def account(self, name):
        return self.db.one("SELECT * FROM accounts WHERE name_key=?", str(name or "").lower())

    def account_by_id(self, aid):
        return self.db.one("SELECT * FROM accounts WHERE id=?", aid)

    def create_account(self, name, password, role="player", actor=None):
        problem = name_problem(name) or password_problem(password)
        if problem:
            raise ApiError(400, problem)
        if role not in ROLES:
            raise ApiError(400, "Unknown role.")
        now = self.now()
        with self.db.tx() as c:
            if c.execute("SELECT 1 FROM accounts WHERE name_key=?", (name.lower(),)).fetchone():
                raise ApiError(409, "%s already has an account." % name)
            c.execute("INSERT INTO accounts(name,name_key,password,role,created_at,password_at) VALUES(?,?,?,?,?,?)",
                      (name, name.lower(), hash_password(password, self.cfg.scrypt_n), role, now, now))
            c.execute("DELETE FROM claim_codes WHERE name_key=?", (name.lower(),))
        self.audit(actor or name, "create-account", "%s (%s)" % (name, role))
        return self.account(name)

    def set_password(self, acc, password, actor):
        problem = password_problem(password)
        if problem:
            raise ApiError(400, problem)
        self.db.run("UPDATE accounts SET password=?, password_at=? WHERE id=?",
                    hash_password(password, self.cfg.scrypt_n), self.now(), acc["id"])
        self.signout_everywhere(acc, actor, keep_web=None)
        self.audit(actor, "set-password", acc["name"])

    def signout_everywhere(self, acc, actor, keep_web=None):
        """Forgets every game token and web session (but keep_web) and signs the player out in game."""
        self.db.run("DELETE FROM game_tokens WHERE account_id=?", acc["id"])
        if keep_web:
            self.db.run("DELETE FROM web_sessions WHERE account_id=? AND token_hash<>?", acc["id"], token_hash(keep_web))
        else:
            self.db.run("DELETE FROM web_sessions WHERE account_id=?", acc["id"])
        self.queue(actor, acc["name"], "signout", [])

    def public_account(self, acc):
        return {"name": acc["name"], "role": acc["role"], "locked": bool(acc["locked"]),
                "created": acc["created_at"], "lastLogin": acc["last_login"]}

    def audit(self, actor, action, detail):
        self.db.run("INSERT INTO audit(at,actor,action,detail) VALUES(?,?,?,?)", self.now(), actor, action, detail)

    # ---------- web sessions ----------

    def web_login(self, name, password, address):
        key = "web:" + str(name or "").lower()
        if self.throttle.blocked(key, "ip:" + str(address)):
            raise ApiError(429, "Too many wrong passwords. Try again in 10 minutes.")
        acc = self.account(name)
        if not acc or not check_password(str(password or ""), acc["password"]):
            self.throttle.fail(key, "ip:" + str(address))
            raise ApiError(401, "Wrong name or password.")
        if acc["locked"]:
            raise ApiError(403, "This account is locked. Ask an admin.")
        self.throttle.clear(key)
        token = secrets.token_urlsafe(32)
        now = self.now()
        self.db.run("INSERT INTO web_sessions(token_hash,account_id,created_at,expires_at) VALUES(?,?,?,?)",
                    token_hash(token), acc["id"], now, now + SESSION_DAYS * 86400)
        self.db.run("UPDATE accounts SET last_login=? WHERE id=?", now, acc["id"])
        self.db.run("DELETE FROM web_sessions WHERE expires_at<?", now)
        return token, acc

    def web_account(self, token):
        if not token:
            return None
        r = self.db.one("SELECT a.* FROM web_sessions s JOIN accounts a ON a.id=s.account_id "
                        "WHERE s.token_hash=? AND s.expires_at>?", token_hash(token), self.now())
        if r and r["locked"]:
            return None
        return r

    def web_logout(self, token):
        if token:
            self.db.run("DELETE FROM web_sessions WHERE token_hash=?", token_hash(token))

    # ---------- registration codes ----------

    def claim(self, name):
        """A code the player types on the website to prove they're the one in game with this name."""
        problem = name_problem(name)
        if problem:
            raise ApiError(400, problem)
        if self.account(name):
            raise ApiError(409, "%s already has an account." % name)
        code = new_code()
        self.db.run("INSERT INTO claim_codes(name_key,name,code,expires_at) VALUES(?,?,?,?) ON CONFLICT(name_key) DO "
                    "UPDATE SET name=excluded.name, code=excluded.code, expires_at=excluded.expires_at",
                    name.lower(), name, norm_code(code), self.now() + CLAIM_MINUTES * 60)
        return code

    def web_register(self, name, code, password, address):
        if self.code_throttle.blocked("ip:" + str(address)):
            raise ApiError(429, "Too many wrong codes. Try again in 10 minutes.")
        problem = name_problem(name) or password_problem(password)
        if problem:
            raise ApiError(400, problem)
        if not self.cfg.open_registration:
            row = self.db.one("SELECT * FROM claim_codes WHERE name_key=? AND expires_at>?", name.lower(), self.now())
            if not row or not hmac.compare_digest(row["code"], norm_code(code)):
                self.code_throttle.fail("ip:" + str(address))
                raise ApiError(400, "That code is wrong or expired. Type /account code in game for a new one.")
            # the spelling used in game wins (it is the player's UUID)
            name = row["name"]
        return self.create_account(name, password)

    # ---------- game sign-in ----------

    def game_login(self, name, password, remember):
        key = "game:" + str(name or "").lower()
        if self.throttle.blocked(key):
            return {"ok": False, "error": "Too many wrong passwords for this name. Try again in 10 minutes."}
        acc = self.account(name)
        if not acc:
            return {"ok": False, "error": "No account", "exists": False}
        if acc["locked"]:
            return {"ok": False, "error": "This account is locked. Ask an admin.", "locked": True}
        if not check_password(str(password or ""), acc["password"]):
            self.throttle.fail(key)
            return {"ok": False, "error": "Wrong password."}
        self.throttle.clear(key)
        out = {"ok": True, "name": acc["name"], "role": acc["role"]}
        if remember:
            out["token"] = self.issue_game_token(acc)
        self.db.run("UPDATE accounts SET last_login=? WHERE id=?", self.now(), acc["id"])
        return out

    def issue_game_token(self, acc):
        token = secrets.token_urlsafe(32)
        now = self.now()
        with self.db.tx() as c:
            c.execute("INSERT INTO game_tokens(token_hash,account_id,created_at,last_used) VALUES(?,?,?,?)",
                      (token_hash(token), acc["id"], now, now))
            old = c.execute("SELECT token_hash FROM game_tokens WHERE account_id=? ORDER BY last_used DESC LIMIT -1 "
                            "OFFSET ?", (acc["id"], MAX_GAME_TOKENS)).fetchall()
            for r in old:
                c.execute("DELETE FROM game_tokens WHERE token_hash=?", (r[0],))
        return token

    def game_token(self, name, token):
        acc = self.account(name)
        if not acc or not token:
            return {"ok": False, "error": "Sign in again."}
        if acc["locked"]:
            return {"ok": False, "error": "This account is locked. Ask an admin.", "locked": True}
        now = self.now()
        row = self.db.one("SELECT * FROM game_tokens WHERE token_hash=? AND account_id=?", token_hash(str(token)), acc["id"])
        if not row or now - row["last_used"] > GAME_TOKEN_DAYS * 86400:
            return {"ok": False, "error": "Sign in again."}
        self.db.run("UPDATE game_tokens SET last_used=? WHERE token_hash=?", now, row["token_hash"])
        self.db.run("UPDATE accounts SET last_login=? WHERE id=?", now, acc["id"])
        return {"ok": True, "name": acc["name"], "role": acc["role"]}

    def forget_game_token(self, token):
        if token:
            self.db.run("DELETE FROM game_tokens WHERE token_hash=?", token_hash(str(token)))

    # ---------- the PlebMc client's start screen ----------

    def client_login(self, name, password, address):
        if self.throttle.blocked("cip:" + str(address)):
            return {"ok": False, "error": "Too many wrong passwords. Try again in 10 minutes."}
        r = self.game_login(name, password, True)
        if not r["ok"] and r.get("exists") is not False and "Too many" not in r["error"] and not r.get("locked"):
            self.throttle.fail("cip:" + str(address))
        if not r["ok"] and r.get("exists") is False:
            r["error"] = "No account with that name. Create one first."
        return r

    def client_register(self, name, password, address):
        if not self.cfg.client_registration:
            return {"ok": False, "error": "Make your account on the website: " + self.cfg.public_url}
        if self.signup_throttle.blocked("ip:" + str(address)):
            return {"ok": False, "error": "Too many new accounts from your network. Try again in an hour."}
        try:
            acc = self.create_account(name, password)
        except ApiError as e:
            return {"ok": False, "error": e.message}
        self.signup_throttle.fail("ip:" + str(address))
        return {"ok": True, "name": acc["name"], "role": acc["role"], "token": self.issue_game_token(acc)}

    def server_register(self, name, password):
        acc = self.create_account(name, password)
        return {"ok": True, "name": acc["name"], "role": acc["role"]}

    def lookup(self, name):
        acc = self.account(name)
        if not acc:
            return {"exists": False}
        return {"exists": True, "name": acc["name"], "role": acc["role"], "locked": bool(acc["locked"])}

    # ---------- coins and cosmetics (from PlebioAPI) ----------

    def sync(self, body):
        players = body.get("players")
        now = self.now()
        if isinstance(players, list):
            with self.db.tx() as c:
                if body.get("full"):
                    c.execute("DELETE FROM players")
                for p in players:
                    if not isinstance(p, dict) or not p.get("uuid"):
                        continue
                    name = str(p.get("name") or "")[:32]
                    c.execute("INSERT INTO players(uuid,name,name_key,coins,owned,equipped,streak,updated_at) "
                              "VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, "
                              "name_key=excluded.name_key, coins=excluded.coins, owned=excluded.owned, "
                              "equipped=excluded.equipped, streak=excluded.streak, updated_at=excluded.updated_at",
                              (str(p["uuid"]).lower()[:36], name, name.lower(), int(p.get("coins") or 0),
                               json.dumps([str(x) for x in (p.get("owned") or [])][:500]),
                               json.dumps({str(k): str(v) for k, v in (p.get("equipped") or {}).items()}),
                               int(p.get("streak") or 0), now))
        if isinstance(body.get("catalog"), list):
            self.db.set_kv("catalog", [
                {"id": str(i.get("id")), "name": str(i.get("name") or i.get("id")), "category": str(i.get("category")),
                 "price": i.get("price")} for i in body["catalog"] if isinstance(i, dict) and i.get("id")])
        if isinstance(body.get("online"), list):
            self.db.set_kv("online", {"at": now, "players": [str(n) for n in body["online"]][:5000]})
        if body.get("currency"):
            self.db.set_kv("currency", str(body["currency"])[:40])
        self.db.set_kv("last_sync", now)
        return {"ok": True}

    def player(self, name):
        p = self.db.one("SELECT * FROM players WHERE name_key=? ORDER BY updated_at DESC", str(name or "").lower())
        if not p:
            return None
        return {"uuid": p["uuid"], "name": p["name"], "coins": p["coins"], "owned": json.loads(p["owned"]),
                "equipped": json.loads(p["equipped"]), "streak": p["streak"]}

    def catalog(self):
        return self.db.get_kv("catalog", [])

    def online(self):
        o = self.db.get_kv("online", {"at": 0, "players": []})
        # the proxy syncs every minute; older than 3 minutes means it isn't connected
        return o["players"] if self.now() - o["at"] < 180 else []

    def server_status(self):
        last = self.db.get_kv("last_sync", 0)
        return {"connected": self.now() - last < 180, "lastSync": last, "online": len(self.online()),
                "currency": self.db.get_kv("currency", "PlebMc Coins")}

    # ---------- queued changes (applied by PlebioAPI) ----------

    def queue(self, actor, target, action, args):
        cur = self.db.run("INSERT INTO commands(created_at,by_name,target,action,args) VALUES(?,?,?,?,?)", self.now(),
                          actor, target, action, json.dumps(args))
        return cur.lastrowid

    def pending(self):
        with self.db.tx() as c:
            rows = [dict(r) for r in c.execute("SELECT * FROM commands WHERE status='pending' ORDER BY id LIMIT 100")]
            for r in rows:
                c.execute("UPDATE commands SET status='sent' WHERE id=?", (r["id"],))
        return [{"id": r["id"], "target": r["target"], "action": r["action"], "args": json.loads(r["args"]),
                 "by": r["by_name"]} for r in rows]

    def ack(self, cid, ok, result):
        self.db.run("UPDATE commands SET status=?, result=?, done_at=? WHERE id=?", "done" if ok else "failed",
                    str(result or "")[:500], self.now(), cid)
        self.db.run("UPDATE commands SET status='lost', result='The server did not answer' WHERE status='sent' AND "
                    "created_at<?", self.now() - 600)

    def admin_command(self, actor, body):
        target = str(body.get("name") or "")
        action = str(body.get("action") or "")
        if name_problem(target):
            raise ApiError(400, "Pick a player.")
        if action == "coins":
            op = str(body.get("op") or "")
            try:
                amount = int(body.get("amount"))
            except (TypeError, ValueError):
                amount = -1
            if op not in ("give", "take", "set") or amount < 0 or amount > 10 ** 12:
                raise ApiError(400, "Choose give, take or set and a whole number.")
            args = [op, amount]
        elif action in ("give", "remove"):
            item = str(body.get("item") or "")
            if not any(i["id"] == item for i in self.catalog()):
                raise ApiError(400, "Unknown item.")
            args = [item]
        elif action == "equip":
            cat = str(body.get("category") or "")
            item = str(body.get("item") or "none")
            if cat not in CATEGORIES:
                raise ApiError(400, "Unknown category.")
            if item != "none" and not any(i["id"] == item and i["category"] == cat for i in self.catalog()):
                raise ApiError(400, "Unknown item.")
            args = [cat, item]
        elif action == "kick":
            args = [str(body.get("reason") or "Kicked by an admin.")[:200]]
        else:
            raise ApiError(400, "Unknown action.")
        cid = self.queue(actor["name"], target, action, args)
        self.audit(actor["name"], "queue-" + action, "%s %s" % (target, json.dumps(args)))
        return {"ok": True, "id": cid}

    def can_manage(self, actor, acc):
        if actor["role"] == "owner":
            return True
        return actor["role"] == "admin" and acc["role"] == "player"

    def admin_account(self, actor, body):
        acc = self.account(body.get("name"))
        action = str(body.get("action") or "")
        if action == "create":
            role = str(body.get("role") or "player")
            if role != "player" and actor["role"] != "owner":
                raise ApiError(403, "Only owners can make admins.")
            acc = self.create_account(str(body.get("name") or ""), body.get("password"), role, actor["name"])
            return {"ok": True, "account": self.public_account(acc)}
        if not acc:
            raise ApiError(404, "No account with that name.")
        if not self.can_manage(actor, acc) or (acc["id"] == actor["id"] and action in ("lock", "delete", "role")):
            raise ApiError(403, "You can't change this account.")
        if action == "lock":
            self.db.run("UPDATE accounts SET locked=1 WHERE id=?", acc["id"])
            self.signout_everywhere(acc, actor["name"])
            self.audit(actor["name"], "lock", acc["name"])
        elif action == "unlock":
            self.db.run("UPDATE accounts SET locked=0 WHERE id=?", acc["id"])
            self.throttle.clear("game:" + acc["name_key"], "web:" + acc["name_key"])
            self.audit(actor["name"], "unlock", acc["name"])
        elif action == "password":
            self.set_password(acc, body.get("password"), actor["name"])
        elif action == "signout":
            self.signout_everywhere(acc, actor["name"])
            self.audit(actor["name"], "signout", acc["name"])
        elif action == "role":
            role = str(body.get("role") or "")
            if actor["role"] != "owner" or role not in ROLES:
                raise ApiError(403, "Only owners can change roles.")
            self.db.run("UPDATE accounts SET role=? WHERE id=?", role, acc["id"])
            self.audit(actor["name"], "role", "%s -> %s" % (acc["name"], role))
        elif action == "delete":
            self.signout_everywhere(acc, actor["name"])
            self.db.run("DELETE FROM accounts WHERE id=?", acc["id"])
            self.audit(actor["name"], "delete-account", acc["name"])
            return {"ok": True}
        else:
            raise ApiError(400, "Unknown action.")
        return {"ok": True, "account": self.public_account(self.account_by_id(acc["id"]))}

    def admin_search(self, q):
        q = str(q or "").lower()
        like = "%" + q.replace("%", "").replace("_", "\\_") + "%"
        accs = {a["name_key"]: a for a in self.db.all(
            "SELECT * FROM accounts WHERE name_key LIKE ? ESCAPE '\\' ORDER BY name_key LIMIT 200", like)}
        players = {p["name_key"]: p for p in self.db.all(
            "SELECT * FROM players WHERE name_key LIKE ? ESCAPE '\\' ORDER BY coins DESC LIMIT 200", like)}
        online = {n.lower() for n in self.online()}
        out = []
        for key in set(accs) | set(players):
            a, p = accs.get(key), players.get(key)
            out.append({"name": (a or p)["name"], "account": bool(a), "role": a["role"] if a else None,
                        "locked": bool(a and a["locked"]), "coins": p["coins"] if p else None,
                        "items": len(json.loads(p["owned"])) if p else 0, "online": key in online})
        out.sort(key=lambda r: (not r["online"], -(r["coins"] or 0), r["name"].lower()))
        return out[:200]

    def admin_player(self, name):
        acc = self.account(name)
        p = self.player(name)
        if not acc and not p:
            raise ApiError(404, "Nobody called %s has joined or registered." % name)
        cmds = self.db.all("SELECT * FROM commands WHERE target=? COLLATE NOCASE ORDER BY id DESC LIMIT 20", name)
        tokens = self.db.one("SELECT COUNT(*) n FROM game_tokens WHERE account_id=?", acc["id"])["n"] if acc else 0
        return {"name": (acc or p)["name"], "account": self.public_account(acc) if acc else None, "player": p,
                "online": name.lower() in {n.lower() for n in self.online()}, "devices": tokens,
                "commands": [{"id": c["id"], "action": c["action"], "args": json.loads(c["args"]), "status": c["status"],
                              "result": c["result"], "by": c["by_name"], "at": c["created_at"]} for c in cmds]}


# ==================== HTTP ====================

class Handler(BaseHTTPRequestHandler):
    panel = None  # set by serve()
    server_version = "PlebMcPanel"
    sys_version = ""

    def log_message(self, fmt, *args):
        sys.stderr.write("%s %s\n" % (self.address(), fmt % args))

    # ---------- helpers ----------

    def address(self):
        if self.panel and self.panel.cfg.trust_proxy:
            fwd = self.headers.get("X-Forwarded-For")
            if fwd:
                return fwd.split(",")[-1].strip()
        return self.client_address[0]

    def send_json(self, status, body, cookies=()):
        data = json.dumps(body).encode("utf-8")
        self.send_response(status)
        if self.path.startswith("/api/client/"):
            self.cors()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.security_headers()
        for c in cookies:
            self.send_header("Set-Cookie", c)
        self.end_headers()
        self.wfile.write(data)

    def cors(self):
        # the PlebMc client is an offline HTML file or a desktop app: any origin, never cookies
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Access-Control-Max-Age", "86400")

    def security_headers(self):
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Content-Security-Policy",
                         "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                         "script-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")

    def body(self, limit=64 * 1024):
        n = int(self.headers.get("Content-Length") or 0)
        if n > limit:
            raise ApiError(413, "Too big.")
        raw = self.rfile.read(n) if n else b"{}"
        try:
            v = json.loads(raw.decode("utf-8") or "{}")
        except (ValueError, UnicodeDecodeError):
            raise ApiError(400, "Send JSON.")
        if not isinstance(v, dict):
            raise ApiError(400, "Send a JSON object.")
        return v

    def cookie(self, name):
        c = SimpleCookie()
        try:
            c.load(self.headers.get("Cookie") or "")
        except Exception:
            return None
        return c[name].value if name in c else None

    def session_cookie(self, token, max_age):
        parts = ["pleb_session=%s" % token, "Path=/", "HttpOnly", "SameSite=Strict", "Max-Age=%d" % max_age]
        if self.panel.cfg.secure_cookies:
            parts.append("Secure")
        return "; ".join(parts)

    def web_user(self, required=True, role=None):
        acc = self.panel.web_account(self.cookie("pleb_session"))
        if not acc and required:
            raise ApiError(401, "Sign in first.")
        if acc and role and ROLES.index(acc["role"]) < ROLES.index(role):
            raise ApiError(403, "Admins only.")
        return acc

    def server_auth(self):
        key = self.panel.cfg.server_key
        got = self.headers.get("Authorization", "")
        got = got[7:] if got.startswith("Bearer ") else self.headers.get("X-API-Key", "")
        if not key or not hmac.compare_digest(got.encode("utf-8"), key.encode("utf-8")):
            raise ApiError(401, "Bad server key.")

    def same_origin(self):
        # browsers send this on fetch(); a form or image on another site can't set it
        if self.headers.get("X-Requested-With") != "PlebPanel":
            raise ApiError(403, "Bad request origin.")

    # ---------- routing ----------

    def do_GET(self):
        self.dispatch("GET")

    def do_POST(self):
        self.dispatch("POST")

    def do_OPTIONS(self):
        self.send_response(204)
        if self.path.startswith("/api/client/"):
            self.cors()
        self.send_header("Content-Length", "0")
        self.end_headers()

    def dispatch(self, method):
        url = urlparse(self.path)
        path = url.path
        try:
            if path.startswith("/api/server/"):
                self.server_auth()
                return self.server_api(method, path[len("/api/server/"):], url)
            if path.startswith("/api/client/") and method == "POST":
                return self.client_api(path[len("/api/client/"):])
            if path.startswith("/api/"):
                if method == "POST":
                    self.same_origin()
                return self.web_api(method, path[len("/api/"):], url)
            if method == "GET":
                return self.static(path)
            raise ApiError(404, "Not found.")
        except ApiError as e:
            self.send_json(e.status, {"ok": False, "error": e.message})
        except (BrokenPipeError, ConnectionResetError):
            pass
        except Exception as e:  # never leak internals
            sys.stderr.write("error: %r\n" % (e,))
            try:
                self.send_json(500, {"ok": False, "error": "Something went wrong."})
            except Exception:
                pass

    def static(self, path):
        if path in ("/", "/index.html") or not path.startswith("/static/"):
            f = os.path.join(STATIC, "index.html")
        else:
            f = os.path.normpath(os.path.join(STATIC, path[len("/static/"):]))
            if not f.startswith(STATIC + os.sep):
                raise ApiError(404, "Not found.")
        if not os.path.isfile(f):
            raise ApiError(404, "Not found.")
        with open(f, "rb") as fh:
            data = fh.read()
        self.send_response(200)
        self.send_header("Content-Type", (mimetypes.guess_type(f)[0] or "application/octet-stream") +
                         ("; charset=utf-8" if f.endswith((".html", ".js", ".css")) else ""))
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-cache")
        self.security_headers()
        self.end_headers()
        self.wfile.write(data)

    def web_api(self, method, route, url):
        p = self.panel
        q = parse_qs(url.query)
        if method == "GET" and route == "info":
            return self.send_json(200, {"site": p.cfg.site_name, "openRegistration": p.cfg.open_registration,
                                        "server": p.server_status()})
        if method == "POST" and route == "register":
            b = self.body()
            acc = p.web_register(str(b.get("name") or ""), b.get("code"), b.get("password"), self.address())
            token, acc = p.web_login(acc["name"], b.get("password"), self.address())
            return self.send_json(200, {"ok": True, "account": p.public_account(acc)},
                                  [self.session_cookie(token, SESSION_DAYS * 86400)])
        if method == "POST" and route == "login":
            b = self.body()
            token, acc = p.web_login(str(b.get("name") or ""), b.get("password"), self.address())
            return self.send_json(200, {"ok": True, "account": p.public_account(acc)},
                                  [self.session_cookie(token, SESSION_DAYS * 86400)])
        if method == "POST" and route == "logout":
            p.web_logout(self.cookie("pleb_session"))
            return self.send_json(200, {"ok": True}, [self.session_cookie("", 0)])
        if method == "GET" and route == "me":
            acc = self.web_user(required=False)
            if not acc:
                return self.send_json(200, {"ok": True, "account": None})
            devices = p.db.one("SELECT COUNT(*) n FROM game_tokens WHERE account_id=?", acc["id"])["n"]
            return self.send_json(200, {"ok": True, "account": p.public_account(acc), "player": p.player(acc["name"]),
                                        "catalog": p.catalog(), "devices": devices,
                                        "online": acc["name_key"] in {n.lower() for n in p.online()}})
        if method == "POST" and route == "me/password":
            acc = self.web_user()
            b = self.body()
            if not check_password(str(b.get("old") or ""), acc["password"]):
                raise ApiError(400, "Your current password is wrong.")
            p.set_password(acc, b.get("password"), acc["name"])
            token, acc = p.web_login(acc["name"], b.get("password"), self.address())
            return self.send_json(200, {"ok": True}, [self.session_cookie(token, SESSION_DAYS * 86400)])
        if method == "POST" and route == "me/signout":
            acc = self.web_user()
            p.signout_everywhere(acc, acc["name"], keep_web=self.cookie("pleb_session"))
            p.audit(acc["name"], "signout", acc["name"])
            return self.send_json(200, {"ok": True})
        # ---------- admin ----------
        if method == "GET" and route == "admin/players":
            self.web_user(role="admin")
            return self.send_json(200, {"ok": True, "players": p.admin_search((q.get("q") or [""])[0]),
                                        "server": p.server_status()})
        if method == "GET" and route == "admin/player":
            self.web_user(role="admin")
            return self.send_json(200, {"ok": True, "catalog": p.catalog(), **p.admin_player((q.get("name") or [""])[0])})
        if method == "POST" and route == "admin/command":
            actor = self.web_user(role="admin")
            return self.send_json(200, p.admin_command(actor, self.body()))
        if method == "POST" and route == "admin/account":
            actor = self.web_user(role="admin")
            return self.send_json(200, p.admin_account(actor, self.body()))
        if method == "GET" and route == "admin/audit":
            self.web_user(role="admin")
            return self.send_json(200, {"ok": True, "audit": p.db.all("SELECT * FROM audit ORDER BY id DESC LIMIT 200")})
        raise ApiError(404, "Not found.")

    def client_api(self, route):
        """The PlebMc client's start screen. Answers with a sign-in token, never a cookie."""
        p = self.panel
        b = self.body()
        name = str(b.get("name") or "").strip()
        if route == "login":
            return self.send_json(200, p.client_login(name, b.get("password"), self.address()))
        if route == "register":
            return self.send_json(200, p.client_register(name, b.get("password"), self.address()))
        if route == "check":
            return self.send_json(200, p.game_token(name, b.get("token")))
        if route == "logout":
            p.forget_game_token(b.get("token"))
            return self.send_json(200, {"ok": True})
        raise ApiError(404, "Not found.")

    def server_api(self, method, route, url):
        p = self.panel
        q = parse_qs(url.query)
        if method == "GET" and route == "account":
            return self.send_json(200, p.lookup((q.get("name") or [""])[0]))
        if method == "GET" and route == "commands":
            return self.send_json(200, {"commands": p.pending()})
        if method != "POST":
            raise ApiError(404, "Not found.")
        b = self.body(limit=16 * 1024 * 1024 if route == "sync" else 64 * 1024)
        if route == "login":
            return self.send_json(200, p.game_login(str(b.get("name") or ""), b.get("password"), bool(b.get("remember"))))
        if route == "token":
            return self.send_json(200, p.game_token(str(b.get("name") or ""), b.get("token")))
        if route == "forget":
            p.forget_game_token(b.get("token"))
            return self.send_json(200, {"ok": True})
        if route == "register":
            try:
                return self.send_json(200, p.server_register(str(b.get("name") or ""), b.get("password")))
            except ApiError as e:
                return self.send_json(200, {"ok": False, "error": e.message})
        if route == "claim":
            try:
                code = p.claim(str(b.get("name") or ""))
            except ApiError as e:
                return self.send_json(200, {"ok": False, "error": e.message})
            return self.send_json(200, {"ok": True, "code": code, "minutes": CLAIM_MINUTES,
                                        "url": "%s/#register?name=%s&code=%s" % (p.cfg.public_url, b.get("name"), code)})
        if route == "sync":
            return self.send_json(200, p.sync(b))
        if route == "ack":
            for a in b.get("results") or []:
                if isinstance(a, dict) and isinstance(a.get("id"), int):
                    p.ack(a["id"], bool(a.get("ok")), a.get("result"))
            return self.send_json(200, {"ok": True})
        raise ApiError(404, "Not found.")


def serve(panel):
    Handler.panel = panel
    httpd = ThreadingHTTPServer((panel.cfg.host, panel.cfg.port), Handler)
    httpd.daemon_threads = True
    print("PlebMc Panel on http://%s:%d (public URL %s)" % (panel.cfg.host, panel.cfg.port, panel.cfg.public_url),
          flush=True)
    httpd.serve_forever()


# ==================== command line ====================

def ask_password():
    if not sys.stdin.isatty():
        return sys.stdin.readline().rstrip("\n")
    while True:
        a = getpass.getpass("Password: ")
        if getpass.getpass("Again: ") == a:
            return a
        print("They don't match, try again.")


def main(argv):
    panel = Panel(Db(CFG.db))
    if not argv:
        if len(CFG.server_key) < 24:
            sys.exit("Set PANEL_SERVER_KEY to a long random secret (at least 24 characters), the same one as "
                     "accounts.server-key in PlebioAPI's config.yml. Example: openssl rand -hex 32")
        serve(panel)
        return
    cmd, args = argv[0], argv[1:]
    try:
        if cmd == "create-account" and args:
            role = args[1] if len(args) > 1 else "player"
            panel.create_account(args[0], ask_password(), role, "console")
            print("Created %s (%s)." % (args[0], role))
        elif cmd == "set-role" and len(args) == 2:
            acc = panel.account(args[0])
            if not acc or args[1] not in ROLES:
                sys.exit("No such account or role (player, admin, owner).")
            panel.db.run("UPDATE accounts SET role=? WHERE id=?", args[1], acc["id"])
            panel.audit("console", "role", "%s -> %s" % (acc["name"], args[1]))
            print("%s is now %s." % (acc["name"], args[1]))
        elif cmd == "reset-password" and args:
            acc = panel.account(args[0])
            if not acc:
                sys.exit("No such account.")
            panel.set_password(acc, ask_password(), "console")
            print("Password changed; %s was signed out everywhere." % acc["name"])
        elif cmd == "unlock" and args:
            acc = panel.account(args[0])
            if not acc:
                sys.exit("No such account.")
            panel.db.run("UPDATE accounts SET locked=0 WHERE id=?", acc["id"])
            print("Unlocked %s." % acc["name"])
        else:
            print(__doc__)
            sys.exit(2)
    except ApiError as e:
        sys.exit(e.message)


if __name__ == "__main__":
    main(sys.argv[1:])
