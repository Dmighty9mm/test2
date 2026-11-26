package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.scoreboard.ScoreObjective;
import net.plebmc.client.hud.PlebVisuals;

/** Scoreboard mod: the restyled sidebar (GuiIngameForge calls this method too). */
@Mixin(GuiIngame.class)
public abstract class MixinGuiIngame {

	@Inject(method = "renderScoreboard", at = @At("HEAD"), cancellable = true)
	private void plebmc$scoreboard(ScoreObjective obj, ScaledResolution res, CallbackInfo ci) {
		if (PlebVisuals.scoreboard(obj, res)) {
			ci.cancel();
		}
	}

	/** Overlays mod: no dark vignette. */
	@Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
	private void plebmc$vignette(float brightness, int w, int h, CallbackInfo ci) {
		if (PlebVisuals.hideVignette()) {
			ci.cancel();
		}
	}
}
