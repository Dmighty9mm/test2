package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.renderer.LightTexture;
import net.plebmc.client.hud.PlebClient;

/** Fullbright: the brightness the light map is built with. */
@Mixin(LightTexture.class)
public abstract class MixinLightTexture {

	@Redirect(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1))
	private float plebmc$gamma(Double gamma) {
		return PlebClient.gamma(gamma.floatValue());
	}
}
