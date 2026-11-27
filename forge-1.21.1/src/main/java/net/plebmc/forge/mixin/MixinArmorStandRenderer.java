package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.plebmc.client.hud.PlebEntities;
import net.plebmc.mc.entity.Entity;

/** Blank hologram lines: no "Armor Stand" label. */
@Mixin(ArmorStandRenderer.class)
public abstract class MixinArmorStandRenderer {

	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/decoration/ArmorStand;)Z", at = @At("RETURN"), cancellable = true)
	private void plebmc$blankName(ArmorStand e, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			try {
				if (!PlebEntities.showArmorStandName(Entity.wrap(e))) {
					cir.setReturnValue(false);
				}
			} catch (Throwable t) {
				// vanilla
			}
		}
	}
}
