package net.plebmc.forge.compat;

/**
 * Eaglercraft scales the canvas to trade resolution for FPS. Java Edition
 * draws at the window's size, so the resolution setting has no effect here
 * (FPS Boost still switches the video settings).
 */
public final class PlatformInput {
	private PlatformInput() {
	}

	public static void setRenderScale(double scale, boolean boost) {
	}
}
