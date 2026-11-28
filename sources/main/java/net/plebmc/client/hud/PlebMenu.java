package net.plebmc.client.hud;

import java.util.List;

import net.lax1dude.eaglercraft.v1_8.EagRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;

/**
 * PlebMc additions to the title screen (hooked from GuiMainMenu): a "Join
 * PlebMc" button that connects straight to the server, and a Discord
 * button. The server is also pinned to the top of the server list (see
 * TeaVMClientConfigAdapter).
 */
public final class PlebMenu {

	public static final String SERVER_NAME = "PlebMc";
	public static final String SERVER_ADDRESS = "wss://eagler.plebmc.top";
	public static final String DISCORD = "https://discord.gg/bR89zXFH9h";

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
		mc.displayGuiScreen(new GuiConnecting(from, mc, new ServerData(SERVER_NAME, SERVER_ADDRESS, false)));
	}
}
