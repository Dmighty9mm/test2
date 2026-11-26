package net.plebmc.mc.client.gui;

/** 1.8's GuiMainMenu: stands for the 1.21.1 screen of the same job. */
public class GuiMainMenu extends GuiScreen.Vanilla {
	private GuiScreen parent;

	public GuiMainMenu(net.minecraft.client.gui.screens.Screen s) {
		super(s);
	}

	public GuiMainMenu() {
		super(null);
	}

	public GuiMainMenu(GuiScreen parent) {
		super(null);
		this.parent = parent;
	}

	public GuiMainMenu(GuiScreen parent, Object unused) {
		this(parent);
	}

	@Override
	public net.minecraft.client.gui.screens.Screen toScreen() {
		return screen != null ? screen : new net.minecraft.client.gui.screens.TitleScreen();
	}
}
