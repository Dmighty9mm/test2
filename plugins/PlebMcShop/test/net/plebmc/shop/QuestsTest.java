package net.plebmc.shop;

import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/** Quests with a fixed clock: categories, day/week rollover, login streaks, the daily pick. */
public class QuestsTest {
    static int checks, failed;
    static void check(boolean ok, String what) { checks++; if (!ok) { failed++; System.out.println("FAIL " + what); } }

    static long at(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ZoneId.of("America/Chicago")).toInstant().toEpochMilli();
    }

    static Cfg config(String... replace) throws Exception {
        String s;
        try (InputStream in = QuestsTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            s = new String(readAll(in), "UTF-8");
        }
        for (int i = 0; i + 1 < replace.length; i += 2) s = s.replace(replace[i], replace[i + 1]);
        return Cfg.load(new java.io.ByteArrayInputStream(s.getBytes("UTF-8")));
    }

    static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192]; int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        return o.toByteArray();
    }

    static int count(List<Quests.Quest> qs, String cat, String period) {
        int n = 0;
        for (Quests.Quest q : qs) if (q.category.equals(cat) && q.period.equals(period)) n++;
        return n;
    }

    public static int run() throws Exception {
        Quests q = new Quests(config());
        check(q.warnings.isEmpty(), "default config has no problems: " + q.warnings);
        check(q.categories.keySet().toString().equals("[general, plebnite]"), "two categories: " + q.categories.keySet());
        Quests.Category gen = q.categories.get("general"), pn = q.categories.get("plebnite");
        check("General".equals(gen.name) && "PlebNite".equals(pn.name) && "general".equals(gen.icon) && "#FF9A3C".equals(pn.color), "names, icons, colors");
        check(gen.daily.size() == 4 && gen.weekly.size() == 3 && pn.daily.size() == 6 && pn.weekly.size() == 4,
                "pool sizes " + gen.daily.size() + "/" + gen.weekly.size() + " " + pn.daily.size() + "/" + pn.weekly.size());
        check(q.categoriesOf("lobby").toString().equals("[general]") && q.categoriesOf("PlebNite").toString().equals("[general, plebnite]")
                && q.categoriesOf("survival").toString().equals("[general]") && q.categoriesOf(null).toString().equals("[general]"),
                "server -> categories: General everywhere, PlebNite only on plebnite");
        check("general".equals(q.loginCategory), "login row in General");
        check(q.quest("kills_3").category.equals("plebnite") && q.quest("general_chat_10").type.equals("chat"), "quest lookup");
        for (Quests.Quest x : q.allQuests()) check(!x.id.startsWith("lobby"), "no lobby quests: " + x.id);

        long mon = at(2026, 11, 23, 12, 0); // a Monday, noon in Chicago
        check(q.dayKey(mon).equals("2026-11-23"), "day key " + q.dayKey(mon));
        check(q.secondsToDailyReset(mon) == 12 * 3600, "12h to midnight: " + q.secondsToDailyReset(mon));
        check(q.secondsToWeeklyReset(mon) == 6 * 86400 + 12 * 3600, "weekly reset next Monday: " + q.secondsToWeeklyReset(mon));
        List<Quests.Quest> a1 = q.active(mon), a2 = q.active(mon + 3600_000);
        check(a1.size() == 10, "3+2 per category: " + a1.size());
        check(count(a1, "general", "daily") == 3 && count(a1, "general", "weekly") == 2 && count(a1, "plebnite", "daily") == 3
                && count(a1, "plebnite", "weekly") == 2, "per-category counts");
        boolean same = true;
        for (int i = 0; i < a1.size(); i++) same &= a1.get(i).id.equals(a2.get(i).id);
        check(same, "same quests all day");
        boolean differs = false;
        for (int d = 1; d < 8; d++) {
            List<Quests.Quest> o = q.active(mon + d * 86400_000L);
            for (int i = 0; i < 3; i++) if (!o.get(i).id.equals(a1.get(i).id)) differs = true;
        }
        check(differs, "general quests change between days");

        // progress only counts in its category
        Quests all = new Quests(config("daily-count: 3", "daily-count: 99", "weekly-count: 2", "weekly-count: 99"));
        Store.Data p = new Store.Data();
        List<Quests.Quest> done = all.add(p, "lobby", "kill", 3, mon);
        check(done.isEmpty() && !p.progress.containsKey("kills_3") && !p.progress.containsKey("kills_40"), "kills in the lobby: PlebNite untouched");
        all.add(p, "plebnite", "kill", 3, mon);
        check(p.done.contains("kills_3") && p.progress.get("kills_8") == 3 && p.progress.get("kills_40") == 3, "plebnite kills");
        all.add(p, "lobby", "play", 10, mon);
        check(p.progress.get("general_play_30") == 10 && !p.progress.containsKey("play_30"), "lobby playtime: General only");
        all.add(p, "plebnite", "play", 10, mon);
        check(p.progress.get("general_play_30") == 20 && p.progress.get("play_30") == 10, "plebnite playtime: General and PlebNite");
        all.add(p, null, "login", 1, mon);
        check(p.progress.get("login_5") == 1, "the login counts in General");
        done = all.add(p, "survival", "chat", 10, mon);
        check(done.size() == 1 && done.get(0).id.equals("general_chat_10"), "chat anywhere counts");
        long before = p.progress.get("general_chat_100");
        check(all.add(p, "lobby", "chat", 5, mon).isEmpty() && p.progress.get("general_chat_10") == 10
                && p.progress.get("general_chat_100") == before + 5, "a finished quest stops counting (pays once)");
        check(all.add(p, "plebnite", "kill", 0, mon).isEmpty(), "0 does nothing");

        // old quests (the retired Lobby ones) are forgotten, everything else is kept
        Store.Data old = new Store.Data();
        old.dailyKey = q.dayKey(mon); old.weeklyKey = q.weekKey(mon);
        old.progress.put("lobby_parkour_5", 3L); old.progress.put("kills_40", 7L); old.done.add("lobby_join_3"); old.done.add("kills_3");
        check(q.roll(old, mon) && !old.progress.containsKey("lobby_parkour_5") && old.progress.get("kills_40") == 7
                && !old.done.contains("lobby_join_3") && old.done.contains("kills_3"), "retired quests purged, others kept");

        // login streak
        Store.Data d = new Store.Data();
        check(q.login(d, mon) == 50 && d.streak == 1, "day 1");
        check(q.login(d, mon + 3600_000) == -1, "same day: nothing");
        check(q.login(d, mon + 21 * 3600_000L) == 75 && d.streak == 2, "day 2 after 21h");
        check(q.login(d, mon + 42 * 3600_000L) == 100 && d.streak == 3, "day 3");
        check(q.login(d, mon + 42 * 3600_000L + 49 * 3600_000L) == 50 && d.streak == 1, "missed 2 days: streak resets");
        Store.Data e = new Store.Data();
        long t = mon, total = 0;
        for (int i = 0; i < 8; i++) { total += q.login(e, t); t += 24 * 3600_000L; }
        check(total == 50 + 75 + 100 + 125 + 150 + 200 + 300 + 50 && e.streak == 8, "8 days in a row wraps: " + total);

        // progress resets with the day, weekly keeps going
        Store.Data r = new Store.Data();
        all.add(r, "plebnite", "kill", 1, mon);
        q.roll(r, mon + 86400_000L);
        check(!r.progress.containsKey("kills_8") && r.progress.get("kills_40") == 1, "daily cleared Tuesday, weekly kept");
        q.roll(r, mon + 7 * 86400_000L);
        check(!r.progress.containsKey("kills_40"), "weekly cleared next Monday");

        // reset-hour shifts the day
        Quests q6 = new Quests(config("reset-hour: 0", "reset-hour: 6"));
        check(q6.dayKey(at(2026, 11, 24, 5, 0)).equals("2026-11-23") && q6.dayKey(at(2026, 11, 24, 6, 0)).equals("2026-11-24"), "reset-hour 6");

        // duplicate ids are reported, not doubled
        Quests dup = new Quests(config("general_play_60:", "kills_3:"));
        check(!dup.warnings.isEmpty() && dup.warnings.get(0).contains("kills_3"), "duplicate id warning: " + dup.warnings);

        // playtime
        Store.Data m = new Store.Data();
        long paid = 0;
        for (int i = 0; i < 45; i++) paid += q.minute(m);
        check(paid == 30 && m.coins == 30, "10 coins per 15 minutes: " + paid);
        System.out.println(checks - failed + "/" + checks + " quest checks passed");
        return failed;
    }
}
