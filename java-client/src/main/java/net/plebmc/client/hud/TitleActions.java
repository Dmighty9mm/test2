package net.plebmc.client.hud;

import java.util.Map;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;
import net.plebmc.forge.compat.EagRuntime;

/**
 * The Forge side of the PlebClient title screen: what each sidebar entry
 * opens (Forge's mod list and Quit instead of Edit Profile), the player's
 * skin (from Mojang), and the strings for the bottom line. {@link TitleMenu} is shared with the browser client.
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
		case "vote":
			EagRuntime.openLink(net.plebmc.client.cosmetics.Cosmetics.voteUrl());
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

	static void openLink(String url) {
		EagRuntime.openLink(url);
	}

	private static volatile GameProfile filled;
	private static volatile boolean filling;
	private static boolean slimSkin;

	/** The player's skin from Mojang (looked up once, in the background), or the default one meanwhile. */
	static ResourceLocation skin() {
		Minecraft mc = Minecraft.getMinecraft();
		GameProfile gp = mc.getSession().getProfile();
		if (filled == null && !filling && gp.getId() != null) {
			filling = true;
			Thread th = new Thread(() -> {
				try {
					filled = mc.getSessionService().fillProfileProperties(gp, false);
				} catch (Throwable t) {
					filled = gp;
				}
			}, "PlebClient skin lookup");
			th.setDaemon(true);
			th.start();
		}
		try {
			if (filled != null) {
				Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> m = mc.getSkinManager().loadSkinFromCache(filled);
				MinecraftProfileTexture tex = m.get(MinecraftProfileTexture.Type.SKIN);
				if (tex != null) {
					slimSkin = "slim".equals(tex.getMetadata("model"));
					return mc.getSkinManager().loadSkin(tex, MinecraftProfileTexture.Type.SKIN);
				}
			}
		} catch (Throwable t) {
			// fall back to the default skin
		}
		slimSkin = gp.getId() != null && "slim".equals(DefaultPlayerSkin.getSkinType(gp.getId()));
		return gp.getId() != null ? DefaultPlayerSkin.getDefaultSkin(gp.getId()) : DefaultPlayerSkin.getDefaultSkinLegacy();
	}

	static boolean slim() {
		return slimSkin;
	}
}
