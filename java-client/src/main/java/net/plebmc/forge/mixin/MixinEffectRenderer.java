package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import net.minecraft.client.particle.EffectRenderer;
import net.plebmc.client.hud.PlebPerf;

/** The particle cap from the Video Settings screen (vanilla keeps 4000 of each kind). */
@Mixin(EffectRenderer.class)
public abstract class MixinEffectRenderer {

	@ModifyConstant(method = "addEffect", constant = @Constant(intValue = 4000))
	private int plebmc$particleCap(int vanilla) {
		return PlebPerf.particleCap();
	}
}
