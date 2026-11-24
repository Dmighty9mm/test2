package net.plebmc.shop;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;

/**
 * How players earn PlebMc Coins: a daily login reward with a streak, coins
 * for time online, and daily/weekly quests. Today's quests are the same for
 * everyone (picked from the pool with the date as the seed) and change at
 * {@code reset-hour} in {@code timezone}. Pure logic, no Bukkit calls, so it
 * is easy to test with any clock.
 */
final class Quests {

	static final String DAILY = "daily";
	static final String WEEKLY = "weekly";

	static final class Quest {
		final String id;
		final String name;
		final String description;
		final String type;
		final long goal;
		final long reward;
		final String period;

		Quest(String id, String name, String description, String type, long goal, long reward, String period) {
			this.id = id;
			this.name = name;
			this.description = description;
			this.type = type;
			this.goal = goal;
			this.reward = reward;
			this.period = period;
		}
	}

	/** A quest a player just finished. */
	static final class Completed {
		final Quest quest;

		Completed(Quest q) {
			this.quest = q;
		}
	}

	final ZoneId zone;
	final int resetHour;
	final int dailyCount;
	final int weeklyCount;
	final List<Quest> dailyPool = new ArrayList<>();
	final List<Quest> weeklyPool = new ArrayList<>();
	final List<String> warnings = new ArrayList<>();

	final boolean loginEnabled;
	final long[] loginRewards;
	final long loginCooldownMs;
	final long streakResetMs;

	final boolean playEnabled;
	final int playEveryMinutes;
	final long playCoins;

	Quests(ConfigurationSection root) {
		ZoneId z;
		String tz = root == null ? "" : root.getString("timezone", "");
		try {
			z = tz == null || tz.isEmpty() ? ZoneId.systemDefault() : ZoneId.of(tz);
		} catch (RuntimeException e) {
			warnings.add("Unknown timezone '" + tz + "', using the server's");
			z = ZoneId.systemDefault();
		}
		zone = z;
		ConfigurationSection q = root == null ? null : root.getConfigurationSection("quests");
		resetHour = q == null ? 0 : Math.max(0, Math.min(23, q.getInt("reset-hour", 0)));
		dailyCount = q == null ? 3 : Math.max(0, q.getInt("daily-count", 3));
		weeklyCount = q == null ? 2 : Math.max(0, q.getInt("weekly-count", 2));
		Set<String> ids = new HashSet<>();
		readPool(q == null ? null : q.getConfigurationSection("daily"), DAILY, dailyPool, ids);
		readPool(q == null ? null : q.getConfigurationSection("weekly"), WEEKLY, weeklyPool, ids);

		ConfigurationSection l = root == null ? null : root.getConfigurationSection("login-rewards");
		loginEnabled = l == null || l.getBoolean("enabled", true);
		List<String> rewards = l == null ? Collections.<String>emptyList() : l.getStringList("rewards");
		List<Long> parsed = new ArrayList<>();
		for (String r : rewards) {
			try {
				parsed.add(Math.max(0, Long.parseLong(r.trim())));
			} catch (NumberFormatException e) {
				warnings.add("login-rewards.rewards: '" + r + "' is not a number");
			}
		}
		if (parsed.isEmpty()) {
			parsed.add(50L);
		}
		loginRewards = new long[parsed.size()];
		for (int i = 0; i < loginRewards.length; i++) {
			loginRewards[i] = parsed.get(i);
		}
		loginCooldownMs = 3_600_000L * (l == null ? 20 : Math.max(1, l.getInt("cooldown-hours", 20)));
		streakResetMs = 3_600_000L * (l == null ? 48 : Math.max(1, l.getInt("streak-reset-hours", 48)));

		ConfigurationSection p = root == null ? null : root.getConfigurationSection("playtime");
		playEnabled = p == null || p.getBoolean("enabled", true);
		playEveryMinutes = p == null ? 15 : Math.max(1, p.getInt("every-minutes", 15));
		playCoins = p == null ? 10 : Math.max(0, p.getLong("coins", 10));
	}

	private void readPool(ConfigurationSection sec, String period, List<Quest> out, Set<String> ids) {
		if (sec == null) {
			return;
		}
		for (String id : sec.getKeys(false)) {
			ConfigurationSection s = sec.getConfigurationSection(id);
			if (s == null) {
				continue;
			}
			if (!ids.add(id)) {
				warnings.add("quest id '" + id + "' is used twice, skipping the " + period + " one");
				continue;
			}
			String type = s.getString("type", "").toLowerCase();
			if (type.isEmpty()) {
				warnings.add("quest " + id + " has no type");
				continue;
			}
			long goal = Math.max(1, s.getLong("goal", 1));
			out.add(new Quest(id, s.getString("name", id), s.getString("description", ""), type, goal,
					Math.max(0, s.getLong("reward", 0)), period));
		}
	}

	// ==================== periods ====================

	/** The quest day: a day starts at reset-hour. */
	private LocalDate questDay(long now) {
		return ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).minusHours(resetHour).toLocalDate();
	}

	String dayKey(long now) {
		return questDay(now).toString();
	}

	String weekKey(long now) {
		LocalDate d = questDay(now);
		return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
	}

	long secondsToDailyReset(long now) {
		ZonedDateTime next = questDay(now).plusDays(1).atStartOfDay(zone).plusHours(resetHour);
		return Math.max(0, (next.toInstant().toEpochMilli() - now) / 1000);
	}

	long secondsToWeeklyReset(long now) {
		LocalDate monday = questDay(now).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
		ZonedDateTime next = monday.atStartOfDay(zone).plusHours(resetHour);
		return Math.max(0, (next.toInstant().toEpochMilli() - now) / 1000);
	}

	List<Quest> activeDaily(long now) {
		return pick(dailyPool, dailyCount, dayKey(now));
	}

	List<Quest> activeWeekly(long now) {
		return pick(weeklyPool, weeklyCount, weekKey(now));
	}

	List<Quest> active(long now) {
		List<Quest> all = new ArrayList<>(activeDaily(now));
		all.addAll(activeWeekly(now));
		return all;
	}

	/** {@code n} quests from the pool, the same for everyone for a given key, in pool order. */
	private static List<Quest> pick(List<Quest> pool, int n, String key) {
		if (n >= pool.size()) {
			return new ArrayList<>(pool);
		}
		List<Integer> idx = new ArrayList<>();
		for (int i = 0; i < pool.size(); i++) {
			idx.add(i);
		}
		Collections.shuffle(idx, new Random(key.hashCode() * 31L + 17));
		List<Integer> chosen = new ArrayList<>(idx.subList(0, n));
		Collections.sort(chosen);
		List<Quest> out = new ArrayList<>();
		for (int i : chosen) {
			out.add(pool.get(i));
		}
		return out;
	}

	// ==================== progress ====================

	/** Clears progress from a finished day/week. True if anything changed. */
	boolean roll(Store.Data d, long now) {
		boolean changed = false;
		String day = dayKey(now);
		if (!day.equals(d.dailyKey)) {
			clear(d, dailyPool);
			d.dailyKey = day;
			changed = true;
		}
		String week = weekKey(now);
		if (!week.equals(d.weeklyKey)) {
			clear(d, weeklyPool);
			d.weeklyKey = week;
			changed = true;
		}
		return changed;
	}

	private static void clear(Store.Data d, List<Quest> pool) {
		for (Quest q : pool) {
			d.progress.remove(q.id);
			d.done.remove(q.id);
		}
	}

	/**
	 * Counts {@code amount} of {@code type} (kill, play, win, ...) toward the
	 * player's active quests and pays out any that are now finished.
	 */
	List<Completed> add(Store.Data d, String type, long amount, long now) {
		roll(d, now);
		List<Completed> out = new ArrayList<>();
		if (amount <= 0) {
			return out;
		}
		for (Quest q : active(now)) {
			if (!q.type.equals(type) || d.done.contains(q.id)) {
				continue;
			}
			long p = Math.min(q.goal, d.progress.getOrDefault(q.id, 0L) + amount);
			d.progress.put(q.id, p);
			if (p >= q.goal) {
				d.done.add(q.id);
				d.coins += q.reward;
				out.add(new Completed(q));
			}
		}
		return out;
	}

	// ==================== login and playtime ====================

	/** Gives the daily login reward if it's due. Returns the coins given, or -1. */
	long login(Store.Data d, long now) {
		if (!loginEnabled || (d.lastLogin != 0 && now - d.lastLogin < loginCooldownMs)) {
			return -1;
		}
		d.streak = d.lastLogin == 0 || now - d.lastLogin > streakResetMs ? 1 : d.streak + 1;
		d.lastLogin = now;
		long r = loginRewards[(d.streak - 1) % loginRewards.length];
		d.coins += r;
		return r;
	}

	boolean loginClaimed(Store.Data d, long now) {
		return d.lastLogin != 0 && now - d.lastLogin < loginCooldownMs;
	}

	/** The reward for the next login (shown in the client before it's claimed). */
	long nextLoginReward(Store.Data d, long now) {
		int next = d.lastLogin == 0 || now - d.lastLogin > streakResetMs ? 1 : d.streak + 1;
		return loginRewards[(next - 1) % loginRewards.length];
	}

	/** One minute online. Returns playtime coins given (0 most minutes). */
	long minute(Store.Data d) {
		if (!playEnabled || playCoins <= 0) {
			return 0;
		}
		d.playMinutes++;
		if (d.playMinutes >= playEveryMinutes) {
			d.playMinutes = 0;
			d.coins += playCoins;
			return playCoins;
		}
		return 0;
	}

	/** The quest list as the client shows it: the login reward first, then daily, then weekly. */
	List<Object> toJson(Store.Data d, long now) {
		roll(d, now);
		List<Object> out = new ArrayList<>();
		if (loginEnabled) {
			boolean claimed = loginClaimed(d, now);
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", "login");
			m.put("name", "Daily login");
			m.put("description", claimed ? "Day " + d.streak + " of your streak. Come back tomorrow!"
					: "Join PlebMc every day for a bigger reward");
			m.put("period", "login");
			m.put("progress", claimed ? 1 : 0);
			m.put("goal", 1);
			m.put("reward", claimed ? loginRewards[(d.streak - 1) % loginRewards.length] : nextLoginReward(d, now));
			m.put("done", claimed);
			out.add(m);
		}
		for (Quest q : active(now)) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", q.id);
			m.put("name", q.name);
			m.put("description", q.description);
			m.put("period", q.period);
			m.put("progress", d.progress.getOrDefault(q.id, 0L));
			m.put("goal", q.goal);
			m.put("reward", q.reward);
			m.put("done", d.done.contains(q.id));
			out.add(m);
		}
		return out;
	}
}
