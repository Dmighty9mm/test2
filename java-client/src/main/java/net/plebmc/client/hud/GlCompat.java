package net.plebmc.client.hud;

/** The few GL calls that differ between the web and Forge clients. */
final class GlCompat {
	private GlCompat() {
	}

	static void lineWidth(float w) {
		org.lwjgl.opengl.GL11.glLineWidth(w);
	}

	/** Vanilla's block selection box lines. */
	static void outline(net.minecraft.util.AxisAlignedBB b) {
		net.minecraft.client.renderer.RenderGlobal.drawSelectionBoundingBox(b);
	}
}
