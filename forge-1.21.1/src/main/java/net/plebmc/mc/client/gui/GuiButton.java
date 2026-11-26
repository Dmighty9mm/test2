package net.plebmc.mc.client.gui;

import net.plebmc.mc.client.Minecraft;
import net.plebmc.mc.client.audio.PositionedSoundRecord;
import net.plebmc.mc.client.audio.SoundHandler;
import net.plebmc.mc.util.ResourceLocation;

/** 1.8's GuiButton, drawn in the PlebClient style. */
public class GuiButton extends Gui {
	public int width = 200;
	public int height = 20;
	public int xPosition;
	public int yPosition;
	public String displayString;
	public int id;
	public boolean enabled = true;
	public boolean visible = true;
	protected boolean hovered;

	public GuiButton(int id, int x, int y, String text) {
		this(id, x, y, 200, 20, text);
	}

	public GuiButton(int id, int x, int y, int w, int h, String text) {
		this.id = id;
		this.xPosition = x;
		this.yPosition = y;
		this.width = w;
		this.height = h;
		this.displayString = text;
	}

	public void drawButton(Minecraft mc, int mx, int my) {
		if (!visible) {
			return;
		}
		hovered = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
		net.plebmc.client.hud.PlebTheme.drawButton(this, mx, my, hovered, !enabled);
		net.plebmc.client.hud.PlebTheme.drawButtonLabel(this, mc.fontRendererObj, hovered);
	}

	public void mouseReleased(int mx, int my) {
	}

	public boolean mousePressed(Minecraft mc, int mx, int my) {
		return enabled && visible && mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
	}

	public boolean isMouseOver() {
		return hovered;
	}

	public void playPressSound(SoundHandler s) {
		s.playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
	}

	public int getButtonWidth() {
		return width;
	}
}
