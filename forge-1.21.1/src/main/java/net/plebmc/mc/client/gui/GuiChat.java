package net.plebmc.mc.client.gui;

/** 1.8's GuiChat: stands for the 1.21.1 screen of the same job. */
public class GuiChat extends GuiScreen.Vanilla {
	private GuiScreen parent;

	public GuiChat(net.minecraft.client.gui.screens.Screen s) {
		super(s);
	}

	public GuiChat() {
		super(null);
	}

	public GuiChat(GuiScreen parent) {
		super(null);
		this.parent = parent;
	}

	public GuiChat(GuiScreen parent, Object unused) {
		this(parent);
	}

	@Override
	public net.minecraft.client.gui.screens.Screen toScreen() {
		return screen;
	}
}
