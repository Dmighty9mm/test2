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
  python3 app.py add-build KIND VERSION FILE [--publish]
                                         put a build on the Downloads page from the
                                         command line (kinds: see BUILD_KINDS)
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
import shutil
import tempfile
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
CATEGORIES = ("hat", "trail", "cloak", "wings")
RANK_NAME = re.compile(r"[a-z0-9_-]{1,36}")
PERM_NODE = re.compile(r"[A-Za-z0-9_.*-]{1,200}")
# builds the panel hosts: kind -> (name, who can download it, file types).
# "public" ones are on the Downloads page for everyone; "staff" ones only admins see.
BUILD_KINDS = {
    "windows": ("PlebClient for Windows", "public", (".zip", ".exe")),
    "wasm": ("PlebClient web (WASM)", "public", (".zip",)),
    "offline": ("PlebClient offline (one HTML file)", "public", (".html", ".zip")),
    "forge-1.8.9": ("PlebMc for Forge 1.8.9", "public", (".zip", ".jar")),
    "forge-1.21.1": ("PlebMc for Forge 1.21.1", "public", (".zip", ".jar")),
    "plebcolors": ("PlebColors plugin", "staff", (".zip", ".jar")),
    "plebioapi": ("PlebioAPI plugin", "staff", (".zip", ".jar")),
    "panel": ("PlebMc Panel", "staff", (".zip",)),
}
CLIENT_BUILD_KINDS = ("windows", "wasm", "offline", "forge-1.8.9", "forge-1.21.1")
BUILD_VERSION = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._+-]{0,31}$")


def clean_ranks(r):
    """What PlebioAPI reports about LuckPerms, trimmed to what the panel shows."""
    def s(v, n):
        return str(v if v is not None else "")[:n]
    groups = []
    for g in (r.get("groups") or [])[:500]:
        if not isinstance(g, dict) or not RANK_NAME.fullmatch(str(g.get("name") or "")):
            continue
        perms = []
        for n in (g.get("perms") or [])[:1000]:
            if isinstance(n, dict) and n.get("node"):
                perms.append({"node": s(n["node"], 200), "value": bool(n.get("value", True)),
                              "server": s(n.get("server"), 36), "world": s(n.get("world"), 36),
                              "expiry": int(n.get("expiry") or 0)})
        try:
            weight = int(g.get("weight") or 0)
        except (TypeError, ValueError):
            weight = 0
        groups.append({"name": g["name"], "display": s(g.get("display"), 80), "weight": weight,
                       "prefix": s(g.get("prefix"), 120), "suffix": s(g.get("suffix"), 120),
                       "parents": [s(x, 36) for x in (g.get("parents") or [])[:50]],
                       "members": int(g.get("members") or 0) if str(g.get("members") or "0").isdigit() else 0,
                       "perms": perms})
    groups.sort(key=lambda g: (-g["weight"], g["name"]))
    tracks = [{"name": s(t.get("name"), 36), "groups": [s(x, 36) for x in (t.get("groups") or [])[:50]]}
              for t in (r.get("tracks") or [])[:50] if isinstance(t, dict)]
    return {"connected": bool(r.get("ok")), "reason": s(r.get("reason"), 200), "groups": groups, "tracks": tracks,
            "at": int(time.time())}
ITEM_ID_RE = re.compile(r"^[a-z0-9_]{2,32}$")
VERSION_RE = re.compile(r"^[0-9]+(\.[0-9]+){0,3}$")
PAGE = 50
MAX_DESIGNS = 30
# a design's data: hat projects are small JSON; cloaks are a PNG (base64) up to 8 MB
MAX_DESIGN_BYTES = 12 * 1024 * 1024
MAX_THUMB_BYTES = 200 * 1024
DESIGN_KINDS = ("hat", "cloak", "wings")


def version_key(v):
    """'1.10.2' -> (1, 10, 2, 0) so versions compare like numbers."""
    parts = [int(x) for x in str(v or "0").split(".") if x.isdigit()][:4]
    return tuple(parts + [0] * (4 - len(parts)))


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
        # the PlebMc client's start screen can make accounts (it signs in before it can play)
        self.client_registration = env("PANEL_CLIENT_REGISTRATION", "1") == "1"
        # the website's Create account page works without a code from the game. Same rule as
        # the client by default: the client can't be used without an account, so there is no
        # way to get a code from the game first.
        self.open_registration = env("PANEL_OPEN_REGISTRATION", "1" if self.client_registration else "0") == "1"
        # accounts one address may make from the client per hour
        self.client_registrations_per_hour = int(env("PANEL_CLIENT_REGISTRATIONS_PER_HOUR", "3"))
        secure = env("PANEL_SECURE_COOKIES")
        self.secure_cookies = (secure == "1") if secure is not None else self.public_url.startswith("https://")
        # scrypt cost; tests turn it down
        self.scrypt_n = int(env("PANEL_SCRYPT_N", str(2 ** 14)))
        # where uploaded builds are kept (next to the database, so the Docker volume keeps them)
        db_dir = os.path.dirname(os.path.abspath(self.db)) if self.db != ":memory:" else tempfile.gettempdir()
        self.builds = env("PANEL_BUILDS", os.path.join(db_dir, "builds"))
        # the biggest build an owner can upload, in MB
        self.max_build_mb = int(env("PANEL_MAX_BUILD_MB", "500"))


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
CREATE INDEX IF NOT EXISTS audit_at ON audit(id);
CREATE TABLE IF NOT EXISTS designs (
  id INTEGER PRIMARY KEY,
  account_id INTEGER NOT NULL,
  kind TEXT NOT NULL,
  name TEXT NOT NULL,
  item_id TEXT NOT NULL,
  data TEXT NOT NULL,
  extra TEXT NOT NULL DEFAULT '{}',
  thumb TEXT NOT NULL DEFAULT '',
  public INTEGER NOT NULL DEFAULT 0,
  published TEXT,
  likes INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS designs_owner ON designs(account_id);
CREATE INDEX IF NOT EXISTS designs_public ON designs(public, updated_at);
CREATE TABLE IF NOT EXISTS design_likes (
  design_id INTEGER NOT NULL,
  account_id INTEGER NOT NULL,
  PRIMARY KEY (design_id, account_id)
);
CREATE TABLE IF NOT EXISTS notes (
  id INTEGER PRIMARY KEY,
  name_key TEXT NOT NULL,
  at INTEGER NOT NULL,
  by_name TEXT NOT NULL,
  text TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS notes_name ON notes(name_key);
CREATE TABLE IF NOT EXISTS builds (
  id INTEGER PRIMARY KEY,
  kind TEXT NOT NULL,
  version TEXT NOT NULL,
  filename TEXT NOT NULL,
  stored TEXT NOT NULL,
  size INTEGER NOT NULL,
  sha256 TEXT NOT NULL,
  notes TEXT NOT NULL DEFAULT '',
  by_name TEXT NOT NULL,
  uploaded_at INTEGER NOT NULL,
  live INTEGER NOT NULL DEFAULT 0,
  published_at INTEGER,
  downloads INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS builds_kind ON builds(kind, id);
CREATE TABLE IF NOT EXISTS client_seen (
  day INTEGER NOT NULL,
  version TEXT NOT NULL,
  platform TEXT NOT NULL,
  count INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (day, version, platform)
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
        if self.cfg.open_registration and not code:
            if self.signup_throttle.blocked("ip:" + str(address)):
                raise ApiError(429, "Too many new accounts from your network. Try again in an hour.")
            acc = self.create_account(name, password)
            self.signup_throttle.fail("ip:" + str(address))
            return acc
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
        if isinstance(body.get("ranks"), dict):
            self.db.set_kv("ranks", clean_ranks(body["ranks"]))
        if isinstance(players, list):
            ranks = self.db.get_kv("player_ranks", {})
            if body.get("full"):
                ranks = {}
            for pl in players:
                if isinstance(pl, dict) and pl.get("name") and isinstance(pl.get("rank"), str):
                    ranks[str(pl["name"]).lower()[:32]] = pl["rank"][:36]
            if len(ranks) > 50000:
                ranks = dict(list(ranks.items())[-50000:])
            self.db.set_kv("player_ranks", ranks)
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
            rows, size = [], 0
            for r in c.execute("SELECT * FROM commands WHERE status='pending' ORDER BY id LIMIT 100"):
                # big shop uploads go one at a time
                if rows and size + len(r["args"]) > 12 * 1024 * 1024:
                    break
                rows.append(dict(r))
                size += len(r["args"])
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
        if action == "coinsall":
            try:
                amount = int(body.get("amount"))
            except (TypeError, ValueError):
                amount = -1
            if amount <= 0 or amount > 10 ** 9:
                raise ApiError(400, "Give a whole number above 0.")
            cid = self.queue(actor["name"], "*", "coinsall", [amount])
            self.audit(actor["name"], "queue-coinsall", "%d to everyone online" % amount)
            return {"ok": True, "id": cid}
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

    # ---------- players list (paged) ----------

    PLAYER_FILTERS = {
        "all": "1=1",
        "online": "n.name_key IN (SELECT value FROM json_each(?))",
        "accounts": "a.id IS NOT NULL",
        "noaccount": "a.id IS NULL",
        "staff": "a.role IN ('admin','owner')",
        "locked": "a.locked=1",
    }
    PLAYER_SORTS = {
        "coins": "COALESCE(p.coins,-1) DESC, n.name_key",
        "name": "n.name_key",
        "recent": "MAX(COALESCE(a.last_login,0), COALESCE(p.updated_at,0)) DESC, n.name_key",
        "new": "COALESCE(a.created_at,0) DESC, n.name_key",
    }

    def admin_players(self, q="", filt="all", sort="coins", offset=0, limit=PAGE):
        """One page of players (accounts and players who joined), so a big network never loads all at once."""
        q = str(q or "").lower()[:32]
        like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        filt = filt if filt in self.PLAYER_FILTERS else "all"
        sort = sort if sort in self.PLAYER_SORTS else "coins"
        try:
            offset = max(0, int(offset))
        except (TypeError, ValueError):
            offset = 0
        limit = max(1, min(100, int(limit)))
        online = [n.lower() for n in self.online()]
        base = ("FROM (SELECT name_key FROM accounts UNION SELECT name_key FROM players) n "
                "LEFT JOIN accounts a ON a.name_key=n.name_key "
                "LEFT JOIN (SELECT name_key, MAX(name) name, MAX(coins) coins, MAX(owned) owned, MAX(updated_at) updated_at "
                "FROM players GROUP BY name_key) p ON p.name_key=n.name_key "
                "WHERE n.name_key LIKE ? ESCAPE '\\' AND " + self.PLAYER_FILTERS[filt])
        args = [like] + ([json.dumps(online)] if filt == "online" else [])
        total = self.db.one("SELECT COUNT(*) n " + base, *args)["n"]
        rows = self.db.all("SELECT n.name_key, a.name aname, a.role, a.locked, a.id aid, a.last_login, a.created_at, "
                           "p.name pname, p.coins, p.owned, p.updated_at " + base + " ORDER BY " +
                           self.PLAYER_SORTS[sort] + " LIMIT ? OFFSET ?", *(args + [limit, offset]))
        on = set(online)
        out = [{"name": r["aname"] or r["pname"] or r["name_key"], "account": r["aid"] is not None,
                "role": r["role"], "locked": bool(r["locked"]), "coins": r["coins"],
                "items": len(json.loads(r["owned"])) if r["owned"] else 0, "online": r["name_key"] in on,
                "lastSeen": max(r["last_login"] or 0, r["updated_at"] or 0) or None, "created": r["created_at"]}
               for r in rows]
        return {"players": out, "total": total, "offset": offset, "more": offset + len(out) < total}

    # ---------- dashboard, leaderboard, shop ----------

    def dashboard(self):
        now = self.now()
        day = 86400
        c = self.db.one("SELECT COUNT(*) accounts, SUM(role<>'player') staff, SUM(locked) locked, "
                        "SUM(created_at>=?) new_day, SUM(created_at>=?) new_week, SUM(last_login>=?) active_day, "
                        "SUM(last_login>=?) active_week FROM accounts", now - day, now - 7 * day, now - day, now - 7 * day)
        p = self.db.one("SELECT COUNT(DISTINCT name_key) players, COALESCE(SUM(coins),0) coins, "
                        "COALESCE(MAX(coins),0) richest FROM players")
        signups = []
        start = (now // day - 13) * day
        counts = {r["d"]: r["n"] for r in self.db.all(
            "SELECT (created_at/86400)*86400 d, COUNT(*) n FROM accounts WHERE created_at>=? GROUP BY d", start)}
        for i in range(14):
            d = start + i * day
            signups.append({"day": d, "count": counts.get(d, 0)})
        owned = {}
        for r in self.db.all("SELECT owned FROM players"):
            for item in json.loads(r["owned"]):
                owned[item] = owned.get(item, 0) + 1
        names = {i["id"]: i for i in self.catalog()}
        popular = sorted(owned.items(), key=lambda kv: -kv[1])[:8]
        return {"accounts": c["accounts"] or 0, "staff": c["staff"] or 0, "locked": c["locked"] or 0,
                "newDay": c["new_day"] or 0, "newWeek": c["new_week"] or 0,
                "activeDay": c["active_day"] or 0, "activeWeek": c["active_week"] or 0,
                "players": p["players"] or 0, "coins": p["coins"] or 0, "richest": p["richest"] or 0,
                "signups": signups, "popular": [{"id": k, "name": (names.get(k) or {}).get("name", k),
                                                  "category": (names.get(k) or {}).get("category", ""), "owners": v}
                                                 for k, v in popular],
                "clients": self.client_versions(), "server": self.server_status(),
                "designs": self.db.one("SELECT COUNT(*) n FROM designs")["n"]}

    def leaderboard(self, offset=0, limit=10, q=""):
        """The richest players (at most the top 100), or with q the players whose name has q, with their rank."""
        try:
            offset = max(0, min(100, int(offset)))
        except (TypeError, ValueError):
            offset = 0
        try:
            limit = max(1, min(25, int(limit)))
        except (TypeError, ValueError):
            limit = 10
        ranked = ("SELECT name, name_key, coins, streak, owned, ROW_NUMBER() OVER (ORDER BY coins DESC, name_key) rank FROM "
                  "(SELECT MAX(name) name, name_key, MAX(coins) coins, MAX(streak) streak, MAX(owned) owned FROM players "
                  "GROUP BY name_key)")
        q = str(q or "").strip().lower()[:16]
        if q:
            like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            rows = self.db.all("SELECT * FROM (" + ranked + ") WHERE name_key LIKE ? ESCAPE '\\' "
                               "ORDER BY (name_key = ?) DESC, rank LIMIT ?", like, q, limit)
            total = len(rows)
        else:
            total = self.db.one("SELECT COUNT(DISTINCT name_key) n FROM players")["n"]
            rows = self.db.all(ranked + " ORDER BY rank LIMIT ? OFFSET ?", limit, offset)
        return {"rows": [{"rank": r["rank"], "name": r["name"], "coins": r["coins"], "streak": r["streak"],
                          "items": len(json.loads(r["owned"] or "[]"))} for r in rows],
                "total": total, "offset": offset, "more": False}

    def public_info(self):
        st = self.server_status()
        return {"site": self.cfg.site_name, "openRegistration": self.cfg.open_registration,
                "server": {"connected": st["connected"], "online": st["online"], "currency": st["currency"]},
                "client": self.client_release(), "announcement": self.db.get_kv("announcement", None),
                "downloads": self.downloads()}

    # ---------- player notes (admins) ----------

    def notes(self, name):
        return self.db.all("SELECT id, at, by_name, text FROM notes WHERE name_key=? ORDER BY id DESC LIMIT 50",
                           str(name or "").lower())

    def add_note(self, actor, name, text):
        text = str(text or "").strip()[:1000]
        if name_problem(str(name or "")) or not text:
            raise ApiError(400, "Write a note first.")
        self.db.run("INSERT INTO notes(name_key,at,by_name,text) VALUES(?,?,?,?)", name.lower(), self.now(),
                    actor["name"], text)
        self.audit(actor["name"], "note", name)

    def delete_note(self, actor, nid):
        n = self.db.one("SELECT * FROM notes WHERE id=?", nid)
        if not n:
            raise ApiError(404, "No such note.")
        if n["by_name"].lower() != actor["name_key"] and actor["role"] != "owner":
            raise ApiError(403, "Only the note's author or an owner can delete it.")
        self.db.run("DELETE FROM notes WHERE id=?", nid)

    # ---------- announcements ----------

    def announce(self, actor, body):
        text = str(body.get("text") or "").strip()[:200]
        if not text:
            raise ApiError(400, "Write a message first.")
        targets = []
        if body.get("site"):
            self.db.set_kv("announcement", {"text": text, "by": actor["name"], "at": self.now()})
            targets.append("website")
        if body.get("game"):
            self.queue(actor["name"], "*", "broadcast", [text])
            targets.append("game")
        if not targets:
            raise ApiError(400, "Pick the website, the game or both.")
        self.audit(actor["name"], "announce", "%s: %s" % ("+".join(targets), text))
        return {"ok": True}

    def clear_announcement(self, actor):
        self.db.set_kv("announcement", None)
        self.audit(actor["name"], "announce", "cleared the website banner")

    # ---------- client releases and the update checker ----------

    def client_release(self):
        r = self.db.get_kv("client_release", None) or {}
        return {"latest": r.get("latest") or "", "minimum": r.get("minimum") or "", "url": r.get("url") or "",
                "message": r.get("message") or "", "at": r.get("at")}

    def set_client_release(self, actor, body):
        latest = str(body.get("latest") or "").strip()
        minimum = str(body.get("minimum") or "").strip()
        url = str(body.get("url") or "").strip()[:300]
        message = str(body.get("message") or "").strip()[:200]
        for v in (latest, minimum):
            if v and not VERSION_RE.match(v):
                raise ApiError(400, "Versions look like 2.1 or 2.1.3.")
        if minimum and latest and version_key(minimum) > version_key(latest):
            raise ApiError(400, "The minimum can't be newer than the latest version.")
        if url and not re.match(r"^https?://", url):
            raise ApiError(400, "The download link must start with https://")
        self.db.set_kv("client_release", {"latest": latest, "minimum": minimum, "url": url, "message": message,
                                          "at": self.now()})
        self.audit(actor["name"], "client-release", "latest %s, minimum %s" % (latest or "-", minimum or "-"))
        return {"ok": True, "client": self.client_release()}

    def client_version(self, version, platform):
        """The client asks at start: is there a newer version? Also counts which versions are in use."""
        version = str(version or "")[:20]
        platform = re.sub(r"[^a-z0-9_-]", "", str(platform or "").lower())[:16] or "unknown"
        if VERSION_RE.match(version):
            self.db.run("INSERT INTO client_seen(day,version,platform,count) VALUES(?,?,?,1) "
                        "ON CONFLICT(day,version,platform) DO UPDATE SET count=count+1",
                        self.now() // 86400 * 86400, version, platform)
        r = self.client_release()
        have = version_key(version)
        outdated = bool(r["latest"]) and VERSION_RE.match(version) is not None and have < version_key(r["latest"])
        required = bool(r["minimum"]) and VERSION_RE.match(version) is not None and have < version_key(r["minimum"])
        return {"ok": True, "latest": r["latest"], "minimum": r["minimum"], "url": r["url"] or self.cfg.public_url,
                "message": r["message"], "outdated": outdated, "required": required}

    def client_versions(self):
        since = self.now() // 86400 * 86400 - 6 * 86400
        rows = self.db.all("SELECT version, platform, SUM(count) n FROM client_seen WHERE day>=? GROUP BY version, platform",
                           since)
        rows.sort(key=lambda r: (version_key(r["version"]), r["n"]), reverse=True)
        return rows[:30]

    # ---------- builds (the Downloads page) ----------

    @staticmethod
    def build_out(b):
        name, who, _ = BUILD_KINDS.get(b["kind"], (b["kind"], "staff", ()))
        return {"id": b["id"], "kind": b["kind"], "kindName": name, "audience": who, "version": b["version"],
                "filename": b["filename"], "size": b["size"], "sha256": b["sha256"], "notes": b["notes"],
                "by": b["by_name"], "uploadedAt": b["uploaded_at"], "live": bool(b["live"]),
                "publishedAt": b["published_at"], "downloads": b["downloads"],
                "url": "/download/" + b["kind"] if b["live"] else "/download/build/%d" % b["id"]}

    def downloads(self, staff=False):
        """The published build of each kind: everyone sees the client ones, admins also the plugins."""
        rows = self.db.all("SELECT * FROM builds WHERE live=1")
        order = list(BUILD_KINDS)
        rows = [r for r in rows if r["kind"] in BUILD_KINDS and (staff or BUILD_KINDS[r["kind"]][1] == "public")]
        rows.sort(key=lambda r: order.index(r["kind"]))
        return [self.build_out(r) for r in rows]

    def admin_builds(self):
        rows = self.db.all("SELECT * FROM builds ORDER BY id DESC LIMIT 300")
        kinds = [{"kind": k, "name": v[0], "audience": v[1], "types": list(v[2])} for k, v in BUILD_KINDS.items()]
        return {"kinds": kinds, "builds": [self.build_out(r) for r in rows], "maxMb": self.cfg.max_build_mb}

    def add_build(self, actor_name, kind, version, filename, src, notes=""):
        """Stores a build. src is a readable file object (the upload) or a path; it is copied in."""
        if kind not in BUILD_KINDS:
            raise ApiError(400, "Pick what this build is.")
        version = str(version or "").strip()
        if not BUILD_VERSION.match(version):
            raise ApiError(400, "Give a version like 2.0 or 2.0.1.")
        filename = os.path.basename(str(filename or "").replace("\\", "/")).strip()
        filename = re.sub(r"[^A-Za-z0-9._ +()-]", "_", filename)[:120]
        if not filename or not filename.lower().endswith(BUILD_KINDS[kind][2]):
            raise ApiError(400, "%s must be a %s file." % (BUILD_KINDS[kind][0], " or ".join(BUILD_KINDS[kind][2])))
        notes = str(notes or "").strip()[:300]
        os.makedirs(self.cfg.builds, exist_ok=True)
        limit = self.cfg.max_build_mb * 1024 * 1024
        fd, tmp = tempfile.mkstemp(prefix=".upload-", dir=self.cfg.builds)
        digest = hashlib.sha256()
        size = 0
        try:
            with os.fdopen(fd, "wb") as out:
                if isinstance(src, str):
                    src = open(src, "rb")
                    close = True
                else:
                    close = False
                try:
                    reader, remaining = (src[0], src[1]) if isinstance(src, tuple) else (src, None)
                    while True:
                        want = 1024 * 1024 if remaining is None else min(1024 * 1024, remaining)
                        if want <= 0:
                            break
                        chunk = reader.read(want)
                        if not chunk:
                            break
                        size += len(chunk)
                        if size > limit:
                            raise ApiError(413, "That build is bigger than %d MB (PANEL_MAX_BUILD_MB)." % self.cfg.max_build_mb)
                        digest.update(chunk)
                        out.write(chunk)
                        if remaining is not None:
                            remaining -= len(chunk)
                    if remaining:
                        raise ApiError(400, "The upload was cut off. Try again.")
                finally:
                    if close:
                        src.close()
            if size == 0:
                raise ApiError(400, "That file is empty.")
            cur = self.db.run("INSERT INTO builds(kind,version,filename,stored,size,sha256,notes,by_name,uploaded_at) "
                              "VALUES(?,?,?,?,?,?,?,?,?)", kind, version, filename, "", size, digest.hexdigest(), notes,
                              actor_name, self.now())
            bid = cur.lastrowid
            stored = "%d-%s" % (bid, filename.replace(" ", "_"))
            os.replace(tmp, os.path.join(self.cfg.builds, stored))
            tmp = None
            self.db.run("UPDATE builds SET stored=? WHERE id=?", stored, bid)
        finally:
            if tmp and os.path.exists(tmp):
                os.remove(tmp)
        self.audit(actor_name, "build-upload", "%s %s (%s, %d bytes)" % (kind, version, filename, size))
        return {"ok": True, "build": self.build_out(self.db.one("SELECT * FROM builds WHERE id=?", bid))}

    def build(self, bid):
        b = self.db.one("SELECT * FROM builds WHERE id=?", int(bid or 0))
        if not b:
            raise ApiError(404, "No such build.")
        return b

    def publish_build(self, actor, bid, notify=False):
        """Makes this build the one on the Downloads page for its kind (the one before is kept, unpublished)."""
        b = self.build(bid)
        with self.db.tx():
            self.db.run("UPDATE builds SET live=0 WHERE kind=?", b["kind"])
            self.db.run("UPDATE builds SET live=1, published_at=? WHERE id=?", self.now(), b["id"])
        detail = "%s %s" % (b["kind"], b["version"])
        if notify and b["kind"] in CLIENT_BUILD_KINDS:
            if not VERSION_RE.match(b["version"]):
                raise ApiError(400, "Published, but clients weren't told: the update checker needs a version like 2.1.")
            r = self.client_release()
            if r["minimum"] and version_key(r["minimum"]) > version_key(b["version"]):
                raise ApiError(400, "Published, but clients weren't told: Oldest allowed is newer than this version.")
            self.db.set_kv("client_release", {"latest": b["version"], "minimum": r["minimum"],
                                              "url": self.cfg.public_url + "/#downloads", "message": r["message"],
                                              "at": self.now()})
            detail += ", clients told to update"
        self.audit(actor["name"], "build-publish", detail)
        return {"ok": True}

    def unpublish_build(self, actor, bid):
        b = self.build(bid)
        self.db.run("UPDATE builds SET live=0 WHERE id=?", b["id"])
        self.audit(actor["name"], "build-unpublish", "%s %s" % (b["kind"], b["version"]))
        return {"ok": True}

    def delete_build(self, actor, bid):
        b = self.build(bid)
        if b["live"]:
            raise ApiError(400, "Unpublish it first, or publish another build in its place.")
        path = os.path.join(self.cfg.builds, b["stored"])
        self.db.run("DELETE FROM builds WHERE id=?", b["id"])
        if b["stored"] and os.path.isfile(path):
            os.remove(path)
        self.audit(actor["name"], "build-delete", "%s %s (%s)" % (b["kind"], b["version"], b["filename"]))
        return {"ok": True}

    def build_file(self, b):
        path = os.path.join(self.cfg.builds, b["stored"]) if b and b["stored"] else None
        if not path or not os.path.isfile(path):
            raise ApiError(404, "That build's file is missing on the panel.")
        return path

    # ---------- designs (hat and cloak makers) ----------

    def design_out(self, d, full=False, viewer=None):
        out = {"id": d["id"], "kind": d["kind"], "name": d["name"], "itemId": d["item_id"], "thumb": d["thumb"],
               "public": bool(d["public"]), "published": d["published"], "likes": d["likes"],
               "created": d["created_at"], "updated": d["updated_at"], "extra": json.loads(d["extra"] or "{}")}
        if "author" in d:
            out["author"] = d["author"]
        if viewer:
            out["mine"] = d["account_id"] == viewer["id"]
            out["liked"] = self.db.one("SELECT 1 x FROM design_likes WHERE design_id=? AND account_id=?",
                                       d["id"], viewer["id"]) is not None
        if full:
            out["data"] = d["data"]
        return out

    def my_designs(self, acc):
        rows = self.db.all("SELECT d.*, a.name author FROM designs d JOIN accounts a ON a.id=d.account_id "
                           "WHERE d.account_id=? ORDER BY d.updated_at DESC", acc["id"])
        return [self.design_out(d, viewer=acc) for d in rows]

    def get_design(self, did, viewer):
        d = self.db.one("SELECT d.*, a.name author FROM designs d JOIN accounts a ON a.id=d.account_id WHERE d.id=?", did)
        if not d or not (d["public"] or (viewer and (d["account_id"] == viewer["id"] or viewer["role"] != "player"))):
            raise ApiError(404, "That design doesn't exist or is private.")
        return self.design_out(d, full=True, viewer=viewer)

    def save_design(self, acc, body):
        kind = str(body.get("kind") or "")
        if kind not in DESIGN_KINDS:
            raise ApiError(400, "Unknown design type.")
        name = str(body.get("name") or "").strip()[:40] or "Untitled"
        item_id = re.sub(r"[^a-z0-9_]+", "_", str(body.get("itemId") or name).lower()).strip("_")[:32] or "my_" + kind
        data = body.get("data")
        if not isinstance(data, str) or not data:
            raise ApiError(400, "Nothing to save.")
        if len(data) > MAX_DESIGN_BYTES:
            raise ApiError(413, "That design is too big to save.")
        if kind == "cloak" and not data.startswith("data:image/png;base64,"):
            raise ApiError(400, "Cloaks are saved as a PNG.")
        if kind in ("hat", "wings"):
            try:
                v = json.loads(data)
                assert isinstance(v.get("vox"), list)
            except Exception:
                raise ApiError(400, "That isn't a Cosmetic Maker project.")
        thumb = str(body.get("thumb") or "")
        if thumb and (not thumb.startswith("data:image/png;base64,") or len(thumb) > MAX_THUMB_BYTES):
            thumb = ""
        extra = body.get("extra") if isinstance(body.get("extra"), dict) else {}
        extra = {k: extra[k] for k in ("frametime", "description", "price", "frames", "resolution") if k in extra}
        public = 0  # no gallery any more
        now = self.now()
        did = body.get("id")
        if did:
            d = self.db.one("SELECT * FROM designs WHERE id=?", did)
            if not d or d["account_id"] != acc["id"]:
                raise ApiError(404, "That design isn't yours.")
            self.db.run("UPDATE designs SET name=?, item_id=?, data=?, extra=?, thumb=?, public=?, updated_at=? WHERE id=?",
                        name, item_id, data, json.dumps(extra), thumb, public, now, did)
        else:
            n = self.db.one("SELECT COUNT(*) n FROM designs WHERE account_id=?", acc["id"])["n"]
            if n >= MAX_DESIGNS:
                raise ApiError(400, "You have %d designs, the most you can keep. Delete one first." % MAX_DESIGNS)
            did = self.db.run("INSERT INTO designs(account_id,kind,name,item_id,data,extra,thumb,public,created_at,updated_at) "
                              "VALUES(?,?,?,?,?,?,?,?,?,?)", acc["id"], kind, name, item_id, data, json.dumps(extra),
                              thumb, public, now, now).lastrowid
        return {"ok": True, "id": did}

    def delete_design(self, acc, did):
        d = self.db.one("SELECT * FROM designs WHERE id=?", did)
        if not d or (d["account_id"] != acc["id"] and acc["role"] == "player"):
            raise ApiError(404, "That design isn't yours.")
        self.db.run("DELETE FROM designs WHERE id=?", did)
        self.db.run("DELETE FROM design_likes WHERE design_id=?", did)
        if d["account_id"] != acc["id"]:
            self.audit(acc["name"], "delete-design", "%s (#%d)" % (d["name"], did))
        return {"ok": True}

    def publish(self, actor, body):
        """Adds a hat or cloak to the server's shop: PlebioAPI saves the file, adds the item and reloads."""
        kind = str(body.get("kind") or "")
        item_id = str(body.get("itemId") or "")
        name = str(body.get("name") or "").strip()[:40]
        if kind not in DESIGN_KINDS:
            raise ApiError(400, "Pick hat, wings or cloak.")
        if not ITEM_ID_RE.match(item_id):
            raise ApiError(400, "Item ids are 2-32 lowercase letters, digits or _.")
        if any(i["id"] == item_id for i in self.catalog()):
            raise ApiError(409, "The shop already has an item called %s. Pick another id." % item_id)
        if not name:
            raise ApiError(400, "Give it a name.")
        try:
            price = int(body.get("price"))
        except (TypeError, ValueError):
            raise ApiError(400, "Price is a whole number (-1 = not for sale).")
        if price < -1 or price > 10 ** 9:
            raise ApiError(400, "Price is a whole number (-1 = not for sale).")
        data = str(body.get("file") or "")
        if kind == "cloak":
            prefix = "data:image/png;base64,"
            if not data.startswith(prefix):
                raise ApiError(400, "Cloaks are a PNG.")
            raw = data[len(prefix):]
        else:
            try:
                json.loads(data)
            except ValueError:
                raise ApiError(400, "Hats and wings are a .bbmodel (JSON).")
            raw = base64.b64encode(data.encode("utf-8")).decode("ascii")
        if len(raw) > MAX_DESIGN_BYTES:
            raise ApiError(413, "That file is too big for the shop (8 MB at most).")
        item = {"id": item_id, "name": name, "category": kind, "price": price,
                "description": str(body.get("description") or "").strip()[:100]}
        for k in ("collection", "creator"):
            v = str(body.get(k) or "").strip()[:40]
            if v:
                item[k] = v
        if kind == "cloak":
            try:
                ft = int(body.get("frametime") or 0)
            except (TypeError, ValueError):
                ft = 0
            if 20 <= ft <= 2000:
                item["frametime"] = ft
        cid = self.queue(actor["name"], "*", "additem", [json.dumps(item), raw])
        if body.get("designId"):
            self.db.run("UPDATE designs SET published=? WHERE id=?", item_id, body.get("designId"))
        self.audit(actor["name"], "publish", "%s %s (%s, %d)" % (kind, item_id, name, price))
        return {"ok": True, "id": cid}

    def audit_page(self, q="", before=None):
        args = []
        where = "1=1"
        if q:
            like = "%" + str(q)[:40].replace("%", "") + "%"
            where = "(actor LIKE ? OR action LIKE ? OR detail LIKE ?)"
            args += [like, like, like]
        if before:
            where += " AND id<?"
            args.append(int(before))
        rows = self.db.all("SELECT * FROM audit WHERE " + where + " ORDER BY id DESC LIMIT ?", *(args + [PAGE + 1]))
        return {"audit": rows[:PAGE], "more": len(rows) > PAGE}

    # ---------- LuckPerms ranks (read from and changed through PlebioAPI) ----------

    def ranks(self):
        r = self.db.get_kv("ranks", None)
        if not r:
            return {"connected": False, "reason": "The proxy hasn't sent any ranks yet.", "groups": [], "tracks": []}
        return r

    def rank_of(self, name):
        return self.db.get_kv("player_ranks", {}).get(str(name or "").lower())

    def rank_change(self, actor, body):
        """Owners change LuckPerms groups; PlebioAPI applies it on the proxy within a few seconds."""
        op = str(body.get("op") or "")
        group = str(body.get("group") or "").lower()
        if op == "player":
            name = str(body.get("name") or "")
            if name_problem(name):
                raise ApiError(400, "Pick a player.")
            mode = str(body.get("mode") or "set")
            if mode not in ("set", "add", "remove"):
                raise ApiError(400, "Choose set, add or remove.")
            if not RANK_NAME.fullmatch(group):
                raise ApiError(400, "Pick a rank.")
            args = ["player", mode, group]
            target = name
        else:
            if not RANK_NAME.fullmatch(group):
                raise ApiError(400, "Rank names are 1-36 letters, numbers, - or _ (lowercase).")
            target = group
            if op in ("create", "delete"):
                if op == "delete" and group == "default":
                    raise ApiError(400, "The default rank can't be deleted.")
                args = [op]
            elif op in ("prefix", "suffix", "display"):
                text = str(body.get("text") or "")
                if len(text) > 80 or "\n" in text:
                    raise ApiError(400, "Keep it to one line of 80 characters.")
                args = [op, text]
            elif op == "weight":
                try:
                    w = int(body.get("weight"))
                except (TypeError, ValueError):
                    raise ApiError(400, "Weight is a whole number.")
                if not -100000 <= w <= 100000:
                    raise ApiError(400, "Weight is between -100000 and 100000.")
                args = [op, w]
            elif op in ("perm", "unperm"):
                node = str(body.get("node") or "").strip()
                if not PERM_NODE.fullmatch(node):
                    raise ApiError(400, "A permission is letters, numbers and . - _ * (like essentials.fly).")
                server = str(body.get("server") or "").strip().lower()
                if server and not RANK_NAME.fullmatch(server):
                    raise ApiError(400, "Server names are letters, numbers, - or _.")
                args = [op, node, bool(body.get("value", True)), server]
            elif op in ("parent", "unparent"):
                parent = str(body.get("parent") or "").lower()
                if not RANK_NAME.fullmatch(parent) or parent == group:
                    raise ApiError(400, "Pick another rank to inherit from.")
                args = [op, parent]
            else:
                raise ApiError(400, "Unknown change.")
        cid = self.queue(actor["name"], target, "rank", args)
        self.audit(actor["name"], "queue-rank", "%s %s" % (target, json.dumps(args)))
        return {"ok": True, "id": cid}

    def admin_player(self, name):
        acc = self.account(name)
        p = self.player(name)
        if not acc and not p:
            raise ApiError(404, "Nobody called %s has joined or registered." % name)
        cmds = self.db.all("SELECT * FROM commands WHERE target=? COLLATE NOCASE ORDER BY id DESC LIMIT 20", name)
        tokens = self.db.one("SELECT COUNT(*) n FROM game_tokens WHERE account_id=?", acc["id"])["n"] if acc else 0
        return {"name": (acc or p)["name"], "account": self.public_account(acc) if acc else None, "player": p,
                "online": name.lower() in {n.lower() for n in self.online()}, "devices": tokens,
                "rank": self.rank_of(name),
                "commands": [{"id": c["id"], "action": c["action"], "args": json.loads(c["args"]), "status": c["status"],
                              "result": c["result"], "by": c["by_name"], "at": c["created_at"]} for c in cmds]}


_STATIC_REF = re.compile(rb'(src|href)="(/static/[A-Za-z0-9_./-]+\.(?:js|css|png))"')


def versioned(html):
    """Adds ?v=<file's change time> to /static/ links in a page, so browsers and caches
    (Cloudflare, nginx) always fetch a new app.js or style.css after an update."""
    def add(m):
        path = os.path.normpath(os.path.join(STATIC, m.group(2)[len(b"/static/"):].decode()))
        try:
            v = int(os.path.getmtime(path)) if path.startswith(STATIC + os.sep) else 0
        except OSError:
            v = 0
        return m.group(1) + b'="' + m.group(2) + b"?v=" + str(v).encode() + b'"'
    return _STATIC_REF.sub(add, html)


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
                         "default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; worker-src 'self' blob:; "
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
            raise ApiError(403, "Owners only." if role == "owner" else "Admins only.")
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
            if method == "GET" and path.startswith("/download/"):
                return self.download(path[len("/download/"):])
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
        if f.endswith(".html"):
            data = versioned(data)
        self.send_response(200)
        self.send_header("Content-Type", (mimetypes.guess_type(f)[0] or "application/octet-stream") +
                         ("; charset=utf-8" if f.endswith((".html", ".js", ".css")) else ""))
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-cache")
        self.security_headers()
        self.end_headers()
        self.wfile.write(data)

    def download(self, rest):
        """/download/<kind> = the published build; /download/build/<id> = any build (admins)."""
        p = self.panel
        if rest.startswith("build/"):
            self.web_user(role="admin")
            try:
                b = p.build(int(rest[len("build/"):]))
            except ValueError:
                raise ApiError(404, "Not found.")
        else:
            kind = rest.strip("/")
            if kind not in BUILD_KINDS:
                raise ApiError(404, "Not found.")
            if BUILD_KINDS[kind][1] != "public":
                self.web_user(role="admin")
            b = p.db.one("SELECT * FROM builds WHERE kind=? AND live=1", kind)
            if not b:
                raise ApiError(404, "Nothing is published for that yet.")
        path = p.build_file(b)
        p.db.run("UPDATE builds SET downloads=downloads+1 WHERE id=?", b["id"])
        size = os.path.getsize(path)
        self.send_response(200)
        # always a download, never shown in the page (the offline client is an HTML file)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Content-Disposition", 'attachment; filename="%s"' % b["filename"].replace('"', ""))
        self.send_header("Content-Length", str(size))
        self.send_header("Cache-Control", "no-cache")
        self.send_header("X-Checksum-SHA256", b["sha256"])
        self.security_headers()
        self.end_headers()
        with open(path, "rb") as fh:
            shutil.copyfileobj(fh, self.wfile, 1024 * 1024)

    def web_api(self, method, route, url):
        p = self.panel
        q = parse_qs(url.query)
        one = lambda k, d="": (q.get(k) or [d])[0]
        if method == "GET" and route == "info":
            return self.send_json(200, {"ok": True, **p.public_info()})
        if method == "GET" and route == "leaderboard":
            return self.send_json(200, {"ok": True, **p.leaderboard(one("offset", "0"), one("limit", "10"), one("q"))})
        if method == "GET" and route == "shop":
            return self.send_json(200, {"ok": True, "catalog": p.catalog(), "currency": p.server_status()["currency"]})
        # ---------- designs (hat and cloak makers) ----------
        # the makers are for admins (they make what goes in the shop)
        if method == "GET" and route == "designs/get":
            viewer = self.web_user(role="admin")
            try:
                did = int(one("id", "0"))
            except ValueError:
                did = 0
            return self.send_json(200, {"ok": True, "design": p.get_design(did, viewer)})
        if method == "GET" and route == "designs/mine":
            return self.send_json(200, {"ok": True, "designs": p.my_designs(self.web_user(role="admin"))})
        if method == "POST" and route == "designs/save":
            acc = self.web_user(role="admin")
            return self.send_json(200, p.save_design(acc, self.body(limit=MAX_DESIGN_BYTES + 400 * 1024)))
        if method == "POST" and route == "designs/delete":
            acc = self.web_user(role="admin")
            return self.send_json(200, p.delete_design(acc, int(self.body().get("id") or 0)))
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
            return self.send_json(200, {"ok": True, **p.admin_players(one("q"), one("filter", "all"), one("sort", "coins"),
                                                                        one("offset", "0")),
                                        "server": p.server_status()})
        if method == "GET" and route == "admin/dashboard":
            self.web_user(role="admin")
            return self.send_json(200, {"ok": True, **p.dashboard()})
        if method == "POST" and route == "admin/note":
            actor = self.web_user(role="admin")
            b = self.body()
            if b.get("delete"):
                p.delete_note(actor, int(b.get("delete")))
            else:
                p.add_note(actor, str(b.get("name") or ""), b.get("text"))
            return self.send_json(200, {"ok": True, "notes": p.notes(str(b.get("name") or ""))})
        if method == "POST" and route == "admin/announce":
            actor = self.web_user(role="admin")
            b = self.body()
            if b.get("clear"):
                p.clear_announcement(actor)
                return self.send_json(200, {"ok": True})
            return self.send_json(200, p.announce(actor, b))
        if method == "GET" and route == "admin/client":
            self.web_user(role="admin")
            return self.send_json(200, {"ok": True, "client": p.client_release(), "versions": p.client_versions()})
        if method == "POST" and route == "admin/client":
            actor = self.web_user(role="owner")
            return self.send_json(200, p.set_client_release(actor, self.body()))
        if method == "POST" and route == "admin/publish":
            actor = self.web_user(role="admin")
            return self.send_json(200, p.publish(actor, self.body(limit=MAX_DESIGN_BYTES + 400 * 1024)))
        if method == "GET" and route == "admin/commands":
            self.web_user(role="admin")
            rows = p.db.all("SELECT id, created_at, by_name, target, action, status, result FROM commands "
                            "ORDER BY id DESC LIMIT 50")
            return self.send_json(200, {"ok": True, "commands": rows})
        if method == "GET" and route == "admin/player":
            self.web_user(role="admin")
            name = one("name")
            return self.send_json(200, {"ok": True, "catalog": p.catalog(), "notes": p.notes(name), **p.admin_player(name)})
        if method == "POST" and route == "admin/command":
            actor = self.web_user(role="admin")
            return self.send_json(200, p.admin_command(actor, self.body()))
        if method == "POST" and route == "admin/account":
            actor = self.web_user(role="admin")
            return self.send_json(200, p.admin_account(actor, self.body()))
        if method == "GET" and route == "admin/ranks":
            self.web_user(role="admin")
            rows = p.db.all("SELECT id, created_at, by_name, target, action, args, status, result FROM commands "
                            "WHERE action='rank' ORDER BY id DESC LIMIT 30")
            for r in rows:
                r["args"] = json.loads(r["args"])
            return self.send_json(200, {"ok": True, **p.ranks(), "changes": rows})
        if method == "POST" and route == "admin/ranks":
            actor = self.web_user(role="owner")
            return self.send_json(200, p.rank_change(actor, self.body()))
        # ---------- builds ----------
        if method == "GET" and route == "downloads":
            acc = self.web_user(required=False)
            staff = bool(acc) and ROLES.index(acc["role"]) >= ROLES.index("admin")
            return self.send_json(200, {"ok": True, "builds": p.downloads(staff)})
        if method == "GET" and route == "admin/builds":
            self.web_user(role="admin")
            return self.send_json(200, {"ok": True, **p.admin_builds()})
        if method == "POST" and route == "admin/builds/upload":
            actor = self.web_user(role="owner")
            n = int(self.headers.get("Content-Length") or 0)
            if n > p.cfg.max_build_mb * 1024 * 1024:
                raise ApiError(413, "That build is bigger than %d MB (PANEL_MAX_BUILD_MB)." % p.cfg.max_build_mb)
            r = p.add_build(actor["name"], one("kind"), one("version"), one("name"), (self.rfile, n), one("notes"))
            if one("publish") == "1":
                p.publish_build(actor, r["build"]["id"], one("notify") == "1")
                r["build"] = p.build_out(p.build(r["build"]["id"]))
            return self.send_json(200, r)
        if method == "POST" and route in ("admin/builds/publish", "admin/builds/unpublish", "admin/builds/delete"):
            actor = self.web_user(role="owner")
            b = self.body()
            bid = int(b.get("id") or 0)
            if route.endswith("publish") and not route.endswith("unpublish"):
                return self.send_json(200, p.publish_build(actor, bid, bool(b.get("notify"))))
            if route.endswith("unpublish"):
                return self.send_json(200, p.unpublish_build(actor, bid))
            return self.send_json(200, p.delete_build(actor, bid))
        if method == "GET" and route == "admin/audit":
            self.web_user(role="admin")
            before = one("before")
            return self.send_json(200, {"ok": True, **p.audit_page(one("q"), int(before) if before.isdigit() else None)})
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
        if route == "version":
            return self.send_json(200, p.client_version(b.get("version"), b.get("platform")))
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
        elif cmd == "add-build" and len(args) >= 3:
            r = panel.add_build("console", args[0], args[1], os.path.basename(args[2]), args[2])
            if "--publish" in args[3:]:
                panel.publish_build({"name": "console"}, r["build"]["id"])
            print("Added %s %s (%s)%s." % (args[0], args[1], r["build"]["filename"],
                                            ", published" if "--publish" in args[3:] else ""))
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
