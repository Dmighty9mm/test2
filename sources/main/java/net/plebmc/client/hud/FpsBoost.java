package net.plebmc.client.hud;

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

	/** Called every frame from the HUD hook; does work only when the mod's state changes. */
	static void sync(Minecraft mc, boolean want) {
		if (mc.gameSettings == null) {
			return;
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
			saved.put("shadows", gs.field_181151_V);
			saved.put("fxaa", (double) gs.fxaa);
			saved.put("fps", (double) gs.limitFramerate);
			PlebClient.STORE.set(KEY, Json.write(saved));
			reload = gs.fancyGraphics || gs.ambientOcclusion != 0;
			gs.clouds = 0;
			gs.particleSetting = 2;
			gs.fancyGraphics = false;
			gs.ambientOcclusion = 0;
			gs.field_181151_V = false;
			gs.fxaa = 2;
			gs.limitFramerate = 260;
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
			if (!gs.field_181151_V) {
				gs.field_181151_V = bool(m, "shadows", true);
			}
			if (gs.fxaa == 2) {
				gs.fxaa = num(m, "fxaa", gs.fxaa);
			}
			if (gs.limitFramerate == 260) {
				gs.limitFramerate = num(m, "fps", gs.limitFramerate);
			}
		}
		gs.saveOptions();
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
