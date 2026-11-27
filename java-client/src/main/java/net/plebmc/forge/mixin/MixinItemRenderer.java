package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.plebmc.client.hud.PlebVisuals;

/** Overlays mod: the on-fire overlay pushed down (Low) or hidden. */
@Mixin(ItemRenderer.class)
public abstract class MixinItemRenderer {

	@Unique
	private boolean plebmc$firePushed;

	@Inject(method = "renderFireInFirstPerson", at = @At("HEAD"), cancellable = true)
	private void plebmc$fireStart(float partialTicks, CallbackInfo ci) {
		float drop = PlebVisuals.fireDrop();
		plebmc$firePushed = false;
		if (drop < 0f) {
			ci.cancel();
		} else if (drop > 0f) {
			GlStateManager.pushMatrix();
			GlStateManager.translate(0.0F, -drop, 0.0F);
			plebmc$firePushed = true;
		}
	}

	@Inject(method = "renderFireInFirstPerson", at = @At("RETURN"))
	private void plebmc$fireEnd(float partialTicks, CallbackInfo ci) {
		if (plebmc$firePushed) {
			GlStateManager.popMatrix();
			plebmc$firePushed = false;
		}
	}
}
