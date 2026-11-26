package net.plebmc.mc.client.renderer;

/** 1.8's OpenGlHelper: 1.21.1 always has VBOs and framebuffers. */
public class OpenGlHelper {
	public static boolean vboSupported = true;
	public static boolean framebufferSupported = true;
	public static int defaultTexUnit = 0x84C0, lightmapTexUnit = 0x84C2;

	public static boolean isFramebufferEnabled() {
		return true;
	}
}
