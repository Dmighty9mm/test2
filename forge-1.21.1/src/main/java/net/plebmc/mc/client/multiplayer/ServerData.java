package net.plebmc.mc.client.multiplayer;

/** 1.8's ServerData. */
public class ServerData {
	public String serverIP;
	public String serverName;

	public ServerData(net.minecraft.client.multiplayer.ServerData d) {
		serverIP = d.ip;
		serverName = d.name;
	}

	public ServerData(String name, String ip, boolean lan) {
		serverName = name;
		serverIP = ip;
	}
}
