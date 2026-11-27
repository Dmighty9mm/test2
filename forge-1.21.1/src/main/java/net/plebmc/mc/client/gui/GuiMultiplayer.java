package net.plebmc.mc.client.gui;

/** 1.8's GuiMultiplayer: stands for the 1.21.1 screen of the same job. */
public class GuiMultiplayer extends GuiScreen.Vanilla {
	private GuiScreen parent;

	public GuiMultiplayer(net.minecraft.client.gui.screens.Screen s) {
		super(s);
	}

	public GuiMultiplayer() {
		super(null);
	}

	public GuiMultiplayer(GuiScreen parent) {
		super(null);
		this.parent = parent;
	}

	public GuiMultiplayer(GuiScreen parent, Object unused) {
		this(parent);
	}

	@Override
	public net.minecraft.client.gui.screens.Screen toScreen() {
		return screen != null ? screen : VanillaScreens.multiplayer(parent);
	}
}
