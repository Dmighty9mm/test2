package net.plebmc.forge;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;

/**
 * Sends a play packet the 1.8 client has no class for (the 1.9+ "swap
 * hands" digging action that PlebNite uses for squad pings). The bytes go
 * in after the packet encoder, so compression and encryption still apply.
 */
public final class RawPackets {
	private RawPackets() {
	}

	public static boolean send(NetworkManager nm, int packetId, byte[] body) {
		if (nm == null || nm.isLocalChannel()) {
			return false;
		}
		Channel ch = nm.channel();
		if (ch == null || !ch.isOpen()) {
			return false;
		}
		ChannelHandlerContext ctx = ch.pipeline().context("encoder");
		if (ctx == null) {
			return false;
		}
		ByteBuf buf = Unpooled.buffer(body.length + 5);
		new PacketBuffer(buf).writeVarIntToBuffer(packetId);
		buf.writeBytes(body);
		ctx.writeAndFlush(buf);
		return true;
	}
}
