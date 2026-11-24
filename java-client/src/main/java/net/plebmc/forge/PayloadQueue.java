package net.plebmc.forge;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.plebmc.client.hud.PlebClient;

/** Moves plugin messages from the network thread to the game thread, in order. */
public final class PayloadQueue {
	private PayloadQueue() {
	}

	public static void received(Minecraft mc, final String channel, final byte[] bytes) {
		if (mc == null) {
			return;
		}
		mc.addScheduledTask(new Runnable() {
			@Override
			public void run() {
				PlebClient.onPayload(channel, new PacketBuffer(Unpooled.wrappedBuffer(bytes)));
			}
		});
	}
}
