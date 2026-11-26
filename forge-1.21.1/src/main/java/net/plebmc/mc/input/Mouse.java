package net.plebmc.mc.input;

/** LWJGL 2's Mouse (the bits PlebClient reads), fed by the hooks on 1.21.1. */
public final class Mouse {
	private Mouse() {
	}

	public static int eventDWheel;
	public static int eventButton = -1;
	public static boolean eventButtonState;

	public static int getEventDWheel() {
		return eventDWheel;
	}

	public static int getDWheel() {
		return eventDWheel;
	}

	public static int getEventButton() {
		return eventButton;
	}

	public static boolean getEventButtonState() {
		return eventButtonState;
	}

	public static boolean isButtonDown(int b) {
		return org.lwjgl.glfw.GLFW.glfwGetMouseButton(net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(),
				b) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
	}

	public static int getX() {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		return (int) mc.mouseHandler.xpos();
	}

	public static int getY() {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		return mc.getWindow().getHeight() - (int) mc.mouseHandler.ypos() - 1;
	}

	public static boolean isGrabbed() {
		return net.minecraft.client.Minecraft.getInstance().mouseHandler.isMouseGrabbed();
	}
}
