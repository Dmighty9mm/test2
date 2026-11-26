package net.plebmc.client.hud;

/** The few GL calls that differ between game versions (1.21.1: the block outline is drawn by PlebVisuals). */
final class GlCompat {
	private GlCompat() {
	}

	static void lineWidth(float w) {
		com.mojang.blaze3d.systems.RenderSystem.lineWidth(w);
	}
}
