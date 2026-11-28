package net.plebmc.client.hud;

import java.util.List;

import net.plebmc.forge.compat.EagRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;

/**
 * PlebMc additions to the title screen (hooked from GuiMainMenu): a "Join
 * PlebMc" button that connects straight to the server, and a Discord
 * button. The server is also pinned to the top of the server list (see
 * net.plebmc.forge.ServerPin).
 */
public final class PlebMenu {

	public static final String SERVER_NAME = "PlebMc";
	/** Java Edition address of the PlebMc network. */
	public static final String SERVER_ADDRESS = "play.plebmc.top";
	public static final String DISCORD = "https://discord.plebmc.top";

	public static final int ID_JOIN = 0x504C01;
	private static final int ID_DISCORD = 0x504C02;

	private PlebMenu() {
	}

	/** Adds the row above Singleplayer. {@code top} is the Singleplayer button's y. */
	public static void initMainMenu(GuiScreen screen, List<GuiButton> buttons, int top) {
		if (net.plebmc.client.cosmetics.UpdateCheck.required) {
			// too old to play: only the update screen
			Minecraft.getMinecraft().displayGuiScreen(new UpdateScreen(screen));
			return;
		}
		// the title screen only opens after the saved profile (name, skin, cape) was read
		PlebClient.profileLoaded();
		if (!net.plebmc.client.cosmetics.ClientSession.usable()) {
			// not signed in and not a guest yet: the start screen comes first
			Minecraft.getMinecraft().displayGuiScreen(new LoginGateScreen(screen));
			return;
		}
		net.plebmc.client.cosmetics.ClientSession.ensureName();
		if (net.plebmc.client.cosmetics.UpdateCheck.shouldShow()) {
			Minecraft.getMinecraft().displayGuiScreen(new UpdateScreen(screen));
			return;
		}
		int y = top - 24;
		int x = screen.width / 2 - 100;
		buttons.add(new GuiButton(ID_JOIN, x, y, 148, 20, "§a§lJoin PlebMc"));
		buttons.add(new GuiButton(ID_DISCORD, x + 152, y, 48, 20, "Discord"));
	}

	/** True when the button was one of ours (and has been handled). */
	public static boolean mainMenuAction(GuiScreen screen, GuiButton b) {
		if (!net.plebmc.client.cosmetics.ClientSession.usable()) {
			Minecraft.getMinecraft().displayGuiScreen(new LoginGateScreen(screen));
			return true;
		}
		if (b.id == ID_JOIN) {
			joinServer(screen);
			return true;
		}
		if (b.id == ID_DISCORD) {
			EagRuntime.openLink(DISCORD);
			return true;
		}
		return false;
	}

	public static void joinServer(GuiScreen from) {
		Minecraft mc = Minecraft.getMinecraft();
		ServerData server = new ServerData(SERVER_NAME, SERVER_ADDRESS, false);
		// PlebMc asks before it loads its resource pack
		server.setResourceMode(ServerData.ServerResourceMode.PROMPT);
		mc.displayGuiScreen(new GuiConnecting(from, mc, server));
	}

	/**
	 * The pause menu (GuiScreen.drawScreen for GuiIngameMenu): the client's name
	 * and the Pleb head between the "Game menu" title and the first button.
	 */
	public static void pauseBrand(int width, int height) {
		try {
			int top = 30, bottom = height / 4 + 8;
			if (bottom - top < 18) {
				return; // a very small window: no room
			}
			String name = net.plebmc.client.mods.ClientInfo.NAME;
			float scale = bottom - top >= 30 ? 1.5f : 1f;
			int logo = Math.round(12 * scale);
			int tw = Math.round(Ui.width(name) * scale);
			int w = logo + 5 + tw;
			int x = (width - w) / 2;
			int y = top + (bottom - top - logo) / 2;
			Ui.mascot(x, y, logo, 1f);
			Ui.big(name, x + logo + 5, y + Math.round((logo - 8 * scale) / 2f) + 1, scale, 0xFFFFFFFF, true);
		} catch (Throwable t) {
			// the menu works without it
		}
	}
}
