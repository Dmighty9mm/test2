package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Objective;
import net.plebmc.client.hud.PlebVisuals;

/** Crosshair, Scoreboard and Overlays mods. */
@Mixin(Gui.class)
public abstract class MixinGui {

	@Shadow
	@Final
	private static ResourceLocation PUMPKIN_BLUR_LOCATION;

	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void plebmc$crosshair(GuiGraphics gg, DeltaTracker dt, CallbackInfo ci) {
		if (PlebVisuals.crosshair(gg)) {
			ci.cancel();
		}
	}

	@Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
	private void plebmc$scoreboard(GuiGraphics gg, Objective obj, CallbackInfo ci) {
		if (PlebVisuals.scoreboard(gg, obj)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
	private void plebmc$vignette(GuiGraphics gg, Entity e, CallbackInfo ci) {
		if (PlebVisuals.hideVignette()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
	private void plebmc$pumpkin(GuiGraphics gg, ResourceLocation tex, float alpha, CallbackInfo ci) {
		if (PUMPKIN_BLUR_LOCATION.equals(tex) && PlebVisuals.hidePumpkin()) {
			ci.cancel();
		}
	}
}
