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

	private static final java.nio.FloatBuffer MAT = org.lwjgl.BufferUtils.createFloatBuffer(16);

	/** The current modelview and projection matrices (column-major, 16 floats each). */
	static void matrices(float[] modelview, float[] projection) {
		MAT.clear();
		org.lwjgl.opengl.GL11.glGetFloat(2982, MAT);
		MAT.get(modelview, 0, 16);
		MAT.clear();
		org.lwjgl.opengl.GL11.glGetFloat(2983, MAT);
		MAT.get(projection, 0, 16);
		MAT.clear();
	}

	/** The HUD is drawn into a buffer a few times a second (true on the web client), not every frame. */
	static boolean hudIsBuffered() {
		return false;
	}

	/** A file shipped inside the client ("/plebclient/emoji.txt"), or null. */
	static byte[] resource(String path) {
		try (java.io.InputStream in = GlCompat.class.getResourceAsStream(path)) {
			if (in == null) {
				return null;
			}
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) > 0) {
				out.write(buf, 0, n);
			}
			return out.toByteArray();
		} catch (java.io.IOException e) {
			return null;
		}
	}
}
