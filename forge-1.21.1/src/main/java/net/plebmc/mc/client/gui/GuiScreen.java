package net.plebmc.mc.client.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.plebmc.mc.R;
import net.plebmc.mc.client.Minecraft;

/**
 * 1.8's GuiScreen. On 1.21.1 a {@link ScreenAdapter} (a real Screen) holds one
 * of these and passes it drawing, keys and the mouse.
 */
public class GuiScreen extends Gui {
	public Minecraft mc;
	public int width;
	public int height;
	public FontRenderer fontRendererObj;
	protected List<GuiButton> buttonList = new ArrayList<>();
	public boolean allowUserInput;
	/** The Screen showing this one (made on demand). */
	ScreenAdapter adapter;

	public void setWorldAndResolution(Minecraft mc, int w, int h) {
		this.mc = mc;
		this.fontRendererObj = mc.fontRendererObj;
		this.width = w;
		this.height = h;
		this.buttonList.clear();
		initGui();
	}

	public void initGui() {
	}

	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		for (GuiButton b : buttonList) {
			b.drawButton(mc, mouseX, mouseY);
		}
	}

	protected void keyTyped(char c, int key) throws IOException {
		if (key == 1) {
			mc.displayGuiScreen(null);
			if (mc.currentScreen == null) {
				mc.setIngameFocus();
			}
		}
	}

	protected void mouseClicked(int mx, int my, int button) throws IOException {
		if (button == 0) {
			for (GuiButton b : new ArrayList<>(buttonList)) {
				if (b.mousePressed(mc, mx, my)) {
					b.playPressSound(mc.getSoundHandler());
					actionPerformed(b);
				}
			}
		}
	}

	protected void mouseReleased(int mx, int my, int state) {
	}

	protected void mouseClickMove(int mx, int my, int button, long held) {
	}

	protected void actionPerformed(GuiButton b) throws IOException {
	}

	public void handleMouseInput() throws IOException {
	}

	public void handleKeyboardInput() throws IOException {
	}

	public void updateScreen() {
	}

	public void onGuiClosed() {
	}

	public boolean doesGuiPauseGame() {
		return true;
	}

	public void drawDefaultBackground() {
		drawWorldBackground(0);
	}

	public void drawWorldBackground(int tint) {
		if (R.gg == null) {
			return;
		}
		if (mc != null && mc.theWorld != null) {
			R.gg.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
		} else if (adapter != null) {
			adapter.vanillaBackground();
		} else {
			R.gg.fill(0, 0, width, height, 0xFF101418);
		}
	}

	public static boolean isShiftKeyDown() {
		return net.minecraft.client.gui.screens.Screen.hasShiftDown();
	}

	public static boolean isCtrlKeyDown() {
		return net.minecraft.client.gui.screens.Screen.hasControlDown();
	}

	public static boolean isAltKeyDown() {
		return net.minecraft.client.gui.screens.Screen.hasAltDown();
	}

	public static boolean isKeyComboCtrlA(int key) {
		return key == 30 && isCtrlKeyDown() && !isShiftKeyDown() && !isAltKeyDown();
	}

	public static boolean isKeyComboCtrlC(int key) {
		return key == 46 && isCtrlKeyDown() && !isShiftKeyDown() && !isAltKeyDown();
	}

	public static boolean isKeyComboCtrlV(int key) {
		return key == 47 && isCtrlKeyDown() && !isShiftKeyDown() && !isAltKeyDown();
	}

	public static boolean isKeyComboCtrlX(int key) {
		return key == 45 && isCtrlKeyDown() && !isShiftKeyDown() && !isAltKeyDown();
	}

	public static String getClipboardString() {
		String s = net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard();
		return s == null ? "" : s;
	}

	public static void setClipboardString(String s) {
		net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(s == null ? "" : s);
	}

	// ---- called by the adapter ----
	void doKey(char c, int key) {
		try {
			keyTyped(c, key);
		} catch (IOException ignored) {
		}
	}

	void doClick(int mx, int my, int b) {
		try {
			mouseClicked(mx, my, b);
		} catch (IOException ignored) {
		}
	}

	void doRelease(int mx, int my, int b) {
		mouseReleased(mx, my, b);
	}

	void doDrag(int mx, int my, int b, long held) {
		mouseClickMove(mx, my, b, held);
	}

	void doMouseInput() {
		try {
			handleMouseInput();
		} catch (IOException ignored) {
		}
	}

	/** The Screen to give 1.21.1 for this one. */
	public net.minecraft.client.gui.screens.Screen toScreen() {
		if (adapter == null) {
			adapter = new ScreenAdapter(this);
		}
		return adapter;
	}

	/** A 1.21.1 screen that isn't ours, seen through 1.8 eyes (instanceof checks, width/height). */
	public static class Vanilla extends GuiScreen {
		public final net.minecraft.client.gui.screens.Screen screen;

		public Vanilla(net.minecraft.client.gui.screens.Screen s) {
			this.screen = s;
			if (s != null) {
				this.width = s.width;
				this.height = s.height;
			}
		}

		@Override
		public net.minecraft.client.gui.screens.Screen toScreen() {
			return screen;
		}
	}
}
