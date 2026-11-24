package net.plebmc.shop;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * players.yml: what each player owns and wears (and their coins in internal
 * mode). Kept in memory, saved when something changes (at most once a
 * second, see {@link #saveIfDirty()}) and on shutdown.
 */
final class Store implements Coins.Internal {

	static final class Data {
		String name = "";
		final Set<String> owned = new LinkedHashSet<>();
		final Map<String, String> equipped = new HashMap<>();
		long coins;
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
			d.owned.addAll(s.getStringList("owned"));
			d.coins = s.getLong("coins", 0);
			for (String cat : new String[] { Catalog.HAT, Catalog.TRAIL }) {
				String v = s.getString("equipped." + cat, null);
				if (v != null && !v.isEmpty()) {
					d.equipped.put(cat, v);
				}
			}
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
			if (d.owned.isEmpty() && d.equipped.isEmpty() && d.coins == 0) {
				continue;
			}
			String base = "players." + e.getKey() + ".";
			y.set(base + "name", d.name);
			y.set(base + "owned", new ArrayList<>(d.owned));
			for (Map.Entry<String, String> q : d.equipped.entrySet()) {
				y.set(base + "equipped." + q.getKey(), q.getValue());
			}
			if (d.coins != 0) {
				y.set(base + "coins", d.coins);
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

	@Override
	public long coins(UUID id) {
		Data d = players.get(id);
		return d == null ? 0 : d.coins;
	}

	@Override
	public void setCoins(UUID id, long coins) {
		get(id).coins = Math.max(0, coins);
		changed();
	}
}
