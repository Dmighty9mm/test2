package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.plebmc.client.hud.PlebEntities;
import net.plebmc.mc.entity.Entity;

/** Apollo limb module: hidden armor pieces. */
@Mixin(HumanoidArmorLayer.class)
public abstract class MixinHumanoidArmorLayer {

	@Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
	private void plebmc$hideArmor(PoseStack pose, MultiBufferSource buf, LivingEntity e, EquipmentSlot slot, int light,
			HumanoidModel<?> model, CallbackInfo ci) {
		int s = slot == EquipmentSlot.HEAD ? 4 : slot == EquipmentSlot.CHEST ? 3 : slot == EquipmentSlot.LEGS ? 2 : 1;
		try {
			if (PlebEntities.armorHidden(Entity.wrap(e), s)) {
				ci.cancel();
			}
		} catch (Throwable t) {
			// vanilla
		}
	}
}
