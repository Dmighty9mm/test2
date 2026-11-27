package net.plebmc.client.hud;

import java.util.List;

import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.plebmc.client.apollo.ApolloText;

/**
 * 2D drawing for the HUD, overlays and screens, using only the vanilla
 * 1.8 GUI paths (Gui.drawRect and FontRenderer): no new shaders or GL state.
 */
public final class Draw {

	private Draw() {
	}

	static FontRenderer font() {
		return Minecraft.getMinecraft().fontRendererObj;
	}

	/** Filled rectangle; fractional coordinates are rounded to GUI pixels. */
	/**
	 * Multiplies the alpha of everything drawn through here (0..1). A screen
	 * sets it around its own drawing to fade in or out, then puts it back to 1.
	 */
	public static float fade = 1f;

	private static int faded(int argb) {
		return fade >= 1f ? argb : alpha(argb, fade);
	}

	public static void fill(float x, float y, float w, float h, int argb) {
		argb = faded(argb);
		if (w <= 0 || h <= 0 || (argb >>> 24) == 0) {
			return;
		}
		int x0 = Math.round(x);
		int y0 = Math.round(y);
		Gui.drawRect(x0, y0, Math.max(x0 + 1, Math.round(x + w)), Math.max(y0 + 1, Math.round(y + h)), argb);
	}

	public static int width(String s) {
		return s == null ? 0 : font().getStringWidth(s);
	}

	/**
	 * Draws text; returns the width drawn. Alpha below 5 is skipped because
	 * the 1.8 font renderer treats a near-zero alpha as fully opaque.
	 */
	public static int text(String s, float x, float y, int argb, boolean shadow) {
		if (s == null || s.isEmpty()) {
			return 0;
		}
		argb = faded(argb);
		if ((argb >>> 24) < 5) {
			return width(s);
		}
		GlStateManager.enableBlend();
		font().drawString(s, x, y, argb, shadow);
		return width(s);
	}

	public static List<String> wrap(String s, int maxWidth) {
		return font().listFormattedStringToWidth(s, maxWidth);
	}

	/** Multiplies the alpha of an ARGB color by {@code a} (0..1). */
	public static int alpha(int argb, float a) {
		int al = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
		return (al << 24) | (argb & 0x00FFFFFF);
	}

	public static int width(List<ApolloText.Run> runs) {
		int w = 0;
		for (int i = 0; i < runs.size(); i++) {
			w += width(runs.get(i).text);
		}
		return w;
	}

	/** Draws colored runs at 1x; returns the width drawn. */
	public static int runs(List<ApolloText.Run> runs, float x, float y, float alpha, boolean shadow) {
		float cx = x;
		for (int i = 0; i < runs.size(); i++) {
			ApolloText.Run r = runs.get(i);
			String s = r.bold ? "§l" + r.text : r.text;
			text(s, cx, y, alpha(r.argb, alpha), shadow);
			cx += width(s);
		}
		return Math.round(cx - x);
	}

	/** Runs centered on {@code centerX}, scaled about their top-center. */
	public static void runsCentered(List<ApolloText.Run> runs, float centerX, float y, float scale, float alpha) {
		GlStateManager.pushMatrix();
		GlStateManager.translate(centerX, y, 0f);
		GlStateManager.scale(scale, scale, 1f);
		runs(runs, -width(runs) / 2f, 0f, alpha, true);
		GlStateManager.popMatrix();
	}

	/** Trims a string with "..." to fit {@code maxWidth}. */
	public static String fit(String s, int maxWidth) {
		if (width(s) <= maxWidth) {
			return s;
		}
		return font().trimStringToWidth(s, Math.max(0, maxWidth - width("..."))) + "...";
	}
}
