package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.plebmc.client.hud.CloakLayer;
import net.plebmc.mc.entity.Entity;

/** A PlebMc cloak replaces the vanilla cape (they would overlap). */
@Mixin(CapeLayer.class)
public abstract class MixinCapeLayer {

	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V", at = @At("HEAD"), cancellable = true)
	private void plebmc$cloak(PoseStack pose, MultiBufferSource buf, int light, AbstractClientPlayer p, float a, float b, float c,
			float d, float e, float f, CallbackInfo ci) {
		try {
			if (CloakLayer.covers(Entity.wrap(p))) {
				ci.cancel();
			}
		} catch (Throwable t) {
			// vanilla
		}
	}
}
