package net.plebmc.client.hud;

import net.lax1dude.eaglercraft.v1_8.EagRuntime;
import net.lax1dude.eaglercraft.v1_8.EaglercraftVersion;
import net.lax1dude.eaglercraft.v1_8.internal.IServerQuery;
import net.lax1dude.eaglercraft.v1_8.internal.QueryResponse;
import net.lax1dude.eaglercraft.v1_8.profile.EaglerProfile;
import net.lax1dude.eaglercraft.v1_8.profile.GuiScreenEditProfile;
import net.lax1dude.eaglercraft.v1_8.socket.AddressResolver;
import net.lax1dude.eaglercraft.v1_8.socket.ServerQueryDispatch;
import net.lax1dude.eaglercraft.v1_8.sp.gui.GuiScreenIntegratedServerStartup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.ServerData;

/**
 * The browser (Eaglercraft) side of the PlebClient title screen: what each
 * sidebar entry opens, the PlebMc server's live status (the same MOTD
 * query the server list uses), and the strings for the info bar. The Forge
 * port has its own copy of this class; {@link TitleMenu} is shared.
 */
final class TitleActions {

	private TitleActions() {
	}

	/** The sidebar: id, label, icon, hint. "-" starts the bottom group. */
	static final String[][] ITEMS = {
			{ "home", "Home", "home", "Mods, shop, quests, HUD and settings" },
			{ "play", "Play PlebMc", "play", "Join play.plebmc.top" },
			{ "single", "Singleplayer", "world", "Your worlds" },
			{ "multi", "Multiplayer", "server", "Servers and LAN worlds" },
			{ "wardrobe", "Wardrobe", "wardrobe", "Hats and cloaks everyone on PlebMc sees" },
			{ "options", "Options", "settings", "Video, controls and sound" },
			{ "profile", "Edit Profile", "account", "Your name and skin" },
			{ "-" },
			{ "discord", "Discord", "discord", "Join the PlebMc Discord" },
			{ "language", "Language", "general", "Change the game language" } };

	static void run(GuiScreen menu, String id) {
		Minecraft mc = Minecraft.getMinecraft();
		switch (id) {
		case "home":
			mc.displayGuiScreen(new LauncherScreen(menu));
			break;
		case "play":
			PlebMenu.joinServer(menu);
			break;
		case "single":
			mc.displayGuiScreen(new GuiScreenIntegratedServerStartup(menu));
			break;
		case "multi":
			mc.displayGuiScreen(new GuiMultiplayer(menu));
			break;
		case "wardrobe":
			mc.displayGuiScreen(new ShopScreen(menu));
			break;
		case "options":
			mc.displayGuiScreen(new GuiOptions(menu, mc.gameSettings));
			break;
		case "profile":
			mc.displayGuiScreen(new GuiScreenEditProfile(menu));
			break;
		case "discord":
			EagRuntime.openLink(PlebMenu.DISCORD);
			break;
		case "language":
			mc.displayGuiScreen(new GuiLanguage(menu, mc.gameSettings, mc.getLanguageManager()));
			break;
		default:
			break;
		}
	}

	static String username() {
		return EaglerProfile.getName();
	}

	static String gameVersion() {
		return EaglercraftVersion.mainMenuStringA;
	}

	static String platform() {
		return EaglercraftVersion.mainMenuStringB;
	}

	static String address() {
		return "play.plebmc.top";
	}

	static boolean hasCredits() {
		return true;
	}

	static void credits() {
		String s = EagRuntime.getResourceString("/assets/eagler/CREDITS.txt");
		if (s != null) {
			EagRuntime.openCreditsPopup(s);
		}
	}

	// ==================== server status ====================

	/** 0 = checking, 1 = online, 2 = offline. */
	static int state;
	static String motd = "";
	static String players = "";
	static long ping = -1;

	private static ServerData data;
	private static IServerQuery query;
	private static long sentAt;
	private static long lastCheck;

	/** Called every frame while the title screen is open: asks every 30 seconds, reads answers. */
	static void pollStatus() {
		long now = System.currentTimeMillis();
		if (query == null && (lastCheck == 0 || now - lastCheck > 30_000L)) {
			lastCheck = now;
			try {
				data = new ServerData(PlebMenu.SERVER_NAME, PlebMenu.SERVER_ADDRESS, false);
				sentAt = now;
				query = ServerQueryDispatch.sendServerQuery(AddressResolver.resolveURI(PlebMenu.SERVER_ADDRESS), "MOTD");
				if (query == null && state != 1) {
					state = 2;
				}
			} catch (Throwable t) {
				query = null;
				if (state != 1) {
					state = 2;
				}
			}
			return;
		}
		if (query == null) {
			return;
		}
		try {
			if (query.responsesAvailable() > 0) {
				QueryResponse pkt = null;
				while (query.responsesAvailable() > 0) {
					pkt = query.getResponse();
				}
				if (pkt != null && pkt.responseType.equalsIgnoreCase("MOTD") && pkt.isResponseJSON()) {
					data.setMOTDFromQuery(pkt);
					motd = data.serverMOTD == null ? "" : data.serverMOTD;
					players = data.populationInfo == null ? "" : data.populationInfo;
					ping = Math.max(0, pkt.clientTime - sentAt);
					state = 1;
				}
				close();
			} else if (query.isClosed() || now - sentAt > 10_000L) {
				if (state != 1 || now - sentAt > 10_000L) {
					state = 2;
				}
				close();
			}
		} catch (Throwable t) {
			state = 2;
			close();
		}
	}

	private static void close() {
		try {
			if (query != null && query.isOpen()) {
				query.close();
			}
		} catch (Throwable t) {
			// ignore
		}
		query = null;
	}

	/** Title screen closed: stop asking. */
	static void stopStatus() {
		close();
		lastCheck = 0;
	}
}
