package net.plebmc.client.mods;

import java.util.LinkedHashMap;
import java.util.Map;

import net.plebmc.client.apollo.proto.Json;
import net.plebmc.client.apollo.proto.ProtoValues;

/**
 * Fps Larp: the secret mod (type "fpslarp" in the mod menu). Changes only the
 * number the FPS counters show, never how fast the game actually runs: the
 * FPS HUD mod and the F3 / debug lines read {@link #shown}. Everything here
 * is local to this browser.
 */
public final class FpsLarp {

	public static final String STORE_KEY = "fpslarp";

	/** The one the game uses. */
	public static final FpsLarp INSTANCE = new FpsLarp();

	public static final double[] MULTIPLIERS = { 1.5, 2, 3, 4, 5, 8, 10, 16, 25, 50, 100 };
	public static final int[] BONUSES = { 0, 30, 60, 144, 240, 500, 1000 };
	/** Fixed number to show instead of multiplying (0 = off). */
	public static final int[] TARGETS = { 0, 60, 144, 240, 360, 500, 999, 1337, 9999 };
	/** Highest number ever shown (0 = no cap). */
	public static final int[] CAPS = { 0, 240, 360, 500, 1000, 9999 };
	/** How much the shown number wobbles around its value, in percent. */
	public static final int[] JITTERS = { 0, 3, 8, 20 };
	public static final String[] JITTER_NAMES = { "Off", "Low", "Medium", "High" };
	/** How often the shown number changes, in ms (vanilla counters change once a second). */
	public static final int[] UPDATES = { 1000, 250 };
	public static final String[] UPDATE_NAMES = { "1s (vanilla)", "4x a second" };
	/** Text styles for the FPS HUD mod; %d is the number. */
	public static final String[] FORMATS = { "%d FPS", "FPS: %d", "%dfps", "%d" };
	/** Color codes for the FPS HUD mod ("rainbow" cycles). */
	public static final String[] COLORS = { "", "§a", "§b", "§6", "§c", "§d", "rainbow" };
	public static final String[] COLOR_NAMES = { "White", "Green", "Aqua", "Gold", "Red", "Pink", "Rainbow" };
	private static final String RAINBOW = "c6eab9d";

	public boolean enabled;
	public int multiplier = 1; // index into MULTIPLIERS: 2x
	public int bonus;
	public int target;
	public int cap;
	public int jitter = 1;
	public int update;
	public int format;
	public int color;
	/** Where the fake number shows. */
	public boolean inHud = true;
	public boolean inDebug = true;

	private long lastBucket = Long.MIN_VALUE;
	private int lastReal = -1;
	private int lastShown;

	/** The number to show for a real frame rate of {@code real} at time {@code nowMs}. */
	public int shown(int real, long nowMs) {
		if (!enabled) {
			return real;
		}
		long bucket = nowMs / UPDATES[clamp(update, UPDATES.length)];
		if (bucket == lastBucket && real == lastReal) {
			return lastShown;
		}
		double v;
		int t = TARGETS[clamp(target, TARGETS.length)];
		if (t > 0) {
			v = t;
		} else {
			v = Math.max(0, real) * MULTIPLIERS[clamp(multiplier, MULTIPLIERS.length)] + BONUSES[clamp(bonus, BONUSES.length)];
		}
		int j = JITTERS[clamp(jitter, JITTERS.length)];
		if (j > 0 && v > 0) {
			v *= 1 + (noise(bucket) * 2 - 1) * j / 100.0;
		}
		int c = CAPS[clamp(cap, CAPS.length)];
		int out = (int) Math.round(v);
		if (c > 0) {
			out = Math.min(out, c - (j > 0 ? (int) (noise(bucket + 7) * c * 0.02) : 0));
		}
		lastBucket = bucket;
		lastReal = real;
		lastShown = Math.max(0, out);
		return lastShown;
	}

	public int forHud(int real, long nowMs) {
		return inHud ? shown(real, nowMs) : real;
	}

	public int forDebug(int real, long nowMs) {
		return inDebug ? shown(real, nowMs) : real;
	}

	/** The FPS HUD text, styled. */
	public String hudText(int real, long nowMs) {
		if (!enabled) {
			return real + " FPS";
		}
		String text = String.format(FORMATS[clamp(format, FORMATS.length)], forHud(real, nowMs));
		String col = COLORS[clamp(color, COLORS.length)];
		if (!"rainbow".equals(col)) {
			return col + text;
		}
		StringBuilder b = new StringBuilder();
		int start = (int) ((nowMs / 120) % RAINBOW.length());
		for (int i = 0; i < text.length(); i++) {
			b.append('§').append(RAINBOW.charAt((start + i) % RAINBOW.length())).append(text.charAt(i));
		}
		return b.toString();
	}

	/** Replaces the leading frame count of a debug line ("14fps | C: ..." or "14 fps (3 chunk updates)..."). */
	public String debugLine(String line, int real, long nowMs) {
		if (!enabled || !inDebug || line == null) {
			return line;
		}
		int i = 0;
		while (i < line.length() && Character.isDigit(line.charAt(i))) {
			i++;
		}
		if (i == 0) {
			return line;
		}
		return shown(real, nowMs) + line.substring(i);
	}

	/** One line summing up the settings, e.g. "2x +60, Low jitter". */
	public String summary() {
		StringBuilder b = new StringBuilder();
		int t = TARGETS[clamp(target, TARGETS.length)];
		if (t > 0) {
			b.append("Locked at ").append(t);
		} else {
			b.append(fmt(MULTIPLIERS[clamp(multiplier, MULTIPLIERS.length)])).append('x');
			if (BONUSES[clamp(bonus, BONUSES.length)] > 0) {
				b.append(" +").append(BONUSES[clamp(bonus, BONUSES.length)]);
			}
		}
		if (CAPS[clamp(cap, CAPS.length)] > 0) {
			b.append(", max ").append(CAPS[clamp(cap, CAPS.length)]);
		}
		b.append(", ").append(JITTER_NAMES[clamp(jitter, JITTERS.length)]).append(" jitter");
		return b.toString();
	}

	public static String fmt(double m) {
		return m == Math.floor(m) ? Long.toString((long) m) : Double.toString(m);
	}

	public void reset() {
		FpsLarp d = new FpsLarp();
		multiplier = d.multiplier;
		bonus = d.bonus;
		target = d.target;
		cap = d.cap;
		jitter = d.jitter;
		update = d.update;
		format = d.format;
		color = d.color;
		inHud = d.inHud;
		inDebug = d.inDebug;
		changed();
	}

	/** Forget the cached number so a settings change shows right away. */
	public void changed() {
		lastBucket = Long.MIN_VALUE;
	}

	// ==================== storage ====================

	public void load(SettingsStore store) {
		String raw = store == null ? null : store.get(STORE_KEY);
		if (raw == null || raw.isEmpty()) {
			return;
		}
		Object root;
		try {
			root = Json.parse(raw);
		} catch (RuntimeException e) {
			return;
		}
		if (!(root instanceof Map)) {
			return;
		}
		Map<?, ?> m = (Map<?, ?>) root;
		enabled = ProtoValues.asBool(m.get("on"), false);
		multiplier = idx(m.get("multiplier"), multiplier, MULTIPLIERS.length);
		bonus = idx(m.get("bonus"), bonus, BONUSES.length);
		target = idx(m.get("target"), target, TARGETS.length);
		cap = idx(m.get("cap"), cap, CAPS.length);
		jitter = idx(m.get("jitter"), jitter, JITTERS.length);
		update = idx(m.get("update"), update, UPDATES.length);
		format = idx(m.get("format"), format, FORMATS.length);
		color = idx(m.get("color"), color, COLORS.length);
		inHud = ProtoValues.asBool(m.get("hud"), true);
		inDebug = ProtoValues.asBool(m.get("debug"), true);
		changed();
	}

	public void save(SettingsStore store) {
		if (store == null) {
			return;
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("on", enabled);
		m.put("multiplier", (double) multiplier);
		m.put("bonus", (double) bonus);
		m.put("target", (double) target);
		m.put("cap", (double) cap);
		m.put("jitter", (double) jitter);
		m.put("update", (double) update);
		m.put("format", (double) format);
		m.put("color", (double) color);
		m.put("hud", inHud);
		m.put("debug", inDebug);
		store.set(STORE_KEY, Json.write(m));
	}

	private static int idx(Object o, int def, int n) {
		double d = ProtoValues.asNumber(o, def);
		return Double.isNaN(d) ? def : clamp((int) d, n);
	}

	private static int clamp(int i, int n) {
		return i < 0 ? 0 : i >= n ? n - 1 : i;
	}

	/** A repeatable number in [0, 1) for a time bucket, so the wobble looks random but tests are stable. */
	private static double noise(long seed) {
		long x = seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
		x ^= x >>> 31;
		x *= 0xBF58476D1CE4E5B9L;
		x ^= x >>> 29;
		return (x >>> 11) / (double) (1L << 53);
	}
}
