package net.plebmc.mc.client.gui;

/** 1.8's ScaledResolution over the 1.21.1 window. */
public class ScaledResolution {
	private final int w, h, f;

	public ScaledResolution(net.plebmc.mc.client.Minecraft mc) {
		var win = net.minecraft.client.Minecraft.getInstance().getWindow();
		w = win.getGuiScaledWidth();
		h = win.getGuiScaledHeight();
		f = (int) win.getGuiScale();
	}

	public int getScaledWidth() {
		return w;
	}

	public int getScaledHeight() {
		return h;
	}

	public double getScaledWidth_double() {
		return w;
	}

	public double getScaledHeight_double() {
		return h;
	}

	public int getScaleFactor() {
		return f;
	}
}
