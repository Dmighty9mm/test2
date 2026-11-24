package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.layers.LayerCape;
import net.plebmc.client.hud.CloakLayer;

/** A PlebMc cloak replaces the vanilla cape (they would overlap). */
@Mixin(LayerCape.class)
public abstract class MixinLayerCape {

	@Inject(method = "doRenderLayer(Lnet/minecraft/client/entity/AbstractClientPlayer;FFFFFFF)V", at = @At("HEAD"), cancellable = true)
	private void plebmc$cloak(AbstractClientPlayer p, float a, float b, float c, float d, float f, float g, float h,
			CallbackInfo ci) {
		if (CloakLayer.covers(p)) {
			ci.cancel();
		}
	}
}
