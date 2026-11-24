import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import me.clip.placeholderapi.PlaceholderAPI;
import com.google.gson.Gson;

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
        String world = "BattleRoyaleLobby"; boolean op;
        FakePlayer(String n) { name = n; id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + n).getBytes(StandardCharsets.UTF_8)); }
        public UUID getUniqueId() { return id; }
        public String getName() { return name; }
        public void sendMessage(String m) { chat.add(m); }
        public boolean hasPermission(String p) { return op; }
        public void sendPluginMessage(org.bukkit.plugin.Plugin p, String c, byte[] d) {
            if (!channels.contains(c)) throw new IllegalStateException("not registered");
            raw.add(new String(d, StandardCharsets.UTF_8));
            got.add(new Gson().fromJson(new String(d, StandardCharsets.UTF_8), Map.class));
        }
        public Set<String> getListeningPluginChannels() { return channels; }
        public World getWorld() { return () -> world; }
        public boolean isOnline() { return Bukkit.online.contains(this); }
        Map<?, ?> last(String type) { for (int i = got.size() - 1; i >= 0; i--) if (type.equals(got.get(i).get("type"))) return got.get(i); return null; }
        void send(String json) { Bukkit.incoming.onPluginMessageReceived("plebmc:shop", this, json.getBytes(StandardCharsets.UTF_8)); }
    }

    static PlebMcShop plugin;
    static FakePlayer join(String name, boolean client) throws Exception {
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
        return p;
    }
    static void sleepCooldown() throws InterruptedException { Thread.sleep(320); }

    public static void main(String[] a) throws Exception {
        File dir = Files.createTempDirectory("plebshop").toFile();
        JavaPlugin.folder = dir;
        // Battle Royale X: coins live in a map, "brx coins remove <p> <n>" takes them
        Map<String, Long> brx = new HashMap<>();
        Bukkit.commandHandler = c -> {
            String[] w = c.split(" ");
            if (w.length == 5 && w[0].equals("brx") && w[1].equals("coins") && w[2].equals("remove")) {
                brx.put(w[3], Math.max(0, brx.getOrDefault(w[3], 0L) - Long.parseLong(w[4])));
                PlaceholderAPI.values.put(w[3], String.format(Locale.ROOT, "%,d", brx.get(w[3])));
            }
        };
        brx.put("Mighty", 53000L); PlaceholderAPI.values.put("Mighty", "53,000");
        brx.put("Poor", 100L); PlaceholderAPI.values.put("Poor", "100");

        plugin = new PlebMcShop();
        plugin.onEnable();

        FakePlayer mighty = join("Mighty", true);
        FakePlayer java = join("JavaGuy", false);
        Map<?, ?> st = mighty.last("state");
        check(st != null, "state sent on register/open");
        check(((Number) st.get("coins")).longValue() == 53000, "balance read from placeholder: " + st.get("coins"));
        check(((List<?>) st.get("catalog")).size() == 19, "catalog has 19 items: " + ((List<?>) st.get("catalog")).size());
        check(((List<?>) st.get("owned")).isEmpty(), "owns nothing yet");
        check(java.got.isEmpty(), "vanilla player gets no shop messages");

        // buy the crown
        mighty.send("{\"type\":\"buy\",\"id\":\"crown\"}");
        Map<?, ?> r = mighty.last("result");
        check(Boolean.TRUE.equals(r.get("ok")), "crown bought: " + r);
        check(brx.get("Mighty") == 38000, "15000 taken via brx command: " + brx.get("Mighty"));
        check(Bukkit.commands.contains("brx coins remove Mighty 15000"), "command text: " + Bukkit.commands);
        st = mighty.last("state");
        check(((Number) st.get("coins")).longValue() == 38000, "new balance in state");
        check(((List<?>) st.get("owned")).contains("crown"), "crown owned");
        check("crown".equals(((Map<?, ?>) st.get("equipped")).get("hat")), "crown auto-equipped");
        Map<?, ?> pl = mighty.last("players");
        check(pl != null && ((Map<?, ?>) pl.get("players")).containsKey(mighty.id.toString()), "equip broadcast");
        String saved = new String(Files.readAllBytes(new File(dir, "players.yml").toPath()), StandardCharsets.UTF_8);
        check(saved.contains("crown") && saved.contains(mighty.id.toString()), "saved to players.yml right away");

        // buying again: no double charge
        sleepCooldown();
        mighty.send("{\"type\":\"buy\",\"id\":\"crown\"}");
        check(brx.get("Mighty") == 38000, "no charge for an owned item");

        // cooldown blocks spam
        sleepCooldown();
        mighty.send("{\"type\":\"buy\",\"id\":\"halo\"}");
        mighty.send("{\"type\":\"buy\",\"id\":\"horns\"}");
        sleepCooldown();
        check(brx.get("Mighty") == 38000 - 8000, "only one buy inside the cooldown: " + brx.get("Mighty"));

        // not for sale / unknown
        sleepCooldown();
        mighty.send("{\"type\":\"buy\",\"id\":\"dropzone_crown\"}");
        check(Boolean.FALSE.equals(mighty.last("result").get("ok")), "not-for-sale refused");
        sleepCooldown();
        mighty.send("{\"type\":\"buy\",\"id\":\"nope\"}");
        check(Boolean.FALSE.equals(mighty.last("result").get("ok")), "unknown item refused");

        // second client sees what Mighty wears
        FakePlayer poor = join("Poor", true);
        Map<?, ?> snap = null;
        for (Map<?, ?> m : poor.got) if (Boolean.TRUE.equals(m.get("clear"))) snap = m;
        check(Boolean.TRUE.equals(snap.get("clear")), "snapshot clears first");
        Map<?, ?> ms = (Map<?, ?>) ((Map<?, ?>) snap.get("players")).get(mighty.id.toString());
        check(ms != null && "halo".equals(ms.get("hat")), "new player sees Mighty's halo: " + snap);

        // not enough coins
        poor.send("{\"type\":\"buy\",\"id\":\"rainbow\"}");
        r = poor.last("result");
        check(Boolean.FALSE.equals(r.get("ok")) && String.valueOf(r.get("message")).contains("14,900"), "not enough coins: " + r);
        check(brx.get("Poor") == 100, "poor keeps coins");

        // equip / unequip
        sleepCooldown();
        mighty.send("{\"type\":\"equip\",\"category\":\"hat\",\"id\":\"crown\"}");
        Map<?, ?> seen = (Map<?, ?>) ((Map<?, ?>) poor.last("players").get("players")).get(mighty.id.toString());
        check(seen != null && "crown".equals(seen.get("hat")), "others see equip change");
        sleepCooldown();
        mighty.send("{\"type\":\"equip\",\"category\":\"hat\",\"id\":null}");
        Map<?, ?> pm = (Map<?, ?>) poor.last("players").get("players");
        check(pm.containsKey(mighty.id.toString()) && pm.get(mighty.id.toString()) == null, "take off sends null: " + pm);
        sleepCooldown();
        poor.send("{\"type\":\"equip\",\"category\":\"hat\",\"id\":\"crown\"}");
        check(Boolean.FALSE.equals(poor.last("result").get("ok")), "can't equip what you don't own");

        // broken placeholder: refuse, take nothing
        PlaceholderAPI.values.put("Poor", "");
        sleepCooldown();
        poor.send("{\"type\":\"buy\",\"id\":\"cloud\"}");
        check(Boolean.FALSE.equals(poor.last("result").get("ok")) && Bukkit.commands.size() == 2, "unreadable balance refused");
        PlaceholderAPI.values.put("Poor", "100");

        // command that does nothing: no item
        brx.put("Poor", 5000L); PlaceholderAPI.values.put("Poor", "5000");
        java.util.function.Consumer<String> h = Bukkit.commandHandler;
        Bukkit.commandHandler = c -> {};
        sleepCooldown();
        poor.send("{\"type\":\"buy\",\"id\":\"cloud\"}");
        check(Boolean.FALSE.equals(poor.last("result").get("ok")), "failed take command -> no item");
        check(!((List<?>) poor.last("state").get("owned")).contains("cloud"), "cloud not granted");
        Bukkit.commandHandler = h;

        // garbage input doesn't crash
        poor.send("not json"); poor.send("[1,2]"); poor.send("{\"type\":\"buy\",\"id\":5}"); poor.send("{\"type\":\"equip\",\"category\":\"x\"}");

        // the real PlebMc client code reads what the plugin sent
        net.plebmc.client.cosmetics.Cosmetics.setSelf(poor.id.toString());
        for (String m : poor.raw) net.plebmc.client.cosmetics.Cosmetics.handle(m);
        check(net.plebmc.client.cosmetics.Cosmetics.serverSupported(), "client: server supported");
        check(net.plebmc.client.cosmetics.Cosmetics.coins() == 5000, "client: coins " + net.plebmc.client.cosmetics.Cosmetics.coins());
        check(net.plebmc.client.cosmetics.Cosmetics.catalog().size() == 19, "client: catalog " + net.plebmc.client.cosmetics.Cosmetics.catalog().size());
        check(net.plebmc.client.cosmetics.Cosmetics.item("gold_tophat").color == 0xE8B923, "client: hat color");
        check(net.plebmc.client.cosmetics.Cosmetics.shown(mighty.id.toString(), "hat") == null, "client: Mighty took the hat off");
        mighty.send("{\"type\":\"equip\",\"category\":\"trail\",\"id\":null}");
        sleepCooldown();
        mighty.send("{\"type\":\"equip\",\"category\":\"hat\",\"id\":\"halo\"}");
        net.plebmc.client.cosmetics.Cosmetics.handle(poor.raw.get(poor.raw.size() - 1));
        net.plebmc.client.cosmetics.Cosmetics.Item shownHat = net.plebmc.client.cosmetics.Cosmetics.shown(mighty.id.toString(), "hat");
        check(shownHat != null && shownHat.render.equals("halo"), "client: sees Mighty's halo");

        // quit
        Bukkit.online.remove(mighty);
        plugin.onQuit((PlayerQuitEvent) new PlayerQuitEvent().with(mighty));
        pm = (Map<?, ?>) poor.last("players").get("players");
        check(pm.containsKey(mighty.id.toString()) && pm.get(mighty.id.toString()) == null, "quit sends null");

        // admin commands
        FakePlayer admin = poor; admin.op = true;
        plugin.onCommand(admin, null, "plebshop", new String[] { "give", "Poor", "dropzone_crown" });
        check(((List<?>) poor.last("state").get("owned")).contains("dropzone_crown"), "admin give");
        plugin.onCommand(admin, null, "plebshop", new String[] { "equip", "Poor", "hat", "dropzone_crown" });
        check("dropzone_crown".equals(((Map<?, ?>) poor.last("state").get("equipped")).get("hat")), "admin equip");
        plugin.onCommand(admin, null, "plebshop", new String[] { "give", "Mighty", "snow" }); // offline, known name
        plugin.onCommand(admin, null, "plebshop", new String[] { "balance", "Poor" });
        check(admin.chat.get(admin.chat.size() - 1).contains("5,000"), "balance command: " + admin.chat);
        check(plugin.onTabComplete(admin, null, "plebshop", new String[] { "equip", "Poor", "trail", "r" }).contains("rainbow"), "tab complete");

        // hidden worlds
        Files.write(new File(dir, "config.yml").toPath(), (new String(Files.readAllBytes(new File(dir, "config.yml").toPath()), StandardCharsets.UTF_8)
                .replace("hidden-in-worlds: []", "hidden-in-worlds: [arena1]")).getBytes(StandardCharsets.UTF_8));
        plugin.onCommand(admin, null, "plebshop", new String[] { "reload" });
        FakePlayer other = join("Other", true);
        poor.world = "arena1";
        plugin.onWorldChange((PlayerChangedWorldEvent) new PlayerChangedWorldEvent().with(poor));
        pm = (Map<?, ?>) other.last("players").get("players");
        check(pm.get(poor.id.toString()) == null && pm.containsKey(poor.id.toString()), "hidden in arena world");

        // persistence across restart
        plugin.onDisable();
        Bukkit.online.clear();
        plugin = new PlebMcShop(); plugin.onEnable();
        FakePlayer back = join("Mighty", true);
        List<?> owned = (List<?>) back.last("state").get("owned");
        check(owned.containsAll(Arrays.asList("crown", "halo", "snow")), "reloaded from disk: " + owned);

        // internal mode
        Files.write(new File(dir, "config.yml").toPath(), (new String(Files.readAllBytes(new File(dir, "config.yml").toPath()), StandardCharsets.UTF_8)
                .replace("mode: command", "mode: internal")).getBytes(StandardCharsets.UTF_8));
        back.op = true;
        plugin.onCommand(back, null, "plebshop", new String[] { "reload" });
        plugin.onCommand(back, null, "plebshop", new String[] { "coins", "give", "Mighty", "3000" });
        sleepCooldown();
        back.send("{\"type\":\"buy\",\"id\":\"tophat\"}");
        check(Boolean.TRUE.equals(back.last("result").get("ok")) && ((Number) back.last("state").get("coins")).longValue() == 0, "internal coins: " + back.last("result"));

        System.out.println(new String(Files.readAllBytes(new File(dir, "players.yml").toPath()), StandardCharsets.UTF_8));
        System.out.println(checks - failed + "/" + checks + " checks passed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
