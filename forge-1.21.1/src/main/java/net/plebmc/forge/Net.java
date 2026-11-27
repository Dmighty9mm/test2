package net.plebmc.forge;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.ChannelListManager;
import net.minecraftforge.network.ForgePayload;
import net.plebmc.client.hud.PlebClient;

/**
 * PlebClient's plugin channels on 1.21.1. Forge only keeps the bytes of
 * channels it knows, so each channel the shared code uses is made a Forge
 * event channel (optional, so vanilla and Bukkit servers are fine). Incoming
 * payloads go to the game thread and then to PlebClient.onPayload.
 */
public final class Net {
	private Net() {
	}

	/** Every channel the shared client code registers. */
	static final String[] CHANNELS = { "lunar:apollo", "apollo:json", "plebmc:rgb", "plebmc:shop" };

	static void init() {
		for (String c : CHANNELS) {
			ResourceLocation rl = ResourceLocation.parse(c);
			ChannelBuilder.named(rl).optional().eventNetworkChannel().addListener((CustomPayloadEvent e) -> received(c, e));
		}
	}

	private static void received(String channel, CustomPayloadEvent e) {
		FriendlyByteBuf buf = e.getPayload();
		e.getSource().setPacketHandled(true);
		if (buf == null) {
			return;
		}
		byte[] bytes = new byte[buf.readableBytes()];
		buf.getBytes(buf.readerIndex(), bytes);
		Minecraft.getInstance().execute(() -> PlebClient.onPayload(channel,
				new net.plebmc.mc.network.PacketBuffer(Unpooled.wrappedBuffer(bytes))));
	}

	private static Connection connection() {
		var nh = Minecraft.getInstance().getConnection();
		return nh == null ? null : nh.getConnection();
	}

	/** Sends a plugin message; "REGISTER" announces channels the 1.21 way. */
	public static boolean send(String channel, byte[] data) {
		Connection c = connection();
		if (c == null || !c.isConnected()) {
			return false;
		}
		if ("REGISTER".equals(channel) || "minecraft:register".equals(channel)) {
			List<ResourceLocation> list = new ArrayList<>();
			for (String s : new String(data, StandardCharsets.UTF_8).split("\0")) {
				ResourceLocation rl = ResourceLocation.tryParse(s);
				if (rl != null) {
					list.add(rl);
				}
			}
			ChannelListManager.addChannels(c, list);
			return true;
		}
		ResourceLocation rl = ResourceLocation.tryParse(channel);
		if (rl == null) {
			return false;
		}
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
		c.send(new ServerboundCustomPayloadPacket(ForgePayload.create(rl, buf)));
		return true;
	}
}
