package net.plebmc.shop;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * players.yml: each player's PlebMc Coins, what they own and wear, and their
 * quest progress. Kept in memory, saved when something changes (every 30
 * seconds at most, right away after a purchase) and on shutdown.
 */
final class Store {

	static final class Data {
		String name = "";
		long coins;
		final Set<String> owned = new LinkedHashSet<>();
		final Map<String, String> equipped = new HashMap<>();
		/** Daily login: when the last reward was given (ms) and the streak. */
		long lastLogin;
		int streak;
		/** Minutes online that haven't been paid out as playtime coins yet. */
		int playMinutes;
		/** Which day/week the quest progress belongs to (see Quests.dayKey/weekKey). */
		String dailyKey = "";
		String weeklyKey = "";
		final Map<String, Long> progress = new LinkedHashMap<>();
		final Set<String> done = new LinkedHashSet<>();
	}

	private final File file;
	private final Logger log;
	private final Map<UUID, Data> players = new HashMap<>();
	private boolean dirty;

	Store(File file, Logger log) {
		this.file = file;
		this.log = log;
		load();
	}

	private void load() {
		players.clear();
		if (!file.exists()) {
			return;
		}
		YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
		ConfigurationSection root = y.getConfigurationSection("players");
		if (root == null) {
			return;
		}
		for (String key : root.getKeys(false)) {
			UUID id;
			try {
				id = UUID.fromString(key);
			} catch (IllegalArgumentException e) {
				continue;
			}
			ConfigurationSection s = root.getConfigurationSection(key);
			if (s == null) {
				continue;
			}
			Data d = new Data();
			d.name = s.getString("name", "");
			d.coins = s.getLong("coins", 0);
			d.owned.addAll(s.getStringList("owned"));
			for (String cat : new String[] { Catalog.HAT, Catalog.TRAIL }) {
				String v = s.getString("equipped." + cat, null);
				if (v != null && !v.isEmpty()) {
					d.equipped.put(cat, v);
				}
			}
			d.lastLogin = s.getLong("login.last", 0);
			d.streak = s.getInt("login.streak", 0);
			d.playMinutes = s.getInt("play-minutes", 0);
			d.dailyKey = s.getString("quests.daily", "");
			d.weeklyKey = s.getString("quests.weekly", "");
			ConfigurationSection p = s.getConfigurationSection("quests.progress");
			if (p != null) {
				for (String q : p.getKeys(false)) {
					d.progress.put(q, p.getLong(q, 0));
				}
			}
			d.done.addAll(s.getStringList("quests.done"));
			players.put(id, d);
		}
	}

	Data get(UUID id) {
		return players.computeIfAbsent(id, k -> new Data());
	}

	Data peek(UUID id) {
		return players.get(id);
	}

	void changed() {
		dirty = true;
	}

	void saveIfDirty() {
		if (dirty) {
			save();
		}
	}

	void save() {
		YamlConfiguration y = new YamlConfiguration();
		for (Map.Entry<UUID, Data> e : players.entrySet()) {
			Data d = e.getValue();
			String base = "players." + e.getKey() + ".";
			y.set(base + "name", d.name);
			y.set(base + "coins", d.coins);
			if (!d.owned.isEmpty()) {
				y.set(base + "owned", new ArrayList<>(d.owned));
			}
			for (Map.Entry<String, String> q : d.equipped.entrySet()) {
				y.set(base + "equipped." + q.getKey(), q.getValue());
			}
			if (d.lastLogin != 0) {
				y.set(base + "login.last", d.lastLogin);
				y.set(base + "login.streak", d.streak);
			}
			if (d.playMinutes != 0) {
				y.set(base + "play-minutes", d.playMinutes);
			}
			if (!d.dailyKey.isEmpty()) {
				y.set(base + "quests.daily", d.dailyKey);
			}
			if (!d.weeklyKey.isEmpty()) {
				y.set(base + "quests.weekly", d.weeklyKey);
			}
			for (Map.Entry<String, Long> q : d.progress.entrySet()) {
				y.set(base + "quests.progress." + q.getKey(), q.getValue());
			}
			if (!d.done.isEmpty()) {
				y.set(base + "quests.done", new ArrayList<>(d.done));
			}
		}
		try {
			File parent = file.getParentFile();
			if (parent != null) {
				parent.mkdirs();
			}
			File tmp = new File(file.getPath() + ".tmp");
			y.save(tmp);
			if (!tmp.renameTo(file)) {
				// Windows can't rename over an existing file
				file.delete();
				if (!tmp.renameTo(file)) {
					throw new IOException("could not replace " + file);
				}
			}
			dirty = false;
		} catch (IOException ex) {
			log.severe("Could not save " + file + ": " + ex);
		}
	}

	List<UUID> ids() {
		return new ArrayList<>(players.keySet());
	}
}
