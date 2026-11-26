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
	public static final String[] ACCENT_NAMES = { "PlebMc Cyan", "Blue", "Mint", "Violet", "Amber", "Rose", "Crimson",
			"Orange", "Lime", "Teal", "Pink", "Snow" };
	public int accent;
	/** Menus slide and fade in, hover states ease. */
	public boolean animations = true;
	/** The PlebClient look on every menu: title screen, buttons, backgrounds and lists. */
	public boolean modernMenus = true;
	/** Click sounds in PlebMc menus. */
	public boolean uiSounds = true;
	public static final String[] BACKDROPS = { "Dim", "Dark", "Light" };
	/** How much of the game shows behind menus. */
	public int backdrop;

	public static final String[] MENU_BACKGROUNDS = { "Blurred wallpaper", "Wallpaper", "Dark", "Glow", "Retro grid" };
	/** What menus show behind their windows when you're not in a world. */
	public int menuBackground;
	public static final String[] WINDOW_OPACITY = { "Solid", "Glass", "Clear" };
	/** How see-through menu windows are. */
	public int windowOpacity;
	public static final String[] HUD_BRACKETS = { "None", "[ ]", "( )", "< >", "| |" };
	/** Brackets around every HUD text line. */
	public int hudBrackets;
	public static final String[] HUD_TEXT_STYLES = { "Normal", "Bold", "Italic", "Bold italic" };
	/** Bold / italic for HUD text. */
	public int hudTextStyle;

	/** Applies the HUD bracket and text-style options to one line of HUD text. */
	public String decorateHud(String s) {
		if (s == null || s.isEmpty()) {
			return s;
		}
		String style = new String[] { "", "\u00a7l", "\u00a7o", "\u00a7l\u00a7o" }[clamp(hudTextStyle, HUD_TEXT_STYLES.length)];
		int b = clamp(hudBrackets, HUD_BRACKETS.length);
		if (b > 0) {
			String br = HUD_BRACKETS[b];
			s = br.charAt(0) + s + style + br.charAt(2);
		}
		return style + s;
	}

	// ---- title screen ----
	public static final String[] SIDEBAR_MODES = { "Opens on hover", "Always open", "Icons only" };
	/** How the title screen's sidebar behaves. */
	public int titleSidebar;
	public static final String[] PANORAMA_DIMS = { "Clear", "Soft", "Dark" };
	/** How much the title screen darkens the panorama. */
	public int titlePanorama = 1;
	public static final String[] BACKGROUNDS = { "PlebMc wallpaper", "Minecraft panorama" };
	/** What the title screen shows behind everything. */
	public int titleBackground;
	/** The little animated mascot in the corner of the title screen. */
	public boolean titleBuddy = true;
	public static final String[] PARTICLES = { "None", "Snow", "Embers", "Fireflies" };
	/** Particles drifting over the title screen. */
	public int titleParticles = 1;
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

	// ---- behavior ----
	public static final String[] OPENS = { "Home", "Mods", "Quests", "Shop" };
	/** What Right Shift opens. */
	public int shiftOpens;

	// ---- text and holograms ----
	/** Draw hex/RGB colors and gradients from newer servers instead of the 16 old colors. */
	public boolean rgbText = true;
	/** Hide "Armor Stand" labels and stray armor stand bodies on holograms. */
	public boolean cleanHolograms = true;

	// ---- video and performance ----
	/** Video Settings opens the PlebClient video screen (pages, sliders, descriptions) instead of Minecraft's list. */
	public boolean videoMenu = true;
	public static final String[] ENTITY_DISTANCES = { "Unlimited", "96 blocks", "64 blocks", "48 blocks", "32 blocks" };
	static final int[] ENTITY_BLOCKS = { 0, 96, 64, 48, 32 };
	/** Mobs, items and other entities past this aren't drawn (players always are). */
	public int entityDistance;
	public static final String[] BLOCK_ENTITY_DISTANCES = { "Unlimited", "48 blocks", "32 blocks", "24 blocks", "16 blocks" };
	static final int[] BLOCK_ENTITY_BLOCKS = { 0, 48, 32, 24, 16 };
	/** Chests, signs, banners and heads past this aren't drawn (beacons always are). */
	public int blockEntityDistance;
	public static final String[] PARTICLE_CAPS = { "4000 (vanilla)", "2000", "1000", "400", "100" };
	static final int[] PARTICLE_COUNTS = { 4000, 2000, 1000, 400, 100 };
	/** Most particles of one kind alive at once; extra ones push out the oldest. */
	public int maxParticles;
	public static final String[] MENU_FPS = { "30 (vanilla)", "60", "Same as in game" };
	/** Frame cap on the title screen and menus outside a world. */
	public int menuFps;
	public static final String[] IDLE_FPS = { "Off", "30", "15", "5" };
	static final int[] IDLE_FPS_VALUES = { 0, 30, 15, 5 };
	/** Frame cap while the game window isn't focused. */
	public int idleFps = 1;

	/** Entity draw distance in blocks (0 = unlimited). */
	public int entityBlocks() {
		return ENTITY_BLOCKS[clamp(entityDistance, ENTITY_BLOCKS.length)];
	}

	/** Block entity draw distance in blocks (0 = unlimited). */
	public int blockEntityBlocks() {
		return BLOCK_ENTITY_BLOCKS[clamp(blockEntityDistance, BLOCK_ENTITY_BLOCKS.length)];
	}

	public int particleCap() {
		return PARTICLE_COUNTS[clamp(maxParticles, PARTICLE_COUNTS.length)];
	}

	/** The frame cap for menus outside a world, given the in-game cap. */
	public int menuFpsCap(int game) {
		switch (clamp(menuFps, MENU_FPS.length)) {
		case 1:
			return Math.min(game, 60);
		case 2:
			return game;
		default:
			return 30;
		}
	}

	/** The frame cap while the window isn't focused (0 = none). */
	public int idleFpsCap() {
		return IDLE_FPS_VALUES[clamp(idleFps, IDLE_FPS_VALUES.length)];
	}

	// ---- secrets ----
	/** The menus cycle through every accent color (a secret). */
	public boolean rainbowAccent;
	/** Ids of the easter eggs found, comma separated. */
	public String secrets = "";

	public boolean hasSecret(String id) {
		return ("," + secrets + ",").contains("," + id + ",");
	}

	/** Records a found easter egg; true the first time. */
	public boolean addSecret(String id) {
		if (id == null || id.isEmpty() || id.contains(",") || hasSecret(id)) {
			return false;
		}
		secrets = secrets.isEmpty() ? id : secrets + "," + id;
		return true;
	}

	public int secretCount() {
		return secrets.isEmpty() ? 0 : secrets.split(",").length;
	}

	// ---- developer ----
	/** Shows Apollo debug in the launcher. */
	public boolean developer;

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
		menuBackground = d.menuBackground;
		windowOpacity = d.windowOpacity;
		hudBrackets = d.hudBrackets;
		hudTextStyle = d.hudTextStyle;
		titleSidebar = d.titleSidebar;
		titlePanorama = d.titlePanorama;
		titleBackground = d.titleBackground;
		titleBuddy = d.titleBuddy;
		titleParticles = d.titleParticles;
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
		videoMenu = d.videoMenu;
		entityDistance = d.entityDistance;
		blockEntityDistance = d.blockEntityDistance;
		maxParticles = d.maxParticles;
		menuFps = d.menuFps;
		idleFps = d.idleFps;
		rainbowAccent = d.rainbowAccent;
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
		menuBackground = idx(m.get("menuBackground"), menuBackground, MENU_BACKGROUNDS.length);
		windowOpacity = idx(m.get("windowOpacity"), windowOpacity, WINDOW_OPACITY.length);
		hudBrackets = idx(m.get("hudBrackets"), hudBrackets, HUD_BRACKETS.length);
		hudTextStyle = idx(m.get("hudTextStyle"), hudTextStyle, HUD_TEXT_STYLES.length);
		titleSidebar = idx(m.get("titleSidebar"), titleSidebar, SIDEBAR_MODES.length);
		titlePanorama = idx(m.get("titlePanorama"), titlePanorama, PANORAMA_DIMS.length);
		titleBackground = idx(m.get("titleBackground"), titleBackground, BACKGROUNDS.length);
		titleBuddy = ProtoValues.asBool(m.get("titleBuddy"), titleBuddy);
		titleParticles = idx(m.get("titleParticles"), titleParticles, PARTICLES.length);
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
		videoMenu = ProtoValues.asBool(m.get("videoMenu"), videoMenu);
		entityDistance = idx(m.get("entityDistance"), entityDistance, ENTITY_DISTANCES.length);
		blockEntityDistance = idx(m.get("blockEntityDistance"), blockEntityDistance, BLOCK_ENTITY_DISTANCES.length);
		maxParticles = idx(m.get("maxParticles"), maxParticles, PARTICLE_CAPS.length);
		menuFps = idx(m.get("menuFps"), menuFps, MENU_FPS.length);
		idleFps = idx(m.get("idleFps"), idleFps, IDLE_FPS.length);
		rainbowAccent = ProtoValues.asBool(m.get("rainbow"), rainbowAccent);
		Object sec = m.get("secrets");
		secrets = sec instanceof String && ((String) sec).matches("[a-z0-9,]{0,200}") ? (String) sec : secrets;
	}

	public String serialize() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("accent", (double) accent);
		m.put("animations", animations);
		m.put("modernMenus", modernMenus);
		m.put("sounds", uiSounds);
		m.put("backdrop", (double) backdrop);
		m.put("menuBackground", (double) menuBackground);
		m.put("windowOpacity", (double) windowOpacity);
		m.put("hudBrackets", (double) hudBrackets);
		m.put("hudTextStyle", (double) hudTextStyle);
		m.put("titleSidebar", (double) titleSidebar);
		m.put("titlePanorama", (double) titlePanorama);
		m.put("titleBackground", (double) titleBackground);
		m.put("titleBuddy", titleBuddy);
		m.put("titleParticles", titleParticles);
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
		m.put("videoMenu", videoMenu);
		m.put("entityDistance", (double) entityDistance);
		m.put("blockEntityDistance", (double) blockEntityDistance);
		m.put("maxParticles", (double) maxParticles);
		m.put("menuFps", (double) menuFps);
		m.put("idleFps", (double) idleFps);
		m.put("rainbow", rainbowAccent);
		m.put("secrets", secrets);
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
