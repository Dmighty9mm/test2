package net.plebmc.mc.network.play.client;

import net.plebmc.mc.network.PacketBuffer;

/** 1.8's serverbound plugin message: a channel name and its bytes. */
public class C17PacketCustomPayload {
	public final String channel;
	public final byte[] data;

	public C17PacketCustomPayload(String channel, PacketBuffer buf) {
		this.channel = channel;
		byte[] b = new byte[buf.readableBytes()];
		buf.getBytes(buf.readerIndex(), b);
		this.data = b;
	}
}
