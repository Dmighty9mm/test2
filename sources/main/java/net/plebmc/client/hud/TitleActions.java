package net.plebmc.client.hud;

import net.lax1dude.eaglercraft.v1_8.EagRuntime;
import net.lax1dude.eaglercraft.v1_8.EaglercraftVersion;
import net.lax1dude.eaglercraft.v1_8.profile.EaglerProfile;
import net.lax1dude.eaglercraft.v1_8.profile.GuiScreenEditProfile;
import net.lax1dude.eaglercraft.v1_8.profile.SkinModel;
import net.lax1dude.eaglercraft.v1_8.sp.gui.GuiScreenIntegratedServerStartup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;

/**
 * The browser (Eaglercraft) side of the PlebClient title screen: what each
 * sidebar entry opens, the player's skin, and the strings for the bottom
 * line. The Forge
 * port has its own copy of this class; {@link TitleMenu} is shared.
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
			{ "profile", "Edit Profile", "nav_profile", "Your name and skin" },
			{ "-" },
			{ "vote", "Vote", "nav_vote", "Vote for PlebMc every day: +100 coins" },
			{ "discord", "Discord", "nav_discord", "Join the PlebMc Discord" },
			{ "language", "Language", "nav_language", "Change the game language" } };

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
		case "vote":
			EagRuntime.openLink(net.plebmc.client.cosmetics.Cosmetics.voteUrl());
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

	static void openLink(String url) {
		EagRuntime.openLink(url);
	}

	/** The player's skin (the one picked in Edit Profile). */
	static ResourceLocation skin() {
		return EaglerProfile.getActiveSkinResourceLocation();
	}

	static boolean slim() {
		return EaglerProfile.getActiveSkinModel() == SkinModel.ALEX;
	}
}
