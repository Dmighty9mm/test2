package net.plebmc.mc.client.gui;

import net.plebmc.mc.R;

/** 1.8's Gui helpers. */
public class Gui {
	protected float zLevel;

	public static void drawRect(int left, int top, int right, int bottom, int color) {
		if (left > right) {
			int t = left;
			left = right;
			right = t;
		}
		if (top > bottom) {
			int t = top;
			top = bottom;
			bottom = t;
		}
		if (R.gg != null) {
			com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
			R.gg.fill(left, top, right, bottom, color);
		}
	}

	public void drawGradientRect(int left, int top, int right, int bottom, int startColor, int endColor) {
		if (R.gg != null) {
			R.gg.fillGradient(left, top, right, bottom, startColor, endColor);
		}
	}

	public void drawCenteredString(FontRenderer f, String s, int x, int y, int color) {
		f.drawStringWithShadow(s, x - f.getStringWidth(s) / 2, y, color);
	}

	public void drawString(FontRenderer f, String s, int x, int y, int color) {
		f.drawStringWithShadow(s, x, y, color);
	}

	public void drawTexturedModalRect(int x, int y, int u, int v, int w, int h) {
		drawModalRectWithCustomSizedTexture(x, y, u, v, w, h, 256, 256);
	}

	public static void drawModalRectWithCustomSizedTexture(int x, int y, float u, float v, int w, int h, float tw, float th) {
		drawScaledCustomSizeModalRect(x, y, u, v, w, h, w, h, tw, th);
	}

	public static void drawScaledCustomSizeModalRect(int x, int y, float u, float v, int uw, int vh, int w, int h, float tw,
			float th) {
		net.plebmc.mc.client.renderer.Tessellator t = net.plebmc.mc.client.renderer.Tessellator.getInstance();
		net.plebmc.mc.client.renderer.WorldRenderer wr = t.getWorldRenderer();
		float fu = 1f / tw, fv = 1f / th;
		wr.begin(7, net.plebmc.mc.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX);
		wr.pos(x, y + h, 0).tex(u * fu, (v + vh) * fv).endVertex();
		wr.pos(x + w, y + h, 0).tex((u + uw) * fu, (v + vh) * fv).endVertex();
		wr.pos(x + w, y, 0).tex((u + uw) * fu, v * fv).endVertex();
		wr.pos(x, y, 0).tex(u * fu, v * fv).endVertex();
		t.draw();
	}
}
