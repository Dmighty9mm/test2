package net.plebmc.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.OldServerPinger;
import net.minecraft.util.EnumChatFormatting;
import net.plebmc.forge.compat.EagRuntime;

/**
 * The Forge side of the PlebClient title screen: what each sidebar entry
 * opens (Forge's mod list and Quit instead of Edit Profile), the PlebMc
 * server's live status (the vanilla server list ping), and the strings for
 * the info bar. {@link TitleMenu} is shared with the browser client.
 */
final class TitleActions {

	private TitleActions() {
	}

	/** The sidebar: id, label, icon, hint. "-" starts the bottom group. */
	static final String[][] ITEMS = {
			{ "play", "Play PlebMc", "nav_play", "Join play.plebmc.top" },
			{ "single", "Singleplayer", "nav_single", "Your worlds" },
			{ "multi", "Multiplayer", "nav_multi", "Servers and LAN worlds" },
			{ "wardrobe", "Wardrobe", "nav_wardrobe", "Hats and cloaks everyone on PlebMc sees" },
			{ "mods", "Mods", "nav_mods", "Turn mods on and off" },
			{ "quests", "Quests", "nav_quests", "Daily and weekly quests" },
			{ "customize", "Customize", "nav_customize", "Colors and the title screen" },
			{ "options", "Options", "nav_options", "Video, controls and sound" },
			{ "forgemods", "Forge Mods", "nav_forge", "The Forge mod list" },
			{ "-" },
			{ "discord", "Discord", "nav_discord", "Join the PlebMc Discord" },
			{ "quit", "Quit", "nav_quit", "Close the game" } };

	static void run(GuiScreen menu, String id) {
		Minecraft mc = Minecraft.getMinecraft();
		switch (id) {
		case "mods":
			mc.displayGuiScreen(new ModsScreen(menu));
			break;
		case "quests":
			mc.displayGuiScreen(new QuestsScreen(menu));
			break;
		case "customize":
			mc.displayGuiScreen(new SettingsScreen(menu, SettingsScreen.TITLE_SCREEN));
			break;
		case "play":
			PlebMenu.joinServer(menu);
			break;
		case "single":
			mc.displayGuiScreen(new GuiSelectWorld(menu));
			break;
		case "multi":
			mc.displayGuiScreen(new GuiMultiplayer(menu));
			break;
		case "wardrobe":
			mc.displayGuiScreen(new ShopScreen(menu));
			break;
		case "forgemods":
			try {
				mc.displayGuiScreen((GuiScreen) Class.forName("net.minecraftforge.fml.client.GuiModList")
						.getConstructor(GuiScreen.class).newInstance(menu));
			} catch (Throwable t) {
				// no Forge mod list
			}
			break;
		case "options":
			mc.displayGuiScreen(new GuiOptions(menu, mc.gameSettings));
			break;
		case "discord":
			EagRuntime.openLink(PlebMenu.DISCORD);
			break;
		case "language":
			mc.displayGuiScreen(new GuiLanguage(menu, mc.gameSettings, mc.getLanguageManager()));
			break;
		case "quit":
			mc.shutdown();
			break;
		default:
			break;
		}
	}

	static String username() {
		return Minecraft.getMinecraft().getSession().getUsername();
	}

	static String gameVersion() {
		return "Minecraft 1.8.9";
	}

	static String platform() {
		try {
			return "Forge " + net.minecraftforge.common.ForgeVersion.getVersion();
		} catch (Throwable t) {
			return "Forge";
		}
	}

	static String address() {
		return PlebMenu.SERVER_ADDRESS;
	}

	static boolean hasCredits() {
		return false;
	}

	static void credits() {
	}

	// ==================== server status ====================

	/** 0 = checking, 1 = online, 2 = offline. */
	static volatile int state;
	static String motd = "";
	static String players = "";
	static long ping = -1;

	private static OldServerPinger pinger;
	private static ServerData data;
	private static volatile boolean asked;
	private static long lastCheck;
	private static long sentAt;

	/** Called every frame while the title screen is open: pings every 30 seconds, reads the answer. */
	static void pollStatus() {
		long now = System.currentTimeMillis();
		if (pinger == null) {
			pinger = new OldServerPinger();
		}
		if (!asked && (lastCheck == 0 || now - lastCheck > 30_000L)) {
			lastCheck = now;
			sentAt = now;
			asked = true;
			final ServerData d = new ServerData(PlebMenu.SERVER_NAME, PlebMenu.SERVER_ADDRESS, false);
			data = d;
			Thread th = new Thread(() -> {
				try {
					pinger.ping(d);
				} catch (Throwable t) {
					d.pingToServer = -1L;
					d.serverMOTD = "";
					d.field_78841_f = true;
					if (state != 1) {
						state = 2;
					}
					asked = false;
				}
			}, "PlebClient title ping");
			th.setDaemon(true);
			th.start();
			return;
		}
		try {
			pinger.pingPendingNetworks();
		} catch (Throwable t) {
			// keep going
		}
		ServerData d = data;
		if (asked && d != null) {
			if (d.pingToServer >= 0 && d.populationInfo != null && !d.populationInfo.isEmpty()) {
				motd = d.serverMOTD == null ? "" : d.serverMOTD;
				players = EnumChatFormatting.getTextWithoutFormattingCodes(d.populationInfo);
				ping = d.pingToServer;
				state = 1;
				asked = false;
			} else if (now - sentAt > 10_000L) {
				state = 2;
				asked = false;
			}
		}
	}

	/** Title screen closed: stop pinging. */
	static void stopStatus() {
		if (pinger != null) {
			try {
				pinger.clearPendingNetworks();
			} catch (Throwable t) {
				// ignore
			}
		}
		asked = false;
		lastCheck = 0;
	}
}
