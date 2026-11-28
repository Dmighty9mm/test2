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

	/** The current modelview and projection matrices (column-major, 16 floats each). */
	static void matrices(float[] modelview, float[] projection) {
		net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager.getFloat(2982, modelview);
		net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager.getFloat(2983, projection);
	}

	/** The HUD is drawn into a buffer a few times a second (true on the web client), not every frame. */
	static boolean hudIsBuffered() {
		return true;
	}

	/** A file shipped inside the client ("/plebclient/emoji.txt"), or null. */
	static byte[] resource(String path) {
		return net.lax1dude.eaglercraft.v1_8.EagRuntime.getResourceBytes(path);
	}
}
