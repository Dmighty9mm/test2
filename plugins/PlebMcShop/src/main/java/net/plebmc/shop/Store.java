package net.plebmc.shop;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

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
		Cfg root;
		try {
			root = Cfg.load(file);
		} catch (IOException | RuntimeException e) {
			log.severe("Could not read " + file + ": " + e);
			// keep a copy so a broken file is never overwritten with an empty one
			file.renameTo(new File(file.getPath() + ".broken-" + System.currentTimeMillis()));
			return;
		}
		Cfg all = root.section("players");
		if (all == null) {
			return;
		}
		for (String key : all.keys()) {
			UUID id;
			try {
				id = UUID.fromString(key);
			} catch (IllegalArgumentException e) {
				continue;
			}
			Cfg s = all.section(key);
			if (s == null) {
				continue;
			}
			Data d = new Data();
			d.name = s.str("name", "");
			d.coins = s.num("coins", 0);
			d.owned.addAll(s.list("owned"));
			for (String cat : new String[] { Catalog.HAT, Catalog.TRAIL }) {
				String v = s.str("equipped." + cat, null);
				if (v != null && !v.isEmpty()) {
					d.equipped.put(cat, v);
				}
			}
			d.lastLogin = s.num("login.last", 0);
			d.streak = s.integer("login.streak", 0);
			d.playMinutes = s.integer("play-minutes", 0);
			d.dailyKey = s.str("quests.daily", "");
			d.weeklyKey = s.str("quests.weekly", "");
			Cfg p = s.section("quests.progress");
			if (p != null) {
				for (String q : p.keys()) {
					d.progress.put(q, p.num(q, 0));
				}
			}
			d.done.addAll(s.list("quests.done"));
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

	synchronized void save() {
		Map<String, Object> all = new LinkedHashMap<>();
		for (Map.Entry<UUID, Data> e : players.entrySet()) {
			Data d = e.getValue();
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("name", d.name);
			m.put("coins", d.coins);
			if (!d.owned.isEmpty()) {
				m.put("owned", new ArrayList<>(d.owned));
			}
			if (!d.equipped.isEmpty()) {
				m.put("equipped", new LinkedHashMap<>(d.equipped));
			}
			if (d.lastLogin != 0) {
				Map<String, Object> l = new LinkedHashMap<>();
				l.put("last", d.lastLogin);
				l.put("streak", d.streak);
				m.put("login", l);
			}
			if (d.playMinutes != 0) {
				m.put("play-minutes", d.playMinutes);
			}
			Map<String, Object> q = new LinkedHashMap<>();
			if (!d.dailyKey.isEmpty()) {
				q.put("daily", d.dailyKey);
			}
			if (!d.weeklyKey.isEmpty()) {
				q.put("weekly", d.weeklyKey);
			}
			if (!d.progress.isEmpty()) {
				q.put("progress", new LinkedHashMap<>(d.progress));
			}
			if (!d.done.isEmpty()) {
				q.put("done", new ArrayList<>(d.done));
			}
			if (!q.isEmpty()) {
				m.put("quests", q);
			}
			all.put(e.getKey().toString(), m);
		}
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("players", all);
		DumperOptions opt = new DumperOptions();
		opt.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		try {
			File parent = file.getParentFile();
			if (parent != null) {
				parent.mkdirs();
			}
			File tmp = new File(file.getPath() + ".tmp");
			try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
				new Yaml(opt).dump(root, w);
			}
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
