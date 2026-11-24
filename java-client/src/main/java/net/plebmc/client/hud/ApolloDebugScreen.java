package net.plebmc.client.hud;

import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.plebmc.client.apollo.Apollo;
import net.plebmc.client.apollo.ApolloDebugLog;
import net.plebmc.client.apollo.ApolloModule;
import net.plebmc.client.network.PluginChannels;

/**
 * Apollo debug (Developer mode): connection status, every module's enable
 * state and current state, and the most recent messages with how each was
 * handled. "Verbose log" also prints every plugin message to the console.
 */
public final class ApolloDebugScreen extends PlebScreen {

	private int scroll;
	private int wx, wy, ww, wh;

	public ApolloDebugScreen(GuiScreen parent) {
		super(parent);
	}

	@Override
	protected void build() {
		ww = this.width - 16;
		wh = this.height - 16;
		wx = 8;
		wy = 8;
		closeButton(wx + ww - 22, wy + 10);
		int by = wy + wh - 24;
		Button back = button("Back", wx + 8, by, 60, 16, this::back);
		back.icon("back").style(Style.GHOST);
		Button preview = button("Preview features", wx + 74, by, 110, 16, () -> {
			ApolloPreview.run(this.mc);
			this.mc.displayGuiScreen(null);
		});
		preview.style(Style.PRIMARY).icon("eye").tip = "Fills every Apollo module, the shop, quests and account with demo data";
		Button clear = button("Clear preview", wx + 190, by, 90, 16, ApolloPreview::clear);
		clear.icon("reset");
		toggle("Verbose log", null, wx + ww - 128, by - 1, 120, 18, Apollo::isDebug, () -> {
			boolean on = !Apollo.isDebug();
			Apollo.setDebug(on);
			PluginChannels.setDebug(on);
		}).tip = "Print every plugin message to the game log";
	}

	@Override
	protected void scrolled(int mx, int my, int dir) {
		scroll = Math.max(0, Math.min(Apollo.debugLog().entries().size(), scroll + dir * 3));
	}

	@Override
	protected void drawContent(int mx, int my, float pt) {
		Ui.window(wx, wy, ww, wh);
		String head = (Apollo.isServerApollo() ? "Server speaks Apollo" : "No Apollo server") + "  ·  "
				+ (Apollo.isJsonMode() ? Apollo.CHANNEL_JSON : Apollo.CHANNEL_PROTO) + "  ·  handshake "
				+ (Apollo.isHandshakeSent() ? "sent" : "not sent") + "  ·  " + Apollo.receivedCount() + " in, "
				+ Apollo.droppedCount() + " dropped";
		header(wx, wy, ww, "debug", "Apollo debug", head, false);
		int top = wy + 44, bottom = wy + wh - 30;
		int half = (ww - 24) / 2;
		int x1 = wx + 8, x2 = wx + 16 + half;
		Ui.text("Modules", x1, top, Ui.MUTED);
		Ui.text("Messages (newest last, scroll for older)", x2, top, Ui.MUTED);
		int y = top + 12;
		for (ApolloModule m : Apollo.modules()) {
			if (y > bottom - 9) {
				break;
			}
			Ui.round(x1, y + 2, 4, 4, m.isEnabled() ? Ui.GOOD : Ui.FAINT);
			String line = m.displayName() + " (" + m.receivedCount() + "): " + m.describeState();
			Ui.text(Ui.fit(line, half - 8), x1 + 7, y, m.isEnabled() ? Ui.TEXT : Ui.FAINT);
			y += 9;
		}
		List<ApolloDebugLog.Entry> log = Apollo.debugLog().entries();
		int lines = (bottom - top - 12) / 9;
		int start = Math.max(0, log.size() - lines - scroll);
		int ly = top + 12;
		for (int i = start; i < log.size() && ly <= bottom - 9; i++) {
			ApolloDebugLog.Entry e = log.get(i);
			int c;
			switch (e.status) {
			case HANDLED:
				c = Ui.GOOD;
				break;
			case SENT:
				c = Ui.accent();
				break;
			case ERROR:
				c = Ui.BAD;
				break;
			default:
				c = Ui.GOLD;
				break;
			}
			Ui.text(Ui.fit(e.toString(), half), x2, ly, c);
			ly += 9;
		}
		if (log.isEmpty()) {
			Ui.text("Nothing yet", x2, top + 12, Ui.FAINT);
		}
	}
}
