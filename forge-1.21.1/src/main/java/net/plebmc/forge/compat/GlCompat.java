package net.plebmc.forge.compat;

/** Small GL helpers that Eaglercraft's GlStateManager has (1.21.1 versions). */
public final class GlCompat {
	private GlCompat() {
	}

	public static float[] getClearColor() {
		float[] f = new float[4];
		org.lwjgl.opengl.GL11.glGetFloatv(org.lwjgl.opengl.GL11.GL_COLOR_CLEAR_VALUE, f);
		return f;
	}

	public static boolean isFogEnabled() {
		return false;
	}

	/** Back to the main framebuffer after drawing offscreen. */
	public static void rebindWorldTarget(net.plebmc.mc.client.Minecraft mc) {
		net.minecraft.client.Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
	}

	public static void setRenderOutlines(Object rm, boolean on) {
	}

	/** True while a shader pack (Oculus/Iris) is on: the offscreen effects are skipped then. */
	public static boolean shadersOn() {
		try {
			Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object api = c.getMethod("getInstance").invoke(null);
			return (Boolean) c.getMethod("isShaderPackInUse").invoke(api);
		} catch (Throwable t) {
			return false;
		}
	}
}
