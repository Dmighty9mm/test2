package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.settings.GameSettings;
import net.plebmc.client.hud.PlebClient;
import net.plebmc.forge.Zoom;

/** Zoom (the FOV while the key is held), the camera FOV for HUD markers, and fullbright. */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {

	@Redirect(method = "getFOVModifier", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;fovSetting:F"))
	private float plebmc$zoomFov(GameSettings gs) {
		return Zoom.fov(gs.fovSetting);
	}

	@Inject(method = "getFOVModifier", at = @At("RETURN"))
	private void plebmc$recordFov(float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
		if (useFovSetting) {
			Zoom.lastFov = cir.getReturnValue();
		}
	}

	@Redirect(method = "updateLightmap", at = @At(value = "FIELD", target = "Lnet/minecraft/client/settings/GameSettings;gammaSetting:F"))
	private float plebmc$gamma(GameSettings gs) {
		return PlebClient.gamma(gs.gammaSetting);
	}

	/** Hurt Camera mod: less (or no) tilt when you take damage. */
	@Inject(method = "hurtCameraEffect", at = @At("HEAD"), cancellable = true)
	private void plebmc$hurtCam(float partialTicks, CallbackInfo ci) {
		if (net.plebmc.client.hud.PlebVisuals.hurtCamera(partialTicks)) {
			ci.cancel();
		}
	}

	/** Clear Weather mod: no rain or snow. */
	@Inject(method = "renderRainSnow", at = @At("HEAD"), cancellable = true)
	private void plebmc$weather(float partialTicks, CallbackInfo ci) {
		if (net.plebmc.client.hud.PlebVisuals.hideWeather()) {
			ci.cancel();
		}
	}

	/** Clear Weather mod: no splash particles or rain sound. */
	@Inject(method = "addRainParticles", at = @At("HEAD"), cancellable = true)
	private void plebmc$splashes(CallbackInfo ci) {
		if (net.plebmc.client.hud.PlebVisuals.hideSplashes()) {
			ci.cancel();
		}
	}
}
