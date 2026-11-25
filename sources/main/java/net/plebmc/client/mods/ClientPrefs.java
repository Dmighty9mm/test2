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
	public static final int[] ACCENTS = { 0xFF3FD8E6, 0xFF4C9BFF, 0xFF4FE39A, 0xFFB085FF, 0xFFF5B942, 0xFFFF6FAE,
			0xFFFF5A5A, 0xFFFF9A3C, 0xFFA6E34F, 0xFF2EC4B6, 0xFFFF7AC6, 0xFFE8EEF2 };
	public static final String[] ACCENT_NAMES = { "PlebClient Cyan", "Blue", "Mint", "Violet", "Amber", "Rose", "Crimson",
			"Orange", "Lime", "Teal", "Pink", "Snow" };
	public int accent;
	/** Menus slide and fade in, hover states ease. */
	public boolean animations = true;
	/** The PlebClient look on every menu: title screen, buttons, backgrounds and lists. */
	public boolean modernMenus = true;
	/** Click sounds in PlebClient menus. */
	public boolean uiSounds = true;
	public static final String[] BACKDROPS = { "Dim", "Dark", "Light" };
	/** How much of the game shows behind menus. */
	public int backdrop;

	// ---- title screen ----
	public static final String[] SIDEBAR_MODES = { "Opens on hover", "Always open", "Icons only" };
	/** How the title screen's sidebar behaves. */
	public int titleSidebar;
	public static final String[] PANORAMA_DIMS = { "Clear", "Soft", "Dark" };
	/** How much the title screen darkens the panorama. */
	public int titlePanorama = 1;
	/** The server's status under the Play button. */
	public boolean titleStatus = true;
	/** The mascot head at the top of the sidebar. */
	public boolean titleMascot = true;
	/** A clock in the corner of the title screen. */
	public boolean titleClock;
	/** The yellow splash text at the bottom of the title screen. */
	public boolean titleSplash;

	// ---- notifications ----
	/** Coin, quest and login popups (otherwise only the shop's own messages). */
	public boolean toasts = true;
	public static final int[] TOAST_SECONDS = { 3, 5, 8 };
	public int toastTime = 1;

	// ---- HUD ----
	public boolean hudShadow = true;

	// ---- custom items ----
	/** Server guns, ammo and medkits from the resource pack, plus totems and netherite. On by default. */
	public boolean customItemModels = true;

	// ---- behavior ----
	public static final String[] OPENS = { "Home", "Mods", "Quests", "Shop" };
	/** What Right Shift opens. */
	public int shiftOpens;

	// ---- text and holograms ----
	/** Draw hex/RGB colors and gradients from newer servers instead of the 16 old colors. */
	public boolean rgbText = true;
	/** Hide "Armor Stand" labels and stray armor stand bodies on holograms. */
	public boolean cleanHolograms = true;

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
		modernMenus = d.modernMenus;
		uiSounds = d.uiSounds;
		backdrop = d.backdrop;
		titleSidebar = d.titleSidebar;
		titlePanorama = d.titlePanorama;
		titleStatus = d.titleStatus;
		titleMascot = d.titleMascot;
		titleClock = d.titleClock;
		titleSplash = d.titleSplash;
		toasts = d.toasts;
		toastTime = d.toastTime;
		hudShadow = d.hudShadow;
		shiftOpens = d.shiftOpens;
		rgbText = d.rgbText;
		cleanHolograms = d.cleanHolograms;
		developer = d.developer;
		itemNbtLog = d.itemNbtLog;
		customItemModels = d.customItemModels;
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
		modernMenus = ProtoValues.asBool(m.get("modernMenus"), modernMenus);
		uiSounds = ProtoValues.asBool(m.get("sounds"), uiSounds);
		backdrop = idx(m.get("backdrop"), backdrop, BACKDROPS.length);
		titleSidebar = idx(m.get("titleSidebar"), titleSidebar, SIDEBAR_MODES.length);
		titlePanorama = idx(m.get("titlePanorama"), titlePanorama, PANORAMA_DIMS.length);
		titleStatus = ProtoValues.asBool(m.get("titleStatus"), titleStatus);
		titleMascot = ProtoValues.asBool(m.get("titleMascot"), titleMascot);
		titleClock = ProtoValues.asBool(m.get("titleClock"), titleClock);
		titleSplash = ProtoValues.asBool(m.get("titleSplash"), titleSplash);
		toasts = ProtoValues.asBool(m.get("toasts"), toasts);
		toastTime = idx(m.get("toastTime"), toastTime, TOAST_SECONDS.length);
		hudShadow = ProtoValues.asBool(m.get("hudShadow"), hudShadow);
		shiftOpens = idx(m.get("shiftOpens"), shiftOpens, OPENS.length);
		rgbText = ProtoValues.asBool(m.get("rgbText"), rgbText);
		cleanHolograms = ProtoValues.asBool(m.get("cleanHolograms"), cleanHolograms);
		developer = ProtoValues.asBool(m.get("developer"), developer);
		itemNbtLog = ProtoValues.asBool(m.get("itemNbtLog"), itemNbtLog);
		customItemModels = ProtoValues.asBool(m.get("customItems"), customItemModels);
	}

	public String serialize() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("accent", (double) accent);
		m.put("animations", animations);
		m.put("modernMenus", modernMenus);
		m.put("sounds", uiSounds);
		m.put("backdrop", (double) backdrop);
		m.put("titleSidebar", (double) titleSidebar);
		m.put("titlePanorama", (double) titlePanorama);
		m.put("titleStatus", titleStatus);
		m.put("titleMascot", titleMascot);
		m.put("titleClock", titleClock);
		m.put("titleSplash", titleSplash);
		m.put("toasts", toasts);
		m.put("toastTime", (double) toastTime);
		m.put("hudShadow", hudShadow);
		m.put("shiftOpens", (double) shiftOpens);
		m.put("rgbText", rgbText);
		m.put("cleanHolograms", cleanHolograms);
		m.put("developer", developer);
		m.put("itemNbtLog", itemNbtLog);
		m.put("customItems", customItemModels);
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
