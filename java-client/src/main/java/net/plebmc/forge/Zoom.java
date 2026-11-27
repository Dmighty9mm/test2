package net.plebmc.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.plebmc.client.hud.PlebClient;

/**
 * The Zoom mod's key (C by default, rebindable under Controls > PlebMc).
 * Eaglercraft has zoom built in; on Java Edition this class keeps the same
 * state the Eaglercraft client keeps (key held, zoomed FOV) and the
 * EntityRenderer mixin uses it.
 */
public final class Zoom {
	private Zoom() {
	}

	static KeyBinding key;
	private static boolean held;
	private static float zoomFov = 12f;

	/** The camera FOV of the last frame (for projecting HUD markers into the world). */
	public static float lastFov;

	public static boolean isZoomKey() {
		return held;
	}

	/** Called every client tick. */
	static void tick(Minecraft mc) {
		boolean down = key != null && key.isKeyDown() && mc.currentScreen == null;
		if (down != held) {
			if (down) {
				zoomFov = PlebClient.zoomStart(12f);
			}
			held = down;
		}
	}

	/** Mouse wheel while zooming; true = used (the hotbar keeps its slot). */
	static boolean scroll(int dwheel) {
		if (!held || dwheel == 0 || !PlebClient.zoomAllowed(true)) {
			return false;
		}
		zoomFov = PlebClient.zoomScroll(zoomFov, Integer.signum(dwheel));
		return true;
	}

	/** The FOV setting to use this frame (the zoomed one while the key is held). */
	public static float fov(float setting) {
		return PlebClient.zoomAllowed(held) ? zoomFov : setting;
	}
}
