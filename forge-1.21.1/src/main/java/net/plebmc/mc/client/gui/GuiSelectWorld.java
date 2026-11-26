package net.plebmc.mc.client.gui;

/** 1.8's GuiSelectWorld: stands for the 1.21.1 screen of the same job. */
public class GuiSelectWorld extends GuiScreen.Vanilla {
	private GuiScreen parent;

	public GuiSelectWorld(net.minecraft.client.gui.screens.Screen s) {
		super(s);
	}

	public GuiSelectWorld() {
		super(null);
	}

	public GuiSelectWorld(GuiScreen parent) {
		super(null);
		this.parent = parent;
	}

	public GuiSelectWorld(GuiScreen parent, Object unused) {
		this(parent);
	}

	@Override
	public net.minecraft.client.gui.screens.Screen toScreen() {
		return screen != null ? screen : VanillaScreens.worlds(parent);
	}
}
