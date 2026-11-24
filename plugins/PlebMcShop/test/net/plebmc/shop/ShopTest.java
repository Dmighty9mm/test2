package net.plebmc.shop;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

import com.google.gson.Gson;

import net.plebmc.client.cosmetics.Cosmetics;
import net.plebmc.client.network.PluginChannels;

/** Drives ShopCore through a fake proxy, and the real client code against it. Run with: sh test/run.sh */
public class ShopTest {
    static int checks, failed;
    static void check(boolean ok, String what) { checks++; if (!ok) { failed++; System.out.println("FAIL " + what); } }

    /** A fake BungeeCord: who's online, on which server, with which permissions, and what they were sent. */
    static class FakeNet implements ShopCore.Net {
        final Map<UUID, P> online = new LinkedHashMap<>();
        long now = QuestsTest.at(2026, 11, 23, 12, 0);
        public void send(UUID p, String json) { online.get(p).raw.add(json); online.get(p).got.add(new Gson().fromJson(json, Map.class)); }
        public void chat(UUID p, String m) { online.get(p).chat.add(m); }
        public List<UUID> online() { return new ArrayList<>(online.keySet()); }
        public String server(UUID p) { P x = online.get(p); return x == null ? null : x.server; }
        public String name(UUID p) { P x = online.get(p); return x == null ? null : x.name; }
        public boolean hasPermission(UUID p, String perm) { P x = online.get(p); return x != null && x.perms.contains(perm); }
        public long now() { return now; }
        public void async(Runnable r) { r.run(); }
    }

    static class P {
        final String name; final UUID id; String server; final Set<String> perms = new HashSet<>();
        final List<Map<?, ?>> got = new ArrayList<>(); final List<String> raw = new ArrayList<>(); final List<String> chat = new ArrayList<>();
        P(String n) { name = n; id = UUID.nameUUIDFromBytes(n.getBytes(StandardCharsets.UTF_8)); }
        Map<?, ?> last(String type) { for (int i = got.size() - 1; i >= 0; i--) if (type.equals(got.get(i).get("type"))) return got.get(i); return null; }
        long coins() { return ((Number) last("state").get("coins")).longValue(); }
        Map<?, ?> quest(String id) {
            for (Object o : (List<?>) last("state").get("quests")) if (id.equals(((Map<?, ?>) o).get("id"))) return (Map<?, ?>) o;
            return null;
        }
        long progress(String id) { Map<?, ?> q = quest(id); return q == null ? -1 : ((Number) q.get("progress")).longValue(); }
        boolean done(String id) { Map<?, ?> q = quest(id); return q != null && Boolean.TRUE.equals(q.get("done")); }
    }

    /** A fake NamelessMC API: one account "Master" linked by code ABC123. */
    static class FakeSite implements Website.Http {
        final List<String> calls = new ArrayList<>();
        String lastBody = "";
        boolean linked, verified = true, down;
        String linkedId = "";
        public Object[] request(String method, String url, String key, String body) throws java.io.IOException {
            calls.add(method + " " + url + " key=" + key);
            if (down) throw new java.io.IOException("connect timed out");
            if (body != null) lastBody = body;
            if (url.endsWith("/api/v2/integration/verify")) {
                Map<?, ?> b = new Gson().fromJson(body, Map.class);
                if (!"ABC123".equals(b.get("code"))) return new Object[] { 400, "{\"error\":\"core:invalid_code\"}" };
                linked = true; linkedId = (String) b.get("identifier");
                return new Object[] { 200, "{\"message\":\"ok\"}" };
            }
            if (url.contains("/api/v2/users/integration_id:minecraft:")) {
                String id = url.substring(url.lastIndexOf(':') + 1);
                if (!linked || !id.equals(linkedId)) return new Object[] { 400, "{\"error\":\"nameless:cannot_find_user\"}" };
                return new Object[] { 200, "{\"id\":7,\"username\":\"Master\",\"displayname\":\"MasterPleb\",\"banned\":false,"
                        + "\"groups\":[{\"id\":1,\"name\":\"Member\",\"staff\":false,\"order\":5},{\"id\":2,\"name\":\"Owner\",\"staff\":true,\"order\":1}],"
                        + "\"integrations\":[{\"integration\":\"Minecraft\",\"identifier\":\"" + linkedId + "\",\"username\":\"Webby\",\"verified\":" + verified + "}]}" };
            }
            return new Object[] { 404, "{\"error\":\"nameless:invalid_api_method\"}" };
        }
    }

    static FakeNet net;
    static ShopCore core;

    /** Joins like the real thing: the PlebMc client says "open" on join, the proxy calls connected() a moment later. */
    static P join(String name, String server, boolean client) {
        P p = new P(name);
        p.server = server;
        net.online.put(p.id, p);
        if (client) core.fromClient(p.id, "{\"type\":\"open\"}");
        core.connected(p.id, null, true);
        return p;
    }
    static void quit(P p) { net.online.remove(p.id); core.quit(p.id, p.server); }
    static void move(P p, String to) { String from = p.server; p.server = to; core.connected(p.id, from, false); }
    static void sleepCooldown() throws InterruptedException { Thread.sleep(320); }

    static List<String> admin = new ArrayList<>();
    static void cmd(String... args) {
        core.command(new ShopCore.Sender() {
            public void reply(String line) { admin.add(line); }
            public boolean hasPermission(String perm) { return true; }
        }, "plebshop", args, () -> {});
    }

    static Cfg cfg(String... replace) throws Exception {
        return QuestsTest.config(replace);
    }

    public static void main(String[] a) throws Exception {
        File dir = Files.createTempDirectory("plebshop").toFile();
        Logger log = Logger.getLogger("test");
        log.setLevel(java.util.logging.Level.WARNING);
        net = new FakeNet();
        // every quest active, so the test doesn't depend on today's pick
        core = new ShopCore(net, new Store(new File(dir, "players.yml"), log),
                cfg("daily-count: 3", "daily-count: 99", "weekly-count: 2", "weekly-count: 99"), log);
        check(core.problems() == 0, "config loads cleanly");

        // ---- joining the lobby: daily login reward ----
        P mighty = join("Mighty", "lobby", true);
        P java = join("JavaGuy", "lobby", false);
        Map<?, ?> st = mighty.last("state");
        check(st != null && "PlebMc Coins".equals(st.get("currency")), "state sent");
        List<?> cats = (List<?>) st.get("questCategories");
        check(cats.size() == 2 && "Lobby".equals(((Map<?, ?>) cats.get(0)).get("tab")) && "PlebNite".equals(((Map<?, ?>) cats.get(1)).get("tab")),
                "questCategories: " + cats);
        Map<?, ?> withCatalog = null;
        for (Map<?, ?> m : mighty.got) if (m.containsKey("catalog")) withCatalog = m;
        check(withCatalog != null && ((List<?>) withCatalog.get("catalog")).size() == 19, "catalog has 19 items");
        check(mighty.coins() == 50, "day 1 login reward: " + mighty.coins());
        check(String.valueOf(mighty.last("notify").get("message")).contains("+50 PlebMc Coins"), "login popup");
        check(mighty.done("login") && mighty.quest("login").get("category") == null, "login row done, in every tab");
        check(mighty.progress("login_5") == 1, "weekly login quest counted");
        check(mighty.progress("lobby_join_3") == 1 && "lobby".equals(mighty.quest("lobby_join_3").get("category")), "lobby join counted");
        check(java.got.isEmpty() && java.chat.stream().anyMatch(c -> c.contains("+50")), "other clients get chat: " + java.chat);
        quit(mighty);
        mighty = join("Mighty", "lobby", true);
        check(mighty.coins() == 50, "no second login reward the same day");

        // ---- categories: lobby kills don't count on PlebNite and the other way round ----
        core.progress(mighty.id, "lobby", "kill", 3);
        check(mighty.done("lobby_pvp_3") && mighty.progress("kills_3") == 0, "lobby pvp only in the lobby");
        long c0 = mighty.coins();
        core.progress(mighty.id, "lobby", "parkour", 1);
        check(mighty.done("lobby_parkour") && mighty.progress("lobby_parkour_5") == 1, "parkour");
        check(mighty.coins() > c0, "parkour paid");
        move(mighty, "plebnite");
        check(mighty.coins() == mighty.coins() && mighty.progress("lobby_join_3") == 2, "switching servers: no login reward, join counted");
        long before = mighty.coins();
        core.progress(mighty.id, "plebnite", "kill", 3);
        check(mighty.done("kills_3") && mighty.progress("kills_8") == 3 && mighty.progress("lobby_pvp_3") == 3, "plebnite kills");
        check(mighty.coins() == before + 75, "kills_3 reward: " + (mighty.coins() - before));
        check(String.valueOf(mighty.last("notify").get("message")).contains("Get 3 kills: +75"), "quest popup");
        before = mighty.coins();
        cmd("progress", "Mighty", "win");
        check(mighty.done("win_1") && mighty.coins() == before + 150, "win via /plebshop progress on the player's server");
        // minutes count as "play" in the category of the server you're on
        java.server = "lobby";
        for (int i = 0; i < 15; i++) core.minute();
        check(mighty.progress("play_30") == 15 && mighty.progress("lobby_play_20") == 0, "playtime on plebnite");
        move(mighty, "lobby");
        for (int i = 0; i < 5; i++) core.minute();
        check(mighty.progress("play_30") == 15 && mighty.progress("lobby_play_20") == 5, "playtime in the lobby");
        core.progress(mighty.id, "survival", "kill", 5);
        check(mighty.progress("kills_8") == 3 && mighty.progress("lobby_pvp_3") == 3, "servers without a category count nothing");

        // ---- the shop ----
        cmd("coins", "set", "Mighty", "10000");
        mighty.got.clear();
        core.fromClient(mighty.id, "{\"type\":\"buy\",\"id\":\"crown\"}");
        check(Boolean.TRUE.equals(mighty.last("result").get("ok")) && mighty.coins() == 2000, "crown bought for 8000");
        Map<?, ?> eq = (Map<?, ?>) mighty.last("state").get("equipped");
        check("crown".equals(eq.get("hat")), "auto-equipped");
        String saved = new String(Files.readAllBytes(new File(dir, "players.yml").toPath()), StandardCharsets.UTF_8);
        check(saved.contains("crown") && saved.contains("coins: 2000"), "saved right away");
        core.fromClient(mighty.id, "{\"type\":\"buy\",\"id\":\"halo\"}");
        check(mighty.coins() == 2000, "cooldown blocks a second action");
        sleepCooldown();
        core.fromClient(mighty.id, "{\"type\":\"buy\",\"id\":\"rainbow\"}");
        check(Boolean.FALSE.equals(mighty.last("result").get("ok")), "not enough coins");
        sleepCooldown();
        core.fromClient(mighty.id, "{\"type\":\"buy\",\"id\":\"dropzone_crown\"}");
        check(Boolean.FALSE.equals(mighty.last("result").get("ok")), "not-for-sale refused");

        // ---- other players see it, per server ----
        P poor = join("Poor", "lobby", true);
        P far = join("Far", "plebnite", true);
        Map<?, ?> snap = null;
        for (Map<?, ?> m : poor.got) if (Boolean.TRUE.equals(m.get("clear"))) snap = m;
        Map<?, ?> ms = (Map<?, ?>) ((Map<?, ?>) snap.get("players")).get(mighty.id.toString());
        check(ms != null && "crown".equals(ms.get("hat")), "lobby player sees Mighty's crown");
        Map<?, ?> farSnap = null;
        for (Map<?, ?> m : far.got) if (Boolean.TRUE.equals(m.get("clear"))) farSnap = m;
        check(!((Map<?, ?>) farSnap.get("players")).containsKey(mighty.id.toString()), "plebnite player doesn't get lobby players");
        sleepCooldown();
        core.fromClient(mighty.id, "{\"type\":\"equip\",\"category\":\"hat\",\"id\":null}");
        Map<?, ?> pm = (Map<?, ?>) poor.last("players").get("players");
        check(pm.containsKey(mighty.id.toString()) && pm.get(mighty.id.toString()) == null, "take off sends null");
        sleepCooldown();
        core.fromClient(poor.id, "{\"type\":\"equip\",\"category\":\"hat\",\"id\":\"crown\"}");
        check(Boolean.FALSE.equals(poor.last("result").get("ok")), "can't equip what you don't own");
        for (String junk : new String[] { "not json", "[1,2]", "{\"type\":\"buy\",\"id\":5}", "{\"type\":\"equip\",\"category\":\"x\"}", "null" })
            core.fromClient(poor.id, junk);

        // ---- the real client code reads it ----
        PluginChannels.reset();
        final P me = mighty;
        PluginChannels.attach((channel, data) -> {
            if (Cosmetics.CHANNEL.equals(channel)) core.fromClient(me.id, new String(data, StandardCharsets.UTF_8));
            return true;
        });
        Cosmetics.init(null);
        PluginChannels.onJoin();
        Cosmetics.setSelf(mighty.id.toString());
        for (String m : mighty.raw) Cosmetics.handle(m);
        check(Cosmetics.questCategories().size() == 2 && "PlebMc Lobby".equals(Cosmetics.questCategories().get(0).name), "client categories");
        List<Cosmetics.Quest> lq = Cosmetics.quests("lobby"), nq = Cosmetics.quests("plebnite");
        check(lq.size() == 1 + 4 + 3 && nq.size() == 1 + 6 + 4, "client quests per tab: " + lq.size() + "/" + nq.size());
        check("login".equals(lq.get(0).id) && "login".equals(nq.get(0).id), "login row in both tabs");
        Cosmetics.Quest k8 = null;
        for (Cosmetics.Quest q : nq) if (q.id.equals("kills_8")) k8 = q;
        check(k8 != null && k8.progress == 3 && k8.goal == 8 && "plebnite".equals(k8.category), "client quest fields");
        check(Cosmetics.coins() == mighty.coins(), "client coins");
        int got = mighty.got.size();
        core.fromClient(mighty.id, "{\"type\":\"dev\",\"action\":\"coins\",\"amount\":5}");
        check(mighty.got.size() == got, "old dev messages are ignored");

        // ---- plebmc.top login (a fake NamelessMC) ----
        FakeSite site = new FakeSite();
        Cfg withSite = cfg("daily-count: 3", "daily-count: 99", "weekly-count: 2", "weekly-count: 99", "api-key: ''", "api-key: 'secret123'");
        ShopCore old = core;
        core = new ShopCore(net, old.store, withSite, log, site);
        check(core.website.configured() && core.website.url.equals("https://plebmc.top"), "website configured");
        P web = join("Webby", "lobby", true);
        Map<?, ?> acc = web.last("account");
        check(acc != null && Boolean.FALSE.equals(acc.get("linked")) && Boolean.TRUE.equals(acc.get("enabled")), "not linked yet: " + acc);
        check(String.valueOf(acc.get("linkUrl")).equals("https://plebmc.top/index.php?route=/user/connections"), "link url");
        check(site.calls.get(0).startsWith("GET https://plebmc.top/index.php?route=/api/v2/users/integration_id:minecraft:" + Website.identifier(web.id))
                && site.calls.get(0).endsWith("key=secret123"), "lookup call: " + site.calls.get(0));
        core.fromClient(web.id, "{\"type\":\"link\",\"code\":\"bad code!\"}");
        check(Boolean.FALSE.equals(web.last("account").get("ok")) && site.calls.size() == 1, "junk code refused without calling the site");
        core.fromClient(web.id, "{\"type\":\"link\",\"code\":\"WRONG1\"}");
        check(String.valueOf(web.last("account").get("message")).contains("isn't right"), "wrong code: " + web.last("account"));
        core.fromClient(web.id, "{\"type\":\"link\",\"code\":\"ABC123\"}");
        check(String.valueOf(web.last("account").get("message")).contains("Wait"), "link cooldown");
        Thread.sleep(4100);
        core.fromClient(web.id, "{\"type\":\"link\",\"code\":\"/verify ABC123\"}");
        acc = web.last("account");
        check(Boolean.TRUE.equals(acc.get("linked")) && "Master".equals(acc.get("username")) && "MasterPleb".equals(acc.get("displayName")), "linked: " + acc);
        check(acc.get("groups").toString().equals("[Owner, Member]") && Boolean.TRUE.equals(acc.get("staff")), "groups by rank: " + acc.get("groups"));
        check(site.lastBody.contains("\"code\":\"ABC123\"") && site.lastBody.contains("\"username\":\"Webby\"") && site.lastBody.contains(Website.identifier(web.id)), "verify body: " + site.lastBody);
        // next join: logged in straight away, with a popup
        quit(web);
        web = join("Webby", "lobby", true);
        check(Boolean.TRUE.equals(web.last("account").get("linked")), "still logged in after rejoin");
        check(String.valueOf(web.last("notify").get("message")).contains("Logged in as MasterPleb"), "login popup");
        admin.clear();
        cmd("info", "Webby");
        check(admin.get(admin.size() - 1).contains("Master [Owner, Member]"), "info shows website account: " + admin);
        // unlinked on the website: logged out next join
        site.verified = false;
        quit(web);
        web = join("Webby", "lobby", true);
        check(Boolean.FALSE.equals(web.last("account").get("linked")), "unlinked on the website = logged out");
        // the website being down doesn't break anything
        site.down = true;
        quit(web);
        web = join("Webby", "lobby", true);
        check(web.coins() >= 0 && Boolean.FALSE.equals(web.last("account").get("ok")), "website down: error shown, shop still works");
        site.down = false;
        // the real client reads it
        Cosmetics.handle("{\"type\":\"account\",\"enabled\":true,\"linked\":true,\"username\":\"Master\",\"displayName\":\"MasterPleb\",\"groups\":[\"Owner\"],\"staff\":true,\"site\":\"https://plebmc.top\",\"linkUrl\":\"x\"}");
        check(Cosmetics.account().linked && "Owner".equals(Cosmetics.account().rank()) && "MasterPleb".equals(Cosmetics.account().displayName), "client account");
        // no key = feature off
        check(!old.website.configured(), "no api key: login off");
        quit(web);
        core = old;

        // ---- admin commands ----
        admin.clear();
        cmd("balance", "Mighty");
        check(admin.get(0).contains("Mighty"), "balance: " + admin);
        cmd("give", "Poor", "dropzone_crown");
        cmd("equip", "Poor", "hat", "dropzone_crown");
        check("dropzone_crown".equals(((Map<?, ?>) poor.last("state").get("equipped")).get("hat")), "admin give + equip");
        check(core.tabComplete(new String[] { "equip", "Poor", "trail", "r" }).contains("rainbow"), "tab complete");
        check(core.tabComplete(new String[] { "progress", "Mighty", "pa" }).contains("parkour"), "tab complete parkour");
        admin.clear();
        cmd("quests");
        check(admin.size() == 1 + 17 && admin.get(1).contains("lobby"), "quests command lists categories");
        admin.clear();
        core.command(new ShopCore.Sender() {
            public void reply(String line) { admin.add(line); }
            public boolean hasPermission(String perm) { return false; }
        }, "plebshop", new String[] { "coins", "set", "Poor", "999999" }, () -> {});
        check(admin.get(0).contains("permission") && poor.coins() != 999999, "commands need plebshop.admin");

        // ---- hidden worlds ----
        core.load(cfg("hidden-in-worlds: []", "hidden-in-worlds: ['lobby:arena1']"));
        core.world(poor.id, "arena1");
        pm = (Map<?, ?>) mighty.last("players").get("players");
        check(pm.containsKey(poor.id.toString()) && pm.get(poor.id.toString()) == null, "hidden in lobby:arena1");
        core.world(poor.id, "spawn");
        pm = (Map<?, ?>) mighty.last("players").get("players");
        check(pm.get(poor.id.toString()) != null, "shown again outside");

        // ---- leaving tells the old server ----
        quit(poor);
        pm = (Map<?, ?>) mighty.last("players").get("players");
        check(pm.containsKey(poor.id.toString()) && pm.get(poor.id.toString()) == null, "quit sends null");

        // ---- restart keeps everything; a new day gives the next streak reward ----
        long keep = mighty.coins();
        core.save();
        net = new FakeNet();
        net.now += 24 * 3600_000L;
        core = new ShopCore(net, new Store(new File(dir, "players.yml"), log), cfg(), log);
        P back = join("Mighty", "plebnite", true);
        check(back.coins() == keep + 75, "coins survive restart, day 2 reward: " + back.coins() + " vs " + keep);
        check(((List<?>) back.last("state").get("owned")).contains("crown") && back.progress("login_5") == 2, "progress survives restart");
        check(((Map<?, ?>) back.quest("login")).get("reward").equals(75.0), "streak day 2");

        System.out.println(checks - failed + "/" + checks + " plugin checks passed");
        int q = QuestsTest.run();
        System.exit(failed == 0 && q == 0 ? 0 : 1);
    }
}
