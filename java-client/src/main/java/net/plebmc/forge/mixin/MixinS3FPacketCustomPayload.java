package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.plebmc.forge.PayloadQueue;

/**
 * Every plugin message from the server, read as it is decoded (before Forge
 * routes it), then handed to PlebMc on the game thread.
 */
@Mixin(S3FPacketCustomPayload.class)
public abstract class MixinS3FPacketCustomPayload {

	@Shadow
	private String channel;

	@Shadow
	private PacketBuffer data;

	@Inject(method = "readPacketData", at = @At("RETURN"))
	private void plebmc$read(PacketBuffer buf, CallbackInfo ci) {
		if (channel == null || data == null) {
			return;
		}
		byte[] bytes = new byte[data.readableBytes()];
		data.getBytes(data.readerIndex(), bytes);
		PayloadQueue.received(Minecraft.getMinecraft(), channel, bytes);
	}
}
