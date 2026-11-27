package net.plebmc.mc.client.renderer.entity;

import net.plebmc.mc.R;
import net.plebmc.mc.item.ItemStack;

/** 1.8's RenderItem (the GUI item calls) over GuiGraphics. */
public class RenderItem {
	public static final RenderItem INSTANCE = new RenderItem();
	public float zLevel;

	public void renderItemAndEffectIntoGUI(ItemStack s, int x, int y) {
		if (s != null && R.gg != null) {
			R.gg.renderItem(s.real, x, y);
		}
	}

	public void renderItemIntoGUI(ItemStack s, int x, int y) {
		renderItemAndEffectIntoGUI(s, x, y);
	}

	public void renderItemOverlays(net.plebmc.mc.client.gui.FontRenderer f, ItemStack s, int x, int y) {
		if (s != null && R.gg != null) {
			R.gg.renderItemDecorations(net.minecraft.client.Minecraft.getInstance().font, s.real, x, y);
		}
	}

	public void renderItemOverlayIntoGUI(net.plebmc.mc.client.gui.FontRenderer f, ItemStack s, int x, int y, String text) {
		if (s != null && R.gg != null) {
			R.gg.renderItemDecorations(net.minecraft.client.Minecraft.getInstance().font, s.real, x, y, text);
		}
	}
}
