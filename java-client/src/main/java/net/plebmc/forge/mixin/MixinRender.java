package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.plebmc.client.hud.PlebEntities;
import net.plebmc.client.hud.PlebPerf;

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

	/** Nametags tilt toward the camera in the front-facing third person view too. */
	@org.spongepowered.asm.mixin.injection.ModifyArg(method = "renderLivingLabel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;rotate(FFFF)V", ordinal = 1), index = 0)
	private float plebmc$nameTilt(float angle) {
		return net.plebmc.client.hud.PlebClient.nameTilt(angle);
	}

	/** Entity distance from the Video Settings screen (players are always drawn). */
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void plebmc$entityDistance(Entity entity, ICamera camera, double camX, double camY, double camZ,
			CallbackInfoReturnable<Boolean> cir) {
		if (PlebPerf.cullEntity(entity, camX, camY, camZ)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "renderEntityOnFire", at = @At("RETURN"))
	private void plebmc$fireColorReset(Entity entity, double x, double y, double z, float partialTicks, CallbackInfo ci) {
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
	}
}
