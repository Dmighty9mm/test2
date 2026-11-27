package net.plebmc.mc.client.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

/** 1.8's NetHandlerPlayClient over 1.21.1's ClientPacketListener. */
public class NetHandlerPlayClient {
	public final net.minecraft.client.multiplayer.ClientPacketListener real;

	public NetHandlerPlayClient(net.minecraft.client.multiplayer.ClientPacketListener c) {
		real = c;
	}

	public NetworkPlayerInfo getPlayerInfo(UUID u) {
		var p = real.getPlayerInfo(u);
		return p == null ? null : new NetworkPlayerInfo(p);
	}

	public NetworkPlayerInfo getPlayerInfo(String name) {
		var p = real.getPlayerInfo(name);
		return p == null ? null : new NetworkPlayerInfo(p);
	}

	public Collection<NetworkPlayerInfo> getPlayerInfoMap() {
		Collection<NetworkPlayerInfo> out = new ArrayList<>();
		for (var p : real.getOnlinePlayers()) {
			out.add(new NetworkPlayerInfo(p));
		}
		return out;
	}

	public void addToSendQueue(net.plebmc.mc.network.play.client.C17PacketCustomPayload p) {
		net.plebmc.forge.Net.send(p.channel, p.data);
	}

	public Object getNetworkManager() {
		return real.getConnection();
	}
}
