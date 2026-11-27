package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.plebmc.client.hud.PlebVisuals;

/** Clear Weather mod: no rain or snow, and no splashes or rain sound. */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {

	@Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
	private void plebmc$weather(LightTexture lt, float pt, double x, double y, double z, CallbackInfo ci) {
		if (PlebVisuals.hideWeather()) {
			ci.cancel();
		}
	}

	@Inject(method = "tickRain", at = @At("HEAD"), cancellable = true)
	private void plebmc$splashes(Camera cam, CallbackInfo ci) {
		if (PlebVisuals.hideSplashes()) {
			ci.cancel();
		}
	}
}
