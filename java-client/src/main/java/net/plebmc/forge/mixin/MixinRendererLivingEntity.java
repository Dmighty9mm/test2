package net.plebmc.forge.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumChatFormatting;
import net.plebmc.client.hud.PlebEntities;

/** Apollo nametags (server lines, server render distance) and flipped entities. */
@Mixin(RendererLivingEntity.class)
public abstract class MixinRendererLivingEntity<T extends EntityLivingBase> extends Render<T> {

	protected MixinRendererLivingEntity(RenderManager renderManager) {
		super(renderManager);
	}

	@Shadow
	protected abstract boolean canRenderName(T entity);

	@Inject(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V", at = @At("HEAD"), cancellable = true)
	private void plebmc$nametag(T entity, double x, double y, double z, CallbackInfo ci) {
		try {
			if (!this.canRenderName(entity)) {
				return;
			}
			double d3 = entity.getDistanceSqToEntity(this.renderManager.livingPlayer);
			float f = (float) PlebEntities.nametagRange(entity.isSneaking() ? 32.0F : 64.0F);
			List<String> tag = PlebEntities.nametag(entity);
			if (tag != null) {
				for (int line = 0; line < tag.size() && d3 < (double) (f * f); ++line) {
					this.renderLivingLabel(entity, tag.get(line), x, y + 0.25D * (tag.size() - 1 - line), z, (int) f);
				}
				ci.cancel();
			} else if (d3 >= (double) (f * f)) {
				ci.cancel();
			}
		} catch (Throwable t) {
			// fall back to the vanilla nametag
		}
	}

	/** Holograms from newer servers whose armor stand lost its invisible flag: draw only the text. */
	@Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
	private void plebmc$hologramStand(T entity, float a, float b, float c, float d, float e, float f, CallbackInfo ci) {
		if (entity instanceof net.minecraft.entity.item.EntityArmorStand
				&& PlebEntities.isHologramStand((net.minecraft.entity.item.EntityArmorStand) entity)) {
			ci.cancel();
		}
	}

	@Inject(method = "rotateCorpse", at = @At("TAIL"))
	private void plebmc$flip(T entity, float p1, float p2, float p3, CallbackInfo ci) {
		if (entity.deathTime > 0 || !PlebEntities.flipped(entity)) {
			return;
		}
		String s = EnumChatFormatting.getTextWithoutFormattingCodes(entity.getName());
		if ("Dinnerbone".equals(s) || "Grumm".equals(s)) {
			return; // already upside down
		}
		GlStateManager.translate(0.0F, entity.height + 0.1F, 0.0F);
		GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
	}
}
