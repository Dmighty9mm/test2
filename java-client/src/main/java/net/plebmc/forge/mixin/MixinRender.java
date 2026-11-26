package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.plebmc.client.hud.PlebEntities;

/** Apollo colored fire: tints the fire on burning players. */
@Mixin(Render.class)
public abstract class MixinRender {

	@Inject(method = "renderEntityOnFire", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V", shift = At.Shift.AFTER))
	private void plebmc$fireColor(Entity entity, double x, double y, double z, float partialTicks, CallbackInfo ci) {
		PlebEntities.applyFireColor(entity);
	}

	/** The Pleb head left of the name of players on PlebClient. */
	@Inject(method = "renderLivingLabel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I", ordinal = 1, shift = At.Shift.AFTER))
	private void plebmc$plebBadge(Entity entity, String str, double x, double y, double z, int max, CallbackInfo ci) {
		try {
			int half = ((Render<?>) (Object) this).getFontRendererFromRenderManager().getStringWidth(str) / 2;
			PlebEntities.nametagBadge(entity, str, half, "deadmau5".equals(str) ? -10 : 0);
		} catch (Throwable t) {
			// just the name
		}
	}

	@Inject(method = "renderEntityOnFire", at = @At("RETURN"))
	private void plebmc$fireColorReset(Entity entity, double x, double y, double z, float partialTicks, CallbackInfo ci) {
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
	}
}
