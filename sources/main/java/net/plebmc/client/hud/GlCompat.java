package net.plebmc.client.hud;

/** The few GL calls that differ between the web and Forge clients. */
final class GlCompat {
	private GlCompat() {
	}

	static void lineWidth(float w) {
		net.lax1dude.eaglercraft.v1_8.opengl.EaglercraftGPU.glLineWidth(w);
	}

	/** Vanilla's block selection box lines. */
	static void outline(net.minecraft.util.AxisAlignedBB b) {
		net.minecraft.client.renderer.RenderGlobal.func_181561_a(b);
	}
}
