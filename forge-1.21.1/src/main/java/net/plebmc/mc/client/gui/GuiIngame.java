package net.plebmc.mc.client.gui;

/** 1.8's GuiIngame: the chat box is all PlebClient needs from it. */
public class GuiIngame {
	private final GuiNewChat chat = new GuiNewChat();

	public GuiNewChat getChatGUI() {
		return chat;
	}

	public int getUpdateCounter() {
		return net.minecraft.client.Minecraft.getInstance().gui.getGuiTicks();
	}
}
