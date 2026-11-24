package net.plebmc.client.mods;

import java.util.LinkedHashMap;
import java.util.Map;

import net.plebmc.client.apollo.proto.Json;
import net.plebmc.client.apollo.proto.ProtoValues;

/**
 * Client preferences from the Settings screen (not tied to one mod). Saved
 * in the browser under {@link #STORE_KEY}. Every field here is read by the
 * code it describes.
 */
public final class ClientPrefs {

	public static final String STORE_KEY = "prefs";

	private static final ClientPrefs INSTANCE = new ClientPrefs();

	public static ClientPrefs get() {
		return INSTANCE;
	}

	// ---- interface ----
	public static final int[] ACCENTS = { 0xFF3FD8E6, 0xFF4C9BFF, 0xFF4FE39A, 0xFFB085FF, 0xFFF5B942, 0xFFFF6FAE };
	public static final String[] ACCENT_NAMES = { "PlebClient Cyan", "Blue", "Mint", "Violet", "Amber", "Rose" };
	public int accent;
	/** Menus slide and fade in, hover states ease. */
	public boolean animations = true;
	/** Click sounds in PlebClient menus. */
	public boolean uiSounds = true;
	public static final String[] BACKDROPS = { "Dim", "Dark", "Light" };
	/** How much of the game shows behind menus. */
	public int backdrop;

	// ---- notifications ----
	/** Coin, quest and login popups (otherwise only the shop's own messages). */
	public boolean toasts = true;
	public static final int[] TOAST_SECONDS = { 3, 5, 8 };
	public int toastTime = 1;

	// ---- HUD ----
	public boolean hudShadow = true;

	// ---- behavior ----
	public static final String[] OPENS = { "Home", "Mods", "Quests", "Shop" };
	/** What Right Shift opens. */
	public int shiftOpens;

	// ---- developer ----
	/** Shows Apollo debug in the launcher. */
	public boolean developer;
	/** Records the full NBT of items as they change (custom item models, step 1). Off by default. */
	public boolean itemNbtLog;

	public int accentColor() {
		return ACCENTS[clamp(accent, ACCENTS.length)];
	}

	public long toastMs() {
		return TOAST_SECONDS[clamp(toastTime, TOAST_SECONDS.length)] * 1000L;
	}

	/** The backdrop alpha behind menus. */
	public int backdropColor() {
		switch (clamp(backdrop, BACKDROPS.length)) {
		case 1:
			return 0xE0080B10;
		case 2:
			return 0x70080B10;
		default:
			return 0xB0080B10;
		}
	}

	public void reset() {
		ClientPrefs d = new ClientPrefs();
		accent = d.accent;
		animations = d.animations;
		uiSounds = d.uiSounds;
		backdrop = d.backdrop;
		toasts = d.toasts;
		toastTime = d.toastTime;
		hudShadow = d.hudShadow;
		shiftOpens = d.shiftOpens;
		developer = d.developer;
		itemNbtLog = d.itemNbtLog;
	}

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
		accent = idx(m.get("accent"), accent, ACCENTS.length);
		animations = ProtoValues.asBool(m.get("animations"), animations);
		uiSounds = ProtoValues.asBool(m.get("sounds"), uiSounds);
		backdrop = idx(m.get("backdrop"), backdrop, BACKDROPS.length);
		toasts = ProtoValues.asBool(m.get("toasts"), toasts);
		toastTime = idx(m.get("toastTime"), toastTime, TOAST_SECONDS.length);
		hudShadow = ProtoValues.asBool(m.get("hudShadow"), hudShadow);
		shiftOpens = idx(m.get("shiftOpens"), shiftOpens, OPENS.length);
		developer = ProtoValues.asBool(m.get("developer"), developer);
		itemNbtLog = ProtoValues.asBool(m.get("itemNbtLog"), itemNbtLog);
	}

	public String serialize() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("accent", (double) accent);
		m.put("animations", animations);
		m.put("sounds", uiSounds);
		m.put("backdrop", (double) backdrop);
		m.put("toasts", toasts);
		m.put("toastTime", (double) toastTime);
		m.put("hudShadow", hudShadow);
		m.put("shiftOpens", (double) shiftOpens);
		m.put("developer", developer);
		m.put("itemNbtLog", itemNbtLog);
		return Json.write(m);
	}

	public void save(SettingsStore store) {
		if (store != null) {
			store.set(STORE_KEY, serialize());
		}
	}

	private static int idx(Object o, int def, int n) {
		double d = ProtoValues.asNumber(o, def);
		return Double.isNaN(d) ? def : clamp((int) d, n);
	}

	private static int clamp(int i, int n) {
		return i < 0 ? 0 : i >= n ? n - 1 : i;
	}
}
