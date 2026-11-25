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
            if (url.endsWith("/integrations/unlink")) {
                String id = url.substring(url.indexOf("minecraft:") + 10, url.indexOf("/integrations"));
                if (!new Gson().fromJson(body, Map.class).get("integration").equals("Minecraft")) return new Object[] { 400, "{\"error\":\"core:invalid_integration\"}" };
                if (!linked || !id.equals(linkedId)) return new Object[] { 400, "{\"error\":\"core:integration_not_linked\"}" };
                linked = false;
                return new Object[] { 200, "{\"success\":true}" };
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

    /** HD cloaks: the proxy reads the PNG from its folder and streams it in parts the client puts back together. */
    static void cloaks(File dir, Logger log) throws Exception {
        File cl = new File(dir, "cloaks");
        cl.mkdirs();
        try (java.io.InputStream in = ShopTest.class.getClassLoader().getResourceAsStream("cloaks/plebmc.png")) {
            Files.write(new File(cl, "plebmc.png").toPath(), QuestsTest.readAll(in));
        }
        // a big noisy 1024x512 cloak, so it needs several messages
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(1024, 512, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.util.Random r = new java.util.Random(1);
        for (int y = 0; y < 512; y++) for (int x = 0; x < 1024; x++) img.setRGB(x, y, 0xFF000000 | r.nextInt(0xFFFFFF));
        javax.imageio.ImageIO.write(img, "png", new File(cl, "storm.png"));
        byte[] big = Files.readAllBytes(new File(cl, "storm.png").toPath());
        // a texture with the wrong shape is refused with a clear warning
        java.awt.image.BufferedImage sq = new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        javax.imageio.ImageIO.write(sq, "png", new File(cl, "square.png"));
        net = new FakeNet();
        core = new ShopCore(net, new Store(new File(dir, "cloak-players.yml"), log),
                cfg("  red_cloak:", "  bad_cloak: {name: Bad, category: cloak, texture: 'cloaks/square.png', price: 5, description: x}\n  esc_cloak: {name: Esc, category: cloak, texture: '../players.yml', price: 5, description: x}\n  red_cloak:"), log);
        core.folder(dir);
        check(core.catalog.get("plebmc_cloak").textureHash != null && core.catalog.get("storm_cloak").textureWidth == 1024,
                "cloak textures load from the plugin folder");
        check(core.catalog.get("bad_cloak").textureHash == null && core.catalog.get("esc_cloak").textureHash == null
                && core.catalog.warnings().size() == 2, "wrong-size and outside-folder textures refused: " + core.catalog.warnings());
        P c = join("Caper", "lobby", true);
        Map<?, ?> storm = null;
        List<?> cat = null;
        for (Map<?, ?> m : c.got) if (m.containsKey("catalog")) cat = (List<?>) m.get("catalog");
        for (Object o : cat) if ("storm_cloak".equals(((Map<?, ?>) o).get("id"))) storm = (Map<?, ?>) o;
        String hash = (String) storm.get("texture");
        check("cloak".equals(storm.get("category")) && "cloak".equals(storm.get("render")) && hash.length() == 16, "catalog lists the cloak texture id");

        // the real client asks for the texture and rebuilds it
        PluginChannels.reset();
        PluginChannels.attach((channel, data) -> {
            if (Cosmetics.CHANNEL.equals(channel)) core.fromClient(c.id, new String(data, StandardCharsets.UTF_8));
            return true;
        });
        PluginChannels.onJoin();
        Cosmetics.setSelf(c.id.toString());
        for (String m : c.raw) Cosmetics.handle(m);
        Cosmetics.Item ci = Cosmetics.item("storm_cloak");
        check(ci != null && hash.equals(ci.texture), "client knows the cloak's texture");
        int before = c.got.size();
        check(Cosmetics.texture(ci.texture) == null, "texture requested");
        int parts = 0;
        for (int i = before; i < c.got.size(); i++) if ("texture".equals(c.got.get(i).get("type"))) parts++;
        check(parts > 1 && parts == ((Number) c.last("texture").get("parts")).intValue(), "big texture sent in " + parts + " parts");
        for (int i = before; i < c.raw.size(); i++) Cosmetics.handle(c.raw.get(i));
        check(java.util.Arrays.equals(Cosmetics.texture(ci.texture), big), "client rebuilt the exact PNG");
        int n = c.got.size();
        core.fromClient(c.id, "{\"type\":\"texture\",\"id\":\"" + hash + "\"}");
        core.fromClient(c.id, "{\"type\":\"texture\",\"id\":\"0123456789abcdef\"}");
        check(c.got.size() == n, "no resend inside 15s, unknown ids ignored");

        // buy and wear it: everyone sees the cloak slot
        core.store.get(c.id).coins = 20000;
        sleepCooldown();
        core.fromClient(c.id, "{\"type\":\"buy\",\"id\":\"storm_cloak\"}");
        sleepCooldown();
        core.fromClient(c.id, "{\"type\":\"equip\",\"category\":\"cloak\",\"id\":\"storm_cloak\"}");
        Map<?, ?> eq = (Map<?, ?>) c.last("state").get("equipped");
        check("storm_cloak".equals(eq.get("cloak")), "cloak equipped: " + eq);
        for (String m : c.raw) Cosmetics.handle(m);
        check(Cosmetics.shown(c.id.toString(), Cosmetics.CLOAK) != null, "client shows the cloak");
        cmd("equip", "Caper", "cloak", "red_cloak");
        check("red_cloak".equals(((Map<?, ?>) c.last("state").get("equipped")).get("cloak")), "admin equip cloak");

        // ---- model hats: a Blockbench file in the plugin folder, downloaded like a cloak ----
        File hats = new File(dir, "hats");
        hats.mkdirs();
        try (java.io.InputStream in = ShopTest.class.getClassLoader().getResourceAsStream("hats/king_crown.bbmodel")) {
            Files.write(new File(hats, "king_crown.bbmodel").toPath(), QuestsTest.readAll(in));
        }
        Files.write(new File(hats, "junk.bbmodel").toPath(), "{not a model".getBytes(StandardCharsets.UTF_8));
        net = new FakeNet();
        core = new ShopCore(net, new Store(new File(dir, "hat-players.yml"), log),
                cfg("  # king_crown:", "  king_crown:", "  red_cloak:", "  junk_hat: {name: Junk, category: hat, model: 'hats/junk.bbmodel', price: 5}\n  big_hat: {name: Big, category: hat, model: 'hats/king_crown.bbmodel', scale: 2, offset: [0, 1, 0], price: 5}\n  red_cloak:"), log);
        core.folder(dir);
        Catalog.Item king = core.catalog.get("king_crown");
        check(king != null && king.modelHash != null && king.textureHash != null && king.textureWidth == 256, "model hat loads from the plugin folder");
        check(core.catalog.get("junk_hat") == null && core.catalog.warnings().toString().contains("junk"), "broken model left out: " + core.catalog.warnings());
        check(!core.catalog.get("big_hat").modelHash.equals(king.modelHash), "scale/offset change the model");
        P h = join("Hatter", "lobby", true);
        Map<?, ?> kj = null;
        for (Map<?, ?> m : h.got) if (m.containsKey("catalog")) for (Object o : (List<?>) m.get("catalog")) if ("king_crown".equals(((Map<?, ?>) o).get("id"))) kj = (Map<?, ?>) o;
        check(kj != null && "model".equals(kj.get("render")) && king.modelHash.equals(kj.get("model")) && king.textureHash.equals(kj.get("texture")), "catalog lists the model hat: " + kj);
        PluginChannels.reset();
        PluginChannels.attach((channel, data) -> {
            if (Cosmetics.CHANNEL.equals(channel)) core.fromClient(h.id, new String(data, StandardCharsets.UTF_8));
            return true;
        });
        PluginChannels.onJoin();
        for (String m : h.raw) Cosmetics.handle(m);
        Cosmetics.Item hi = Cosmetics.item("king_crown");
        check(hi != null && Cosmetics.MODEL.equals(hi.render) && king.modelHash.equals(hi.model), "client knows the model hat");
        check(Cosmetics.model(hi.model) == null && Cosmetics.texture(hi.texture) == null, "model and texture requested");
        for (String m : h.raw) Cosmetics.handle(m);
        float[] quads = Cosmetics.model(hi.model);
        check(quads != null && quads.length == 186 * 20, "client rebuilt the model: " + (quads == null ? 0 : quads.length / 20) + " faces");
        check(java.util.Arrays.equals(Cosmetics.texture(hi.texture), king.png), "client rebuilt the texture");
        // the crown sits on the head: centered, standing on top (head top = y 8)
        float minY = 99, maxAbsX = 0;
        for (int i = 0; i < quads.length; i += 5) { minY = Math.min(minY, quads[i + 1]); maxAbsX = Math.max(maxAbsX, Math.abs(quads[i])); }
        check(Math.abs(minY - 7.6f) < 0.01f && maxAbsX < 6f, "auto placement: " + minY + " " + maxAbsX);
    }

    /** Shop collections (Regular / Partners), partner creators, and old trail items being left out. */
    static void collections(File dir, Logger log) throws Exception {
        net = new FakeNet();
        core = new ShopCore(net, new Store(new File(dir, "col-players.yml"), log),
                cfg("  red_cloak:", "  p_cloak: {name: P, category: cloak, color: '#FF00FF', collection: Partners, creator: ' Streamer ', price: 5}\n"
                        + "  lost_hat: {name: L, category: hat, render: cap, collection: nope, price: 5}\n"
                        + "  old_trail: {name: T, category: trail, render: flame, price: 5}\n  red_cloak:"), log);
        Catalog.Item pc = core.catalog.get("p_cloak");
        check(pc != null && "partners".equals(pc.collection) && "Streamer".equals(pc.creator), "partner item: collection and creator");
        check("regular".equals(core.catalog.get("lost_hat").collection) && core.catalog.warnings().toString().contains("nope"),
                "unknown collection falls back to regular with a warning");
        check(core.catalog.get("old_trail") == null && core.catalog.warnings().toString().contains("trails were removed"),
                "trail items are left out: " + core.catalog.warnings());
        P p = join("Partnered", "lobby", true);
        PluginChannels.reset();
        for (String m : p.raw) Cosmetics.handle(m);
        check(Cosmetics.collections().size() == 2 && "partners".equals(Cosmetics.collections().get(1).id)
                && Cosmetics.collections().get(1).color == 0xFFFF7AC6, "client reads the collections");
        Cosmetics.Item ci = Cosmetics.item("p_cloak");
        check(ci != null && "partners".equals(ci.collection) && "Streamer".equals(ci.creator)
                && Cosmetics.catalog(Cosmetics.CLOAK, "partners").size() == 1
                && Cosmetics.catalog(Cosmetics.CLOAK, "regular").size() == 6, "client splits the shop by collection");
        // the equipped cloak survives a restart (it used to be dropped on load)
        cmd("give", "Partnered", "red_cloak");
        cmd("equip", "Partnered", "cloak", "red_cloak");
        core.saveIfDirty();
        Store again = new Store(new File(dir, "col-players.yml"), log);
        check("red_cloak".equals(again.get(p.id).equipped.get("cloak")), "equipped cloak saved and loaded");
    }

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
        check(cats.size() == 2 && "General".equals(((Map<?, ?>) cats.get(0)).get("name")) && "PlebNite".equals(((Map<?, ?>) cats.get(1)).get("name"))
                && "plebnite".equals(((Map<?, ?>) cats.get(1)).get("icon")) && "#FF9A3C".equals(((Map<?, ?>) cats.get(1)).get("color")),
                "questCategories: " + cats);
        Map<?, ?> withCatalog = null;
        for (Map<?, ?> m : mighty.got) if (m.containsKey("catalog")) withCatalog = m;
        check(withCatalog != null && ((List<?>) withCatalog.get("catalog")).size() == 24, "catalog has 24 items");
        List<?> cols = (List<?>) withCatalog.get("collections");
        check(cols != null && cols.size() == 2 && "regular".equals(((Map<?, ?>) cols.get(0)).get("id"))
                && "Partners".equals(((Map<?, ?>) cols.get(1)).get("name")), "collections sent: " + cols);
        boolean noTrails = true;
        for (Object o : (List<?>) withCatalog.get("catalog")) {
            Map<?, ?> im = (Map<?, ?>) o;
            noTrails &= !"trail".equals(im.get("category")) && "regular".equals(im.get("collection"));
        }
        check(noTrails, "no trails in the shop, everything in Regular by default");
        check(mighty.coins() == 50, "day 1 login reward: " + mighty.coins());
        check(String.valueOf(mighty.last("notify").get("message")).contains("+50 PlebMc Coins"), "login popup");
        check(mighty.done("login") && "general".equals(mighty.quest("login").get("category")) && ((Number) mighty.quest("login").get("streak")).intValue() == 1,
                "login row done, once, in General, streak 1");
        int loginRows = 0;
        for (Object o : (List<?>) mighty.last("state").get("quests")) if ("login".equals(((Map<?, ?>) o).get("id"))) loginRows++;
        check(loginRows == 1, "exactly one login row: " + loginRows);
        check(mighty.progress("login_5") == 1, "weekly login quest counted");
        check(mighty.progress("general_join_3") == 1 && "general".equals(mighty.quest("general_join_3").get("category")), "joining the lobby counts for General");
        check(java.got.isEmpty() && java.chat.stream().anyMatch(c -> c.contains("+50")), "other clients get chat: " + java.chat);
        quit(mighty);
        mighty = join("Mighty", "lobby", true);
        check(mighty.coins() == 50 && mighty.progress("login_5") == 1, "no second login reward (or login count) the same day");

        // ---- categories: PlebNite quests only count on PlebNite, General everywhere ----
        core.progress(mighty.id, "lobby", "kill", 3);
        check(mighty.progress("kills_3") == 0 && mighty.progress("kills_40") == 0, "lobby kills don't count for PlebNite");
        move(mighty, "plebnite");
        check(mighty.progress("general_join_3") == 3, "rejoin and switching servers count as joins: " + mighty.progress("general_join_3"));
        long before = mighty.coins();
        core.progress(mighty.id, "plebnite", "kill", 3);
        check(mighty.done("kills_3") && mighty.progress("kills_8") == 3 && mighty.progress("kills_40") == 3, "plebnite kills");
        check(mighty.coins() == before + 75, "kills_3 reward: " + (mighty.coins() - before));
        check(String.valueOf(mighty.last("notify").get("message")).contains("Get 3 kills: +75"), "quest popup");
        before = mighty.coins();
        core.progress(mighty.id, "plebnite", "kill", 3);
        check(mighty.coins() == before && mighty.progress("kills_3") == 3, "a done quest never pays twice");
        before = mighty.coins();
        cmd("progress", "Mighty", "win");
        check(mighty.done("win_1") && mighty.coins() == before + 150, "win via /plebshop progress on the player's server");
        // minutes count as "play" in every category covering the server you're on
        java.server = "lobby";
        for (int i = 0; i < 15; i++) core.minute();
        check(mighty.progress("play_30") == 15 && mighty.progress("general_play_30") == 15, "playtime on plebnite: both");
        move(mighty, "lobby");
        for (int i = 0; i < 5; i++) core.minute();
        check(mighty.progress("play_30") == 15 && mighty.progress("general_play_30") == 20, "playtime in the lobby: General only");
        check(!core.store.get(java.id).progress.containsKey("play_30") && core.store.get(java.id).progress.get("general_play_30") == 20, "lobby-only player: no PlebNite progress");
        core.progress(mighty.id, "survival", "kill", 5);
        check(mighty.progress("kills_8") == 6, "kills elsewhere don't count for PlebNite");
        // chat: at most one every 10 seconds
        for (int i = 0; i < 5; i++) core.chat(mighty.id);
        check(mighty.progress("general_chat_10") == 1, "chat spam counts once: " + mighty.progress("general_chat_10"));
        net.now += ShopCore.CHAT_EVERY_MS;
        core.chat(mighty.id);
        check(mighty.progress("general_chat_10") == 2, "chat again after 10s");

        // ---- the shop ----
        cmd("coins", "set", "Mighty", "10000");
        mighty.got.clear();
        core.fromClient(mighty.id, "{\"type\":\"buy\",\"id\":\"horns\"}");
        check(Boolean.TRUE.equals(mighty.last("result").get("ok")) && mighty.coins() == 6500, "horns bought for 3500");
        Map<?, ?> eq = (Map<?, ?>) mighty.last("state").get("equipped");
        check("horns".equals(eq.get("hat")), "auto-equipped");
        String saved = new String(Files.readAllBytes(new File(dir, "players.yml").toPath()), StandardCharsets.UTF_8);
        check(saved.contains("horns") && saved.contains("coins: 6500"), "saved right away");
        core.fromClient(mighty.id, "{\"type\":\"buy\",\"id\":\"halo\"}");
        check(mighty.coins() == 6500, "cooldown blocks a second action");
        sleepCooldown();
        core.fromClient(mighty.id, "{\"type\":\"buy\",\"id\":\"diamond_crown\"}");
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
        check(ms != null && "horns".equals(ms.get("hat")), "lobby player sees Mighty's horns");
        Map<?, ?> farSnap = null;
        for (Map<?, ?> m : far.got) if (Boolean.TRUE.equals(m.get("clear"))) farSnap = m;
        check(!((Map<?, ?>) farSnap.get("players")).containsKey(mighty.id.toString()), "plebnite player doesn't get lobby players");
        sleepCooldown();
        core.fromClient(mighty.id, "{\"type\":\"equip\",\"category\":\"hat\",\"id\":null}");
        Map<?, ?> pm = (Map<?, ?>) poor.last("players").get("players");
        check(pm.containsKey(mighty.id.toString()) && pm.get(mighty.id.toString()) == null, "take off sends null");
        sleepCooldown();
        core.fromClient(poor.id, "{\"type\":\"equip\",\"category\":\"hat\",\"id\":\"horns\"}");
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
        check(Cosmetics.questCategories().size() == 2 && "General".equals(Cosmetics.questCategories().get(0).name)
                && "plebnite".equals(Cosmetics.questCategories().get(1).icon) && Cosmetics.questCategories().get(1).color == 0xFFFF9A3C, "client categories");
        List<Cosmetics.Quest> lq = Cosmetics.quests("general"), nq = Cosmetics.quests("plebnite"), aq = Cosmetics.quests(null);
        check(lq.size() == 1 + 4 + 3 && nq.size() == 6 + 4 && aq.size() == lq.size() + nq.size(), "client quests per tab: " + lq.size() + "/" + nq.size() + "/" + aq.size());
        check("login".equals(lq.get(0).id) && lq.get(0).streak == 1 && lq.get(0).streakLength == 7, "login row once, in General, with its streak");
        Cosmetics.Quest k8 = null;
        for (Cosmetics.Quest q : nq) if (q.id.equals("kills_8")) k8 = q;
        check(k8 != null && k8.progress == 6 && k8.goal == 8 && "plebnite".equals(k8.category), "client quest fields");
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

        // ---- admin menu: website Owners only, checked on every action ----
        check(Boolean.TRUE.equals(acc.get("admin")), "Owner gets the admin flag");
        P nobody = join("Nobody", "lobby", true);
        check(Boolean.FALSE.equals(nobody.last("account") == null ? Boolean.FALSE : nobody.last("account").get("admin")), "others don't");
        core.fromClient(nobody.id, "{\"type\":\"admin\",\"action\":\"coins\",\"op\":\"set\",\"amount\":999999,\"player\":\"" + nobody.id + "\"}");
        check(Boolean.FALSE.equals(nobody.last("admin").get("allowed")) && nobody.coins() != 999999, "non-admin refused");
        core.fromClient(web.id, "{\"type\":\"admin\",\"action\":\"open\"}");
        Map<?, ?> ad = web.last("admin");
        check(Boolean.TRUE.equals(ad.get("allowed")) && ((List<?>) ad.get("players")).size() == net.online.size()
                && !((List<?>) ad.get("items")).isEmpty(), "admin data: " + ad.keySet());
        sleepCooldown();
        core.fromClient(web.id, "{\"type\":\"admin\",\"action\":\"coins\",\"op\":\"give\",\"amount\":250,\"player\":\"" + nobody.id + "\"}");
        check(Boolean.TRUE.equals(web.last("admin").get("ok")) && nobody.coins() >= 250, "admin gives coins: " + web.last("admin").get("message"));
        sleepCooldown();
        core.fromClient(web.id, "{\"type\":\"admin\",\"action\":\"item\",\"op\":\"give\",\"id\":\"halo\",\"player\":\"Nobody\"}");
        check(((List<?>) nobody.last("state").get("owned")).contains("halo"), "admin gives an item (by name too)");
        sleepCooldown();
        core.fromClient(web.id, "{\"type\":\"admin\",\"action\":\"broadcast\",\"message\":\"Hello all\"}");
        check(nobody.last("notify") != null && String.valueOf(nobody.last("notify").get("message")).contains("Hello all"), "broadcast popup");
        sleepCooldown();
        core.fromClient(web.id, "{\"type\":\"admin\",\"action\":\"kick\",\"player\":\"" + web.id + "\"}");
        check(Boolean.FALSE.equals(web.last("admin").get("ok")), "can't kick yourself");
        sleepCooldown();
        core.fromClient(web.id, "{\"type\":\"admin\",\"action\":\"reload\"}");
        check(web.last("admin").get("message").toString().startsWith("Reloaded"), "admin reload");
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
        // log out from the client: unlinks on the website, and can link again
        Thread.sleep(4100);
        core.fromClient(web.id, "{\"type\":\"logout\"}");
        check(Boolean.FALSE.equals(web.last("account").get("linked")) && !site.linked
                && site.calls.get(site.calls.size() - 1).contains("/integrations/unlink"), "logout unlinks: " + web.last("account"));
        Thread.sleep(4100);
        core.fromClient(web.id, "{\"type\":\"link\",\"code\":\"ABC123\"}");
        check(Boolean.TRUE.equals(web.last("account").get("linked")), "link again after logout");
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
        check(core.tabComplete(new String[] { "equip", "Poor", "cloak", "r" }).contains("red_cloak"), "tab complete");
        check(core.tabComplete(new String[] { "progress", "Mighty", "ch" }).contains("chat"), "tab complete chat");
        admin.clear();
        cmd("quests");
        check(admin.size() == 1 + 17 && admin.get(1).contains("general"), "quests command lists categories");
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
        core = new ShopCore(net, new Store(new File(dir, "players.yml"), log), cfg("daily-count: 3", "daily-count: 99", "weekly-count: 2", "weekly-count: 99"), log);
        P back = join("Mighty", "plebnite", true);
        check(back.coins() == keep + 75, "coins survive restart, day 2 reward: " + back.coins() + " vs " + keep);
        check(((List<?>) back.last("state").get("owned")).contains("horns") && back.progress("login_5") == 2, "progress survives restart");
        check(((Map<?, ?>) back.quest("login")).get("reward").equals(75.0), "streak day 2");

        cloaks(dir, log);
        collections(dir, log);

        System.out.println(checks - failed + "/" + checks + " plugin checks passed");
        int q = QuestsTest.run();
        System.exit(failed == 0 && q == 0 ? 0 : 1);
    }
}
