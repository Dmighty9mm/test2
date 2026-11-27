package net.plebmc.forge;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.plebmc.client.hud.PlebClient;

/**
 * The Zoom mod's key (C by default, rebindable under Controls > PlebMc),
 * keeping the same state as the Eaglercraft client's built-in zoom.
 */
public final class Zoom {
	private Zoom() {
	}

	static final KeyMapping KEY = new KeyMapping("Zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "PlebMc");
	private static boolean held;
	private static float zoomFov = 12f;

	/** The camera FOV of the last frame (for projecting HUD markers into the world). */
	public static float lastFov;

	public static boolean isZoomKey() {
		return held;
	}

	static void tick(Minecraft mc) {
		boolean down = KEY.isDown() && mc.screen == null;
		if (down != held) {
			if (down) {
				zoomFov = PlebClient.zoomStart(12f);
			}
			held = down;
		}
	}

	/** Mouse wheel while zooming; true = used (the hotbar keeps its slot). */
	static boolean scroll(double delta) {
		if (!held || delta == 0 || !PlebClient.zoomAllowed(true)) {
			return false;
		}
		zoomFov = PlebClient.zoomScroll(zoomFov, delta > 0 ? 1 : -1);
		return true;
	}

	/** The FOV to use this frame. */
	static double fov(double fov) {
		double out = PlebClient.zoomAllowed(held) ? zoomFov : fov;
		lastFov = (float) out;
		return out;
	}
}
