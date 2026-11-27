package net.plebmc.mc.input;

/** The bits of LWJGL 2's Display the shared code uses, over 1.21.1's window. */
public final class Display {
	private Display() {
	}

	/** Vsync follows the game option on 1.21.1 (set when the options are saved). */
	public static void setVSyncEnabled(boolean on) {
		net.minecraft.client.Minecraft.getInstance().getWindow().updateVsync(on);
	}

	public static boolean isActive() {
		return net.minecraft.client.Minecraft.getInstance().isWindowActive();
	}

	public static int getWidth() {
		return net.minecraft.client.Minecraft.getInstance().getWindow().getWidth();
	}

	public static int getHeight() {
		return net.minecraft.client.Minecraft.getInstance().getWindow().getHeight();
	}
}
