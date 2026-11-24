package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.plebmc.client.hud.PlebClient;

/** Apollo combat (miss penalty) and per-connection reset when the world unloads. */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

	@Redirect(method = "clickMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;isNotCreative()Z"))
	private boolean plebmc$missPenalty(PlayerControllerMP pc) {
		return pc.isNotCreative() && PlebClient.missPenalty();
	}

	@Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("HEAD"))
	private void plebmc$leave(WorldClient world, String message, CallbackInfo ci) {
		if (world == null) {
			PlebClient.onLeave();
		}
	}
}
