package net.plebmc.client.hud;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.resources.PlayerSkin;
import net.plebmc.forge.compat.EagRuntime;
import net.plebmc.mc.client.Minecraft;
import net.plebmc.mc.client.gui.GuiLanguage;
import net.plebmc.mc.client.gui.GuiMultiplayer;
import net.plebmc.mc.client.gui.GuiOptions;
import net.plebmc.mc.client.gui.GuiScreen;
import net.plebmc.mc.client.gui.GuiSelectWorld;
import net.plebmc.mc.util.ResourceLocation;

/**
 * The Forge 1.21.1 side of the PlebClient title screen: what each sidebar
 * entry opens (Forge's mod list and Quit instead of Edit Profile), the
 * player's skin, and the strings for the bottom line.
 */
final class TitleActions {

	private TitleActions() {
	}

	/** The sidebar: id, label, icon, hint. "-" starts the bottom group. */
	static final String[][] ITEMS = {
			{ "play", "Play PlebMc", "nav_play", "Join play.plebmc.top" },
			{ "single", "Singleplayer", "nav_single", "Your worlds" },
			{ "multi", "Multiplayer", "nav_multi", "Servers and LAN worlds" },
			{ "shop", "Shop", "nav_shop", "Buy hats, cloaks and wings" },
			{ "locker", "Locker", "nav_locker", "Everything you own: pick what you wear" },
			{ "mods", "Mods", "nav_mods", "Turn mods on and off" },
			{ "quests", "Quests", "nav_quests", "Daily and weekly quests" },
			{ "customize", "Customize", "nav_customize", "Colors and the title screen" },
			{ "options", "Options", "nav_options", "Video, controls and sound" },
			{ "forgemods", "Forge Mods", "nav_forge", "The Forge mod list" },
			{ "-" },
			{ "vote", "Vote", "nav_vote", "Vote for PlebMc every day: +100 coins" },
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
		case "shop":
			mc.displayGuiScreen(new ShopScreen(menu));
			break;
		case "locker":
			mc.displayGuiScreen(new ShopScreen(menu, true));
			break;
		case "forgemods":
			mc.displayGuiScreen(new GuiScreen.Vanilla(new net.minecraftforge.client.gui.ModListScreen(menu.toScreen())));
			break;
		case "options":
			mc.displayGuiScreen(new GuiOptions(menu, mc.gameSettings));
			break;
		case "discord":
			EagRuntime.openLink(PlebMenu.DISCORD);
			break;
		case "vote":
			EagRuntime.openLink(net.plebmc.client.cosmetics.Cosmetics.voteUrl());
			break;
		case "language":
			mc.displayGuiScreen(new GuiLanguage(menu));
			break;
		case "quit":
			net.minecraft.client.Minecraft.getInstance().stop();
			break;
		default:
			break;
		}
	}

	static String username() {
		return net.minecraft.client.Minecraft.getInstance().getUser().getName();
	}

	static String gameVersion() {
		return "Minecraft 1.21.1";
	}

	static String platform() {
		try {
			return "Forge " + net.minecraftforge.versions.forge.ForgeVersion.getVersion();
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

	static void openLink(String url) {
		EagRuntime.openLink(url);
	}

	private static boolean slimSkin;

	/** The player's skin (looked up by the game in the background), or the default one meanwhile. */
	static ResourceLocation skin() {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		GameProfile gp = mc.getGameProfile();
		PlayerSkin s = mc.getSkinManager().getInsecureSkin(gp);
		slimSkin = s.model() == PlayerSkin.Model.SLIM;
		return new ResourceLocation(s.texture());
	}

	static boolean slim() {
		return slimSkin;
	}
}
