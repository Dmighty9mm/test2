package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.plebmc.client.hud.PlebEntities;
import net.plebmc.mc.entity.Entity;

/** Apollo flipped entities, and hologram lines whose armor stand lost its invisible flag. */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {

	@Inject(method = "isEntityUpsideDown", at = @At("RETURN"), cancellable = true)
	private static void plebmc$flip(LivingEntity e, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && e.deathTime <= 0) {
			try {
				if (PlebEntities.flipped(Entity.wrap(e))) {
					cir.setReturnValue(true);
				}
			} catch (Throwable t) {
				// vanilla
			}
		}
	}

	@Inject(method = "isBodyVisible", at = @At("RETURN"), cancellable = true)
	private void plebmc$hologram(LivingEntity e, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && e instanceof ArmorStand) {
			try {
				if (PlebEntities.isHologramStand(Entity.wrap(e))) {
					cir.setReturnValue(false);
				}
			} catch (Throwable t) {
				// vanilla
			}
		}
	}
}
