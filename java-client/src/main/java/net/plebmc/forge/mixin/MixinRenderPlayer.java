package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.plebmc.client.hud.PlebEntities;

/** Apollo limb module: hidden body parts. */
@Mixin(RenderPlayer.class)
public abstract class MixinRenderPlayer {

	@Inject(method = "setModelVisibilities", at = @At("TAIL"))
	private void plebmc$limbs(AbstractClientPlayer player, CallbackInfo ci) {
		PlebEntities.applyLimbs(player, ((RenderPlayer) (Object) this).getMainModel());
	}
}
