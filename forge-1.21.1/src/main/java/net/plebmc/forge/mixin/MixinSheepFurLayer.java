package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.renderer.entity.layers.SheepFurLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.Sheep;
import net.plebmc.client.hud.PlebEntities;
import net.plebmc.mc.entity.Entity;

/** Apollo entity module: rainbow sheep. */
@Mixin(SheepFurLayer.class)
public abstract class MixinSheepFurLayer {

	private static boolean plebmc$rainbow(Sheep s) {
		try {
			return PlebEntities.rainbowSheep(Entity.wrap(s));
		} catch (Throwable t) {
			return false;
		}
	}

	@Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/animal/Sheep;FFFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Sheep;hasCustomName()Z"))
	private boolean plebmc$hasName(Sheep s) {
		return plebmc$rainbow(s) || s.hasCustomName();
	}

	@Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/animal/Sheep;FFFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Sheep;getName()Lnet/minecraft/network/chat/Component;"))
	private Component plebmc$name(Sheep s) {
		return plebmc$rainbow(s) ? Component.literal("jeb_") : s.getName();
	}
}
