package net.plebmc.forge.compat;

import java.lang.reflect.Method;
import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;

/** Small GL helpers that Eaglercraft's GlStateManager has and Java Edition's doesn't. */
public final class GlCompat {
	private GlCompat() {
	}

	private static final FloatBuffer BUF = BufferUtils.createFloatBuffer(16);

	public static float[] getClearColor() {
		BUF.clear();
		GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, BUF);
		return new float[] { BUF.get(0), BUF.get(1), BUF.get(2), BUF.get(3) };
	}

	public static boolean isFogEnabled() {
		return GL11.glIsEnabled(GL11.GL_FOG);
	}

	/** Back to the main framebuffer after drawing offscreen. */
	public static void rebindWorldTarget(Minecraft mc) {
		if (mc.getFramebuffer() != null) {
			mc.getFramebuffer().bindFramebuffer(false);
		}
	}

	public static void setRenderOutlines(RenderManager rm, boolean on) {
		rm.setRenderOutlines(on);
	}

	private static Method optifineShaders;
	private static boolean optifineChecked;

	/** True while OptiFine shaders are on (the offscreen effects are skipped then). */
	public static boolean shadersOn() {
		if (!optifineChecked) {
			optifineChecked = true;
			try {
				Class<?> c = Class.forName("Config");
				optifineShaders = c.getMethod("isShaders");
			} catch (Throwable t) {
				optifineShaders = null;
			}
		}
		if (optifineShaders == null) {
			return false;
		}
		try {
			return (Boolean) optifineShaders.invoke(null);
		} catch (Throwable t) {
			return false;
		}
	}
}
