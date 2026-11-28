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

	/** The color of the highlight on a block you just pinged. */
	static final int PING_ARGB = 0xFFFFE14F;

	/**
	 * The blocks to light up, as {x, y, z, argb}: one per waypoint, plus the
	 * block you just pinged. A server marker that floats in the air right over
	 * a block (a squad ping is placed on top of what was pinged) lights up that
	 * block instead of the air; your own waypoints stay where you made them.
	 */
	static List<int[]> markBlocks(WaypointModule wm) {
		List<int[]> out = new ArrayList<>();
		Minecraft mc = Minecraft.getMinecraft();
		if (wm != null && wm.isEnabled()) {
			for (WaypointModule.Waypoint wp : wm.visible("")) {
				int x = (int) Math.floor(wp.pos.x), y = (int) Math.floor(wp.pos.y), z = (int) Math.floor(wp.pos.z);
				try {
					if (mc.theWorld != null && y > 0 && mc.theWorld.isAirBlock(new net.minecraft.util.BlockPos(x, y, z))
							&& !mc.theWorld.isAirBlock(new net.minecraft.util.BlockPos(x, y - 1, z))) {
						y--;
					}
				} catch (Throwable t) {
					// an unloaded chunk: keep the marker's own block
				}
				out.add(new int[] { x, y, z, wp.argb });
			}
		}
		List<WaypointModule.Waypoint> own = new ArrayList<>();
		addOwn(own);
		for (WaypointModule.Waypoint wp : own) {
			out.add(new int[] { (int) Math.floor(wp.pos.x), (int) Math.floor(wp.pos.y), (int) Math.floor(wp.pos.z), wp.argb });
		}
		long[] ping = PlebClient.localPing;
		if (ping != null) {
			if (System.currentTimeMillis() > ping[3]) {
				PlebClient.localPing = null;
			} else {
				boolean have = false;
				for (int[] b : out) {
					have |= b[0] == ping[0] && b[1] == ping[1] && b[2] == ping[2];
				}
				if (!have) {
					// the server's own marker takes over (in its color) once it arrives
					out.add(new int[] { (int) ping[0], (int) ping[1], (int) ping[2], PING_ARGB });
				}
			}
		}
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
