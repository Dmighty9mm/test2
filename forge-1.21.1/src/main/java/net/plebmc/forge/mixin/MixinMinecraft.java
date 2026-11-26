package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.plebmc.client.hud.PlebClient;
import net.plebmc.client.hud.PlebPerf;

/** Menu and background frame caps, and Apollo combat (miss penalty). */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

	@Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
	private void plebmc$frameLimit(CallbackInfoReturnable<Integer> cir) {
		Minecraft mc = (Minecraft) (Object) this;
		try {
			cir.setReturnValue(PlebPerf.frameLimit(mc.options.framerateLimit().get(), mc.level == null && mc.screen != null,
					mc.isWindowActive()));
		} catch (Throwable t) {
			// vanilla
		}
	}

	@Redirect(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;hasMissTime()Z"))
	private boolean plebmc$missPenalty(MultiPlayerGameMode gm) {
		return gm.hasMissTime() && PlebClient.missPenalty();
	}
}
