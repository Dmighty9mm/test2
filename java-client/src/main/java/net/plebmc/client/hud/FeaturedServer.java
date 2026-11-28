package net.plebmc.client.hud;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.ServerData;

/**
 * PlebMc on the multiplayer screen: not an entry of the server list, but a starred card
 * of its own above it. Click it to join. GuiMultiplayer makes room for it, draws it,
 * and passes clicks and ticks on.
 */
public final class FeaturedServer {

	private FeaturedServer() {
	}

	/** The room the card takes above the server list. */
	public static final int HEIGHT = 42;
	private static final int CARD_H = 38;

	private static ServerData data;
	private static float hover;

	private static ServerData data() {
		if (data == null) {
			data = new ServerData(PlebMenu.SERVER_NAME, PlebMenu.SERVER_ADDRESS, false);
			data.setResourceMode(ServerData.ServerResourceMode.PROMPT);
		}
		return data;
	}

	/** True for an address of the PlebMc server (so it isn't also listed as an ordinary server). */
	public static boolean isPlebMc(String address) {
		if (address == null) {
			return false;
		}
		String a = address.toLowerCase();
		return a.contains("play.plebmc.top");
	}

	private static net.minecraft.client.network.OldServerPinger pinger;
	private static volatile boolean pinging;

	/** The screen opened or Refresh was pressed: ask the server again. */
	public static void refresh() {
		final ServerData d = data();
		if (pinging) {
			return;
		}
		pinging = true;
		d.field_78841_f = false;
		d.pingToServer = -2L;
		if (pinger == null) {
			pinger = new net.minecraft.client.network.OldServerPinger();
		}
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					pinger.ping(d);
				} catch (Throwable e) {
					d.pingToServer = -1L;
					d.field_78841_f = true;
				}
				pinging = false;
			}
		}, "PlebMc featured server ping");
		t.setDaemon(true);
		t.start();
	}

	/** Every tick while the multiplayer screen is open. */
	public static void update() {
		try {
			if (pinger != null) {
				pinger.pingPendingNetworks();
			}
		} catch (Throwable t) {
			// the ping failed: the card says Offline
		}
	}

	private static int cardW(int w) {
		return Math.min(305, w - 20);
	}

	private static boolean over(int w, int top, int mx, int my) {
		int cw = cardW(w), x = (w - cw) / 2;
		return mx >= x && mx < x + cw && my >= top && my < top + CARD_H;
	}

	public static void draw(GuiScreen s, int top, int mx, int my) {
		Minecraft mc = Minecraft.getMinecraft();
		FontRenderer font = mc.fontRendererObj;
		ServerData d = data();
		int w = s.width, cw = cardW(w), x = (w - cw) / 2;
		boolean over = over(w, top, mx, my);
		hover += ((over ? 1f : 0f) - hover) * 0.35f;
		int gold = Ui.GOLD;
		GlStateManager.disableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.enableTexture2D();
		Ui.round(x - 1, top - 1, cw + 2, CARD_H + 2, Ui.alpha(gold, Math.round(0x28 + 0x40 * hover)));
		Ui.round(x, top, cw, CARD_H, Ui.mix(0xF0101821, 0xF0182330, hover));
		Ui.outline(x, top, cw, CARD_H, Ui.alpha(gold, Math.round(0x90 + 0x6F * hover)));
		// the server's own icon, or Plebio until it answers
		GlStateManager.color(1f, 1f, 1f, 1f);
		Ui.mascot(x + 5, top + 5, 28, 1f);
		GlStateManager.enableBlend();
		int tx = x + 40;
		Ui.icon("star", tx, top + 4, 1, gold);
		font.drawStringWithShadow(PlebMenu.SERVER_NAME, tx + 12, top + 5, 0xFFFFE07A);
		int nameEnd = tx + 12 + font.getStringWidth(PlebMenu.SERVER_NAME);
		font.drawString("Official server", nameEnd + 6, top + 5, 0xFF8E9BA8);

		String status;
		int statusColor;
		if (!d.field_78841_f || d.pingToServer == -2L) {
			status = "Pinging...";
			statusColor = 0xFF8E9BA8;
		} else if (d.pingToServer < 0L) {
			status = "Offline";
			statusColor = 0xFFFF6B6B;
		} else {
			status = (d.populationInfo == null || d.populationInfo.isEmpty() ? "" : d.populationInfo + "§r  ") + d.pingToServer + "ms";
			statusColor = 0xFF9FE8B0;
		}
		font.drawString(status, x + cw - 6 - font.getStringWidth(status), top + 5, statusColor);

		String line2 = over ? "Click to join" : null;
		List<String> motd = d.serverMOTD == null || d.serverMOTD.isEmpty() ? null
				: font.listFormattedStringToWidth(d.serverMOTD, cw - 46);
		if (motd != null && !motd.isEmpty()) {
			font.drawString(motd.get(0), tx, top + 16, 0xFFB9C4CD);
			if (line2 == null && motd.size() > 1) {
				line2 = motd.get(1);
			}
		} else {
			font.drawString(PlebMenu.SERVER_ADDRESS, tx, top + 16, 0xFF6F7C89);
		}
		if (line2 == null) {
			line2 = motd != null && !motd.isEmpty() ? PlebMenu.SERVER_ADDRESS : "";
		}
		font.drawString(line2, tx, top + 26, over ? Ui.accent() : 0xFF8E9BA8);
		GlStateManager.color(1f, 1f, 1f, 1f);
	}

	/** True if the click was on the card (it joins PlebMc). */
	public static boolean click(GuiScreen s, int top, int mx, int my, int button) {
		if (button != 0 || !over(s.width, top, mx, my)) {
			return false;
		}
		PlebMenu.joinServer(s);
		return true;
	}
}
