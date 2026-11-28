"""Tests for the PlebMc Panel: python3 -m unittest discover -s panel/tests"""
import hashlib
import json
import os
import tempfile
import sys
import threading
import unittest
import urllib.error
import urllib.parse
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

    def test_website_signup_without_code_is_limited(self):
        p, _ = make_panel(open_registration=True, client_registrations_per_hour=2)
        p.web_register("One", "", "password1", "5.5.5.5")
        p.web_register("Two", "", "password1", "5.5.5.5")
        with self.assertRaises(app.ApiError) as e:
            p.web_register("Three", "", "password1", "5.5.5.5")
        self.assertEqual(e.exception.status, 429)
        with self.assertRaises(app.ApiError):
            p.web_register("One", "", "password1", "6.6.6.6")  # name taken

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

    def test_last_seen(self):
        a, b = "11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222"
        t = self.clock.t
        self.p.sync({"full": True, "players": [{"uuid": a, "name": "Old"}, {"uuid": b, "name": "Bob", "seen": (t - 3600) * 1000}],
                     "online": []})
        seen = {p["name"]: p["lastSeen"] for p in self.p.admin_players(sort="name")["players"]}
        self.assertEqual(seen, {"Old": None, "Bob": t - 3600})  # syncing is not being seen
        self.clock.t += 600
        self.p.sync({"full": True, "players": [{"uuid": a, "name": "Old"}, {"uuid": b, "name": "Bob"}], "online": ["old"]})
        seen = {p["name"]: p["lastSeen"] for p in self.p.admin_players(sort="name")["players"]}
        self.assertEqual(seen, {"Old": self.clock.t, "Bob": t - 3600})  # online now; Bob's kept
        self.p.sync({"full": True, "players": [{"uuid": a, "name": "Old"}], "online": []})
        self.clock.t += 600
        self.assertEqual(self.p.admin_players(sort="name")["players"][0]["lastSeen"], t + 600)

    def test_shop_previews_and_web_buy(self):
        import base64, hashlib
        png = b"\x89PNG\r\n\x1a\n" + b"cloak" * 10
        h = hashlib.sha1(png).hexdigest()[:16]
        bob = self.p.create_account("Bob", "password1")
        r = self.p.sync({"full": True, "players": [
            {"uuid": "11111111-1111-1111-1111-111111111111", "name": "Bob", "coins": 500, "owned": []}],
            "catalog": [{"id": "tophat", "name": "&6Top Hat", "category": "hat", "render": "tophat", "price": 100,
                         "color": "#112233", "description": "Classy"},
                        {"id": "fire", "name": "Fire", "category": "cloak", "render": "cloak", "price": 900,
                         "texture": h, "textureWidth": 64, "frames": 4, "frameTime": 2},
                        {"id": "vip", "name": "VIP", "category": "hat", "render": "crown", "price": 10, "permission": True},
                        {"id": "event", "name": "Event", "category": "hat", "render": "halo", "price": 999999999,
                         "forSale": False, "texture": "not-a-hash"}],
            "online": ["Bob"]})
        self.assertEqual(r["need"], [h])  # the website asks for the cloak texture
        items = {i["id"]: i for i in self.p.catalog()}
        self.assertEqual(items["tophat"]["name"], "Top Hat")
        self.assertEqual(items["tophat"]["color"], "#112233")
        self.assertEqual(items["fire"]["frames"], 4)
        self.assertIsNone(items["event"]["price"])
        self.assertNotIn("texture", items["event"])
        with self.assertRaises(app.ApiError):  # must match its id
            self.p.put_asset({"hash": h, "data": base64.b64encode(b"other").decode()})
        self.p.put_asset({"hash": h, "data": base64.b64encode(png).decode()})
        self.assertEqual(self.p.asset(h), png)
        self.assertIsNone(self.p.asset("../etc"))
        self.assertEqual(self.p.sync({"catalog": self.p.catalog()})["need"], [])
        # buying
        for item in ("event", "vip", "fire", "nope"):  # not for sale, needs a rank, too dear, unknown
            with self.assertRaises(app.ApiError):
                self.p.web_buy(bob, {"item": item})
        cid = self.p.web_buy(bob, {"item": "tophat"})["id"]
        with self.assertRaises(app.ApiError):  # one at a time
            self.p.web_buy(bob, {"item": "tophat"})
        cmd = self.p.pending()[0]
        self.assertEqual((cmd["target"], cmd["action"], cmd["args"]), ("Bob", "buy", ["tophat"]))
        self.p.ack(cid, True, "bought tophat for 100")
        o = self.p.web_order(bob, cid)
        self.assertEqual(o["status"], "done")
        self.assertEqual((o["player"]["coins"], o["player"]["owned"]), (400, ["tophat"]))
        with self.assertRaises(app.ApiError):  # owned now
            self.p.web_buy(bob, {"item": "tophat"})
        other = self.p.create_account("Eve", "password1")
        with self.assertRaises(app.ApiError):  # someone else's order
            self.p.web_order(other, cid)
        # the texture goes when no item uses it
        self.p.sync({"catalog": [{"id": "tophat", "name": "Top Hat", "category": "hat", "price": 100}]})
        self.assertIsNone(self.p.asset(h))

    def test_download_target(self):
        url, name, html = self.p.download_target("offline")
        self.assertEqual((name, html), ("PlebClient_Offline.html", True))
        self.assertFalse(self.p.download_target("wasm")[2])
        self.assertIsNone(self.p.download_target("nope"))

    def test_sync_and_commands(self):
        owner = self.p.create_account("Owner", "password1", "owner")
        self.p.sync({"full": True, "players": [
            {"uuid": "11111111-1111-1111-1111-111111111111", "name": "Bob", "coins": 500, "owned": ["crown"],
             "equipped": {"hat": "crown"}, "streak": 3}],
            "catalog": [{"id": "crown", "name": "Crown", "category": "hat", "price": 100}],
            "online": ["Bob"], "currency": "PlebMc Coins"})
        self.assertEqual(self.p.player("bob")["coins"], 500)
        self.assertTrue(self.p.server_status()["connected"])
        rows = self.p.admin_players("bo")["players"]
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
        self.p.admin_command(owner, {"action": "coinsall", "amount": 250})
        with self.assertRaises(app.ApiError):
            self.p.admin_command(owner, {"action": "coinsall", "amount": 0})
        everyone = self.p.pending()
        self.assertEqual([(c["target"], c["action"], c["args"]) for c in everyone], [("*", "coinsall", [250])])
        self.p.ack(cmds[0]["id"], True, "Bob now has 550")
        detail = self.p.admin_player("Bob")
        self.assertEqual(detail["commands"][-1]["status"], "done")
        self.assertEqual(detail["commands"][0]["status"], "sent")
        # the proxy goes quiet
        self.clock.t += 600
        self.assertFalse(self.p.server_status()["connected"])
        self.assertEqual(self.p.online(), [])

    def test_luckperms_ranks(self):
        owner = self.p.create_account("Owner", "password1", "owner")
        self.assertFalse(self.p.ranks()["connected"])
        self.p.sync({"players": [{"uuid": "11111111-1111-1111-1111-111111111111", "name": "Bob", "coins": 1, "rank": "vip"}],
                     "ranks": {"ok": True, "groups": [
                         {"name": "default", "weight": 0, "prefix": "&7", "perms": [{"node": "essentials.spawn", "value": True}]},
                         {"name": "vip", "weight": 10, "prefix": "&#00E5FF&lVIP ", "parents": ["default"], "members": 3,
                          "perms": [{"node": "essentials.fly", "value": True, "server": "lobby"}]},
                         {"name": "Bad Name!", "weight": 5}],
                         "tracks": [{"name": "staff", "groups": ["helper", "mod"]}]}})
        r = self.p.ranks()
        self.assertTrue(r["connected"])
        self.assertEqual([g["name"] for g in r["groups"]], ["vip", "default"])
        self.assertEqual(r["groups"][0]["perms"][0]["server"], "lobby")
        self.assertEqual(self.p.admin_player("Bob")["rank"], "vip")
        self.p.rank_change(owner, {"op": "create", "group": "mvp"})
        self.p.rank_change(owner, {"op": "prefix", "group": "mvp", "text": "&6[MVP] "})
        self.p.rank_change(owner, {"op": "weight", "group": "mvp", "weight": 20})
        self.p.rank_change(owner, {"op": "perm", "group": "mvp", "node": "essentials.fly", "value": False, "server": "plebnite"})
        self.p.rank_change(owner, {"op": "parent", "group": "mvp", "parent": "vip"})
        self.p.rank_change(owner, {"op": "player", "group": "mvp", "name": "Bob", "mode": "set"})
        for bad in ({"op": "create", "group": "Has Space"}, {"op": "delete", "group": "default"},
                    {"op": "perm", "group": "mvp", "node": "bad node;rm"}, {"op": "parent", "group": "mvp", "parent": "mvp"},
                    {"op": "weight", "group": "mvp", "weight": "x"}, {"op": "player", "group": "mvp", "name": ""},
                    {"op": "prefix", "group": "mvp", "text": "x" * 81}, {"op": "explode", "group": "mvp"}):
            with self.assertRaises(app.ApiError, msg=str(bad)):
                self.p.rank_change(owner, bad)
        cmds = self.p.pending()
        self.assertEqual([(c["target"], c["args"]) for c in cmds], [
            ("mvp", ["create"]), ("mvp", ["prefix", "&6[MVP] "]), ("mvp", ["weight", 20]),
            ("mvp", ["perm", "essentials.fly", False, "plebnite"]), ("mvp", ["parent", "vip"]), ("Bob", ["player", "set", "mvp"])])
        self.assertTrue(all(c["action"] == "rank" for c in cmds))

    def test_players_are_paged(self):
        self.p.sync({"full": True, "players": [
            {"uuid": "00000000-0000-0000-0000-%012d" % i, "name": "P%03d" % i, "coins": i, "owned": [], "equipped": {},
             "streak": 0} for i in range(120)], "online": ["P005", "P100"]})
        self.p.create_account("P007", "password1", "admin")
        self.p.create_account("Zed", "password1")
        r = self.p.admin_players()
        self.assertEqual((r["total"], len(r["players"]), r["more"]), (121, 50, True))
        self.assertEqual(r["players"][0]["name"], "P119")  # most coins first
        r2 = self.p.admin_players(offset=100)
        self.assertEqual((len(r2["players"]), r2["more"]), (21, False))
        names = [x["name"] for x in self.p.admin_players(offset=0, limit=100)["players"] + r2["players"]]
        self.assertEqual(len(set(names)), 121)  # every player exactly once
        self.assertEqual(self.p.admin_players(filt="online")["total"], 2)
        self.assertEqual(self.p.admin_players(filt="accounts")["total"], 2)
        self.assertEqual([x["name"] for x in self.p.admin_players(filt="staff")["players"]], ["P007"])
        self.assertEqual(self.p.admin_players(filt="noaccount")["total"], 119)
        self.assertEqual(self.p.admin_players(q="P11")["total"], 10)
        self.assertEqual(self.p.admin_players(q="%")["total"], 0)  # LIKE wildcards are plain text
        self.assertEqual(self.p.admin_players(sort="name")["players"][0]["name"], "P000")
        self.assertEqual(self.p.admin_players(sort="bogus")["players"][0]["name"], "P119")
        lb = self.p.leaderboard(0, 10)
        self.assertEqual((lb["rows"][0]["name"], lb["rows"][0]["rank"], lb["total"], len(lb["rows"])), ("P119", 1, 120, 10))
        self.assertEqual(self.p.leaderboard(0, 500)["rows"][-1]["rank"], 25)  # at most 25 at a time
        found = self.p.leaderboard(q="p005")["rows"]
        self.assertEqual((found[0]["name"], found[0]["rank"]), ("P005", 115))  # the exact name first, with its rank
        self.assertEqual(len(self.p.leaderboard(q="P11")["rows"]), 10)
        self.assertEqual(self.p.leaderboard(q="%")["rows"], [])  # LIKE wildcards are plain text

    def test_dashboard_notes_and_announcements(self):
        owner = self.p.create_account("Owner", "password1", "owner")
        admin = self.p.create_account("Mod", "password1", "admin")
        self.p.sync({"full": True, "players": [
            {"uuid": "1" * 32, "name": "Bob", "coins": 50, "owned": ["crown", "cape"], "equipped": {}, "streak": 1},
            {"uuid": "2" * 32, "name": "Amy", "coins": 70, "owned": ["crown"], "equipped": {}, "streak": 1}],
            "catalog": [{"id": "crown", "name": "Crown", "category": "hat", "price": 100}]})
        d = self.p.dashboard()
        self.assertEqual((d["accounts"], d["staff"], d["players"], d["coins"], d["richest"]), (2, 2, 2, 120, 70))
        self.assertEqual(d["popular"][0], {"id": "crown", "name": "Crown", "category": "hat", "owners": 2})
        self.assertEqual(len(d["signups"]), 14)
        self.assertEqual(d["signups"][-1]["count"], 2)
        self.p.add_note(admin, "Bob", "Warned for spam")
        self.p.add_note(owner, "bob", "Second warning")
        notes = self.p.notes("BOB")
        self.assertEqual([n["text"] for n in notes], ["Second warning", "Warned for spam"])
        with self.assertRaises(app.ApiError):
            self.p.add_note(admin, "Bob", "   ")
        with self.assertRaises(app.ApiError):
            self.p.delete_note(admin, notes[0]["id"])  # someone else's note
        self.p.delete_note(owner, notes[1]["id"])  # owners can delete any
        self.assertEqual(len(self.p.notes("bob")), 1)
        with self.assertRaises(app.ApiError):
            self.p.announce(admin, {"text": "hi"})  # nowhere picked
        self.p.announce(admin, {"text": "Double coins!", "site": True, "game": True})
        self.assertEqual(self.p.public_info()["announcement"]["text"], "Double coins!")
        cmds = self.p.pending()
        self.assertEqual((cmds[0]["target"], cmds[0]["action"], cmds[0]["args"]), ("*", "broadcast", ["Double coins!"]))
        self.p.clear_announcement(admin)
        self.assertIsNone(self.p.public_info()["announcement"])

    def test_download_links(self):
        owner = self.p.create_account("Owner", "password1", "owner")
        info = self.p.public_info()["downloads"]
        self.assertEqual([d["kind"] for d in info], ["offline", "wasm", "forge-1.8.9"])
        self.assertTrue(all(d["url"].startswith("https://gitflic.ru/") for d in info))
        with self.assertRaises(app.ApiError):
            self.p.set_downloads(owner, {"offline": "javascript:alert(1)"})
        self.assertEqual(info[0]["name"], "PlebClient JS")
        self.p.set_downloads(owner, {"offline": {"url": "https://example.com/p.html", "name": "PlebClient Web"}, "wasm": {"url": ""},
                                     "forge-1.8.9": {"url": info[2]["url"]}})
        self.assertEqual(self.p.public_info()["downloads"][0]["name"], "PlebClient Web")
        self.assertEqual(self.p.public_info()["downloads"][1]["help"], info[2]["help"])  # empty = the default
        self.assertEqual([d["kind"] for d in self.p.public_info()["downloads"]], ["offline", "forge-1.8.9"])
        self.assertEqual(self.p.public_info()["downloads"][0]["url"], "https://example.com/p.html")

    def test_posts(self):
        owner, admin = {"name": "Boss", "role": "owner"}, {"name": "Mod", "role": "admin"}
        self.assertEqual(self.p.posts()["posts"], [])
        for bad in ({"title": "", "body": "x"}, {"title": "x", "body": ""}, {"title": "x", "body": "y", "tag": "spam"},
                    {"title": "x", "body": "y" * (app.MAX_POST + 1)}):
            with self.assertRaises(app.ApiError):
                self.p.save_post(owner, bad)
        a = self.p.save_post(owner, {"title": "Server 1.2", "body": "## Fixed\n- lag", "tag": "server"})["post"]
        self.clock.t += 100
        b = self.p.save_post(owner, {"title": "Client 2.1", "body": "**new** <script>", "tag": "client"})["post"]
        self.assertEqual([x["title"] for x in self.p.posts()["posts"]], ["Client 2.1", "Server 1.2"])
        self.assertEqual([x["title"] for x in self.p.posts(tag="server")["posts"]], ["Server 1.2"])
        self.assertEqual(self.p.post(b["id"])["body"], "**new** <script>")  # stored as written; the page never renders it as HTML
        self.p.save_post(admin, {"id": a["id"], "title": "Server 1.2.1", "body": "edited", "tag": "server", "pinned": True})
        got = self.p.posts()
        self.assertEqual([(x["title"], x["pinned"], x["author"]) for x in got["posts"]], [("Server 1.2.1", True, "Boss"), ("Client 2.1", False, "Boss")])
        self.assertEqual(self.p.public_info()["latestPosts"][0]["title"], "Server 1.2.1")
        # the homepage's notice points at the newest post (not the pinned one) for a week
        self.assertIsNone(make_panel()[0].public_info()["newPost"])
        self.assertEqual(self.p.public_info()["newPost"]["title"], "Client 2.1")
        self.clock.t += app.NEWS_NOTICE_DAYS * 86400 - 50
        self.assertEqual(self.p.public_info()["newPost"]["id"], b["id"])
        self.clock.t += 100
        self.assertIsNone(self.p.public_info()["newPost"])
        self.assertTrue(self.p.posts(limit=1)["more"])
        self.p.delete_post(owner, b["id"])
        self.assertEqual(self.p.posts()["total"], 1)
        with self.assertRaises(app.ApiError):
            self.p.post(b["id"])

    def test_last_seen_on_profiles(self):
        now = self.p.now()
        self.p.sync({"players": [{"uuid": "u1", "name": "Bob", "coins": 5, "seen": (now - 3600) * 1000},
                                 {"uuid": "u2", "name": "Ann", "coins": 9, "seen": (now - 60) * 1000}], "online": ["Ann"]})
        self.assertEqual(self.p.seen("bob"), {"online": False, "lastSeen": now - 3600})
        self.assertEqual(self.p.seen("ANN"), {"online": True, "lastSeen": now})
        self.assertEqual(self.p.seen("Nobody"), {"online": False, "lastSeen": None})
        stats = self.p.player_stats("Bob")
        self.assertEqual((stats["online"], stats["lastSeen"]), (False, now - 3600))
        self.assertTrue(self.p.player_stats("Ann")["online"])
        admin = self.p.admin_player("Bob")
        self.assertEqual((admin["online"], admin["lastSeen"]), (False, now - 3600))
        # a website sign-in counts on the admin page, as in the players list, but not on the public profile
        self.p.server_register("Bob", "password1")
        self.p.db.run("UPDATE accounts SET last_login=? WHERE name_key='bob'", now - 10)
        self.assertEqual(self.p.admin_player("Bob")["lastSeen"], now - 10)
        self.assertEqual(self.p.player_stats("Bob")["lastSeen"], now - 3600)

    def test_ideas_board(self):
        mod = {"name": "Mod", "role": "admin"}
        self.assertEqual([s["name"] for s in self.p.ideas()["sections"]], ["PlebMc", "PlebNite", "PlebRPG", "Other"])
        rpg = self.p.ideas()["sections"][2]["id"]
        for bad in ({"action": "save", "section": rpg, "name": ""}, {"action": "save", "section": 999, "name": "x"},
                    {"action": "save", "section": rpg, "name": "x", "priority": "asap"}, {"action": "section-add", "name": " "},
                    {"action": "save", "section": rpg, "name": "x", "description": "y" * (app.MAX_IDEA + 1)}, {"action": "nope"}):
            with self.assertRaises(app.ApiError):
                self.p.idea_action(mod, bad)
        self.p.idea_action(mod, {"action": "save", "section": rpg, "name": "Pets"})
        self.p.idea_action(mod, {"action": "save", "section": rpg, "name": "Boss raid", "description": "10 players", "priority": "urgent"})
        got = self.p.idea_action(mod, {"action": "save", "section": rpg, "name": "Fishing", "priority": "high", "status": "done"})
        ideas = got["sections"][2]["ideas"]
        self.assertEqual([(i["name"], i["priority"], i["status"]) for i in ideas],
                         [("Boss raid", "urgent", "idea"), ("Pets", "medium", "idea"), ("Fishing", "high", "done")])
        self.assertEqual(ideas[0]["author"], "Mod")
        pets = ideas[1]["id"]
        other = got["sections"][3]["id"]
        self.p.idea_action(mod, {"action": "set", "id": pets, "status": "doing", "priority": "low"})
        got = self.p.idea_action(mod, {"action": "save", "id": pets, "section": other, "name": "Pets!", "priority": "low", "status": "doing"})
        self.assertEqual([(i["name"], i["status"]) for i in got["sections"][3]["ideas"]], [("Pets!", "doing")])
        got = self.p.idea_action(mod, {"action": "section-add", "name": "Website"})
        got = self.p.idea_action(mod, {"action": "section-move", "section": got["sections"][4]["id"], "dir": "up"})
        self.assertEqual([s["name"] for s in got["sections"]], ["PlebMc", "PlebNite", "PlebRPG", "Website", "Other"])
        self.p.idea_action(mod, {"action": "section-rename", "section": other, "name": "Misc"})
        got = self.p.idea_action(mod, {"action": "section-delete", "section": rpg})
        self.assertEqual([s["name"] for s in got["sections"]], ["PlebMc", "PlebNite", "Website", "Misc"])
        self.assertEqual(self.p.db.one("SELECT COUNT(*) n FROM ideas")["n"], 1)
        got = self.p.idea_action(mod, {"action": "delete", "id": pets})
        self.assertEqual(sum(len(s["ideas"]) for s in got["sections"]), 0)

    def test_punishments_from_litebans(self):
        now = self.p.now()
        ms = lambda sec: sec * 1000
        self.assertFalse(self.p.punishments_page()["source"]["ever"])
        rows = [
            {"kind": "ban", "id": 1, "uuid": "U-1", "name": "Griefer", "reason": "Griefing spawn", "staff": "Mod", "time": ms(now - 500), "until": 0, "active": True},
            {"kind": "ban", "id": 2, "uuid": "u-2", "name": "Cheater", "reason": "Fly hacks", "staff": "Console", "time": ms(now - 400), "until": ms(now - 10), "active": True},
            {"kind": "ban", "id": 3, "uuid": "u-3", "name": "Sorry", "reason": "Spam", "staff": "Mod", "time": ms(now - 300), "until": 0, "active": False,
             "removedBy": "Boss", "removedReason": "Appealed", "removedAt": ms(now - 5)},
            {"kind": "ban", "id": 4, "uuid": "u-4", "name": "Old", "reason": "x", "staff": "Mod", "time": ms(now - 900), "until": ms(now - 800), "active": False, "removedBy": "#expired"},
            {"kind": "mute", "id": 1, "uuid": "u-1", "name": "Griefer", "reason": "100% toxic_chat", "staff": "Mod", "time": ms(now - 200), "until": ms(now + 3600), "active": True},
            {"kind": "warn", "id": 1, "uuid": "u-5", "name": "Newbie", "reason": "Caps", "staff": "Helper", "time": ms(now - 100), "until": 0, "active": True},
            {"kind": "kick", "id": 1, "uuid": "u-5", "name": "Newbie", "reason": "AFK", "staff": "Helper", "time": ms(now - 50), "until": 0, "active": True, "server": "plebnite"},
            {"kind": "nuke", "id": 9, "name": "x"}, {"kind": "ban"}, "junk",
        ]
        self.assertEqual(self.p.punishments_sync({"available": True, "rows": rows})["saved"], 7)
        page = self.p.punishments_page()
        self.assertEqual([(i["kind"], i["name"], i["status"]) for i in page["items"]],
                         [("kick", "Newbie", ""), ("warn", "Newbie", "active"), ("mute", "Griefer", "active"), ("ban", "Sorry", "removed"),
                          ("ban", "Cheater", "expired"), ("ban", "Griefer", "active"), ("ban", "Old", "expired")])
        self.assertEqual(page["counts"], {"ban": {"total": 4, "active": 1}, "mute": {"total": 1, "active": 1},
                                          "warn": {"total": 1, "active": 1}, "kick": {"total": 1, "active": 0}})
        self.assertTrue(page["source"]["connected"] and page["source"]["available"])
        sorry = page["items"][3]
        self.assertEqual((sorry["removedBy"], sorry["removedReason"], sorry["removedAt"], sorry["until"]), ("Boss", "Appealed", now - 5, 0))
        self.assertEqual(page["items"][6]["removedBy"], "")
        self.assertEqual(page["items"][2]["until"], now + 3600)
        names = lambda **kw: [(i["kind"], i["name"]) for i in self.p.punishments_page(**kw)["items"]]
        self.assertEqual(names(kind="ban", status="active"), [("ban", "Griefer")])
        self.assertEqual(names(status="expired"), [("ban", "Cheater"), ("ban", "Old")])
        self.assertEqual(names(status="removed"), [("ban", "Sorry")])
        self.assertEqual(names(status="active"), [("warn", "Newbie"), ("mute", "Griefer"), ("ban", "Griefer")])
        self.assertEqual(names(q="griefer"), [("mute", "Griefer"), ("ban", "Griefer")])
        self.assertEqual(names(q="helper"), [("kick", "Newbie"), ("warn", "Newbie")])
        self.assertEqual(names(q="fly"), [("ban", "Cheater")])
        self.assertEqual(names(q="u-1"), [("mute", "Griefer"), ("ban", "Griefer")])
        self.assertEqual(names(q="%"), [("mute", "Griefer")])
        self.assertEqual(names(q="_"), [("mute", "Griefer")])
        self.assertEqual(names(kind="kick"), [("kick", "Newbie")])
        # the mute runs out by itself
        self.clock.t += 7200
        self.assertEqual(names(status="active"), [("warn", "Newbie"), ("ban", "Griefer")])
        # an unban shows up on the next sync; a full pass drops what LiteBans no longer has
        rows[0].update(active=False, removedBy="Boss", removedAt=ms(self.p.now()))
        self.p.punishments_sync({"available": True, "rows": rows[:2], "sweep": {"id": 77, "kinds": ["ban"]}})
        self.assertEqual(len(names(kind="ban")), 4)
        self.p.punishments_sync({"available": True, "rows": rows[2:3], "sweep": {"id": 77, "kinds": ["ban"], "done": True}})
        self.assertEqual(names(kind="ban"), [("ban", "Sorry"), ("ban", "Cheater"), ("ban", "Griefer")])
        self.assertEqual(names(kind="ban", status="removed"), [("ban", "Sorry"), ("ban", "Griefer")])
        self.assertEqual(len(names()), 6)
        # LiteBans missing on the proxy
        self.p.punishments_sync({"available": False, "reason": "LiteBans isn't on this proxy."})
        src = self.p.punishments_page()["source"]
        self.assertEqual((src["available"], src["reason"], src["connected"]), (False, "LiteBans isn't on this proxy.", True))
        self.assertEqual(len(names()), 6)
        self.clock.t += 600
        self.assertFalse(self.p.punishments_page()["source"]["connected"])

    def test_coin_gift_says_who_gets_it(self):
        owner = {"name": "Boss", "role": "owner"}
        for flag, args in ((True, [50, "client"]), (False, [50, "all"]), (None, [50])):
            body = {"action": "coinsall", "amount": 50}
            if flag is not None:
                body["clientOnly"] = flag
            cid = self.p.admin_command(owner, body)["id"]
            self.assertEqual(json.loads(self.p.db.one("SELECT args FROM commands WHERE id=?", cid)["args"]), args)

    def test_site_content(self):
        owner = self.p.create_account("Owner", "password1", "owner")
        c = self.p.public_info()["content"]
        self.assertEqual(c["logo"], "/static/logo.png")
        self.assertEqual(len(c["features"]), 3)
        png = b"\x89PNG\r\n\x1a\n" + b"x" * 40
        with self.assertRaises(app.ApiError):
            self.p.put_site_image(owner, {"data": app.b64(b"<svg onload=alert(1)>")})
        url = self.p.put_site_image(owner, {"data": "data:image/png;base64," + app.b64(png)})["url"]
        self.assertRegex(url, r"^/api/site/img/[0-9a-f]{16}$")
        self.assertEqual(bytes(self.p.site_image(url.rsplit("/", 1)[-1])["data"]), png)
        for bad in ({"logo": "https://evil.example/x.png"}, {"logo": "/static/../app.py"}, {"accent": "red"},
                    {"discordUrl": "javascript:alert(1)"}):
            with self.assertRaises(app.ApiError):
                self.p.set_site_content(owner, bad)
        r = self.p.set_site_content(owner, {"title1": "Hello", "logo": url, "accent": "#22B8E6", "discordUrl": "https://discord.gg/x",
                                            "features": [{"title": "One", "text": "x", "image": url}, {"title": "", "text": ""}],
                                            "faq": [{"q": "Q?", "a": "A."}, {"q": "no answer", "a": ""}]})
        c = self.p.public_info()["content"]
        self.assertEqual((c["title1"], c["logo"], c["accent"]), ("Hello", url, "#22b8e6"))
        self.assertEqual(len(c["features"]), 1)
        self.assertEqual(c["faq"], [{"q": "Q?", "a": "A."}])
        self.assertEqual(c["subtitle"], app.SITE_DEFAULTS["subtitle"])  # left out = the default
        # voting: defaults, then the owner's own list
        d = app.SITE_DEFAULTS
        self.assertEqual(c["voteLinks"], d["voteLinks"])  # left out = the default
        self.assertEqual(d["voteLinks"][0]["url"], "https://eaglercraftservers.com/server/plebmc")
        for bad in ({"voteLinks": [{"name": "X", "url": "http://plain.example"}]}, {"voteLinks": [{"name": "", "url": "https://a.example"}]},
                    {"voteReward": "lots"}):
            with self.assertRaises(app.ApiError):
                self.p.set_site_content(owner, bad)
        self.p.set_site_content(owner, {"voteLinks": [{"name": "A", "url": "https://a.example/v"}, {"name": "", "url": ""}], "voteReward": 250})
        c = self.p.public_info()["content"]
        self.assertEqual((c["voteLinks"], c["voteReward"]), ([{"name": "A", "url": "https://a.example/v"}], 250))
        self.p.set_site_content(owner, {"voteLinks": [], "voteReward": 0})
        c = self.p.public_info()["content"]
        self.assertEqual((c["voteLinks"], c["voteReward"]), ([], 0))  # none = voting hidden
        self.assertTrue(c["mascot"])  # Plebio shows unless an owner turns him off
        self.p.set_site_content(owner, {"mascot": False})
        self.assertFalse(self.p.public_info()["content"]["mascot"])
        self.p.reset_site_content(owner)
        self.assertEqual(self.p.public_info()["content"]["logo"], "/static/logo.png")

    def test_client_versions_start_over_at_1_0(self):
        owner = self.p.create_account("Owner", "password1", "owner")
        self.p.set_client_release(owner, {"latest": "2.1", "minimum": "2.0", "message": "hi"})
        for v in ("2.0", "2.1", "1.0"):
            self.p.client_version(v, "wasm")
        self.p.db.set_kv("client_versions_reset_1_0", None)  # as on a panel from before the reset
        p2 = app.Panel(self.p.db, self.p.cfg, self.p.clock)
        self.assertEqual([r["version"] for r in p2.client_versions()], ["1.0"])
        rel = p2.client_release()
        self.assertEqual((rel["latest"], rel["minimum"], rel["message"]), ("1.0", "", "hi"))
        self.assertFalse(p2.client_version("1.0", "wasm")["outdated"])
        # only once: a later 2.x setting of the owner's own is left alone
        p2.set_client_release(owner, {"latest": "2.5"})
        self.assertEqual(app.Panel(self.p.db, self.p.cfg, self.p.clock).client_release()["latest"], "2.5")

    def test_pre_release_versions_sort_below_their_version(self):
        k = app.version_key
        self.assertTrue(k("1.0") < k("1.1-alpha1") < k("1.1-beta1") < k("1.1-beta2") < k("1.1-rc1") < k("1.1") < k("1.1.1"))
        owner = self.p.create_account("Owner", "password1", "owner")
        self.p.set_client_release(owner, {"latest": "1.1", "minimum": "1.1-beta1"})
        r = self.p.client_version("1.1-beta1", "wasm")
        self.assertTrue(r["outdated"])
        self.assertFalse(r["required"])
        self.assertTrue(self.p.client_version("1.0", "wasm")["required"])
        self.assertFalse(self.p.client_version("1.1", "wasm")["outdated"])
        self.assertEqual([x["version"] for x in self.p.client_versions()], ["1.1", "1.1-beta1", "1.0"])

    def test_client_update_checker(self):
        owner = self.p.create_account("Owner", "password1", "owner")
        r = self.p.client_version("2.0", "web")
        self.assertFalse(r["outdated"] or r["required"])
        self.p.set_client_release(owner, {"latest": "2.1", "minimum": "1.10", "url": "https://plebmc.top/dl",
                                          "message": "New cloaks"})
        r = self.p.client_version("2.0", "wasm")
        self.assertTrue(r["outdated"])
        self.assertFalse(r["required"])
        self.assertEqual((r["latest"], r["url"], r["message"]), ("2.1", "https://plebmc.top/dl", "New cloaks"))
        self.assertTrue(self.p.client_version("1.9", "forge")["required"])  # 1.9 < 1.10
        self.assertFalse(self.p.client_version("2.1", "web")["outdated"])
        self.assertFalse(self.p.client_version("10.0", "web")["outdated"])
        self.assertFalse(self.p.client_version("junk", "web")["outdated"])
        seen = {(v["version"], v["platform"]): v["n"] for v in self.p.client_versions()}
        self.assertEqual(seen[("2.0", "web")], 1)
        self.assertEqual(seen[("1.9", "forge")], 1)
        self.assertNotIn(("junk", "web"), seen)
        for bad in ({"latest": "two"}, {"latest": "1.0", "minimum": "2.0"}, {"latest": "1.0", "url": "javascript:x"}):
            with self.assertRaises(app.ApiError):
                self.p.set_client_release(owner, bad)

    def test_designs(self):
        amy = self.p.create_account("Amy", "password1")
        bob = self.p.create_account("Bob", "password1")
        admin = self.p.create_account("Mod", "password1", "admin")
        hat = json.dumps({"v": 1, "vox": [["0,8,0", "#ff0000"]]})
        r = self.p.save_design(amy, {"kind": "hat", "name": "Red Hat", "data": hat, "public": True})
        did = r["id"]
        priv = self.p.save_design(amy, {"kind": "cloak", "name": "Secret", "data": "data:image/png;base64,AAAA"})["id"]
        for bad in ({"kind": "boots", "data": hat}, {"kind": "hat", "data": "nope"}, {"kind": "cloak", "data": "data:text/html,x"},
                    {"kind": "hat"}):
            with self.assertRaises(app.ApiError):
                self.p.save_design(amy, bad)
        with self.assertRaises(app.ApiError):
            self.p.save_design(bob, {"id": did, "kind": "hat", "data": hat})  # not his
        with self.assertRaises(app.ApiError):
            self.p.get_design(priv, bob)
        self.assertEqual(self.p.get_design(priv, amy)["data"], "data:image/png;base64,AAAA")
        self.assertFalse(self.p.get_design(did, amy)["public"])  # no gallery: nothing is shared
        wings = self.p.save_design(amy, {"kind": "wings", "name": "Bat", "data": hat})["id"]
        self.assertEqual(self.p.get_design(wings, admin)["kind"], "wings")
        with self.assertRaises(app.ApiError):
            self.p.delete_design(bob, did)
        self.p.delete_design(admin, did)
        self.p.delete_design(admin, wings)
        for i in range(app.MAX_DESIGNS - 1):
            self.p.save_design(amy, {"kind": "hat", "name": "H%d" % i, "data": hat})
        with self.assertRaises(app.ApiError):
            self.p.save_design(amy, {"kind": "hat", "data": hat})

    def test_publish_to_shop(self):
        admin = self.p.create_account("Mod", "password1", "admin")
        self.p.sync({"catalog": [{"id": "crown", "name": "Crown", "category": "hat", "price": 100}]})
        r = self.p.publish(admin, {"kind": "cloak", "itemId": "fire_cloak", "name": "Fire Cloak", "price": 5000,
                                   "file": "data:image/png;base64,iVBORw0KGgo=", "frametime": 90, "description": "Hot",
                                   "collection": "partners", "creator": "Amy"})
        self.assertTrue(r["ok"])
        cmd = self.p.pending()[0]
        self.assertEqual(cmd["action"], "additem")
        item = json.loads(cmd["args"][0])
        self.assertEqual(item, {"id": "fire_cloak", "name": "Fire Cloak", "category": "cloak", "price": 5000,
                                "description": "Hot", "collection": "partners", "creator": "Amy", "frametime": 90})
        self.assertEqual(cmd["args"][1], "iVBORw0KGgo=")
        bb = json.dumps({"elements": []})
        self.p.publish(admin, {"kind": "hat", "itemId": "box_hat", "name": "Box", "price": -1, "file": bb})
        import base64 as b64
        self.assertEqual(b64.b64decode(self.p.pending()[0]["args"][1]).decode(), bb)
        for bad in ({"kind": "hat", "itemId": "crown", "name": "X", "price": 1, "file": bb},  # already in the shop
                    {"kind": "hat", "itemId": "Bad Id", "name": "X", "price": 1, "file": bb},
                    {"kind": "hat", "itemId": "ok_id", "name": "", "price": 1, "file": bb},
                    {"kind": "hat", "itemId": "ok_id", "name": "X", "price": "free", "file": bb},
                    {"kind": "hat", "itemId": "ok_id", "name": "X", "price": 1, "file": "{nope"},
                    {"kind": "cloak", "itemId": "ok_id", "name": "X", "price": 1, "file": "data:image/gif;base64,AA"},
                    {"kind": "boots", "itemId": "ok_id", "name": "X", "price": 1, "file": bb}):
            with self.assertRaises(app.ApiError, msg=str(bad)):
                self.p.publish(admin, bad)
        self.assertTrue(any(a["action"] == "publish" for a in self.p.audit_page()["audit"]))

    def test_audit_is_paged(self):
        for i in range(120):
            self.p.audit("Owner", "test", "entry %d" % i)
        a = self.p.audit_page()
        self.assertEqual((len(a["audit"]), a["more"], a["audit"][0]["detail"]), (50, True, "entry 119"))
        b = self.p.audit_page(before=a["audit"][-1]["id"])
        self.assertEqual(b["audit"][0]["detail"], "entry 69")
        self.assertEqual(len(self.p.audit_page(q="entry 11")["audit"]), 11)

    def test_game_stats_and_leaderboards(self):
        # PlebNite and PlebRPG have their boards before their servers send anything
        before = {g["id"]: [s["id"] for s in g["stats"]] for g in self.p.stat_games()}
        self.assertEqual(list(before), ["plebmc", "plebnite", "plebrpg"])
        self.assertIn("kills", before["plebnite"])
        self.assertIn("mob_kills", before["plebrpg"])
        self.assertEqual(self.p.stats_top("plebrpg", "level")["rows"], [])
        self.p.sync({"players": [{"uuid": "u1", "name": "Bob", "coins": 50, "owned": ["a", "b"], "streak": 3},
                                 {"uuid": "u2", "name": "Ann", "coins": 90, "owned": [], "streak": 1}]})
        r = self.p.put_stats({"game": "plebnite", "labels": {"normal_wins": "Wins"}, "players": [
            {"name": "Bob", "stats": {"kills": 12, "normal_wins": 2, "play_time": 3600, "bad": "x", "flag": True}},
            {"name": "Ann", "stats": {"kills": 30, "normal_wins": 0}},
            {"name": "not a name!", "stats": {"kills": 99}}]})
        self.assertEqual(r["saved"], 5)
        games = self.p.stat_games()
        self.assertEqual([g["id"] for g in games], ["plebmc", "plebnite", "plebrpg"])
        nite = games[1]
        self.assertEqual(nite["title"], "PlebNite")
        self.assertEqual([(s["id"], s["label"]) for s in nite["stats"]],
                         [("kills", "Kills"), ("normal_wins", "Wins"), ("play_time", "Play Time")])
        self.assertEqual(nite["stats"][2]["kind"], "time")
        top = self.p.stats_top("plebnite", "kills")
        self.assertEqual([(x["place"], x["name"], x["value"]) for x in top["rows"]], [(1, "Ann", 30), (2, "Bob", 12)])
        # zeroes are left off a leaderboard
        self.assertEqual([x["name"] for x in self.p.stats_top("plebnite", "normal_wins")["rows"]], ["Bob"])
        self.assertEqual([x["name"] for x in self.p.stats_top("plebmc", "items")["rows"]], ["Bob"])
        self.assertEqual(self.p.stats_top("plebmc", "coins")["rows"][0]["name"], "Ann")
        # a newer push replaces a value
        self.p.put_stats({"game": "plebnite", "players": [{"name": "bob", "stats": {"kills": 40}}]})
        self.assertEqual(self.p.stats_top("plebnite", "kills")["rows"][0]["value"], 40)
        me = self.p.player_stats("BOB")
        by = {g["id"]: {s["id"]: (s["value"], s["place"]) for s in g["stats"]} for g in me["games"]}
        self.assertEqual(by["plebmc"]["coins"], (50, 2))
        self.assertEqual(by["plebnite"]["kills"], (40, 1))
        with self.assertRaises(app.ApiError):
            self.p.player_stats("Nobody")
        with self.assertRaises(app.ApiError):
            self.p.put_stats({"game": "", "players": []})
        with self.assertRaises(app.ApiError):
            self.p.stats_top("nogame", "x")
        # every stat's top 10 at once, like the Discord bot
        boards = self.p.stats_boards("plebnite")
        self.assertEqual([b["stat"]["id"] for b in boards["boards"]], ["kills", "normal_wins", "play_time"])
        self.assertEqual([x["name"] for x in boards["boards"][0]["rows"]], ["bob", "Ann"])
        self.assertEqual([b["stat"]["id"] for b in self.p.stats_boards("plebmc")["boards"]], ["coins", "items", "streak"])
        self.assertEqual(len(self.p.stats_boards("plebmc", 1)["boards"][0]["rows"]), 1)
        with self.assertRaises(app.ApiError):
            self.p.stats_boards("nogame")


class FakeGitHub:
    """Serves a folder the way GitHub does: a branch head, its trees and raw files."""

    def __init__(self, folder, when="2030-01-01T00:00:00Z"):
        self.files, self.when, self.calls = {}, when, []
        for root, _, names in os.walk(folder):
            for n in names:
                full = os.path.join(root, n)
                rel = os.path.relpath(full, folder).replace(os.sep, "/")
                if "__pycache__" not in rel and not rel.startswith(("data/", "BUILD")):
                    with open(full, "rb") as f:
                        self.files[rel] = f.read()

    @staticmethod
    def blob(data):
        return hashlib.sha1(b"blob %d\0" % len(data) + data).hexdigest()

    def tree(self):
        return hashlib.sha1(json.dumps(sorted((k, self.blob(v)) for k, v in self.files.items())).encode()).hexdigest()

    def fetch(self, url, accept=None, limit=0):
        self.calls.append(url)
        if "/branches/" in url:
            return json.dumps({"commit": {"sha": "c" * 40, "commit": {"committer": {"date": self.when}}}}).encode()
        if url.endswith("/git/trees/" + "c" * 40):
            return json.dumps({"tree": [{"path": "panel", "type": "tree", "sha": self.tree()}]}).encode()
        if "/git/trees/" in url:
            return json.dumps({"tree": [{"path": k, "type": "blob", "sha": self.blob(v), "size": len(v)} for k, v in self.files.items()]}).encode()
        return self.files[urllib.parse.unquote(url.split("/" + "c" * 40 + "/panel/", 1)[1])]


class PanelSkins(unittest.TestCase):
    def setUp(self):
        self.p, self.clock = make_panel()
        self.p.create_account("Steve_1", "password1")
        self.png = app.png_from_rgba(bytes([10, 20, 30, 255]) * (64 * 64))
        self.asked = []

    def b64(self, data):
        return __import__("base64").b64encode(data).decode()

    def fake_mojang(self, skin_url="http://textures.minecraft.net/texture/" + "ab" * 20):
        def fetch(url, limit=0):
            self.asked.append(url)
            if url.startswith(app.MOJANG_PROFILE):
                return json.dumps({"id": "0" * 32, "name": "Steve_1"}).encode() if url.lower().endswith("steve_1") else None
            if url.startswith(app.MOJANG_SESSION):
                tex = json.dumps({"textures": {"SKIN": {"url": skin_url}} if skin_url else {}}).encode()
                return json.dumps({"properties": [{"name": "textures", "value": self.b64(tex)}]}).encode()
            return self.png
        self.p.skin_fetch = fetch

    def test_only_real_skins_are_taken(self):
        self.assertIsNone(app.skin_problem(self.png))
        self.assertTrue(app.skin_problem(b"GIF89a"))
        self.assertTrue(app.skin_problem(self.png[:-5]))
        self.assertTrue(app.skin_problem(self.png + b"<script>"))
        self.assertTrue(app.skin_problem(app.png_from_rgba(bytes(4 * 32 * 32), 32, 32)))
        self.assertIsNone(app.skin_problem(app.png_from_rgba(bytes(4 * 64 * 32), 64, 32)))
        with self.assertRaises(app.ApiError):
            self.p.set_skin("Steve_1", png="not base64!")
        with self.assertRaises(app.ApiError):
            self.p.set_skin("Nobody", png=self.b64(self.png))

    def test_own_skin_from_png_or_pixels(self):
        self.p.skin_queue.extend(["x"] * 500)          # no lookups in this test
        self.assertIsNone(self.p.skin("Steve_1"))
        self.p.set_skin("steve_1", png="data:image/png;base64," + self.b64(self.png))
        self.assertEqual(self.p.skin("STEVE_1"), self.png)
        self.p.set_skin("Steve_1", pixels=self.b64(bytes([1, 2, 3, 255]) * 4096))
        self.assertIsNone(app.skin_problem(self.p.skin("Steve_1")))
        self.assertNotEqual(self.p.skin("Steve_1"), self.png)
        self.p.clear_skin("Steve_1")
        self.assertIsNone(self.p.skin("Steve_1"))

    def test_minecraft_lookup(self):
        self.fake_mojang()
        self.assertTrue(self.p.lookup_skin("steve_1"))
        self.assertEqual(self.p.skin("Steve_1"), self.png)
        self.assertTrue(self.asked[-1].startswith("https://textures.minecraft.net/texture/"))
        # the player's own skin wins and a later lookup leaves it alone
        mine = app.png_from_rgba(bytes([9, 9, 9, 255]) * 4096)
        self.p.set_skin("Steve_1", png=self.b64(mine))
        self.p.lookup_skin("steve_1")
        self.assertEqual(self.p.skin("Steve_1"), mine)

    def test_lookup_misses_and_outages(self):
        self.fake_mojang(skin_url="")
        self.assertTrue(self.p.lookup_skin("steve_1"))
        self.assertEqual(self.p.db.one("SELECT source FROM skins WHERE name_key='steve_1'")["source"], "none")
        self.fake_mojang(skin_url="https://evil.test/texture/" + "ab" * 20)
        self.p.lookup_skin("steve_1")
        self.assertFalse(any("evil" in u for u in self.asked))
        def down(url, limit=0):
            raise OSError("no network")
        self.p.skin_fetch = down
        self.p.clear_skin("Steve_1")
        self.assertFalse(self.p.lookup_skin("steve_1"))
        self.assertIsNone(self.p.db.one("SELECT 1 FROM skins WHERE name_key='steve_1'"))

    def test_lookups_only_for_known_names_and_when_on(self):
        self.fake_mojang()
        self.p.skin_pause = 0
        self.p.skin("Stranger")
        self.p.skin("../etc")
        self.assertEqual(self.p.skin_queue, [])
        self.p.set_site_content({"name": "Owner"}, dict(self.p.site_content(), mojangSkins=False))
        self.p.skin("Steve_1")
        self.assertEqual(self.p.skin_queue, [])
        self.p.set_site_content({"name": "Owner"}, dict(self.p.site_content(), mojangSkins=True))
        self.p.skin("Steve_1")
        for _ in range(100):
            if self.p.skin("Steve_1"):
                break
            __import__("time").sleep(0.02)
        self.assertEqual(self.p.skin("Steve_1"), self.png)
        # fresh for a day, then asked again
        n = len(self.asked)
        self.p.skin("Steve_1")
        self.assertEqual(len(self.asked), n)


class PanelRename(unittest.TestCase):
    def setUp(self):
        self.p, self.clock = make_panel()
        self.acc = self.p.create_account("OldName", "password1")
        self.p.create_account("Taken", "password1")
        self.p.sync({"players": [{"uuid": "u1", "name": "OldName", "coins": 50, "owned": [], "equipped": {}},
                                 {"uuid": "u2", "name": "Drifter", "coins": 5, "owned": [], "equipped": {}}]})
        self.p.put_stats({"game": "plebmc", "players": [{"name": "OldName", "stats": {"kills": 9}}]})
        self.p.set_skin("OldName", png=__import__("base64").b64encode(app.png_from_rgba(bytes(16384))).decode())

    def test_rename_moves_everything_and_tells_the_game(self):
        for bad in ("Taken", "taken", "Drifter", "x", "OldName", "bad name"):
            with self.assertRaises(app.ApiError):
                self.p.rename_account(self.p.account("OldName"), bad, "OldName")
        acc = self.p.rename_account(self.p.account("OldName"), "NewName", "OldName")
        self.assertEqual((acc["name"], acc["name_key"]), ("NewName", "newname"))
        self.assertIsNone(self.p.account("OldName"))
        self.assertTrue(self.p.skin("NewName"))
        self.assertEqual(self.p.db.one("SELECT name FROM stats WHERE name_key='newname'")["name"], "NewName")
        cmd = self.p.pending()[-1]
        self.assertEqual((cmd["target"], cmd["action"], cmd["args"]), ("OldName", "rename", ["NewName"]))
        self.p.ack(cmd["id"], True, "moved")
        self.assertIsNone(self.p.db.one("SELECT 1 FROM players WHERE name_key='oldname'"))
        self.assertEqual(self.p.web_login("NewName", "password1", "1.1.1.1")[1]["name"], "NewName")

    def test_seven_day_cooldown_for_players_not_admins(self):
        self.p.rename_account(self.p.account("OldName"), "NewName", "OldName")
        with self.assertRaises(app.ApiError) as e:
            self.p.rename_account(self.p.account("NewName"), "Third_1", "NewName")
        self.assertEqual(e.exception.status, 429)
        self.assertIn("7 days", e.exception.message)
        owner = self.p.create_account("Boss", "password1", "owner")
        self.p.admin_account(owner, {"name": "NewName", "action": "rename", "newName": "ByAdmin"})
        self.assertTrue(self.p.account("ByAdmin"))
        self.assertGreater(self.p.rename_wait(self.p.account("ByAdmin")), 6 * 86400)
        self.clock.t += 7 * 86400 + 1
        self.p.rename_account(self.p.account("ByAdmin"), "byadmin", "ByAdmin")      # only the capitals
        self.assertEqual(self.p.account("BYADMIN")["name"], "byadmin")


class PanelUpdater(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.up = app.Updater(os.path.join(self.tmp.name, "updates"))
        self.gh = FakeGitHub(app.HERE)
        self.up.fetch = self.gh.fetch
        self.restarts = []
        self.up.restart = lambda: self.restarts.append(1)

    def test_downloads_checks_and_installs(self):
        self.assertEqual(self.up.install(), "installed")
        build = self.gh.tree()
        folder = os.path.join(self.up.dir, build)
        self.assertEqual(app.Updater.build_of(folder)[0], build)
        self.assertTrue(os.path.isfile(os.path.join(folder, "static", "index.html")))
        self.assertFalse(os.path.exists(os.path.join(folder, "tests")))  # only what the site needs
        self.assertEqual(self.up.state()["current"], build)
        self.assertEqual(self.up.status["busy"], "restarting")

    def test_refuses_a_changed_file(self):
        real = self.gh.fetch
        self.up.fetch = lambda url, **kw: real(url, **kw) + (b"#" if url.endswith("/panel/app.py") else b"")
        with self.assertRaises(app.ApiError):
            self.up.install()
        self.assertEqual(self.up.state()["current"], "")
        self.assertEqual(os.listdir(self.up.dir) if os.path.isdir(self.up.dir) else [], [])

    def test_a_build_that_does_not_start_is_skipped(self):
        self.gh.files["app.py"] = b"raise SystemExit(3)\n"
        with self.assertRaises(app.ApiError):
            self.up.install()
        self.assertEqual(self.up.state()["bad"], [self.gh.tree()])
        before = len(self.gh.calls)
        self.assertEqual(self.up.install(), "bad")  # asked GitHub what is newest, downloaded nothing
        self.assertEqual(len(self.gh.calls) - before, 1)

    def test_never_goes_back_to_an_older_build(self):
        self.gh.when = "2001-01-01T00:00:00Z"
        real = app.Updater.build_of
        app.Updater.build_of = staticmethod(lambda folder: ("a" * 40, 2000000000) if folder == app.HERE else real(folder))
        self.addCleanup(setattr, app.Updater, "build_of", staticmethod(real))
        self.assertEqual(self.up.install(), "older")
        self.assertEqual(self.restarts, [])

    def test_a_build_that_fails_twice_is_dropped_at_start(self):
        build = "b" * 40
        os.makedirs(os.path.join(self.up.dir, build))
        with open(os.path.join(self.up.dir, build, "app.py"), "w") as f:
            f.write("")
        self.up.save_state({**self.up.state(), "base": self.up.base_id(), "current": build, "pending": build, "tries": 2})
        self.up.hand_over([])  # returns instead of starting it again
        st = self.up.state()
        self.assertEqual((st["current"], st["bad"]), ("", [build]))


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

    def test_skin_over_http(self):
        self.p.skin_queue.extend(["x"] * 500)          # no lookups from the test server
        self.p.create_account("Skin_Kid", "kidpass99")
        png = app.png_from_rgba(bytes([5, 6, 7, 255]) * 4096)
        b64 = __import__("base64").b64encode(png).decode()
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(self.base + "/api/skin/Skin_Kid", timeout=5) as r:
            self.assertEqual(r.status, 204)
        s, j, _ = self.call("/api/client/skin", {"name": "Skin_Kid", "token": "wrong", "png": b64})
        self.assertFalse(j["ok"])
        s, j, _ = self.call("/api/client/login", {"name": "Skin_Kid", "password": "kidpass99"})
        s, j, _ = self.call("/api/client/skin", {"name": "Skin_Kid", "token": j["token"], "png": b64})
        self.assertTrue(j["ok"], j)
        with opener.open(self.base + "/api/skin/skin_kid", timeout=5) as r:
            self.assertEqual((r.status, r.headers["Content-Type"], r.read()), (200, "image/png", png))
            tag = r.headers["ETag"]
        with self.assertRaises(urllib.error.HTTPError) as e:
            opener.open(urllib.request.Request(self.base + "/api/skin/skin_kid", headers={"If-None-Match": tag}), timeout=5)
        self.assertEqual(e.exception.code, 304)
        s, j, _ = self.call("/api/me/skin", {"remove": True})
        self.assertEqual(s, 401)
        self.p.skin_queue.clear()

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

    def test_client_start_screen(self):
        s, j, h = self.call("/api/client/register", {"name": "Kid_1", "password": "kidpass99"})
        self.assertTrue(j["ok"], j)
        self.assertEqual(h["Access-Control-Allow-Origin"], "*")
        self.assertIsNone(h["Set-Cookie"])
        tok = j["token"]
        s, j, _ = self.call("/api/client/check", {"name": "Kid_1", "token": tok})
        self.assertTrue(j["ok"])
        # the client's token also signs in on the server
        s, j, _ = self.server("token", {"name": "Kid_1", "token": tok})
        self.assertTrue(j["ok"])
        s, j, _ = self.call("/api/client/register", {"name": "kid_1", "password": "kidpass99"})
        self.assertFalse(j["ok"])
        s, j, _ = self.call("/api/client/login", {"name": "Nobody9", "password": "kidpass99"})
        self.assertIn("No account", j["error"])
        s, j, _ = self.call("/api/client/login", {"name": "Kid_1", "password": "kidpass99"})
        self.assertTrue(j["ok"] and j["token"])
        s, j, _ = self.call("/api/client/logout", {"token": tok})
        s, j, _ = self.call("/api/client/check", {"name": "Kid_1", "token": tok})
        self.assertFalse(j["ok"])
        # preflight
        req = urllib.request.Request(self.base + "/api/client/login", method="OPTIONS")
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(req, timeout=5) as r:
            self.assertEqual(r.status, 204)
            self.assertEqual(r.headers["Access-Control-Allow-Origin"], "*")

    def test_client_signup_limit(self):
        p, _ = make_panel(client_registrations_per_hour=2)
        p.signup_throttle.max = 2
        self.assertTrue(p.client_register("Aaa1", "password1", "5.5.5.5")["ok"])
        self.assertTrue(p.client_register("Aaa2", "password1", "5.5.5.5")["ok"])
        self.assertFalse(p.client_register("Aaa3", "password1", "5.5.5.5")["ok"])
        self.assertTrue(p.client_register("Aaa3", "password1", "6.6.6.6")["ok"])
        p.cfg.client_registration = False
        self.assertFalse(p.client_register("Aaa4", "password1", "6.6.6.6")["ok"])

    def test_public_routes_and_version(self):
        s, j, _ = self.call("/api/info")
        self.assertTrue(j["ok"] and "client" in j and "server" in j)
        s, j, _ = self.call("/api/leaderboard?offset=0&limit=5")
        self.assertEqual(s, 200)
        s, j, h = self.call("/api/client/version", {"version": "2.0", "platform": "web"})
        self.assertTrue(j["ok"])
        self.assertEqual(h["Access-Control-Allow-Origin"], "*")
        s, j, _ = self.call("/api/designs/public")
        self.assertEqual(s, 404)  # the gallery is gone
        s, _, _ = self.call("/api/designs/save", {"kind": "hat", "data": "{}"})
        self.assertEqual(s, 401)
        s, _, _ = self.call("/api/admin/dashboard")
        self.assertEqual(s, 401)
        s, _, _ = self.call("/api/admin/site", {"title1": "Hacked"})
        self.assertEqual(s, 401)
        s, _, _ = self.call("/api/site/img/0000000000000000")
        self.assertEqual(s, 404)

    def test_static_tools(self):
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        for path in ("/static/tools/hat.html", "/static/tools/cloak.html", "/static/tools/hat.js", "/static/vendor/three.min.js",
                     "/static/logo.png", "/static/home/wallpaper.jpg", "/static/fonts/jersey-25.woff2", "/static/plebio/idle.gif", "/static/plebio/still.png", "/static/home/client-menu.jpg",
                     "/static/home/client-hud.jpg"):
            with opener.open(urllib.request.Request(self.base + path), timeout=5) as r:
                self.assertEqual(r.status, 200, path)
                self.assertIn("script-src 'self'", r.headers["Content-Security-Policy"])

    def test_short_links_open_their_page(self):
        import http.client
        host, port = self.base[len("http://"):].split(":")
        for path, to in (("/download", "/#downloads"), ("/Downloads/", "/#downloads"), ("/vote", "/#vote"), ("/leaderboards", "/#leaderboard"),
                         ("/account", "/#me")):
            c = http.client.HTTPConnection(host, int(port), timeout=5)
            c.request("GET", path)
            r = c.getresponse()
            self.assertEqual((r.status, r.getheader("Location")), (302, to), path)
            c.close()
        c = http.client.HTTPConnection(host, int(port), timeout=5)
        c.request("GET", "/something-else")
        self.assertEqual(c.getresponse().status, 200)      # anything else is still the site itself
        c.close()

    def test_static_and_headers(self):
        req = urllib.request.Request(self.base + "/")
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(req, timeout=5) as r:
            page = r.read()
            self.assertIn(b"<title>PlebMc</title>", page)
            # links carry a version, so an update is never mixed with a cached old app.js
            self.assertRegex(page, rb'src="/static/app.js\?v=[1-9][0-9]+"')
            self.assertRegex(page, rb'href="/static/style.css\?v=[1-9][0-9]+"')
            self.assertEqual(r.headers["X-Frame-Options"], "DENY")
        s, _, _ = self.call("/static/../app.py")
        self.assertEqual(s, 404)


    def test_punishments_are_for_admins(self):
        s, _, _ = self.call("/api/admin/punishments")
        self.assertEqual(s, 401)
        s, _, _ = self.call("/api/server/punishments", {"rows": []}, headers={"Authorization": "Bearer nope"})
        self.assertEqual(s, 401)

    def test_stats_key_only_sends_stats(self):
        self.p.cfg.stats_key = "s" * 40
        try:
            auth = {"Authorization": "Bearer " + "s" * 40}
            s, b, _ = self.call("/api/server/stats", {"game": "plebrpg", "players": [{"name": "Cal", "stats": {"level": 7}}]},
                                headers=auth)
            self.assertEqual((s, b["saved"]), (200, 1))
            s, _, _ = self.call("/api/server/sync", {"players": []}, headers=auth)
            self.assertEqual(s, 401)
            s, _, _ = self.call("/api/server/stats", {"game": "plebrpg", "players": []},
                                headers={"Authorization": "Bearer nope"})
            self.assertEqual(s, 401)
            s, b, _ = self.call("/api/stats/top?game=plebrpg&stat=level")
            self.assertEqual((s, b["rows"][0]["name"], b["stat"]["label"]), (200, "Cal", "Level"))
            s, b, _ = self.call("/api/stats/player?name=cal")
            self.assertEqual((s, b["name"]), (200, "Cal"))
            s, b, _ = self.call("/api/stats/games")
            self.assertIn("plebrpg", [g["id"] for g in b["games"]])
            s, b, _ = self.call("/api/stats/boards?game=plebrpg")
            lv = next(x for x in b["boards"] if x["stat"]["id"] == "level")
            self.assertEqual((s, lv["rows"][0]["name"]), (200, "Cal"))
        finally:
            self.p.cfg.stats_key = ""


if __name__ == "__main__":
    unittest.main()
