package net.plebmc.client.hud;

import net.plebmc.forge.compat.PlatformInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.plebmc.client.apollo.proto.Json;

/**
 * The FPS Boost mod: while it's on, the heavy video settings are switched
 * to their fastest values. The player's own values are saved first (in
 * browser storage, so a reload with the mod still on can put them back)
 * and restored when the mod is turned off. A setting the player changed by
 * hand while the boost was on is left as they set it.
 */
final class FpsBoost {

	private static final String KEY = "fpsboost";

	private FpsBoost() {
	}

	private static Boolean applied;
	private static double userScale = -1;
	private static double appliedScale = -1;
	private static boolean appliedCap;

	/** Resolution steps offered in the mod menu. */
	static final double[] SCALES = { 1.0, 0.85, 0.75, 0.6, 0.5 };
	/** What the boost lowers the resolution to (unless the player picked lower). */
	static final double BOOST_SCALE = 0.75;
	/** The boost's render distance cap, in chunks. */
	static final int BOOST_DISTANCE = 8;

	/** The player's own resolution choice (1.0 = full). */
	static double userScale() {
		if (userScale < 0) {
			userScale = 1.0;
			String raw = PlebClient.STORE.get("renderScale");
			if (raw != null) {
				try {
					userScale = Math.max(0.25, Math.min(1.0, Double.parseDouble(raw)));
				} catch (NumberFormatException e) {
					userScale = 1.0;
				}
			}
		}
		return userScale;
	}

	/** Moves to the next resolution step (wraps around). */
	static void cycleScale() {
		cycleScale(1);
	}

	/** Moves one resolution step forward (+1) or back (-1), wrapping around. */
	static void cycleScale(int dir) {
		double cur = userScale();
		int next = 0;
		for (int i = 0; i < SCALES.length; i++) {
			if (Math.abs(SCALES[i] - cur) < 0.01) {
				next = ((i + dir) % SCALES.length + SCALES.length) % SCALES.length;
				break;
			}
		}
		userScale = SCALES[next];
		PlebClient.STORE.set("renderScale", String.valueOf(userScale));
	}

	/** "75%" (and what the boost makes it, if lower). */
	static String scaleLabel() {
		boolean boost = PlebClient.mods().fpsBoost.isActive();
		int user = (int) Math.round(userScale() * 100);
		int eff = (int) Math.round(effectiveScale(boost) * 100);
		return user + "%" + (eff != user ? " (" + eff + "% now)" : "");
	}

	/** The resolution the game draws at right now. */
	static double effectiveScale(boolean boost) {
		return boost ? Math.min(userScale(), BOOST_SCALE) : userScale();
	}

	/** Called every frame from the HUD hook; does work only when the mod's state changes. */
	static void sync(Minecraft mc, boolean want) {
		if (mc.gameSettings == null) {
			return;
		}
		double scale = effectiveScale(want);
		if (scale != appliedScale || want != appliedCap) {
			appliedScale = scale;
			appliedCap = want;
			try {
				PlatformInput.setRenderScale(scale, want);
			} catch (Throwable t) {
				// a runtime without resolution scaling
			}
		}
		if (applied == null) {
			// first frame: a saved copy means the boost was on when the page closed
			applied = PlebClient.STORE.get(KEY) != null;
		}
		if (want == applied) {
			return;
		}
		applied = want;
		GameSettings gs = mc.gameSettings;
		boolean reload;
		if (want) {
			java.util.Map<String, Object> saved = new java.util.LinkedHashMap<>();
			saved.put("clouds", (double) gs.clouds);
			saved.put("particles", (double) gs.particleSetting);
			saved.put("fancy", gs.fancyGraphics);
			saved.put("ao", (double) gs.ambientOcclusion);
			saved.put("shadows", gs.entityShadows);
			saved.put("fps", (double) gs.limitFramerate);
			saved.put("vsync", gs.enableVsync);
			saved.put("rd", (double) gs.renderDistanceChunks);
			PlebClient.STORE.set(KEY, Json.write(saved));
			reload = gs.fancyGraphics || gs.ambientOcclusion != 0;
			gs.clouds = 0;
			gs.particleSetting = 2;
			gs.fancyGraphics = false;
			gs.ambientOcclusion = 0;
			gs.entityShadows = false;
			gs.limitFramerate = 260;
			// vsync holds the browser to the screen's refresh rate; off, frames are drawn as fast as they can be
			gs.enableVsync = false;
			if (gs.renderDistanceChunks > BOOST_DISTANCE) {
				gs.renderDistanceChunks = BOOST_DISTANCE;
				reload = true;
			}
		} else {
			String raw = PlebClient.STORE.get(KEY);
			PlebClient.STORE.set(KEY, null);
			if (raw == null) {
				return;
			}
			Object o;
			try {
				o = Json.parse(raw);
			} catch (RuntimeException e) {
				return;
			}
			if (!(o instanceof java.util.Map)) {
				return;
			}
			java.util.Map<?, ?> m = (java.util.Map<?, ?>) o;
			reload = false;
			if (gs.clouds == 0) {
				gs.clouds = num(m, "clouds", gs.clouds);
			}
			if (gs.particleSetting == 2) {
				gs.particleSetting = num(m, "particles", gs.particleSetting);
			}
			if (!gs.fancyGraphics && bool(m, "fancy", false)) {
				gs.fancyGraphics = true;
				reload = true;
			}
			if (gs.ambientOcclusion == 0 && num(m, "ao", 0) != 0) {
				gs.ambientOcclusion = num(m, "ao", 0);
				reload = true;
			}
			if (!gs.entityShadows) {
				gs.entityShadows = bool(m, "shadows", true);
			}
			if (gs.limitFramerate == 260) {
				gs.limitFramerate = num(m, "fps", gs.limitFramerate);
			}
			if (!gs.enableVsync) {
				gs.enableVsync = bool(m, "vsync", gs.enableVsync);
			}
			if (gs.renderDistanceChunks == BOOST_DISTANCE && num(m, "rd", BOOST_DISTANCE) > BOOST_DISTANCE) {
				gs.renderDistanceChunks = num(m, "rd", BOOST_DISTANCE);
				reload = true;
			}
		}
		gs.saveOptions();
		try {
			org.lwjgl.opengl.Display.setVSyncEnabled(gs.enableVsync);
		} catch (Throwable t) {
			// display not ready
		}
		if (reload && mc.renderGlobal != null && mc.theWorld != null) {
			mc.renderGlobal.loadRenderers();
		}
	}

	private static int num(java.util.Map<?, ?> m, String k, int def) {
		Object v = m.get(k);
		return v instanceof Number ? ((Number) v).intValue() : def;
	}

	private static boolean bool(java.util.Map<?, ?> m, String k, boolean def) {
		Object v = m.get(k);
		return v instanceof Boolean ? (Boolean) v : def;
	}
}
