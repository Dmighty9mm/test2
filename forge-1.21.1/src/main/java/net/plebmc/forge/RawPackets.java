package net.plebmc.forge;

/**
 * The 1.8 client sends the 1.9+ "swap hands" action by hand for squad pings.
 * 1.21.1 has the swap-hands key (F) itself, so nothing extra is sent here.
 */
public final class RawPackets {
	private RawPackets() {
	}

	public static boolean send(Object connection, int packetId, byte[] body) {
		return false;
	}
}
