package net.plebmc.client.hud;

import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.plebmc.client.text.ModernText;

/**
 * Draws the small capitals newer versions use (ᴛʜɪꜱ ꜱᴛʏʟᴇ) the way newer
 * Minecraft does, from glyphs in the font's own style (see
 * {@link ModernText#glyph}). FontRenderer calls {@link #width} and
 * {@link #draw} for each character before its own lookup.
 */
public final class ModernFont {

	private static final int COLS = 16, CELL = 8;
	private static ResourceLocation texture;
	private static boolean failed;

	private ModernFont() {
	}

	/** The advance of {@code c} in pixels, or -1 when it isn't one of ours. */
	public static int width(char c) {
		int g = ModernText.glyph(c);
		return g < 0 ? -1 : ModernText.glyphWidth(g) + 1;
	}

	/**
	 * Draws {@code c} at (x, y) in the current color; returns its advance, or
	 * -1 when it isn't one of ours (the font renderer then draws it as usual).
	 */
	public static float draw(char c, float x, float y, boolean italic) {
		int g = ModernText.glyph(c);
		if (g < 0 || failed) {
			return -1;
		}
		ResourceLocation tex = texture();
		if (tex == null) {
			return -1;
		}
		Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
		int w = ModernText.glyphWidth(g);
		float u0 = (g % COLS) * CELL, v0 = (g / COLS) * CELL;
		float tw = COLS * CELL, th = rows() * CELL;
		float k = italic ? 1f : 0f;
		WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
		wr.begin(7, DefaultVertexFormats.POSITION_TEX);
		wr.pos(x + k, y, 0).tex(u0 / tw, v0 / th).endVertex();
		wr.pos(x - k, y + 7.99f, 0).tex(u0 / tw, (v0 + 7.99f) / th).endVertex();
		wr.pos(x + w - k, y + 7.99f, 0).tex((u0 + w) / tw, (v0 + 7.99f) / th).endVertex();
		wr.pos(x + w + k, y, 0).tex((u0 + w) / tw, v0 / th).endVertex();
		Tessellator.getInstance().draw();
		return w + 1;
	}

	private static int rows() {
		return (ModernText.glyphCount() + COLS - 1) / COLS;
	}

	private static ResourceLocation texture() {
		if (texture != null) {
			return texture;
		}
		try {
			int tw = COLS * CELL, th = rows() * CELL;
			DynamicTexture t = new DynamicTexture(tw, th);
			int[] px = t.getTextureData();
			for (int g = 0; g < ModernText.glyphCount(); g++) {
				int ox = (g % COLS) * CELL, oy = (g / COLS) * CELL + ModernText.GLYPH_TOP;
				for (int y = 0; y < 5; y++) {
					for (int x = 0; x < ModernText.glyphWidth(g); x++) {
						if (ModernText.glyphPixel(g, x, y)) {
							px[(oy + y) * tw + ox + x] = 0xFFFFFFFF;
						}
					}
				}
			}
			t.updateDynamicTexture();
			texture = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("plebclient_smallcaps", t);
		} catch (Throwable e) {
			failed = true;
		}
		return texture;
	}
}
