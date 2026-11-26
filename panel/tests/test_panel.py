"""Tests for the PlebMc Panel: python3 -m unittest discover -s panel/tests"""
import json
import os
import sys
import threading
import unittest
import urllib.error
import urllib.request

os.environ["PANEL_SCRYPT_N"] = "1024"  # fast hashing for tests
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import app  # noqa: E402

KEY = "k" * 40


class Clock:
    def __init__(self):
        self.t = 1_800_000_000

    def __call__(self):
        return self.t


def make_panel(**cfg):
    c = app.Config()
    c.server_key = KEY
    c.scrypt_n = 1024
    c.public_url = "https://panel.test"
    c.secure_cookies = True
    for k, v in cfg.items():
        setattr(c, k, v)
    clock = Clock()
    return app.Panel(app.Db(":memory:"), c, clock), clock


class PanelLogic(unittest.TestCase):
    def setUp(self):
        self.p, self.clock = make_panel()

    def test_passwords_hash_and_check(self):
        h = app.hash_password("hunter2hunter2", 1024)
        self.assertTrue(app.check_password("hunter2hunter2", h))
        self.assertFalse(app.check_password("hunter2hunter3", h))
        self.assertFalse(app.check_password("x", "garbage"))

    def test_register_needs_code_from_game(self):
        with self.assertRaises(app.ApiError):
            self.p.web_register("Bob", "AAAA-BBBB", "password1", "1.2.3.4")
        code = self.p.claim("Bob")
        with self.assertRaises(app.ApiError):
            self.p.web_register("Bob", "ZZZZ-ZZZZ", "password1", "1.2.3.4")
        acc = self.p.web_register("bob", code.lower(), "password1", "1.2.3.4")
        # the in-game spelling wins
        self.assertEqual(acc["name"], "Bob")
        # code is used up and the name is taken
        with self.assertRaises(app.ApiError):
            self.p.claim("BOB")
        with self.assertRaises(app.ApiError):
            self.p.web_register("Bob", code, "password1", "1.2.3.4")

    def test_code_expires(self):
        code = self.p.claim("Bob")
        self.clock.t += app.CLAIM_MINUTES * 60 + 1
        with self.assertRaises(app.ApiError):
            self.p.web_register("Bob", code, "password1", "1.2.3.4")

    def test_wrong_codes_are_throttled(self):
        code = self.p.claim("Bob")
        for _ in range(10):
            with self.assertRaises(app.ApiError):
                self.p.web_register("Bob", "WRONG-CODE", "password1", "9.9.9.9")
        with self.assertRaises(app.ApiError) as e:
            self.p.web_register("Bob", code, "password1", "9.9.9.9")
        self.assertEqual(e.exception.status, 429)

    def test_open_registration(self):
        p, _ = make_panel(open_registration=True)
        self.assertEqual(p.web_register("Alice", None, "password1", "1.1.1.1")["name"], "Alice")

    def test_rules(self):
        for name, pw in (("ab", "password1"), ("bad name", "password1"), ("Bob", "short")):
            with self.assertRaises(app.ApiError):
                self.p.create_account(name, pw)

    def test_game_login_and_tokens(self):
        self.p.create_account("Owner", "correct horse", "owner")
        self.assertFalse(self.p.game_login("Owner", "wrong", True)["ok"])
        r = self.p.game_login("owner", "correct horse", True)
        self.assertTrue(r["ok"])
        self.assertEqual(r["name"], "Owner")
        self.assertEqual(r["role"], "owner")
        tok = r["token"]
        self.assertTrue(self.p.game_token("Owner", tok)["ok"])
        # a token only works for its own account
        self.p.create_account("Bob", "password1")
        self.assertFalse(self.p.game_token("Bob", tok)["ok"])
        self.assertFalse(self.p.game_token("Owner", "made-up")["ok"])
        # no token unless asked
        self.assertNotIn("token", self.p.game_login("Owner", "correct horse", False))
        # tokens expire after not being used for a while
        self.clock.t += app.GAME_TOKEN_DAYS * 86400 + 5
        self.assertFalse(self.p.game_token("Owner", tok)["ok"])

    def test_game_login_throttle_per_name(self):
        self.p.create_account("Owner", "correct horse")
        for _ in range(app.MAX_FAILS):
            self.assertFalse(self.p.game_login("Owner", "nope", False)["ok"])
        # even the right password waits now (players share one IP, so it's per name)
        r = self.p.game_login("Owner", "correct horse", False)
        self.assertFalse(r["ok"])
        self.assertIn("Too many", r["error"])
        self.clock.t += app.FAIL_WINDOW + 1
        self.assertTrue(self.p.game_login("Owner", "correct horse", False)["ok"])

    def test_password_change_signs_out_everywhere(self):
        acc = self.p.create_account("Bob", "password1")
        tok = self.p.game_login("Bob", "password1", True)["token"]
        self.p.set_password(acc, "password2", "Bob")
        self.assertFalse(self.p.game_token("Bob", tok)["ok"])
        cmds = self.p.pending()
        self.assertEqual([(c["target"], c["action"]) for c in cmds], [("Bob", "signout")])
        self.assertTrue(self.p.game_login("Bob", "password2", False)["ok"])

    def test_token_limit(self):
        self.p.create_account("Bob", "password1")
        toks = []
        for i in range(app.MAX_GAME_TOKENS + 3):
            self.clock.t += 1
            toks.append(self.p.game_login("Bob", "password1", True)["token"])
        self.assertFalse(self.p.game_token("Bob", toks[0])["ok"])
        self.assertTrue(self.p.game_token("Bob", toks[-1])["ok"])

    def test_locked_accounts(self):
        owner = self.p.create_account("Owner", "password1", "owner")
        self.p.create_account("Bob", "password1")
        tok = self.p.game_login("Bob", "password1", True)["token"]
        self.p.admin_account(owner, {"name": "Bob", "action": "lock"})
        self.assertFalse(self.p.game_login("Bob", "password1", False)["ok"])
        self.assertFalse(self.p.game_token("Bob", tok)["ok"])
        with self.assertRaises(app.ApiError):
            self.p.web_login("Bob", "password1", "1.1.1.1")
        self.p.admin_account(owner, {"name": "Bob", "action": "unlock"})
        self.assertTrue(self.p.game_login("Bob", "password1", False)["ok"])

    def test_admins_cant_touch_admins(self):
        owner = self.p.create_account("Owner", "password1", "owner")
        admin = self.p.create_account("Mod", "password1", "admin")
        self.p.create_account("Mod2", "password1", "admin")
        with self.assertRaises(app.ApiError):
            self.p.admin_account(admin, {"name": "Mod2", "action": "lock"})
        with self.assertRaises(app.ApiError):
            self.p.admin_account(admin, {"name": "Owner", "action": "password", "password": "password9"})
        with self.assertRaises(app.ApiError):
            self.p.admin_account(admin, {"name": "X1", "action": "create", "password": "password1", "role": "owner"})
        with self.assertRaises(app.ApiError):
            self.p.admin_account(owner, {"name": "Owner", "action": "lock"})
        self.p.admin_account(owner, {"name": "Mod2", "action": "role", "role": "player"})
        self.assertEqual(self.p.account("Mod2")["role"], "player")

    def test_sync_and_commands(self):
        owner = self.p.create_account("Owner", "password1", "owner")
        self.p.sync({"full": True, "players": [
            {"uuid": "11111111-1111-1111-1111-111111111111", "name": "Bob", "coins": 500, "owned": ["crown"],
             "equipped": {"hat": "crown"}, "streak": 3}],
            "catalog": [{"id": "crown", "name": "Crown", "category": "hat", "price": 100}],
            "online": ["Bob"], "currency": "PlebMc Coins"})
        self.assertEqual(self.p.player("bob")["coins"], 500)
        self.assertTrue(self.p.server_status()["connected"])
        rows = self.p.admin_search("bo")
        self.assertEqual(rows[0]["name"], "Bob")
        self.assertTrue(rows[0]["online"])
        self.assertFalse(rows[0]["account"])
        self.p.admin_command(owner, {"name": "Bob", "action": "coins", "op": "give", "amount": 50})
        self.p.admin_command(owner, {"name": "Bob", "action": "give", "item": "crown"})
        with self.assertRaises(app.ApiError):
            self.p.admin_command(owner, {"name": "Bob", "action": "give", "item": "nope"})
        with self.assertRaises(app.ApiError):
            self.p.admin_command(owner, {"name": "Bob", "action": "coins", "op": "give", "amount": -5})
        with self.assertRaises(app.ApiError):
            self.p.admin_command(owner, {"name": "Bob", "action": "op"})
        cmds = self.p.pending()
        self.assertEqual([c["action"] for c in cmds], ["coins", "give"])
        self.assertEqual(cmds[0]["args"], ["give", 50])
        # each command is handed out once
        self.assertEqual(self.p.pending(), [])
        self.p.ack(cmds[0]["id"], True, "Bob now has 550")
        detail = self.p.admin_player("Bob")
        self.assertEqual(detail["commands"][-1]["status"], "done")
        self.assertEqual(detail["commands"][0]["status"], "sent")
        # the proxy goes quiet
        self.clock.t += 600
        self.assertFalse(self.p.server_status()["connected"])
        self.assertEqual(self.p.online(), [])


class PanelHttp(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.p, _ = make_panel(port=0, host="127.0.0.1")
        cls.p.clock = __import__("time").time
        cls.p.throttle.clock = cls.p.clock
        cls.p.code_throttle.clock = cls.p.clock
        app.Handler.panel = cls.p
        app.Handler.log_message = lambda *a: None
        cls.httpd = app.ThreadingHTTPServer(("127.0.0.1", 0), app.Handler)
        cls.base = "http://127.0.0.1:%d" % cls.httpd.server_address[1]
        threading.Thread(target=cls.httpd.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()

    def call(self, path, body=None, headers=None, cookie=None):
        h = {"Content-Type": "application/json"}
        if path.startswith("/api/") and not path.startswith("/api/server/") and body is not None:
            h["X-Requested-With"] = "PlebPanel"
        if cookie:
            h["Cookie"] = cookie
        h.update(headers or {})
        req = urllib.request.Request(self.base + path, data=None if body is None else json.dumps(body).encode(),
                                     headers=h, method="GET" if body is None else "POST")
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        try:
            with opener.open(req, timeout=5) as r:
                return r.status, json.loads(r.read() or b"{}"), r.headers
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read() or b"{}"), e.headers

    def server(self, path, body=None):
        return self.call("/api/server/" + path, body, {"Authorization": "Bearer " + KEY})

    def test_server_key_required(self):
        s, _, _ = self.call("/api/server/account?name=Bob")
        self.assertEqual(s, 401)
        s, _, _ = self.call("/api/server/account?name=Bob", headers={"Authorization": "Bearer wrong"})
        self.assertEqual(s, 401)
        s, j, _ = self.server("account?name=Bob")
        self.assertEqual((s, j["exists"]), (200, False))

    def test_full_flow(self):
        s, j, _ = self.server("claim", {"name": "Steve"})
        self.assertTrue(j["ok"])
        self.assertIn("panel.test/#register?name=Steve&code=", j["url"])
        # web posts need the header a cross-site form can't send
        s, j2, _ = self.call("/api/register", {"name": "Steve", "code": j["code"], "password": "password1"},
                             headers={"X-Requested-With": "nope"})
        self.assertEqual(s, 403)
        s, j2, hdr = self.call("/api/register", {"name": "Steve", "code": j["code"], "password": "password1"})
        self.assertEqual(s, 200, j2)
        cookie = hdr["Set-Cookie"].split(";")[0]
        self.assertIn("HttpOnly", hdr["Set-Cookie"])
        self.assertIn("Secure", hdr["Set-Cookie"])
        s, me, _ = self.call("/api/me", cookie=cookie)
        self.assertEqual(me["account"]["name"], "Steve")
        # players can't use admin routes
        s, _, _ = self.call("/api/admin/players", cookie=cookie)
        self.assertEqual(s, 403)
        # sign in from the game
        s, j, _ = self.server("login", {"name": "Steve", "password": "password1", "remember": True})
        self.assertTrue(j["ok"])
        s, j, _ = self.server("token", {"name": "Steve", "token": j["token"]})
        self.assertTrue(j["ok"])
        s, j, _ = self.server("register", {"name": "Steve", "password": "password1"})
        self.assertFalse(j["ok"])
        s, _, _ = self.call("/api/logout", {}, cookie=cookie)
        s, me, _ = self.call("/api/me", cookie=cookie)
        self.assertIsNone(me["account"])

    def test_static_and_headers(self):
        req = urllib.request.Request(self.base + "/")
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(req, timeout=5) as r:
            self.assertIn(b"PlebMc Panel", r.read())
            self.assertEqual(r.headers["X-Frame-Options"], "DENY")
        s, _, _ = self.call("/static/../app.py")
        self.assertEqual(s, 404)


if __name__ == "__main__":
    unittest.main()
