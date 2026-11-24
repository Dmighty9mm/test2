import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.google.gson.Gson;

import net.plebmc.client.cosmetics.Cosmetics;
import net.plebmc.shop.PlebMcShop;

/** Drives PlebMcShop against the fake server in fake/. Run with: sh test/run.sh */
public class ShopTest {
    static int checks, failed;
    static void check(boolean ok, String what) {
        checks++;
        if (!ok) { failed++; System.out.println("FAIL " + what); }
    }

    static class FakePlayer implements Player {
        final String name; final UUID id; final Set<String> channels = new HashSet<>();
        final List<Map<?, ?>> got = new ArrayList<>(); final List<String> raw = new ArrayList<>(); final List<String> chat = new ArrayList<>();
        String world = "BattleRoyaleLobby"; boolean op; Player killer;
        FakePlayer(String n) { name = n; id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + n).getBytes(StandardCharsets.UTF_8)); }
        public UUID getUniqueId() { return id; }
        public String getName() { return name; }
        public void sendMessage(String m) { chat.add(m); }
        public boolean hasPermission(String p) { return op; }
        public void sendPluginMessage(org.bukkit.plugin.Plugin p, String c, byte[] d) {
            if (!channels.contains(c)) throw new IllegalStateException("not registered");
            raw.add(new String(d, StandardCharsets.UTF_8));
            got.add(new Gson().fromJson(raw.get(raw.size() - 1), Map.class));
        }
        public Set<String> getListeningPluginChannels() { return channels; }
        public World getWorld() { return () -> world; }
        public boolean isOnline() { return Bukkit.online.contains(this); }
        public Player getKiller() { return killer; }
        Map<?, ?> last(String type) { for (int i = got.size() - 1; i >= 0; i--) if (type.equals(got.get(i).get("type"))) return got.get(i); return null; }
        long coins() { return ((Number) last("state").get("coins")).longValue(); }
        void send(String json) { Bukkit.incoming.onPluginMessageReceived("plebmc:shop", this, json.getBytes(StandardCharsets.UTF_8)); }
    }

    static PlebMcShop plugin;
    static FakePlayer join(String name, boolean client) {
        FakePlayer p = new FakePlayer(name);
        Bukkit.online.add(p);
        plugin.onJoin((PlayerJoinEvent) new PlayerJoinEvent().with(p));
        if (client) {
            p.channels.add("plebmc:shop");
            PlayerRegisterChannelEvent e = new PlayerRegisterChannelEvent();
            e.with(p); e.channel = "plebmc:shop";
            plugin.onRegister(e);
            p.send("{\"type\":\"open\"}");
        }
        Bukkit.runLater();
        return p;
    }
    static void sleepCooldown() throws InterruptedException { Thread.sleep(320); }
    static void cmd(Player who, String... args) { plugin.onCommand(who, null, "plebshop", args); }
    static Map<?, ?> quest(FakePlayer p, String id) {
        for (Object o : (List<?>) p.last("state").get("quests")) if (id.equals(((Map<?, ?>) o).get("id"))) return (Map<?, ?>) o;
        return null;
    }
    static String config(File dir) throws Exception { return new String(Files.readAllBytes(new File(dir, "config.yml").toPath()), StandardCharsets.UTF_8); }
    static void config(File dir, String s) throws Exception { Files.write(new File(dir, "config.yml").toPath(), s.getBytes(StandardCharsets.UTF_8)); }

    public static void main(String[] a) throws Exception {
        File dir = Files.createTempDirectory("plebshop").toFile();
        JavaPlugin.folder = dir;
        plugin = new PlebMcShop();
        plugin.onEnable();
        // every quest active, so the test doesn't depend on today's pick
        config(dir, config(dir).replace("daily-count: 3", "daily-count: 99").replace("weekly-count: 2", "weekly-count: 99"));
        FakePlayer admin = new FakePlayer("Admin"); admin.op = true;
        cmd(admin, "reload");

        // ---- joining: daily login reward ----
        FakePlayer mighty = join("Mighty", true);
        FakePlayer java = join("JavaGuy", false);
        Map<?, ?> st = mighty.last("state");
        check(st != null, "state sent");
        check("PlebMc Coins".equals(st.get("currency")), "currency name");
        Map<?, ?> withCatalog = null;
        for (Map<?, ?> m : mighty.got) if (m.containsKey("catalog")) withCatalog = m;
        check(withCatalog != null && ((List<?>) withCatalog.get("catalog")).size() == 19, "catalog has 19 items");
        check(mighty.coins() == 50, "day 1 login reward: " + mighty.coins());
        Map<?, ?> n = mighty.last("notify");
        check(n != null && String.valueOf(n.get("message")).contains("+50 PlebMc Coins"), "login popup: " + n);
        check(Boolean.TRUE.equals(quest(mighty, "login").get("done")), "login row done");
        check(((Number) quest(mighty, "login_5").get("progress")).intValue() == 1, "weekly login quest counted");
        check(((Number) st.get("dailyResetIn")).longValue() > 0, "daily reset time");
        check(java.got.isEmpty() && java.chat.stream().anyMatch(c -> c.contains("+50")), "other clients get chat: " + java.chat);
        // rejoin the same day: no second reward
        Bukkit.online.remove(mighty);
        plugin.onQuit((PlayerQuitEvent) new PlayerQuitEvent().with(mighty));
        mighty = join("Mighty", true);
        check(mighty.coins() == 50, "no second login reward the same day");

        // ---- quests ----
        FakePlayer victim = join("Victim", true);
        for (int i = 0; i < 3; i++) {
            victim.killer = mighty;
            plugin.onDeath(new PlayerDeathEvent(victim));
        }
        check(((Number) quest(mighty, "kills_3").get("progress")).intValue() == 3 && Boolean.TRUE.equals(quest(mighty, "kills_3").get("done")), "kills_3 done");
        check(((Number) quest(mighty, "kills_8").get("progress")).intValue() == 3, "kills_8 at 3");
        check(((Number) quest(victim, "matches_3").get("progress")).intValue() == 3, "deaths count as matches for the victim");
        check(mighty.coins() == 50 + 75, "kills_3 reward paid: " + mighty.coins());
        n = mighty.last("notify");
        check(String.valueOf(n.get("message")).contains("Get 3 kills: +75"), "quest popup: " + n);
        cmd(admin, "progress", "Mighty", "win");
        check(Boolean.TRUE.equals(quest(mighty, "win_1").get("done")) && ((Number) quest(mighty, "matches_3").get("progress")).intValue() == 1, "win via command counts a match too");
        check(mighty.coins() == 50 + 75 + 150, "win reward: " + mighty.coins());
        // playtime: 15 minutes -> 10 coins, play_30 progress
        Runnable minute = Bukkit.timers.get(1);
        for (int i = 0; i < 15; i++) minute.run();
        check(mighty.coins() == 50 + 75 + 150 + 10, "playtime coins: " + mighty.coins());
        check(((Number) quest(mighty, "play_30").get("progress")).intValue() == 15, "play minutes counted");
        for (int i = 0; i < 15; i++) minute.run();
        check(mighty.coins() == 285 + 10 + 60, "play_30 done + playtime: " + mighty.coins());

        // ---- shop ----
        cmd(admin, "coins", "give", "Mighty", "10000");
        long before = mighty.coins();
        check(before == 10355, "admin coins give: " + before);
        mighty.send("{\"type\":\"buy\",\"id\":\"crown\"}");
        Map<?, ?> r = mighty.last("result");
        check(Boolean.TRUE.equals(r.get("ok")), "crown bought: " + r);
        check(mighty.coins() == before - 8000, "8000 taken");
        st = mighty.last("state");
        check(((List<?>) st.get("owned")).contains("crown") && "crown".equals(((Map<?, ?>) st.get("equipped")).get("hat")), "owned + equipped");
        String saved = new String(Files.readAllBytes(new File(dir, "players.yml").toPath()), StandardCharsets.UTF_8);
        check(saved.contains("crown") && saved.contains("coins: 2355"), "saved right away");
        sleepCooldown();
        mighty.send("{\"type\":\"buy\",\"id\":\"crown\"}");
        check(mighty.coins() == 2355, "no double charge");
        sleepCooldown();
        mighty.send("{\"type\":\"buy\",\"id\":\"rainbow\"}");
        r = mighty.last("result");
        check(Boolean.FALSE.equals(r.get("ok")) && String.valueOf(r.get("message")).contains("5,145"), "not enough: " + r);
        sleepCooldown();
        mighty.send("{\"type\":\"buy\",\"id\":\"halo\"}");
        mighty.send("{\"type\":\"buy\",\"id\":\"horns\"}");
        check(mighty.coins() == 2355, "halo too expensive, horns blocked by cooldown");
        sleepCooldown();
        mighty.send("{\"type\":\"buy\",\"id\":\"dropzone_crown\"}");
        check(Boolean.FALSE.equals(mighty.last("result").get("ok")), "not-for-sale refused");

        // ---- others see cosmetics ----
        FakePlayer poor = join("Poor", true);
        Map<?, ?> snap = null;
        for (Map<?, ?> m : poor.got) if (Boolean.TRUE.equals(m.get("clear"))) snap = m;
        Map<?, ?> ms = (Map<?, ?>) ((Map<?, ?>) snap.get("players")).get(mighty.id.toString());
        check(ms != null && "crown".equals(ms.get("hat")), "new player sees Mighty's crown");
        sleepCooldown();
        mighty.send("{\"type\":\"equip\",\"category\":\"hat\",\"id\":null}");
        Map<?, ?> pm = (Map<?, ?>) poor.last("players").get("players");
        check(pm.containsKey(mighty.id.toString()) && pm.get(mighty.id.toString()) == null, "take off sends null");
        sleepCooldown();
        poor.send("{\"type\":\"equip\",\"category\":\"hat\",\"id\":\"crown\"}");
        check(Boolean.FALSE.equals(poor.last("result").get("ok")), "can't equip what you don't own");
        poor.send("not json"); poor.send("[1,2]"); poor.send("{\"type\":\"buy\",\"id\":5}"); poor.send("{\"type\":\"equip\",\"category\":\"x\"}");

        // ---- the real client code reads it ----
        Cosmetics.setSelf(mighty.id.toString());
        List<String> toasts = new ArrayList<>();
        Cosmetics.setToasts((t, m) -> toasts.add(t + "|" + m));
        for (String m : mighty.raw) Cosmetics.handle(m);
        check(Cosmetics.coins() == 2355, "client coins " + Cosmetics.coins());
        check("PlebMc Coins".equals(Cosmetics.currency()), "client currency");
        check(Cosmetics.quests().size() == 1 + 7 + 5, "client quests: " + Cosmetics.quests().size());
        Cosmetics.Quest k = null;
        for (Cosmetics.Quest q : Cosmetics.quests()) if (q.id.equals("kills_8")) k = q;
        check(k != null && k.progress == 3 && k.goal == 8 && k.reward == 150 && !k.done, "client quest fields");
        check(Cosmetics.loginStreak() == 1 && Cosmetics.dailyResetSeconds() > 0, "client streak/reset");
        check(toasts.stream().anyMatch(t -> t.contains("Quest complete") && t.contains("Get 3 kills")), "client toasts: " + toasts);
        check(Cosmetics.item("gold_tophat").color == 0xE8B923, "client hat color");

        // ---- quit, admin, hidden worlds ----
        Bukkit.online.remove(mighty);
        plugin.onQuit((PlayerQuitEvent) new PlayerQuitEvent().with(mighty));
        pm = (Map<?, ?>) poor.last("players").get("players");
        check(pm.containsKey(mighty.id.toString()) && pm.get(mighty.id.toString()) == null, "quit sends null");
        cmd(admin, "give", "Poor", "dropzone_crown");
        cmd(admin, "equip", "Poor", "hat", "dropzone_crown");
        check("dropzone_crown".equals(((Map<?, ?>) poor.last("state").get("equipped")).get("hat")), "admin give + equip");
        cmd(admin, "balance", "Mighty");
        check(admin.chat.get(admin.chat.size() - 1).contains("2,355"), "balance command: " + admin.chat.get(admin.chat.size() - 1));
        check(plugin.onTabComplete(admin, null, "plebshop", new String[] { "equip", "Poor", "trail", "r" }).contains("rainbow"), "tab complete");
        config(dir, config(dir).replace("hidden-in-worlds: []", "hidden-in-worlds: [arena1]"));
        cmd(admin, "reload");
        FakePlayer other = join("Other", true);
        poor.world = "arena1";
        plugin.onWorldChange((PlayerChangedWorldEvent) new PlayerChangedWorldEvent().with(poor));
        pm = (Map<?, ?>) other.last("players").get("players");
        check(pm.containsKey(poor.id.toString()) && pm.get(poor.id.toString()) == null, "hidden in arena world");

        // ---- restart keeps everything ----
        plugin.onDisable();
        Bukkit.online.clear(); Bukkit.timers.clear();
        plugin = new PlebMcShop(); plugin.onEnable();
        FakePlayer back = join("Mighty", true);
        check(back.coins() == 2355, "coins survive restart: " + back.coins());
        check(((List<?>) back.last("state").get("owned")).contains("crown"), "items survive restart");
        check(Boolean.TRUE.equals(quest(back, "kills_3").get("done")) && ((Number) quest(back, "play_60").get("progress")).intValue() == 30, "quest progress survives restart");

        System.out.println(new String(Files.readAllBytes(new File(dir, "players.yml").toPath()), StandardCharsets.UTF_8).substring(0, 600));
        System.out.println(checks - failed + "/" + checks + " plugin checks passed");
        int q = net.plebmc.shop.QuestsTest.run();
        System.exit(failed == 0 && q == 0 ? 0 : 1);
    }
}
