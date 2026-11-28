package net.plebmc.client.mods;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.plebmc.client.apollo.proto.Json;

/**
 * The player's own waypoints (the Waypoints mod), kept per server (or
 * singleplayer world) and dimension under {@link #STORE_KEY}.
 */
public final class OwnWaypoints {

	public static final String STORE_KEY = "waypoints";
	public static final int MAX = 200;
	public static final int[] COLORS = { 0xFF4FE3F0, 0xFF6EE78A, 0xFFF5C542, 0xFFFF6B6B, 0xFFFF8AD8, 0xFFB085FF,
			0xFFFF9A3C, 0xFFFFFFFF };

	public static final class Entry {
		public String name;
		public int x, y, z;
		public int color;
		/** Where it was made: the server address, or "world:" + the singleplayer world's name. */
		public String place;
		public int dimension;
		public boolean hidden;

		public int argb() {
			return COLORS[Math.max(0, Math.min(COLORS.length - 1, color))];
		}
	}

	private static final List<Entry> ALL = new ArrayList<>();

	private OwnWaypoints() {
	}

	public static List<Entry> all() {
		return ALL;
	}

	/** The waypoints of one place and dimension, in the order they were made. */
	public static List<Entry> in(String place, int dimension) {
		List<Entry> out = new ArrayList<>();
		for (Entry e : ALL) {
			if (e.dimension == dimension && e.place != null && e.place.equals(place)) {
				out.add(e);
			}
		}
		return out;
	}

	public static Entry add(String name, int x, int y, int z, int color, String place, int dimension) {
		if (ALL.size() >= MAX || place == null) {
			return null;
		}
		Entry e = new Entry();
		e.name = clean(name);
		e.x = x;
		e.y = y;
		e.z = z;
		e.color = Math.max(0, Math.min(COLORS.length - 1, color));
		e.place = place;
		e.dimension = dimension;
		ALL.add(e);
		return e;
	}

	public static void remove(Entry e) {
		ALL.remove(e);
	}

	static String clean(String name) {
		String n = name == null ? "" : name.replace('\u00a7', ' ').replace('\n', ' ').trim();
		if (n.isEmpty()) {
			n = "Waypoint";
		}
		return n.length() > 24 ? n.substring(0, 24) : n;
	}

	public static String serialize() {
		List<Object> list = new ArrayList<>();
		for (Entry e : ALL) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("name", e.name);
			m.put("x", (long) e.x);
			m.put("y", (long) e.y);
			m.put("z", (long) e.z);
			m.put("color", (long) e.color);
			m.put("place", e.place);
			m.put("dim", (long) e.dimension);
			m.put("hidden", e.hidden);
			list.add(m);
		}
		return Json.write(list);
	}

	public static void save(SettingsStore store) {
		if (store != null) {
			store.set(STORE_KEY, serialize());
		}
	}

	/** Loads the saved list; bad entries are skipped. */
	public static void load(SettingsStore store) {
		ALL.clear();
		String raw = store == null ? null : store.get(STORE_KEY);
		if (raw == null || raw.isEmpty()) {
			return;
		}
		try {
			Object root = Json.parse(raw);
			if (!(root instanceof List)) {
				return;
			}
			for (Object o : (List<?>) root) {
				if (!(o instanceof Map) || ALL.size() >= MAX) {
					continue;
				}
				Map<?, ?> m = (Map<?, ?>) o;
				if (!(m.get("place") instanceof String)) {
					continue;
				}
				Entry e = add(m.get("name") instanceof String ? (String) m.get("name") : null, num(m.get("x")),
						num(m.get("y")), num(m.get("z")), num(m.get("color")), (String) m.get("place"), num(m.get("dim")));
				if (e != null) {
					e.hidden = Boolean.TRUE.equals(m.get("hidden"));
				}
			}
		} catch (RuntimeException e) {
			ALL.clear();
		}
	}

	private static int num(Object o) {
		return o instanceof Number ? ((Number) o).intValue() : 0;
	}
}
