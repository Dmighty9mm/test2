package net.plebmc.client.hud;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.plebmc.client.apollo.Apollo;
import net.plebmc.client.apollo.module.RichPresenceModule;
import net.plebmc.client.mods.ClientInfo;
import net.plebmc.client.mods.Mod;

/**
 * The mod menu (Right Shift in game): one toggle per mod (a mod the
 * server blocks shows "Blocked by server" and cannot be turned on), plus
 * the HUD editor and the Apollo debug screen.
 */
public final class ModMenuScreen extends GuiScreen {

	private static final int ID_EDITOR = 1000;
	private static final int ID_DEBUG = 1001;
	private static final int ID_DONE = 1002;

	private final GuiScreen parent;

	public ModMenuScreen(GuiScreen parent) {
		this.parent = parent;
	}

	@Override
	public void initGui() {
		this.buttonList.clear();
		List<Mod> mods = PlebClient.mods().all();
		int colW = 150;
		int gap = 4;
		int rowH = 20 + gap;
		int rows = (mods.size() + 1) / 2;
		int x0 = this.width / 2 - colW - gap / 2;
		int y0 = Math.max(34, this.height / 2 - (rows * rowH + 2 * rowH) / 2);
		for (int i = 0; i < mods.size(); i++) {
			Mod m = mods.get(i);
			String state = m.isBlockedByServer() ? "Blocked by server" : m.isEnabled() ? "ON" : "OFF";
			GuiButton b = new GuiButton(i, x0 + (i % 2) * (colW + gap), y0 + (i / 2) * rowH, colW, 20,
					m.name() + ": " + state);
			b.enabled = !m.isBlockedByServer();
			this.buttonList.add(b);
		}
		int by = y0 + rows * rowH + 6;
		this.buttonList.add(new GuiButton(ID_EDITOR, x0, by, colW, 20, "Edit HUD layout"));
		this.buttonList.add(new GuiButton(ID_DEBUG, x0 + colW + gap, by, colW, 20, "Apollo debug"));
		this.buttonList.add(new GuiButton(ID_DONE, this.width / 2 - 100, by + rowH, 200, 20, "Done"));
	}

	@Override
	protected void actionPerformed(GuiButton b) {
		List<Mod> mods = PlebClient.mods().all();
		if (b.id >= 0 && b.id < mods.size()) {
			Mod m = mods.get(b.id);
			m.setEnabled(!m.isEnabled());
			PlebClient.saveSettings();
			this.initGui();
		} else if (b.id == ID_EDITOR) {
			this.mc.displayGuiScreen(new HudEditorScreen(this));
		} else if (b.id == ID_DEBUG) {
			this.mc.displayGuiScreen(new ApolloDebugScreen(this));
		} else if (b.id == ID_DONE) {
			close();
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawDefaultBackground();
		String title = ClientInfo.NAME + " Mods";
		this.drawCenteredString(this.fontRendererObj, title, this.width / 2, 10, 0xFFFFFF);
		String status = Apollo.isServerApollo() ? "Server API: connected (" + Apollo.receivedCount() + " messages)"
				: "Server API: not used by this server";
		RichPresenceModule rp = Apollo.module(RichPresenceModule.class);
		if (rp != null && !rp.summary().isEmpty()) {
			status += " - " + rp.summary();
		}
		this.drawCenteredString(this.fontRendererObj, Draw.fit(status, this.width - 8), this.width / 2, 21, 0xAAAAAA);
		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	@Override
	protected void keyTyped(char c, int key) {
		if (key == 1 || key == PlebClient.KEY_RSHIFT) {
			close();
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	private void close() {
		PlebClient.saveSettings();
		this.mc.displayGuiScreen(parent);
		if (parent == null) {
			this.mc.setIngameFocus();
		}
	}
}
