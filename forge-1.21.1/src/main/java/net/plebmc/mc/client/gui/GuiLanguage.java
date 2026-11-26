package net.plebmc.mc.client.gui;

/** 1.8's GuiLanguage: stands for the 1.21.1 screen of the same job. */
public class GuiLanguage extends GuiScreen.Vanilla {
	private GuiScreen parent;

	public GuiLanguage(net.minecraft.client.gui.screens.Screen s) {
		super(s);
	}

	public GuiLanguage() {
		super(null);
	}

	public GuiLanguage(GuiScreen parent) {
		super(null);
		this.parent = parent;
	}

	public GuiLanguage(GuiScreen parent, Object unused) {
		this(parent);
	}

	@Override
	public net.minecraft.client.gui.screens.Screen toScreen() {
		return screen != null ? screen : VanillaScreens.language(parent);
	}
}
