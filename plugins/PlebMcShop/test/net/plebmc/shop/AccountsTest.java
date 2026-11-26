package net.plebmc.shop;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

import com.google.gson.Gson;

import net.plebmc.client.cosmetics.Cosmetics;
import net.plebmc.client.cosmetics.PlebAccount;
import net.plebmc.client.network.PluginChannels;

/**
 * PlebMc accounts end to end: the real panel (panel/app.py, started here),
 * PlebioAPI's SignIn and ShopCore through a fake proxy, and the real client
 * code signing in and remembering its token.
 */
public class AccountsTest {
    static int checks, failed;
    static void check(boolean ok, String what) { checks++; if (!ok) { failed++; System.out.println("FAIL " + what); } }

    static final String KEY = "test-server-key-0123456789abcdef";

    /** ShopTest's fake proxy, where "logged in" means signed in to the PlebMc account, and kicks are recorded. */
    static class AccNet extends ShopTest.FakeNet {
        SignIn signin;
        final List<String> kicked = new ArrayList<>();
        public boolean authenticated(UUID p) { return signin.signedIn(p); }
        public boolean kick(UUID p, String reason) {
            ShopTest.P x = online.get(p);
            if (x == null) return false;
            kicked.add(x.name + ": " + reason);
            online.remove(p);
            signin.left(p);
            return true;
        }
        public void send(UUID p, String json) { if (online.containsKey(p)) super.send(p, json); }
        public void chat(UUID p, String m) { if (online.containsKey(p)) super.chat(p, m); }
        public long now() { return System.currentTimeMillis() + skew; }
        long skew;
    }

    static AccNet net;
    static ShopCore core;
    static SignIn signin;

    static ShopTest.P join(String name) {
        ShopTest.P p = new ShopTest.P(name);
        p.server = "lobby";
        net.online.put(p.id, p);
        signin.joined(p.id, name);
        if (!net.online.containsKey(p.id)) return p; // kicked at once
        core.fromClient(p.id, "{\"type\":\"open\"}");
        core.connected(p.id, null, true);
        return p;
    }

    static void quit(ShopTest.P p) { net.online.remove(p.id); signin.left(p.id); core.quit(p.id, p.server); }

    static Map<?, ?> lastSignin(ShopTest.P p) { return p.last("signin"); }

    static List<String> chat(ShopTest.P p) { return p.chat; }

    static boolean chatHas(ShopTest.P p, String s) { for (String c : p.chat) if (c.contains(s)) return true; return false; }

    static void account(ShopTest.P p, String... args) {
        signin.command(p.id, args, line -> p.chat.add(line));
    }

    static void wait(int ms) throws InterruptedException { Thread.sleep(ms); }

    // ---- the real client, wired to one player ----
    static ShopTest.P clientPlayer;
    static int delivered;

    static void connectClient(ShopTest.P p) {
        clientPlayer = p;
        delivered = 0;
        PluginChannels.reset();
        PluginChannels.attach((channel, data) -> {
            if (Cosmetics.CHANNEL.equals(channel)) core.fromClient(p.id, new String(data, StandardCharsets.UTF_8));
            return true;
        });
    }

    /** Delivers what the proxy sent to the client, until it stops answering. */
    static void pump() {
        for (int round = 0; round < 20 && delivered < clientPlayer.raw.size(); round++) {
            int end = clientPlayer.raw.size();
            for (; delivered < end; delivered++) Cosmetics.handle(clientPlayer.raw.get(delivered));
        }
    }

    // ---- the panel's website (for admin changes) ----
    static String base;

    static Object[] web(String method, String path, String body, String cookie) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection(java.net.Proxy.NO_PROXY);
        c.setRequestMethod(method);
        c.setRequestProperty("X-Requested-With", "PlebPanel");
        if (cookie != null) c.setRequestProperty("Cookie", cookie);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }
        int s = c.getResponseCode();
        java.io.InputStream in = s >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = new String(QuestsTest.readAll(in), StandardCharsets.UTF_8);
        String set = c.getHeaderField("Set-Cookie");
        return new Object[] { s, new Gson().fromJson(text, Map.class), set == null ? null : set.split(";")[0] };
    }

    /** Local calls skip the proxy the test JVM may have configured. */
    static final PanelHttp DIRECT = (method, url, apiKey, jsonBody) -> {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(java.net.Proxy.NO_PROXY);
        c.setRequestMethod(method);
        c.setRequestProperty("Authorization", "Bearer " + apiKey);
        if (jsonBody != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        int s = c.getResponseCode();
        java.io.InputStream in = s >= 400 ? c.getErrorStream() : c.getInputStream();
        try {
            return new Object[] { s, in == null ? "" : new String(QuestsTest.readAll(in), StandardCharsets.UTF_8) };
        } catch (Exception e) {
            throw new java.io.IOException(e);
        }
    };

    public static int run(File dir, Logger log) throws Exception {
        int port;
        try (ServerSocket ss = new ServerSocket(0)) { port = ss.getLocalPort(); }
        base = "http://127.0.0.1:" + port;
        File db = new File(dir, "panel.db");
        ProcessBuilder pb = new ProcessBuilder("python3", new File("../../panel/app.py").getAbsolutePath());
        Map<String, String> env = pb.environment();
        env.put("PANEL_SERVER_KEY", KEY);
        env.put("PANEL_PORT", String.valueOf(port));
        env.put("PANEL_HOST", "127.0.0.1");
        env.put("PANEL_DB", db.getAbsolutePath());
        env.put("PANEL_SCRYPT_N", "1024");
        env.put("PANEL_PUBLIC_URL", "https://panel.example");
        pb.redirectErrorStream(true).redirectOutput(new File(dir, "panel.log"));
        Process panel = pb.start();
        try {
            for (int i = 0; i < 100; i++) {
                try { if ((Integer) web("GET", "/api/info", null, null)[0] == 200) break; } catch (java.io.IOException e) { wait(100); }
            }
            scenario(dir, log, env);
        } finally {
            panel.destroy();
        }
        System.out.println(checks - failed + "/" + checks + " account checks passed");
        return failed;
    }

    static void scenario(File dir, Logger log, Map<String, String> env) throws Exception {
        net = new AccNet();
        Cfg cfg = QuestsTest.config("accounts:\n  enabled: false", "accounts:\n  enabled: true",
                "url: 'https://client.plebmc.top'", "url: '" + base + "'",
                "server-key: ''", "server-key: '" + KEY + "'",
                "public-url: ''", "public-url: 'https://panel.example'");
        core = new ShopCore(net, new Store(new File(dir, "acc-players.yml"), log), cfg, log);
        signin = new SignIn(net, log);
        net.signin = signin;
        core.signin = signin;
        signin.changed = core::authChanged;
        PanelLink link = new PanelLink(DIRECT, cfg.str("accounts.url", ""), cfg.str("accounts.server-key", ""));
        signin.configure(cfg, link);
        check(signin.enabled(), "accounts turn on with a url and a long server key");
        SignIn off = new SignIn(net, log);
        off.configure(cfg, new PanelLink(DIRECT, base, "short"));
        check(!off.enabled(), "a short server key leaves accounts off");

        // ---- no account yet: can earn, can't spend ----
        ShopTest.P owner = join("Owner");
        Map<?, ?> st = lastSignin(owner);
        check(st != null && Boolean.TRUE.equals(st.get("enabled")) && Boolean.FALSE.equals(st.get("exists"))
                && Boolean.FALSE.equals(st.get("signedIn")) && "https://panel.example".equals(st.get("panel")), "join: no account yet " + st);
        check(chatHas(owner, "/account register"), "told how to make an account");
        core.command(new ShopCore.Sender() {
            public void reply(String l) { }
            public boolean hasPermission(String p) { return true; }
        }, "plebshop", new String[] { "coins", "set", "Owner", "5000" }, () -> { });
        int before = owner.got.size();
        core.fromClient(owner.id, "{\"type\":\"buy\",\"id\":\"tophat\"}");
        Map<?, ?> n = owner.last("notify");
        check(n != null && String.valueOf(n.get("message")).contains("/account login"), "buying needs a signed-in account: " + n);
        check(owner.coins() == 5000, "no coins spent");

        // ---- make it in game ----
        account(owner, "register", "short", "short");
        check(chatHas(owner, "at least 8"), "short passwords refused");
        account(owner, "register", "correct horse", "different!");
        check(chatHas(owner, "don't match"), "passwords must match");
        signin.mayRegister = p -> false;
        account(owner, "register", "correct horse", "correct horse");
        check(chatHas(owner, "Log in first"), "with nLogin, /login comes first");
        signin.mayRegister = p -> true;
        account(owner, "register", "correct horse", "correct horse");
        check(signin.signedIn(owner.id) && chatHas(owner, "Account made"), "account made and signed in");
        ShopTest.sleepCooldown();
        core.fromClient(owner.id, "{\"type\":\"buy\",\"id\":\"partyhat\"}");
        check(owner.coins() < 5000, "signed in: can buy (" + owner.coins() + ")");
        account(owner);
        check(chatHas(owner, "Signed in as"), "/account shows status");
        quit(owner);

        // ---- an impostor joins with the Owner's name ----
        ShopTest.P fake = join("Owner");
        st = lastSignin(fake);
        check(Boolean.TRUE.equals(st.get("exists")) && Boolean.FALSE.equals(st.get("signedIn")) && ((Number) st.get("kickIn")).intValue() > 80,
                "an account name has to sign in: " + st);
        long coins = fake.coins();
        core.fromClient(fake.id, "{\"type\":\"buy\",\"id\":\"tophat\"}");
        core.fromClient(fake.id, "{\"type\":\"equip\",\"category\":\"hat\",\"id\":null}");
        check(fake.coins() == coins, "the impostor can't spend");
        core.fromClient(fake.id, "{\"type\":\"admin\",\"action\":\"open\"}");
        Map<?, ?> adm = fake.last("admin");
        check(adm != null && Boolean.FALSE.equals(adm.get("allowed")), "nor open the admin menu");
        account(fake, "login", "letmein123");
        check(!signin.signedIn(fake.id) && chatHas(fake, "Wrong password"), "wrong password refused");
        account(fake, "login", "letmein124");
        check(chatHas(fake, "One moment"), "tries are spaced out");
        // too slow: kicked
        net.skew = 91_000;
        signin.tick();
        net.skew = 0;
        check(!net.online.containsKey(fake.id) && net.kicked.size() == 1 && net.kicked.get(0).contains("didn't sign in"), "kicked: " + net.kicked);

        // ---- a look-alike spelling ----
        ShopTest.P alike = join("owner");
        check(net.kicked.size() == 2 && net.kicked.get(1).contains("registered as"), "look-alike spelling kicked: " + net.kicked);

        // ---- the real client: sign in, remember, come back ----
        Cosmetics.init(new PluginChannels.Log() { public void info(String m) { } public void warn(String m) { } });
        ShopTest.P real = new ShopTest.P("Owner");
        real.server = "lobby";
        net.online.put(real.id, real);
        signin.joined(real.id, "Owner");
        connectClient(real);
        PluginChannels.onJoin();
        pump();
        PlebAccount pa = Cosmetics.plebAccount();
        check(pa.enabled && pa.checked && pa.exists && !pa.signedIn && "Owner".equals(pa.name) && pa.secondsLeft() > 0,
                "client sees: sign in needed " + pa.name + " " + pa.secondsLeft());
        Cosmetics.signIn("wrong password", true);
        pump();
        check(!pa.signedIn && pa.message != null && pa.message.contains("Wrong"), "client: wrong password message " + pa.message);
        ShopTest.sleepCooldown();
        wait(1300);
        Cosmetics.signIn("correct horse", true);
        pump();
        check(pa.signedIn && signin.signedIn(real.id) && pa.hasSavedToken(), "client signed in and kept a token");
        // the token is kept by panel + name, never the password
        quit(real);
        PluginChannels.reset();
        Cosmetics.reset();

        real = new ShopTest.P("Owner");
        real.server = "lobby";
        net.online.put(real.id, real);
        signin.joined(real.id, "Owner");
        connectClient(real);
        PluginChannels.onJoin();
        pump();
        pa = Cosmetics.plebAccount();
        check(pa.signedIn && signin.signedIn(real.id), "next join: signed in by itself with the saved token");

        // ---- the panel: sync, admin changes, password reset signs everyone out ----
        Map<String, Object> snap = core.snapshot();
        link.sync(snap);
        String ownerCookie = null;
        // the first owner is made from the command line
        ProcessBuilder cli = new ProcessBuilder("python3", new File("../../panel/app.py").getAbsolutePath(), "set-role", "Owner", "owner");
        cli.environment().putAll(env);
        check(cli.redirectErrorStream(true).start().waitFor() == 0, "set-role from the command line");
        Object[] r = web("POST", "/api/login", "{\"name\":\"Owner\",\"password\":\"correct horse\"}", null);
        ownerCookie = (String) r[2];
        check((Integer) r[0] == 200 && ownerCookie != null, "owner signs in on the website");
        r = web("GET", "/api/admin/player?name=Owner", null, ownerCookie);
        Map<?, ?> pl = (Map<?, ?>) ((Map<?, ?>) r[1]).get("player");
        check(pl != null && ((Number) pl.get("coins")).longValue() == real.coins() && ((List<?>) pl.get("owned")).contains("partyhat"),
                "panel shows coins and cosmetics: " + pl);
        r = web("POST", "/api/admin/command", "{\"name\":\"Owner\",\"action\":\"coins\",\"op\":\"give\",\"amount\":250}", ownerCookie);
        check((Integer) r[0] == 200, "admin queues coins");
        long had = real.coins();
        List<Map<?, ?>> cmds = link.commands();
        check(cmds.size() == 1, "proxy picks up the change: " + cmds);
        Map<?, ?> c = cmds.get(0);
        List<String> args = new ArrayList<>();
        for (Object o : (List<?>) c.get("args")) args.add(o instanceof Number ? String.valueOf(((Number) o).longValue()) : String.valueOf(o));
        String[] a = PanelLink.plebshopArgs((String) c.get("target"), (String) c.get("action"), args);
        core.command(new ShopCore.Sender() {
            public void reply(String l) { }
            public boolean hasPermission(String p) { return true; }
        }, "plebshop", a, () -> { });
        check(real.coins() == had + 250, "and applies it: " + had + " -> " + real.coins());
        List<Map<String, Object>> res = new ArrayList<>();
        Map<String, Object> one = new LinkedHashMap<>();
        one.put("id", ((Number) c.get("id")).intValue()); one.put("ok", true); one.put("result", "ok");
        res.add(one);
        link.ack(res);
        check(link.commands().isEmpty(), "a change is applied once");

        // web registration with a code from the game
        ShopTest.P newbie = join("Newbie");
        account(newbie, "code");
        String codeLine = null;
        for (String l : newbie.chat) if (l.contains("Your code")) codeLine = l;
        check(codeLine != null, "/account code gives a code: " + newbie.chat);
        String code = codeLine.replaceAll("(?i)§[0-9a-fk-or]", "").replaceAll(".*Your code: ([A-Z0-9-]+).*", "$1");
        r = web("POST", "/api/register", "{\"name\":\"newbie\",\"code\":\"" + code + "\",\"password\":\"newbie pass\"}", null);
        check((Integer) r[0] == 200 && "Newbie".equals(((Map<?, ?>) ((Map<?, ?>) r[1]).get("account")).get("name")), "website account with the game's code: " + r[1]);

        // admin resets the Owner's password: the saved client token stops working and they're signed out in game
        r = web("POST", "/api/admin/account", "{\"name\":\"Newbie\",\"action\":\"password\",\"password\":\"reset pass 1\"}", ownerCookie);
        check((Integer) r[0] == 200, "owner resets a player's password");
        cmds = link.commands();
        check(cmds.size() == 1 && "signout".equals(cmds.get(0).get("action")), "sign-out queued for the game: " + cmds);
        signin.signOutName("Newbie");
        check(!signin.signedIn(newbie.id), "Newbie signed out");

        // signing out on the client forgets the token
        Cosmetics.signOut();
        pump();
        check(!pa.signedIn && !pa.hasSavedToken() && !signin.signedIn(real.id), "client sign out forgets the token");
    }

    public static void main(String[] a) throws Exception {
        File dir = Files.createTempDirectory("plebacc").toFile();
        Logger log = Logger.getLogger("test");
        log.setLevel(java.util.logging.Level.WARNING);
        System.exit(run(dir, log) == 0 ? 0 : 1);
    }
}
