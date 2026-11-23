package net.plebmc.client.hud;

import java.util.List;

import net.lax1dude.eaglercraft.v1_8.Mouse;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.plebmc.client.apollo.Apollo;
import net.plebmc.client.apollo.ApolloDebugLog;
import net.plebmc.client.apollo.ApolloModule;
import net.plebmc.client.network.PluginChannels;

/**
 * Apollo debug screen: connection status, every module's enable state and
 * current state, and the most recent messages with how each was handled.
 * "Verbose log" also prints every plugin message to the browser console.
 */
public final class ApolloDebugScreen extends GuiScreen {

	private final GuiScreen parent;
	private int scroll;

	public ApolloDebugScreen(GuiScreen parent) {
		this.parent = parent;
	}

	@Override
	public void initGui() {
		this.buttonList.clear();
		this.buttonList.add(new GuiButton(1, this.width - 154, this.height - 24, 150, 20,
				"Verbose log: " + (Apollo.isDebug() ? "ON" : "OFF")));
		this.buttonList.add(new GuiButton(0, 4, this.height - 24, 100, 20, "Back"));
	}

	@Override
	protected void actionPerformed(GuiButton b) {
		if (b.id == 0) {
			this.mc.displayGuiScreen(parent);
		} else if (b.id == 1) {
			boolean on = !Apollo.isDebug();
			Apollo.setDebug(on);
			PluginChannels.setDebug(on);
			this.initGui();
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		Draw.fill(0, 0, this.width, this.height, 0xC0000000);
		int y = 4;
		String head = "Apollo: " + (Apollo.isServerApollo() ? "server speaks Apollo" : "no Apollo server") + " | channel "
				+ (Apollo.isJsonMode() ? Apollo.CHANNEL_JSON : Apollo.CHANNEL_PROTO) + " | handshake "
				+ (Apollo.isHandshakeSent() ? "sent" : "not sent") + " | received " + Apollo.receivedCount()
				+ ", dropped " + Apollo.droppedCount();
		Draw.text(Draw.fit(head, this.width - 8), 4, y, 0xFFFFFF55, true);
		y += 12;
		int half = this.width / 2 - 6;
		int my = y;
		for (ApolloModule m : Apollo.modules()) {
			String line = (m.isEnabled() ? "+ " : "- ") + m.displayName() + " (" + m.receivedCount() + "): "
					+ m.describeState();
			Draw.text(Draw.fit(line, half), 4, my, m.isEnabled() ? 0xFFDDDDDD : 0xFF888888, false);
			my += 9;
			if (my > this.height - 32) {
				break;
			}
		}
		List<ApolloDebugLog.Entry> log = Apollo.debugLog().entries();
		int ly = y;
		int lines = (this.height - 32 - y) / 9;
		int start = Math.max(0, log.size() - lines - scroll);
		for (int i = start; i < log.size() && ly < this.height - 32; i++) {
			ApolloDebugLog.Entry e = log.get(i);
			int c;
			switch (e.status) {
			case HANDLED:
				c = 0xFF55FF55;
				break;
			case SENT:
				c = 0xFF55FFFF;
				break;
			case ERROR:
				c = 0xFFFF5555;
				break;
			default:
				c = 0xFFFFAA00;
				break;
			}
			Draw.text(Draw.fit(e.toString(), half), this.width / 2f + 2, ly, c, false);
			ly += 9;
		}
		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	@Override
	public void handleMouseInput() {
		super.handleMouseInput();
		int d = Mouse.getEventDWheel();
		if (d != 0) {
			scroll = Math.max(0, Math.min(Apollo.debugLog().entries().size(), scroll + (d > 0 ? 3 : -3)));
		}
	}

	@Override
	protected void keyTyped(char c, int key) {
		if (key == 1) {
			this.mc.displayGuiScreen(parent);
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
