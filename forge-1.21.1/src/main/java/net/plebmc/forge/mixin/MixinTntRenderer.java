package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.PrimedTnt;
import net.plebmc.client.hud.PlebEntities;
import net.plebmc.mc.entity.Entity;

/** Apollo TNT countdown: seconds above primed TNT. */
@Mixin(TntRenderer.class)
public abstract class MixinTntRenderer extends EntityRenderer<PrimedTnt> {

	protected MixinTntRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Inject(method = "render(Lnet/minecraft/world/entity/item/PrimedTnt;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
	private void plebmc$tnt(PrimedTnt tnt, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light, CallbackInfo ci) {
		try {
			String label = PlebEntities.tntLabel(Entity.wrap(tnt));
			if (label != null) {
				this.renderNameTag(tnt, Component.literal(label), pose, buf, light, pt);
			}
		} catch (Throwable t) {
			// no countdown
		}
	}
}
