package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.plebmc.client.hud.PlebClient;

/** Joined a server: register PlebMc's plugin channels and say hello to Apollo. */
@Mixin(NetHandlerPlayClient.class)
public abstract class MixinNetHandlerPlayClient {

	@Inject(method = "handleJoinGame", at = @At("RETURN"))
	private void plebmc$join(S01PacketJoinGame packet, CallbackInfo ci) {
		PlebClient.onJoin();
	}
}
