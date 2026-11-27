package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.entity.item.EntityArmorStand;
import net.plebmc.client.hud.PlebEntities;

/** Blank hologram lines from newer servers: no "Armor Stand" label. */
@Mixin(ArmorStandRenderer.class)
public abstract class MixinArmorStandRenderer {

	@Inject(method = "canRenderName(Lnet/minecraft/entity/item/EntityArmorStand;)Z", at = @At("RETURN"), cancellable = true)
	private void plebmc$blankName(EntityArmorStand entity, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && !PlebEntities.showArmorStandName(entity)) {
			cir.setReturnValue(false);
		}
	}
}
