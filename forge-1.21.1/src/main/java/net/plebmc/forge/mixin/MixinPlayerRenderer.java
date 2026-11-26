package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.plebmc.client.hud.PlebEntities;
import net.plebmc.mc.client.model.ModelPlayer;
import net.plebmc.mc.entity.Entity;

/** Apollo limb module: hidden body parts. */
@Mixin(PlayerRenderer.class)
public abstract class MixinPlayerRenderer {

	@Inject(method = "setModelProperties", at = @At("TAIL"))
	private void plebmc$limbs(AbstractClientPlayer p, CallbackInfo ci) {
		try {
			ModelPlayer m = new ModelPlayer(((PlayerRenderer) (Object) this).getModel());
			PlebEntities.applyLimbs(Entity.wrap(p), m);
			m.pushVisibility();
		} catch (Throwable t) {
			// vanilla
		}
	}
}
