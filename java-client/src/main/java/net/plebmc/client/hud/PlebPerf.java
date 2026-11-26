package net.plebmc.client.hud;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBeacon;
import net.plebmc.client.mods.ClientPrefs;

/**
 * The performance options from the Video Settings screen, read by the game's
 * render code: entity and block entity draw distance, the particle cap and
 * frame caps for menus and an unfocused window.
 */
public final class PlebPerf {

	private PlebPerf() {
	}

	private static boolean idleCapped;

	/** Render.shouldRender: true when this entity is too far away to draw. Players are always drawn. */
	public static boolean cullEntity(Entity e, double camX, double camY, double camZ) {
		int d = ClientPrefs.get().entityBlocks();
		if (d <= 0 || e == null || e instanceof EntityPlayer) {
			return false;
		}
		double dx = e.posX - camX, dy = e.posY - camY, dz = e.posZ - camZ;
		return dx * dx + dy * dy + dz * dz > (double) d * d;
	}

	/** TileEntityRendererDispatcher.renderTileEntity: true when this block entity is too far away to draw. */
	public static boolean cullBlockEntity(TileEntity te, double x, double y, double z) {
		int d = ClientPrefs.get().blockEntityBlocks();
		if (d <= 0 || te == null || te instanceof TileEntityBeacon) {
			return false;
		}
		return te.getDistanceSq(x, y, z) > (double) d * d;
	}

	/** The most particles kept per particle list (vanilla 4000). */
	public static int particleCap() {
		return ClientPrefs.get().particleCap();
	}

	/**
	 * Minecraft.getLimitFramerate.
	 *
	 * @param game   the in-game frame cap (gameSettings.limitFramerate)
	 * @param menu   a menu with no world loaded (vanilla caps these at 30)
	 * @param active the game window has focus
	 */
	public static int frameLimit(int game, boolean menu, boolean active) {
		ClientPrefs p = ClientPrefs.get();
		int cap = menu ? p.menuFpsCap(game) : game;
		int idle = p.idleFpsCap();
		idleCapped = !active && idle > 0;
		return idleCapped ? Math.min(cap, idle) : cap;
	}

	/** The window is unfocused and capped: the cap applies even with vsync on. */
	public static boolean idleCapped() {
		return idleCapped;
	}
}
