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

        // ---- the secret dev panel: silent for normal players ----
        int gotBefore = poor.got.size();
        long poorCoins = poor.coins();
        for (String act : new String[] { "open", "coins", "unlockall", "complete" })
            core.fromClient(poor.id, "{\"type\":\"dev\",\"action\":\"" + act + "\",\"amount\":99999}");
        check(poor.got.size() == gotBefore && poor.last("dev") == null, "no answer at all without plebshop.dev");
        check(poor.coins() == poorCoins && ((List<?>) poor.last("state").get("owned")).isEmpty(), "and nothing changed");

        // ---- the dev panel through the real client code ----
        mighty.perms.add(ShopCore.DEV);
        PluginChannels.reset();
        final P dev = mighty;
        PluginChannels.attach((channel, data) -> {
            if (Cosmetics.CHANNEL.equals(channel)) core.fromClient(dev.id, new String(data, StandardCharsets.UTF_8));
            return true;
        });
        Cosmetics.init(null);
        PluginChannels.onJoin();
        Cosmetics.setSelf(mighty.id.toString());
        List<String> toasts = new ArrayList<>();
        Cosmetics.setToasts((t, m) -> toasts.add(t + "|" + m));
        int seen = 0;
        Runnable deliver = null;
        final int[] at = { 0 };
        deliver = () -> { while (at[0] < dev.raw.size()) Cosmetics.handle(dev.raw.get(at[0]++)); };
        deliver.run();
        check(Cosmetics.questCategories().size() == 2 && "PlebMc Lobby".equals(Cosmetics.questCategories().get(0).name), "client categories");
        List<Cosmetics.Quest> lq = Cosmetics.quests("lobby"), nq = Cosmetics.quests("plebnite");
        check(lq.size() == 1 + 4 + 3 && nq.size() == 1 + 6 + 4, "client quests per tab: " + lq.size() + "/" + nq.size());
        check("login".equals(lq.get(0).id) && "login".equals(nq.get(0).id), "login row in both tabs");
        Cosmetics.Quest k8 = null;
        for (Cosmetics.Quest q : nq) if (q.id.equals("kills_8")) k8 = q;
        check(k8 != null && k8.progress == 3 && k8.goal == 8 && "plebnite".equals(k8.category), "client quest fields");
        check(!Cosmetics.devAllowed(), "not a dev until the server says so");

        Cosmetics.dev("open"); deliver.run();
        check(Cosmetics.devAllowed() && Cosmetics.devMessage() == null, "dev panel opens for plebshop.dev");
        check(Cosmetics.devInfo().size() == 5 && Cosmetics.devInfo().get(2).contains("lobby -> category lobby"), "dev info: " + Cosmetics.devInfo());
        check(Cosmetics.devQuests().size() == 17 && Cosmetics.devQuests().get(0)[2].equals("lobby"), "dev quest list: " + Cosmetics.devQuests().size());
        int v = Cosmetics.devVersion();
        long c = Cosmetics.coins();
        Cosmetics.dev("coins", "amount", 1000L); deliver.run();
        check(Cosmetics.coins() == c + 1000 && "+1,000 coins".equals(Cosmetics.devMessage()) && Cosmetics.devVersion() != v, "+1000");
        Cosmetics.dev("zero"); deliver.run();
        check(Cosmetics.coins() == 0, "coins to 0");
        Cosmetics.dev("complete", "id", "kills_8"); deliver.run();
        check(Cosmetics.coins() == 150 && "Finished 1 quest".equals(Cosmetics.devMessage()), "finish one quest: " + Cosmetics.coins());
        check(toasts.stream().anyMatch(t -> t.contains("Quest complete")), "finishing shows the normal popup");
        Cosmetics.dev("complete", "id", "kills_8"); deliver.run();
        check("Finished 0 quests".equals(Cosmetics.devMessage()), "can't finish twice");
        Cosmetics.dev("progress", "questType", "parkour", "category", "lobby"); deliver.run();
        check(mighty.progress("lobby_parkour_5") == 2 && mighty.progress("kills_40") == 3, "+1 progress in one category");
        Cosmetics.dev("complete"); deliver.run();
        boolean allDone = true;
        for (Cosmetics.Quest q : Cosmetics.quests()) allDone &= q.done;
        check(allDone && Cosmetics.coins() > 150, "finish all");
        Cosmetics.dev("resetquests"); deliver.run();
        boolean noneDone = true;
        for (Cosmetics.Quest q : Cosmetics.quests()) if (!q.id.equals("login")) noneDone &= !q.done && q.progress == 0;
        check(noneDone, "reset quests");
        Cosmetics.dev("claimlogin"); deliver.run();
        check("Login reward already claimed".equals(Cosmetics.devMessage()), "login already claimed");
        Cosmetics.dev("resetlogin"); deliver.run();
        c = Cosmetics.coins();
        Cosmetics.dev("claimlogin"); deliver.run();
        check(Cosmetics.coins() == c + 50 && mighty.progress("login_5") == 1, "reset + claim login: " + (Cosmetics.coins() - c));
        Cosmetics.dev("unlockall"); deliver.run();
        check(Cosmetics.owns("rainbow") && Cosmetics.owns("dropzone_crown"), "unlock all");
        Cosmetics.dev("lockall"); deliver.run();
        check(!Cosmetics.owns("crown") && Cosmetics.equippedId("hat") == null, "remove all");
        int before2 = Cosmetics.devVersion();
        Cosmetics.dev("nonsense"); deliver.run();
        check(Cosmetics.devVersion() == before2, "unknown action ignored");
        check(Cosmetics.protocolLog().size() > 5 && Cosmetics.protocolLog().get(Cosmetics.protocolLog().size() - 1).contains("\"dev\""), "protocol log");
        // a dev only ever touches their own account
        check(poor.coins() == poorCoins, "dev actions don't touch other players");
        mighty.perms.clear();
        int n = mighty.got.size();
        Cosmetics.dev("coins", "amount", 5L); deliver.run();
        check(mighty.got.size() == n, "permission taken away: silent again");

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
        check(((List<?>) back.last("state").get("owned")).isEmpty() && back.progress("login_5") == 2, "progress survives restart");
        check(((Map<?, ?>) back.quest("login")).get("reward").equals(75.0), "streak day 2");

        System.out.println(checks - failed + "/" + checks + " plugin checks passed");
        int q = QuestsTest.run();
        System.exit(failed == 0 && q == 0 ? 0 : 1);
    }
}
