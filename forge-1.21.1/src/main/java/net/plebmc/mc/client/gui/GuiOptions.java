package net.plebmc.mc.client.gui;

/** 1.8's GuiOptions: stands for the 1.21.1 screen of the same job. */
public class GuiOptions extends GuiScreen.Vanilla {
	private GuiScreen parent;

	public GuiOptions(net.minecraft.client.gui.screens.Screen s) {
		super(s);
	}

	public GuiOptions() {
		super(null);
	}

	public GuiOptions(GuiScreen parent) {
		super(null);
		this.parent = parent;
	}

	public GuiOptions(GuiScreen parent, Object unused) {
		this(parent);
	}

	@Override
	public net.minecraft.client.gui.screens.Screen toScreen() {
		return screen != null ? screen : VanillaScreens.options(parent);
	}
}
