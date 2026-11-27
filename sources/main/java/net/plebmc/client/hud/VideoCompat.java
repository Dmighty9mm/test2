package net.plebmc.client.hud;

import net.lax1dude.eaglercraft.v1_8.Display;
import net.lax1dude.eaglercraft.v1_8.minecraft.GuiScreenVideoSettingsWarning;
import net.lax1dude.eaglercraft.v1_8.opengl.EaglercraftGPU;
import net.lax1dude.eaglercraft.v1_8.opengl.ext.dynamiclights.DynamicLightsStateManager;
import net.lax1dude.eaglercraft.v1_8.recording.ScreenRecordingController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.gui.ScaledResolution;

/** The bits of {@link VideoOptionsScreen} that differ between game versions (this is the browser client's). */
final class VideoCompat {

	private VideoCompat() {
	}

	/** False for an option this device can't use (greyed out, like Minecraft's own list does). */
	static boolean available(String option) {
		try {
			switch (option) {
			case "EAGLER_DYNAMIC_LIGHTS":
				return DynamicLightsStateManager.isSupported();
			case "MIPMAP_LEVELS":
				return EaglercraftGPU.checkOpenGLESVersion() >= 300;
			case "FULLSCREEN":
				return Display.supportsFullscreen();
			case "EAGLER_VSYNC":
				return !ScreenRecordingController.isVSyncLocked();
			default:
				return true;
			}
		} catch (Throwable t) {
			return true;
		}
	}

	/** After the GUI scale changed: lay the screen (and the HUD) out at the new size. */
	static void guiScaleChanged(Minecraft mc, GuiScreen s) {
		ScaledResolution sr = mc.scaledResolution = new ScaledResolution(mc);
		int w = sr.getScaledWidth(), h = sr.getScaledHeight();
		s.setWorldAndResolution(mc, w, h);
		mc.voiceOverlay.setResolution(w, h);
		mc.notifRenderer.setResolution(mc, w, h, sr.getScaleFactor());
	}

	/** Where closing the screen goes: the parent, or the game's warning about bad video settings first. */
	static GuiScreen closeTo(Minecraft mc, GuiScreen parent) {
		int issues = mc.gameSettings.checkBadVideoSettings();
		return issues != 0 ? new GuiScreenVideoSettingsWarning(parent, issues) : parent;
	}

	static GuiScreen vanilla(Minecraft mc, GuiScreen parent) {
		return new GuiVideoSettings(parent, mc.gameSettings);
	}

	static boolean isVanilla(GuiScreen s) {
		return s instanceof GuiVideoSettings;
	}
}
