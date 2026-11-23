package net.plebmc.client.hud;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.plebmc.client.mods.HudBox;
import net.plebmc.client.mods.Mod;
import net.plebmc.client.mods.ModRegistry;

/**
 * Drag HUD elements to place them. Positions are stored as fractions of
 * the screen, so a layout survives window and GUI-scale changes, and are
 * saved when a drag ends and when the editor closes.
 */
public final class HudEditorScreen extends GuiScreen {

	private final GuiScreen parent;
	private Mod dragging;
	private int grabDX;
	private int grabDY;

	public HudEditorScreen(GuiScreen parent) {
		this.parent = parent;
	}

	@Override
	public void initGui() {
		this.buttonList.clear();
		this.buttonList.add(new GuiButton(0, this.width / 2 - 50, this.height - 28, 100, 20, "Done"));
	}

	@Override
	protected void actionPerformed(GuiButton b) {
		if (b.id == 0) {
			close();
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		Draw.fill(0, 0, this.width, this.height, 0x40000000);
		if (dragging != null) {
			dragging.setPosition((mouseX - grabDX) / (float) this.width, (mouseY - grabDY) / (float) this.height);
		}
		for (Mod m : PlebClient.mods().all()) {
			if (!m.hasHud() || !m.isEnabled()) {
				continue;
			}
			HudBox box = m.preview(PlebClient.view(), PlebClient.MEASURE);
			int[] r = PlebClient.drawMod(m, box, this.width, this.height, true);
			if (r != null && m.isBlockedByServer()) {
				Draw.fill(r[0], r[1], r[2], r[3], 0x80FF0000);
			}
		}
		this.drawCenteredString(this.fontRendererObj, "Drag elements to move them - toggle mods in the mod menu",
				this.width / 2, this.height - 42, 0xFFFFFF);
		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		super.mouseClicked(mouseX, mouseY, button);
		if (button != 0) {
			return;
		}
		// topmost (last drawn) element under the cursor
		Mod hit = null;
		int[] hitRect = null;
		for (Mod m : PlebClient.mods().all()) {
			if (!m.hasHud() || !m.isEnabled()) {
				continue;
			}
			HudBox box = m.preview(PlebClient.view(), PlebClient.MEASURE);
			if (box == null || box.width <= 0) {
				continue;
			}
			int[] p = ModRegistry.place(m, box.width, box.height, this.width, this.height);
			if (mouseX >= p[0] && mouseX < p[0] + box.width && mouseY >= p[1] && mouseY < p[1] + box.height) {
				hit = m;
				hitRect = p;
			}
		}
		if (hit != null) {
			dragging = hit;
			grabDX = mouseX - hitRect[0];
			grabDY = mouseY - hitRect[1];
		}
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int button) {
		if (dragging != null) {
			dragging = null;
			PlebClient.saveSettings();
		}
		super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	protected void keyTyped(char c, int key) {
		if (key == 1) {
			close();
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	private void close() {
		dragging = null;
		PlebClient.saveSettings();
		this.mc.displayGuiScreen(parent);
	}
}
