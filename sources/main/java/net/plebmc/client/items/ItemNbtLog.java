package net.plebmc.client.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Step 1 of custom item models: a record of the item data that actually
 * reaches this 1.8 client after the server's Via plugins have translated
 * it. Off unless "Log item NBT" is on (Settings &gt; Developer). Pure Java so it
 * can be unit tested; the game side ({@code hud/ItemNbtWatch}) feeds it.
 * <p>
 * Each entry keeps the whole NBT, plus "hints": keys that could identify a
 * custom item (CustomModelData anywhere, Via backup tags, plugin data, the
 * original item id).
 */
public final class ItemNbtLog {

	/** Entries kept (oldest dropped first). */
	public static final int MAX_ENTRIES = 400;
	/** Longest NBT text kept per entry. */
	public static final int MAX_NBT_CHARS = 32768;

	public static final class Entry {
		public final long time;
		/** Where the item was seen, e.g. "held", "hotbar 3", "Steve held". */
		public final String where;
		/** Registry name, e.g. "minecraft:diamond_hoe". */
		public final String item;
		public final int numericId;
		public final int damage;
		public final int count;
		/** Custom display name (with § codes) or null. */
		public final String name;
		public final List<String> lore;
		/** The whole NBT as text, or null when the item has none. */
		public final String nbt;
		public final List<String> hints;

		public Entry(long time, String where, String item, int numericId, int damage, int count, String name,
				List<String> lore, String nbt, List<String> hints) {
			this.time = time;
			this.where = where;
			this.item = item;
			this.numericId = numericId;
			this.damage = damage;
			this.count = count;
			this.name = name;
			this.lore = lore == null ? Collections.<String>emptyList() : lore;
			this.nbt = nbt == null ? null
					: nbt.length() > MAX_NBT_CHARS ? nbt.substring(0, MAX_NBT_CHARS) + "...(cut)" : nbt;
			this.hints = hints == null ? Collections.<String>emptyList() : hints;
		}

		/** One line for the log screen. */
		public String summary() {
			StringBuilder sb = new StringBuilder();
			sb.append(where).append(": ").append(item).append(" #").append(numericId);
			if (damage != 0) {
				sb.append(" dmg ").append(damage);
			}
			if (count != 1) {
				sb.append(" x").append(count);
			}
			if (name != null) {
				sb.append(" \"").append(name).append("§r\"");
			}
			return sb.toString();
		}
	}

	private final List<Entry> entries = new ArrayList<>();
	private int total;

	public synchronized void add(Entry e) {
		if (e == null) {
			return;
		}
		entries.add(e);
		total++;
		if (entries.size() > MAX_ENTRIES) {
			entries.remove(0);
		}
	}

	public synchronized List<Entry> entries() {
		return new ArrayList<>(entries);
	}

	public synchronized int size() {
		return entries.size();
	}

	/** Entries logged since the last clear, including ones dropped for space. */
	public synchronized int total() {
		return total;
	}

	public synchronized void clear() {
		entries.clear();
		total = 0;
	}

	/**
	 * What a single NBT key/value says about a custom item, or null when it says
	 * nothing useful. {@code path} is the dotted path of the key ("tag" is the
	 * item's root), {@code value} the value as NBT text.
	 */
	public static String hintFor(String path, String key, String value) {
		if (key == null) {
			return null;
		}
		String k = key.toLowerCase(Locale.ROOT);
		if (k.equals("custommodeldata")) {
			return "CustomModelData " + value + " at " + path;
		}
		if (k.startsWith("vb|") || k.startsWith("vv|") || k.contains("viabackwards") || k.contains("viaversion")
				|| k.contains("viarewind") || k.startsWith("vr|")) {
			return "Via backup tag " + path;
		}
		if (k.equals("publicbukkitvalues")) {
			return "plugin data (PublicBukkitValues) at " + path;
		}
		if (k.contains("qualityarmory") || k.startsWith("qa_") || k.startsWith("qa.") || k.startsWith("qa-")
				|| k.equals("qa")) {
			return "QualityArmory key " + path + " = " + clip(value);
		}
		if ((k.equals("id") || k.endsWith("id")) && value != null && value.contains(":") && path.contains("|")) {
			return "original id " + clip(value) + " at " + path;
		}
		if (k.equals("damage") && path.contains("|")) {
			return "original damage " + value + " at " + path;
		}
		if (k.equals("unbreakable")) {
			return "Unbreakable " + value;
		}
		return null;
	}

	/** Everything, as text for Copy / Save. */
	public synchronized String export(String server, String clientName) {
		StringBuilder sb = new StringBuilder();
		sb.append(clientName).append(" item NBT log\n");
		sb.append("Server: ").append(server == null ? "(none)" : server).append("\n");
		sb.append("Entries: ").append(entries.size());
		if (total > entries.size()) {
			sb.append(" (oldest ").append(total - entries.size()).append(" dropped)");
		}
		sb.append("\n\n");
		for (Entry e : entries) {
			sb.append('[').append(clock(e.time)).append("] ").append(strip(e.summary())).append('\n');
			if (e.name != null) {
				sb.append("  name: ").append(e.name).append('\n');
			}
			for (String l : e.lore) {
				sb.append("  lore: ").append(l).append('\n');
			}
			for (String h : e.hints) {
				sb.append("  hint: ").append(h).append('\n');
			}
			sb.append("  nbt: ").append(e.nbt == null ? "(none)" : e.nbt).append("\n\n");
		}
		return sb.toString();
	}

	static String clock(long ms) {
		long s = (ms / 1000) % 86400;
		return two(s / 3600) + ":" + two((s / 60) % 60) + ":" + two(s % 60) + " UTC";
	}

	private static String two(long v) {
		return v < 10 ? "0" + v : Long.toString(v);
	}

	private static String clip(String s) {
		if (s == null) {
			return "null";
		}
		return s.length() > 80 ? s.substring(0, 80) + "..." : s;
	}

	/** Removes § color codes. */
	public static String strip(String s) {
		if (s == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '§' && i + 1 < s.length()) {
				i++;
				continue;
			}
			sb.append(c);
		}
		return sb.toString();
	}
}
