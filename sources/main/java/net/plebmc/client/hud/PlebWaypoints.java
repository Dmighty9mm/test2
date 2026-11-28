package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.plebmc.client.apollo.module.Ids;
import net.plebmc.client.apollo.module.WaypointModule;
import net.plebmc.client.mods.MoreMods;
import net.plebmc.client.mods.OwnWaypoints;

/** The waypoints to draw: the server's (Apollo) and the player's own (the Waypoints mod). */
final class PlebWaypoints {

	private PlebWaypoints() {
	}

	/** Where the player is: the server address, or "singleplayer:" + the world's spawn point. */
	static String place() {
		Minecraft mc = Minecraft.getMinecraft();
		ServerData sd = mc.getCurrentServerData();
		if (sd != null && sd.serverIP != null && !sd.serverIP.isEmpty()) {
			return sd.serverIP.toLowerCase();
		}
		// singleplayer: worlds are told apart by their spawn point (the client isn't told the world's name)
		try {
			net.minecraft.util.BlockPos sp = mc.theWorld.getSpawnPoint();
			return "singleplayer:" + sp.getX() + "," + sp.getY() + "," + sp.getZ();
		} catch (Throwable t) {
			return "singleplayer";
		}
	}

	static int dimension() {
		Minecraft mc = Minecraft.getMinecraft();
		return mc.thePlayer == null ? 0 : mc.thePlayer.dimension;
	}

	static boolean ownOn() {
		MoreMods.Waypoints m = PlebClient.mods() == null ? null : PlebClient.mods().waypoints;
		return m != null && m.isActive();
	}

	/** The player's own waypoints here that aren't hidden. */
	static List<OwnWaypoints.Entry> ownHere() {
		return OwnWaypoints.in(place(), dimension());
	}

	private static void addOwn(List<WaypointModule.Waypoint> out) {
		if (!ownOn() || OwnWaypoints.all().isEmpty()) {
			return;
		}
		for (OwnWaypoints.Entry e : ownHere()) {
			if (!e.hidden) {
				out.add(new WaypointModule.Waypoint(e.name, new Ids.Pos("", e.x, e.y, e.z), e.argb(), false, false));
			}
		}
	}

	/** Every waypoint that gets a label and a compass pip. */
	static List<WaypointModule.Waypoint> visible(WaypointModule wm) {
		List<WaypointModule.Waypoint> out = new ArrayList<>();
		if (wm != null && wm.isEnabled()) {
			out.addAll(wm.visible(""));
		}
		addOwn(out);
		return out;
	}

	/** Every waypoint that gets a sky beam (the player's own only when that option is on). */
	static List<WaypointModule.Waypoint> beams(WaypointModule wm) {
		List<WaypointModule.Waypoint> out = new ArrayList<>();
		if (wm != null && wm.isEnabled()) {
			out.addAll(wm.visible(""));
		}
		if (ownOn() && PlebClient.mods().waypoints.beams()) {
			addOwn(out);
		}
		return out;
	}
}
