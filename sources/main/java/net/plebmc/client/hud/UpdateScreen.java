package net.plebmc.client.hud;

import net.lax1dude.eaglercraft.v1_8.EagRuntime;
import net.minecraft.client.gui.GuiScreen;
import net.plebmc.client.cosmetics.UpdateCheck;
import net.plebmc.client.mods.ClientInfo;

/**
 * "A new PlebClient is out": shown on the title screen when the panel says this
 * client is outdated. "Later" closes it for this session; when the version is
 * below the minimum the owner set, there's no "Later" and the client can't be played.
 */
public final class UpdateScreen extends PlebScreen {

	private int wx, wy, ww, wh;

	public UpdateScreen(GuiScreen parent) {
		super(parent);
	}

	@Override
	protected void build() {
		ww = Math.min(this.width - 16, 300);
		wh = Math.min(this.height - 16, 150);
		wx = (this.width - ww) / 2;
		wy = (this.height - wh) / 2;
		int x = wx + 14, w = ww - 28;
		int by = wy + wh - 26;
		Button go = button("Update now", x + w - 120, by, 120, 18, () -> EagRuntime.openLink(UpdateCheck.url));
		go.style(Style.PRIMARY).icon("link").tip = UpdateCheck.url;
		if (!UpdateCheck.required) {
			button("Later", x, by, 80, 18, this::later).icon("clock");
		}
	}

	private void later() {
		if (!UpdateCheck.required) {
			UpdateCheck.dismissed = true;
			this.mc.displayGuiScreen(parent);
		}
	}

	@Override
	protected void key(char c, int key) {
		if (key == 1) {
			later();
		}
	}

	@Override
	protected void keyTyped(char c, int key) {
		key(c, key);
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	@Override
	protected void drawContent(int mx, int my, float pt) {
		Ui.window(wx, wy, ww, wh);
		boolean req = UpdateCheck.required;
		header(wx, wy, ww, req ? "lock" : "bell", req ? "Update required" : "Update available",
				ClientInfo.NAME + " " + UpdateCheck.latest + " is out", false);
		int x = wx + 14, w = ww - 28;
		int y = wy + 44;
		Ui.text("You have " + ClientInfo.NAME + " " + ClientInfo.VERSION + ".", x, y, Ui.MUTED);
		y += 14;
		String m = UpdateCheck.message;
		if (m == null || m.isEmpty()) {
			m = req ? "This version is too old to play on PlebMc. Get the new one to keep playing."
					: "Get the new version for the latest fixes and features.";
		}
		for (String line : Ui.wrap(m, w)) {
			if (y > wy + wh - 40) {
				break;
			}
			Ui.text(line, x, y, Ui.TEXT);
			y += 11;
		}
	}
}
