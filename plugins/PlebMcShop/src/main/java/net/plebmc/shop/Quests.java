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

/**
 * How players earn PlebMc Coins: a daily login reward with a streak, coins
 * for time online, and daily/weekly quests in categories (one per game, e.g.
 * PlebMc Lobby and PlebNite). Each category's quests for the day are the
 * same for everyone (picked from its pool with the date as the seed) and
 * change at {@code reset-hour} in {@code timezone}. Pure logic, no server
 * calls, so it is easy to test with any clock.
 */
final class Quests {

	static final String DAILY = "daily";
	static final String WEEKLY = "weekly";

	static final class Category {
		final String id;
		final String name;
		final String icon;
		final String color;
		final List<String> servers;
		final List<Quest> daily = new ArrayList<>();
		final List<Quest> weekly = new ArrayList<>();

		Category(String id, String name, String icon, String color, List<String> servers) {
			this.id = id;
			this.name = name;
			this.icon = icon;
			this.color = color;
			this.servers = servers;
		}

		/** True for '*' (every server) or a listed server. A null server (the login) only matches '*'. */
		boolean covers(String server) {
			for (String s : servers) {
				if (s.equals("*") || (server != null && s.equalsIgnoreCase(server))) {
					return true;
				}
			}
			return false;
		}
	}

	static final class Quest {
		final String id;
		final String name;
		final String description;
		final String type;
		final long goal;
		final long reward;
		final String period;
		final String category;

		Quest(String id, String name, String description, String type, long goal, long reward, String period,
				String category) {
			this.id = id;
			this.name = name;
			this.description = description;
			this.type = type;
			this.goal = goal;
			this.reward = reward;
			this.period = period;
			this.category = category;
		}
	}

	final ZoneId zone;
	final int resetHour;
	final int dailyCount;
	final int weeklyCount;
	final Map<String, Category> categories = new LinkedHashMap<>();
	final List<String> warnings = new ArrayList<>();

	final boolean loginEnabled;
	/** The category the daily login row shows under. */
	final String loginCategory;
	final long[] loginRewards;
	final long loginCooldownMs;
	final long streakResetMs;

	final boolean playEnabled;
	final int playEveryMinutes;
	final long playCoins;

	Quests(Cfg root) {
		if (root == null) {
			root = new Cfg(null);
		}
		ZoneId z;
		String tz = root.str("timezone", "");
		try {
			z = tz.isEmpty() ? ZoneId.systemDefault() : ZoneId.of(tz);
		} catch (RuntimeException e) {
			warnings.add("Unknown timezone '" + tz + "', using the server's");
			z = ZoneId.systemDefault();
		}
		zone = z;
		resetHour = Math.max(0, Math.min(23, root.integer("quests.reset-hour", 0)));
		dailyCount = Math.max(0, root.integer("quests.daily-count", 3));
		weeklyCount = Math.max(0, root.integer("quests.weekly-count", 2));

		Cfg cats = root.section("quests.categories");
		Set<String> ids = new HashSet<>();
		if (cats != null) {
			for (String cid : cats.keys()) {
				Cfg c = cats.section(cid);
				if (c == null) {
					continue;
				}
				String name = c.str("name", cid);
				String color = c.str("color", "");
				if (!color.isEmpty() && !color.matches("#[0-9A-Fa-f]{6}")) {
					warnings.add("quests.categories." + cid + ".color must look like '#3FD8E6'");
					color = "";
				}
				Category cat = new Category(cid, name, c.str("icon", "quests"), color, c.list("servers"));
				if (cat.servers.isEmpty()) {
					warnings.add("quests.categories." + cid + " has no servers (use ['*'] for every server)");
				}
				readPool(c.section("daily"), DAILY, cat, cat.daily, ids);
				readPool(c.section("weekly"), WEEKLY, cat, cat.weekly, ids);
				categories.put(cid, cat);
			}
		}
		if (categories.isEmpty()) {
			warnings.add("quests.categories is empty: there are no quests");
		}

		loginEnabled = root.bool("login-rewards.enabled", true);
		String lc = root.str("login-rewards.category", "");
		loginCategory = categories.containsKey(lc) ? lc : categories.isEmpty() ? null : categories.keySet().iterator().next();
		List<Long> parsed = new ArrayList<>();
		for (String r : root.list("login-rewards.rewards")) {
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
		loginCooldownMs = 3_600_000L * Math.max(1, root.integer("login-rewards.cooldown-hours", 20));
		streakResetMs = 3_600_000L * Math.max(1, root.integer("login-rewards.streak-reset-hours", 48));

		playEnabled = root.bool("playtime.enabled", true);
		playEveryMinutes = Math.max(1, root.integer("playtime.every-minutes", 15));
		playCoins = Math.max(0, root.num("playtime.coins", 10));
	}

	private void readPool(Cfg sec, String period, Category cat, List<Quest> out, Set<String> ids) {
		if (sec == null) {
			return;
		}
		for (String id : sec.keys()) {
			Cfg s = sec.section(id);
			if (s == null) {
				continue;
			}
			if (!ids.add(id)) {
				warnings.add("quest id '" + id + "' is used twice, skipping the one in " + cat.id + "." + period);
				continue;
			}
			String type = s.str("type", "").toLowerCase();
			if (type.isEmpty()) {
				warnings.add("quest " + id + " has no type");
				continue;
			}
			out.add(new Quest(id, s.str("name", id), s.str("description", ""), type, Math.max(1, s.num("goal", 1)),
					Math.max(0, s.num("reward", 0)), period, cat.id));
		}
	}

	/** The categories whose quests count events from this server (null = network events like the login). */
	List<String> categoriesOf(String server) {
		List<String> out = new ArrayList<>();
		for (Category c : categories.values()) {
			if (c.covers(server)) {
				out.add(c.id);
			}
		}
		return out;
	}

	/** True when anything counts on this server. */
	boolean counts(String server) {
		return !categoriesOf(server).isEmpty();
	}

	List<Quest> allQuests() {
		List<Quest> out = new ArrayList<>();
		for (Category c : categories.values()) {
			out.addAll(c.daily);
			out.addAll(c.weekly);
		}
		return out;
	}

	Quest quest(String id) {
		for (Quest q : allQuests()) {
			if (q.id.equals(id)) {
				return q;
			}
		}
		return null;
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

	/** Today's and this week's quests in every category. */
	List<Quest> active(long now) {
		List<Quest> all = new ArrayList<>();
		for (Category c : categories.values()) {
			all.addAll(pick(c.daily, dailyCount, c.id + ":" + dayKey(now)));
			all.addAll(pick(c.weekly, weeklyCount, c.id + ":" + weekKey(now)));
		}
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
		// java.util.Random gives near-identical first shuffles for nearby seeds (and "day 23" vs
		// "day 24" hash to nearby numbers), so mix the key first or the pick would never rotate.
		long z = key.hashCode() * 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		Collections.shuffle(idx, new Random(z ^ (z >>> 31)));
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
		// quests removed from the config (e.g. the old Lobby ones): forget their progress.
		// Never when the config has no quests at all (a broken config shouldn't wipe everyone).
		Set<String> known = new HashSet<>();
		for (Quest q : allQuests()) {
			known.add(q.id);
		}
		if (!known.isEmpty()) {
			changed |= d.progress.keySet().retainAll(known);
			changed |= d.done.retainAll(known);
		}
		String day = dayKey(now);
		if (!day.equals(d.dailyKey)) {
			for (Quest q : allQuests()) {
				if (DAILY.equals(q.period)) {
					d.progress.remove(q.id);
					d.done.remove(q.id);
				}
			}
			d.dailyKey = day;
			changed = true;
		}
		String week = weekKey(now);
		if (!week.equals(d.weeklyKey)) {
			for (Quest q : allQuests()) {
				if (WEEKLY.equals(q.period)) {
					d.progress.remove(q.id);
					d.done.remove(q.id);
				}
			}
			d.weeklyKey = week;
			changed = true;
		}
		return changed;
	}

	/**
	 * Counts {@code amount} of {@code type} (kill, play, win, ...) that
	 * happened on {@code server} (null = a network event such as the login)
	 * toward the player's active quests in every category covering that
	 * server, and pays out any that are now finished (once: a finished quest
	 * never counts again until it resets). Returns the finished quests.
	 */
	List<Quest> add(Store.Data d, String server, String type, long amount, long now) {
		roll(d, now);
		List<Quest> out = new ArrayList<>();
		if (amount <= 0) {
			return out;
		}
		for (Quest q : active(now)) {
			if (!q.type.equals(type) || d.done.contains(q.id) || !categories.get(q.category).covers(server)) {
				continue;
			}
			long p = Math.min(q.goal, d.progress.getOrDefault(q.id, 0L) + amount);
			d.progress.put(q.id, p);
			if (p >= q.goal) {
				d.done.add(q.id);
				d.coins += q.reward;
				out.add(q);
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
		return loginRewards[(nextStreak(d, now) - 1) % loginRewards.length];
	}

	/** The streak day the next login will be (1 when the streak has run out). */
	int nextStreak(Store.Data d, long now) {
		return d.lastLogin == 0 || now - d.lastLogin > streakResetMs ? 1 : d.streak + 1;
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

	/** The categories as the client shows them (one quest tab each). */
	List<Object> categoriesJson() {
		List<Object> out = new ArrayList<>();
		for (Category c : categories.values()) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", c.id);
			m.put("name", c.name);
			m.put("tab", c.name);
			m.put("icon", c.icon);
			if (!c.color.isEmpty()) {
				m.put("color", c.color);
			}
			out.add(m);
		}
		return out;
	}

	/** The quest list as the client shows it: the login reward first (in the login category), then the rest. */
	List<Object> toJson(Store.Data d, long now) {
		roll(d, now);
		List<Object> out = new ArrayList<>();
		if (loginEnabled) {
			boolean claimed = loginClaimed(d, now);
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", "login");
			m.put("category", loginCategory);
			m.put("streak", claimed ? d.streak : nextStreak(d, now) - 1);
			m.put("streakLength", loginRewards.length);
			m.put("name", "Daily login");
			m.put("description", claimed ? "Day " + d.streak + " of your streak. Come back tomorrow!"
					: "Join PlebMc every day for a bigger reward");
			m.put("period", "login");
			m.put("cycle", dayKey(now));
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
			m.put("cycle", DAILY.equals(q.period) ? dayKey(now) : weekKey(now));
			m.put("category", q.category);
			m.put("progress", d.progress.getOrDefault(q.id, 0L));
			m.put("goal", q.goal);
			m.put("reward", q.reward);
			m.put("done", d.done.contains(q.id));
			out.add(m);
		}
		return out;
	}
}
