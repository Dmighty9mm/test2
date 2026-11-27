package net.plebmc.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;

/** The bits of {@link VideoOptionsScreen} that differ between game versions (this is the Forge client's). */
final class VideoCompat {

	private VideoCompat() {
	}

	/** False for an option this computer can't use (greyed out, like Minecraft's own list does). */
	static boolean available(String option) {
		try {
			switch (option) {
			case "USE_VBO":
				return OpenGlHelper.vboSupported;
			case "FBO_ENABLE":
				return OpenGlHelper.isFramebufferEnabled() || OpenGlHelper.framebufferSupported;
			default:
				return true;
			}
		} catch (Throwable t) {
			return true;
		}
	}

	/** After the GUI scale changed: lay the screen out at the new size. */
	static void guiScaleChanged(Minecraft mc, GuiScreen s) {
		ScaledResolution sr = new ScaledResolution(mc);
		s.setWorldAndResolution(mc, sr.getScaledWidth(), sr.getScaledHeight());
	}

	/** Where closing the screen goes. */
	static GuiScreen closeTo(Minecraft mc, GuiScreen parent) {
		return parent;
	}

	static GuiScreen vanilla(Minecraft mc, GuiScreen parent) {
		return new GuiVideoSettings(parent, mc.gameSettings);
	}

	static boolean isVanilla(GuiScreen s) {
		return s instanceof GuiVideoSettings;
	}
}
