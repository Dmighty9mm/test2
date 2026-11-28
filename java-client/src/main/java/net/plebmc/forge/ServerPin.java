package net.plebmc.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.plebmc.client.hud.PlebMenu;

/** Takes the PlebMc entry older builds pinned out of the server list: it has its own card above the list now. */
final class ServerPin {
	private ServerPin() {
	}

	static void pin(Minecraft mc) {
		try {
			ServerList list = new ServerList(mc);
			list.loadServerList();
			boolean changed = false;
			for (int i = list.countServers() - 1; i >= 0; i--) {
				ServerData d = list.getServerData(i);
				// the entry this client used to pin: PlebMc has its own card above the list now
				if (d.serverIP != null && d.serverIP.equalsIgnoreCase(PlebMenu.SERVER_ADDRESS) && PlebMenu.SERVER_NAME.equals(d.serverName)) {
					list.removeServerData(i);
					changed = true;
				}
			}
			if (changed) {
				list.saveServerList();
			}
		} catch (Throwable t) {
			// a broken servers.dat must not stop the game
		}
	}
}
