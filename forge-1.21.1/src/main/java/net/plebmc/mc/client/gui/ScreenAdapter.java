package net.plebmc.mc.client.gui;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.plebmc.mc.R;
import net.plebmc.mc.client.Minecraft;
import net.plebmc.mc.input.Keyboard;
import net.plebmc.mc.input.Mouse;

/** A 1.21.1 Screen that shows a 1.8-style {@link GuiScreen}. */
public final class ScreenAdapter extends Screen {
	public final GuiScreen gui;
	private int heldButton = -1;
	private long heldSince;
	private int pendingKey;

	ScreenAdapter(GuiScreen gui) {
		super(Component.literal("PlebClient"));
		this.gui = gui;
	}

	@Override
	protected void init() {
		gui.setWorldAndResolution(Minecraft.getMinecraft(), this.width, this.height);
	}

	@Override
	public void resize(net.minecraft.client.Minecraft mc, int w, int h) {
		this.width = w;
		this.height = h;
		gui.setWorldAndResolution(Minecraft.getMinecraft(), w, h);
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		net.minecraft.client.gui.GuiGraphics prev = R.gg;
		R.gg = g;
		try {
			Minecraft.getMinecraft();
			gui.drawScreen(mx, my, pt);
		} finally {
			g.flush();
			R.gg = prev;
		}
	}

	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
	}

	void vanillaBackground() {
		if (R.gg != null) {
			super.renderBackground(R.gg, 0, 0, 0f);
		}
	}

	@Override
	public void tick() {
		gui.updateScreen();
	}

	@Override
	public void removed() {
		gui.onGuiClosed();
	}

	@Override
	public boolean isPauseScreen() {
		return gui.doesGuiPauseGame();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	private static boolean printable(int glfw, int mods) {
		if ((mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0) {
			return false;
		}
		return glfw >= GLFW.GLFW_KEY_SPACE && glfw <= GLFW.GLFW_KEY_GRAVE_ACCENT
				|| glfw >= GLFW.GLFW_KEY_KP_0 && glfw <= GLFW.GLFW_KEY_KP_EQUAL && glfw != GLFW.GLFW_KEY_KP_ENTER;
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		int lw = Keyboard.fromGlfw(key);
		Keyboard.eventKey = lw;
		Keyboard.eventState = true;
		if (printable(key, mods)) {
			pendingKey = lw; // the character arrives in charTyped
			return true;
		}
		char c = 0;
		if (key == GLFW.GLFW_KEY_BACKSPACE) {
			c = 8;
		} else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			c = 13;
		} else if ((mods & GLFW.GLFW_MOD_CONTROL) != 0 && key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
			c = (char) (key - GLFW.GLFW_KEY_A + 1);
		}
		Keyboard.eventChar = c;
		gui.doKey(c, lw);
		return true;
	}

	@Override
	public boolean charTyped(char c, int mods) {
		Keyboard.eventChar = c;
		gui.doKey(c, pendingKey);
		pendingKey = 0;
		return true;
	}

	@Override
	public boolean mouseClicked(double x, double y, int b) {
		heldButton = b;
		heldSince = System.currentTimeMillis();
		Mouse.eventButton = b;
		Mouse.eventButtonState = true;
		gui.doClick((int) x, (int) y, b);
		return true;
	}

	@Override
	public boolean mouseReleased(double x, double y, int b) {
		heldButton = -1;
		Mouse.eventButton = b;
		Mouse.eventButtonState = false;
		gui.doRelease((int) x, (int) y, b);
		return true;
	}

	@Override
	public boolean mouseDragged(double x, double y, int b, double dx, double dy) {
		gui.doDrag((int) x, (int) y, b, System.currentTimeMillis() - heldSince);
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		if (sy == 0) {
			return false;
		}
		Mouse.eventDWheel = sy > 0 ? 120 : -120;
		try {
			gui.doMouseInput();
		} finally {
			Mouse.eventDWheel = 0;
		}
		return true;
	}
}
