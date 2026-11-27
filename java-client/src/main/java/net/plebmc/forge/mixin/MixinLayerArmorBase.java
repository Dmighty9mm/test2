package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.entity.EntityLivingBase;
import net.plebmc.client.hud.PlebEntities;

/** Apollo limb module: hidden armor pieces. */
@Mixin(LayerArmorBase.class)
public abstract class MixinLayerArmorBase {

	@Inject(method = "renderLayer", at = @At("HEAD"), cancellable = true)
	private void plebmc$hideArmor(EntityLivingBase e, float a, float b, float c, float d, float f, float g, float h,
			int armorSlot, CallbackInfo ci) {
		if (PlebEntities.armorHidden(e, armorSlot)) {
			ci.cancel();
		}
	}
}
