package net.plebmc.mc.client.gui;

/** Picks the 1.8 class a 1.21.1 screen looks like to PlebClient. */
public final class VanillaScreens {
	private VanillaScreens() {
	}

	public static GuiScreen wrap(net.minecraft.client.gui.screens.Screen s) {
		if (s instanceof net.minecraft.client.gui.screens.TitleScreen) {
			return new GuiMainMenu(s);
		}
		if (s instanceof net.minecraft.client.gui.screens.ChatScreen) {
			return new GuiChat(s);
		}
		if (s instanceof net.minecraft.client.gui.screens.options.VideoSettingsScreen) {
			return new GuiVideoSettings(s);
		}
		return new GuiScreen.Vanilla(s);
	}

	private static net.minecraft.client.gui.screens.Screen parent(GuiScreen p) {
		return p == null ? null : p.toScreen();
	}

	static net.minecraft.client.gui.screens.Screen options(GuiScreen p) {
		return new net.minecraft.client.gui.screens.options.OptionsScreen(parent(p), net.minecraft.client.Minecraft.getInstance().options);
	}

	static net.minecraft.client.gui.screens.Screen video(GuiScreen p) {
		return new net.minecraft.client.gui.screens.options.VideoSettingsScreen(parent(p), net.minecraft.client.Minecraft.getInstance(),
				net.minecraft.client.Minecraft.getInstance().options);
	}

	static net.minecraft.client.gui.screens.Screen language(GuiScreen p) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		return new net.minecraft.client.gui.screens.options.LanguageSelectScreen(parent(p), mc.options, mc.getLanguageManager());
	}

	static net.minecraft.client.gui.screens.Screen multiplayer(GuiScreen p) {
		return new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(parent(p));
	}

	static net.minecraft.client.gui.screens.Screen worlds(GuiScreen p) {
		return new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(parent(p));
	}
}
