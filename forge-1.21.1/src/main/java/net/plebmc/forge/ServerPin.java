package net.plebmc.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.plebmc.client.hud.PlebMenu;

/** Keeps PlebMc first in the multiplayer server list. */
final class ServerPin {
	private ServerPin() {
	}

	static void pin(Minecraft mc) {
		try {
			ServerList list = new ServerList(mc);
			list.load();
			int found = -1;
			for (int i = 0; i < list.size(); i++) {
				String ip = list.get(i).ip;
				if (ip != null && ip.toLowerCase().contains("plebmc.top")) {
					found = i;
					break;
				}
			}
			if (found == 0) {
				return;
			}
			if (found < 0) {
				list.add(new ServerData(PlebMenu.SERVER_NAME, PlebMenu.SERVER_ADDRESS, ServerData.Type.OTHER), false);
				found = list.size() - 1;
			}
			for (int i = found; i > 0; i--) {
				list.swap(i, i - 1);
			}
			list.save();
		} catch (Throwable t) {
			// a broken servers.dat must not stop the game
		}
	}
}
