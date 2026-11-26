package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.GameRenderer;
import net.plebmc.client.hud.PlebVisuals;

/** Hurt Camera mod: less (or no) tilt when you take damage. */
@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void plebmc$hurtCam(PoseStack pose, float pt, CallbackInfo ci) {
		if (PlebVisuals.hurtCamera(pose, pt)) {
			ci.cancel();
		}
	}
}
