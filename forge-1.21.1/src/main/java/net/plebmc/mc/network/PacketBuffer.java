package net.plebmc.mc.network;

/** 1.8's PacketBuffer: 1.21.1's FriendlyByteBuf. */
public class PacketBuffer extends net.minecraft.network.FriendlyByteBuf {
	public PacketBuffer(io.netty.buffer.ByteBuf b) {
		super(b);
	}
}
