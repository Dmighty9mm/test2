package net.plebmc.shop;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;

/** Quests with a fixed clock: day/week rollover, login streaks, the daily pick. */
public class QuestsTest {
    static int checks, failed;
    static void check(boolean ok, String what) { checks++; if (!ok) { failed++; System.out.println("FAIL " + what); } }

    static long at(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ZoneId.of("America/Chicago")).toInstant().toEpochMilli();
    }

    public static int run() throws Exception {
        File f = File.createTempFile("cfg", ".yml");
        try (java.io.InputStream in = QuestsTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            Files.copy(in, f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Quests q = new Quests(YamlConfiguration.loadConfiguration(f));
        check(q.warnings.isEmpty(), "default config has no problems: " + q.warnings);
        check(q.dailyPool.size() == 7 && q.weeklyPool.size() == 5, "pools");

        long mon = at(2026, 11, 23, 12, 0); // a Monday, noon in Chicago
        check(q.dayKey(mon).equals("2026-11-23"), "day key " + q.dayKey(mon));
        check(q.secondsToDailyReset(mon) == 12 * 3600, "12h to midnight: " + q.secondsToDailyReset(mon));
        check(q.secondsToWeeklyReset(mon) == 6 * 86400 + 12 * 3600, "weekly reset next Monday: " + q.secondsToWeeklyReset(mon));
        List<Quests.Quest> a1 = q.activeDaily(mon), a2 = q.activeDaily(mon + 3600_000);
        check(a1.size() == 3 && a1.get(0).id.equals(a2.get(0).id) && a1.get(2).id.equals(a2.get(2).id), "same quests all day");
        boolean differs = false;
        for (int d = 1; d < 8; d++) {
            List<Quests.Quest> o = q.activeDaily(mon + d * 86400_000L);
            if (!o.get(0).id.equals(a1.get(0).id) || !o.get(1).id.equals(a1.get(1).id) || !o.get(2).id.equals(a1.get(2).id)) differs = true;
        }
        check(differs, "quests change between days");
        check(q.activeWeekly(mon).size() == 2, "2 weekly quests");

        // login streak
        Store.Data d = new Store.Data();
        check(q.login(d, mon) == 50 && d.streak == 1, "day 1");
        check(q.login(d, mon + 3600_000) == -1, "same day: nothing");
        check(q.login(d, mon + 21 * 3600_000L) == 75 && d.streak == 2, "day 2 after 21h");
        check(q.login(d, mon + 42 * 3600_000L) == 100 && d.streak == 3, "day 3");
        check(q.login(d, mon + 42 * 3600_000L + 49 * 3600_000L) == 50 && d.streak == 1, "missed 2 days: streak resets");
        Store.Data e = new Store.Data();
        long t = mon;
        long total = 0;
        for (int i = 0; i < 8; i++) { total += q.login(e, t); t += 24 * 3600_000L; }
        check(total == 50 + 75 + 100 + 125 + 150 + 200 + 300 + 50 && e.streak == 8, "8 days in a row wraps: " + total);
        check(d.coins == 50 + 75 + 100 + 50, "login coins add up: " + d.coins);

        // progress resets with the day, weekly keeps going
        Store.Data p = new Store.Data();
        Quests.Quest daily = q.activeDaily(mon).get(0);
        q.add(p, daily.type, 1, mon);
        check(p.progress.getOrDefault(daily.id, 0L) >= 1 || p.done.contains(daily.id), "daily progress counted");
        q.add(p, "kill", 1, mon);
        long weeklyKills = p.progress.getOrDefault("kills_40", 0L);
        q.roll(p, mon + 86400_000L);
        check(!p.progress.containsKey(daily.id) || q.weeklyPool.stream().anyMatch(w -> w.id.equals(daily.id)), "daily progress cleared next day");
        check(p.progress.getOrDefault("kills_40", 0L) == weeklyKills, "weekly progress kept on Tuesday");
        q.roll(p, mon + 7 * 86400_000L);
        check(!p.progress.containsKey("kills_40"), "weekly cleared next Monday");

        // reset-hour shifts the day
        String cfg = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).replace("reset-hour: 0", "reset-hour: 6");
        Files.write(f.toPath(), cfg.getBytes(StandardCharsets.UTF_8));
        Quests q6 = new Quests(YamlConfiguration.loadConfiguration(f));
        check(q6.dayKey(at(2026, 11, 24, 5, 0)).equals("2026-11-23") && q6.dayKey(at(2026, 11, 24, 6, 0)).equals("2026-11-24"), "reset-hour 6");

        // playtime
        Store.Data m = new Store.Data();
        long paid = 0;
        for (int i = 0; i < 45; i++) paid += q.minute(m);
        check(paid == 30 && m.coins == 30, "10 coins per 15 minutes: " + paid);
        System.out.println(checks - failed + "/" + checks + " quest checks passed");
        return failed;
    }
}
