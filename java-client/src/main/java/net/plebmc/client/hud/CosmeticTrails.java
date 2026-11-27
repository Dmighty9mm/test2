package net.plebmc.client.hud;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.plebmc.client.cosmetics.Cosmetics;

/**
 * Trail cosmetics: particles at the feet of players wearing one, spawned
 * once per game tick (more while moving). Uses the normal particle system,
 * so the player's particle setting still applies.
 */
final class CosmeticTrails {

	private CosmeticTrails() {
	}

	private static long lastTick = Long.MIN_VALUE;

	/** Called every frame; does the work once per game tick. */
	static void update(Minecraft mc) {
		if (mc.theWorld == null || mc.thePlayer == null) {
			return;
		}
		Cosmetics.setSelf(PlebEntities.uuid(mc.thePlayer));
		long tick = mc.theWorld.getTotalWorldTime();
		if (tick == lastTick || Cosmetics.nothingToDraw()) {
			lastTick = tick;
			return;
		}
		// at a low frame rate several ticks pass per frame: catch up on (at most 4 of) them
		int ticks = lastTick == Long.MIN_VALUE ? 1 : (int) Math.max(1, Math.min(4, tick - lastTick));
		lastTick = tick;
		for (int k = ticks - 1; k >= 0; k--) {
			tick(mc, tick - k);
		}
	}

	private static void tick(Minecraft mc, long tick) {
		List<EntityPlayer> players = mc.theWorld.playerEntities;
		for (int i = 0; i < players.size(); i++) {
			EntityPlayer p = players.get(i);
			if (p.isInvisible() || p.isSpectator() || p.getDistanceSqToEntity(mc.thePlayer) > 48 * 48) {
				continue;
			}
			Cosmetics.Item trail = Cosmetics.shown(PlebEntities.uuid(p), Cosmetics.TRAIL);
			if (trail == null) {
				continue;
			}
			double dx = p.posX - p.lastTickPosX;
			double dz = p.posZ - p.lastTickPosZ;
			boolean moving = dx * dx + dz * dz > 0.0004;
			if (!moving && (tick + p.getEntityId()) % 6 != 0) {
				continue; // standing still: an occasional puff
			}
			spawn(mc, p, trail, tick, moving ? 2 : 1);
		}
	}

	private static void spawn(Minecraft mc, EntityPlayer p, Cosmetics.Item trail, long tick, int count) {
		for (int n = 0; n < count; n++) {
			double x = p.posX + (Math.random() - 0.5) * 0.5;
			double y = p.posY + 0.1 + Math.random() * 0.2;
			double z = p.posZ + (Math.random() - 0.5) * 0.5;
			switch (trail.render) {
			case "flame":
				mc.theWorld.spawnParticle(EnumParticleTypes.FLAME, x, y, z, 0.0, 0.01, 0.0);
				break;
			case "hearts":
				if (n == 0) {
					mc.theWorld.spawnParticle(EnumParticleTypes.HEART, x, y + 0.3, z, 0.0, 0.0, 0.0);
				}
				break;
			case "notes":
				if (n == 0) {
					mc.theWorld.spawnParticle(EnumParticleTypes.NOTE, x, y + 0.4, z, (tick % 24) / 24.0, 0.0, 0.0);
				}
				break;
			case "rainbow": {
				float hue = ((tick * 4 + n * 30) % 360) / 360f;
				int rgb = hue(hue);
				double r = Math.max(0.001, ((rgb >> 16) & 0xFF) / 255.0);
				double g = ((rgb >> 8) & 0xFF) / 255.0;
				double b = (rgb & 0xFF) / 255.0;
				mc.theWorld.spawnParticle(EnumParticleTypes.REDSTONE, x, y + 0.1, z, r, g, b);
				break;
			}
			case "cloud":
				mc.theWorld.spawnParticle(EnumParticleTypes.CLOUD, x, y, z, 0.0, 0.0, 0.0);
				break;
			case "portal":
				mc.theWorld.spawnParticle(EnumParticleTypes.PORTAL, x, y + 0.2, z, (Math.random() - 0.5) * 0.6,
						-0.1, (Math.random() - 0.5) * 0.6);
				break;
			case "emerald":
				mc.theWorld.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, x, y + 0.2, z, 0.0, 0.0, 0.0);
				break;
			case "snow":
				mc.theWorld.spawnParticle(EnumParticleTypes.SNOW_SHOVEL, x, y + 0.2, z, 0.0, 0.0, 0.0);
				break;
			default:
				break;
			}
		}
	}

	/** A fully saturated color for a hue in 0..1. */
	private static int hue(float h) {
		float x = (h - (float) Math.floor(h)) * 6f;
		int i = (int) x;
		float f = x - i;
		float q = 1f - f;
		float r, g, b;
		switch (i % 6) {
		case 0: r = 1; g = f; b = 0; break;
		case 1: r = q; g = 1; b = 0; break;
		case 2: r = 0; g = 1; b = f; break;
		case 3: r = 0; g = q; b = 1; break;
		case 4: r = f; g = 0; b = 1; break;
		default: r = 1; g = 0; b = q; break;
		}
		return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
	}
}
