package net.plebmc.mc.client.gui;

/** 1.8's GuiVideoSettings: stands for the 1.21.1 screen of the same job. */
public class GuiVideoSettings extends GuiScreen.Vanilla {
	private GuiScreen parent;

	public GuiVideoSettings(net.minecraft.client.gui.screens.Screen s) {
		super(s);
	}

	public GuiVideoSettings() {
		super(null);
	}

	public GuiVideoSettings(GuiScreen parent) {
		super(null);
		this.parent = parent;
	}

	public GuiVideoSettings(GuiScreen parent, Object unused) {
		this(parent);
	}

	@Override
	public net.minecraft.client.gui.screens.Screen toScreen() {
		return screen != null ? screen : VanillaScreens.video(parent);
	}
}
