package net.plebmc.shop;

import java.io.File;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * LiteBans' punishments on their way to the panel: an H2 database with
 * LiteBans' tables behind a stand-in for its API (test/litebans/api), the real
 * Punishments reading it, and the real panel (panel/app.py) taking the rows.
 */
public class PunishmentsTest {
    static int checks, failed;
    static void check(boolean ok, String what) { checks++; if (!ok) { failed++; System.out.println("FAIL " + what); } }

    static final String KEY = AccountsTest.KEY;
    static final String STEVE = "11111111-1111-1111-1111-111111111111", ALEX = "22222222-2222-2222-2222-222222222222";

    static void sql(String... statements) throws Exception {
        try (Connection c = DriverManager.getConnection(litebans.api.Database.url); Statement st = c.createStatement()) {
            for (String s : statements) st.execute(s);
        }
    }

    /** LiteBans' tables (2.x), as it makes them in H2. */
    static void tables() throws Exception {
        for (String t : new String[] { "bans", "mutes", "warnings", "kicks" }) {
            sql("CREATE TABLE litebans_" + t + " (id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36), ip VARCHAR(45), reason VARCHAR(2048),"
                    + " banned_by_uuid VARCHAR(36) NOT NULL, banned_by_name VARCHAR(128),"
                    + (t.equals("kicks") ? "" : " removed_by_uuid VARCHAR(36), removed_by_name VARCHAR(128), removed_by_reason VARCHAR(2048), removed_by_date TIMESTAMP,")
                    + " time BIGINT NOT NULL, until BIGINT NOT NULL, template SMALLINT DEFAULT 255, server_scope VARCHAR(32), server_origin VARCHAR(32),"
                    + " silent BIT NOT NULL, ipban BIT NOT NULL, ipban_wildcard BIT NOT NULL DEFAULT 0, active BIT NOT NULL)");
        }
        sql("CREATE TABLE litebans_history (id BIGINT AUTO_INCREMENT PRIMARY KEY, date TIMESTAMP NOT NULL, name VARCHAR(16), uuid VARCHAR(36), ip VARCHAR(45))");
    }

    static void punish(String table, String uuid, String reason, String by, long time, long until, boolean active) throws Exception {
        sql("INSERT INTO litebans_" + table + " (uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, server_scope, server_origin, silent, ipban, active)"
                + " VALUES ('" + uuid + "', '203.0.113.9', '" + reason + "', 'CONSOLE', '" + by + "', " + time + ", " + until + ", '*', 'proxy', 0, 0, " + (active ? 1 : 0) + ")");
    }

    public static int run(File dir, Logger log) throws Exception {
        int port;
        try (ServerSocket ss = new ServerSocket(0)) { port = ss.getLocalPort(); }
        ProcessBuilder pb = new ProcessBuilder("python3", new File("../../panel/app.py").getAbsolutePath());
        Map<String, String> env = pb.environment();
        env.put("PANEL_SERVER_KEY", KEY);
        env.put("PANEL_PORT", String.valueOf(port));
        env.put("PANEL_HOST", "127.0.0.1");
        env.put("PANEL_DB", new File(dir, "punish-panel.db").getAbsolutePath());
        env.put("PANEL_AUTO_UPDATE", "0");
        pb.redirectErrorStream(true).redirectOutput(new File(dir, "punish-panel.log"));
        Process panel = pb.start();
        try {
            PanelLink link = new PanelLink(PanelHttp.REAL, "http://127.0.0.1:" + port, KEY);
            for (int i = 0; i < 100; i++) {
                try { link.lookup("Nobody"); break; } catch (java.io.IOException e) { Thread.sleep(100); }
            }
            scenario(link, log);
        } finally {
            panel.destroy();
        }
        System.out.println(checks - failed + "/" + checks + " punishment checks passed");
        return failed;
    }

    @SuppressWarnings("unchecked")
    static void scenario(PanelLink link, Logger log) throws Exception {
        litebans.api.Database.url = "jdbc:h2:mem:litebans;DB_CLOSE_DELAY=-1";
        tables();
        long t0 = 1_800_000_000_000L;
        sql("INSERT INTO litebans_history (date, name, uuid, ip) VALUES ('2026-04-01 10:00:00', 'Steve', '" + STEVE + "', '203.0.113.9')",
                "INSERT INTO litebans_history (date, name, uuid, ip) VALUES ('2026-03-01 10:00:00', 'Alex', '" + ALEX + "', '203.0.113.9')",
                // Steve tried another name in February, then went back: LiteBans re-dates the old row, so the newest date wins, not the newest row
                "INSERT INTO litebans_history (date, name, uuid, ip) VALUES ('2026-02-01 10:00:00', 'Steve_New', '" + STEVE + "', '203.0.113.9')");
        punish("bans", STEVE, "Griefing", "Mod", t0, 0, true);
        punish("bans", ALEX, "Fly hacks", "Console", t0 + 1000, t0 + 86_400_000L, true);
        punish("mutes", ALEX, "Spam", "Mod", t0 + 2000, t0 + 3_600_000L, true);
        punish("warnings", STEVE, "Caps", "Helper", t0 + 3000, 0, true);
        sql("INSERT INTO litebans_kicks (uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, server_scope, server_origin, silent, ipban, active)"
                + " VALUES ('" + ALEX + "', '203.0.113.9', 'AFK', 'CONSOLE', 'Helper', " + (t0 + 4000) + ", 0, 'plebnite', 'plebnite', 1, 0, 1)");

        Punishments.Source src = Punishments.liteBans();
        check(src != null, "LiteBans' API is found");
        List<Map<String, Object>> sent = new ArrayList<>();
        Punishments.Sink sink = body -> { sent.add(body); link.punishments(body); };
        Punishments p = new Punishments(src, log);
        long now = t0 + 10_000;

        // the first tick sends everything, one kind at a time, and each kind's last page says so
        p.tick(sink, now);
        check(sent.size() == 4, "a full pass sends a page per kind: " + sent.size());
        List<Map<String, Object>> bans = (List<Map<String, Object>>) sent.get(0).get("rows");
        Map<String, Object> ban = bans.get(0);
        check(bans.size() == 2 && "ban".equals(ban.get("kind")) && "Steve".equals(ban.get("name")) && "Griefing".equals(ban.get("reason"))
                && "Mod".equals(ban.get("staff")) && Long.valueOf(t0).equals(ban.get("time")) && Long.valueOf(0).equals(ban.get("until"))
                && Boolean.TRUE.equals(ban.get("active")) && STEVE.equals(ban.get("uuid")), "a ban with the player's newest name: " + ban);
        check("".equals(ban.get("removedBy")) && Long.valueOf(0).equals(ban.get("removedAt")), "nobody lifted it: " + ban);
        check(Boolean.TRUE.equals(((Map<?, ?>) sent.get(0).get("sweep")).get("done")), "the page ends the pass for bans");
        Map<String, Object> kick = ((List<Map<String, Object>>) sent.get(3).get("rows")).get(0);
        check("kick".equals(kick.get("kind")) && "Alex".equals(kick.get("name")) && "plebnite".equals(kick.get("server")) && Boolean.TRUE.equals(kick.get("silent")),
                "a kick (its table has no removed_by columns): " + kick);
        check(!new com.google.gson.Gson().toJson(sent).contains("203.0.113"), "IP addresses are never sent");

        // nothing changed: nothing is sent, until the 2 minute check-in
        sent.clear();
        p.tick(sink, now += 20_000);
        check(sent.size() == 1 && ((List<?>) sent.get(0).get("rows")).size() == 5 && sent.get(0).get("sweep") == null, "then the newest of every kind: " + sent);
        sent.clear();
        p.tick(sink, now += 20_000);
        check(sent.isEmpty(), "nothing new, nothing sent");
        p.tick(sink, now += Punishments.HEARTBEAT);
        check(sent.size() == 1, "a check-in every 2 minutes");

        // an unban is sent on the next tick
        sent.clear();
        sql("UPDATE litebans_bans SET active=0, removed_by_name='Boss', removed_by_reason='Appealed', removed_by_date='2027-01-15 12:00:00' WHERE id=1");
        p.tick(sink, now += 20_000);
        Map<String, Object> lifted = null;
        if (sent.size() == 1) {
            for (Map<String, Object> r : (List<Map<String, Object>>) sent.get(0).get("rows")) {
                if ("ban".equals(r.get("kind")) && Long.valueOf(1).equals(r.get("id"))) lifted = r;
            }
        }
        check(lifted != null && Boolean.FALSE.equals(lifted.get("active")) && "Boss".equals(lifted.get("removedBy")) && "Appealed".equals(lifted.get("removedReason"))
                && ((Number) lifted.get("removedAt")).longValue() > t0, "an unban: " + lifted);

        // more rows than a page: a full pass sends them all
        StringBuilder many = new StringBuilder("INSERT INTO litebans_warnings (uuid, reason, banned_by_uuid, banned_by_name, time, until, silent, ipban, active) VALUES ");
        for (int i = 0; i < Punishments.PAGE + 50; i++) {
            many.append(i == 0 ? "" : ",").append("('").append(ALEX).append("', 'w").append(i).append("', 'CONSOLE', 'Console', ").append(t0 + 5000 + i).append(", 0, 0, 0, 1)");
        }
        sql(many.toString());
        sent.clear();
        p.tick(sink, now += Punishments.FULL_EVERY);
        int warns = 0, pages = 0;
        for (Map<String, Object> b : sent) {
            Map<?, ?> s = (Map<?, ?>) b.get("sweep");
            if (s != null && String.valueOf(s.get("kinds")).contains("warn")) { pages++; warns += ((List<?>) b.get("rows")).size(); }
        }
        check(pages == 2 && warns == Punishments.PAGE + 51, "a full pass pages through every warning: " + pages + " pages, " + warns);

        // LiteBans stops answering: the panel is told, and nothing is thrown
        litebans.api.Database.broken = true;
        sent.clear();
        Logger quiet = Logger.getLogger("punishments-quiet");
        quiet.setLevel(java.util.logging.Level.OFF);
        Punishments p2 = new Punishments(src, quiet);
        p2.tick(sink, now);
        check(sent.size() == 1 && Boolean.FALSE.equals(sent.get(0).get("available")) && String.valueOf(sent.get(0).get("reason")).contains("database is closed"),
                "a LiteBans error is reported: " + sent);
        litebans.api.Database.broken = false;
    }

    public static void main(String[] a) throws Exception {
        File dir = java.nio.file.Files.createTempDirectory("plebpunish").toFile();
        Logger log = Logger.getLogger("test");
        log.setLevel(java.util.logging.Level.WARNING);
        System.exit(run(dir, log) == 0 ? 0 : 1);
    }
}
