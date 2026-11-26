package net.plebmc.client.hud;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.plebmc.client.mods.ClientPrefs;

/**
 * The performance options from the Video Settings screen, read by the game's
 * render code (1.21.1): entity and block entity draw distance, the particle
 * cap and frame caps for menus and an unfocused window.
 */
public final class PlebPerf {

	private PlebPerf() {
	}

	private static boolean idleCapped;

	/** True when this entity is too far away to draw. Players are always drawn. */
	public static boolean cullEntity(Entity e, double camX, double camY, double camZ) {
		int d = ClientPrefs.get().entityBlocks();
		if (d <= 0 || e == null || e instanceof Player) {
			return false;
		}
		return e.distanceToSqr(camX, camY, camZ) > (double) d * d;
	}

	/** True when this block entity is too far away to draw. */
	public static boolean cullBlockEntity(BlockEntity te, double x, double y, double z) {
		int d = ClientPrefs.get().blockEntityBlocks();
		if (d <= 0 || te == null || te instanceof BeaconBlockEntity) {
			return false;
		}
		return te.getBlockPos().getCenter().distanceToSqr(x, y, z) > (double) d * d;
	}

	/** The most particles kept per particle group (vanilla 16384 on 1.21). */
	public static int particleCap() {
		return ClientPrefs.get().particleCap();
	}

	/**
	 * The frame cap.
	 *
	 * @param game   the in-game frame cap
	 * @param menu   a menu with no world loaded
	 * @param active the game window has focus
	 */
	public static int frameLimit(int game, boolean menu, boolean active) {
		ClientPrefs p = ClientPrefs.get();
		int cap = menu ? p.menuFpsCap(game) : game;
		int idle = p.idleFpsCap();
		idleCapped = !active && idle > 0;
		return idleCapped ? Math.min(cap, idle) : cap;
	}

	public static boolean idleCapped() {
		return idleCapped;
	}
}
