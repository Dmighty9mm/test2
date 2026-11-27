package net.plebmc.mc.world;

import java.util.ArrayList;
import java.util.List;

import net.plebmc.mc.entity.Entity;
import net.plebmc.mc.entity.player.EntityPlayer;

/** 1.8's World over the 1.21.1 client level. */
public class World {
	public final net.minecraft.client.multiplayer.ClientLevel level;
	public final List<EntityPlayer> playerEntities = new ArrayList<>();
	private static World cur;

	private World(net.minecraft.client.multiplayer.ClientLevel l) {
		level = l;
	}

	public static World current() {
		var l = net.minecraft.client.Minecraft.getInstance().level;
		if (l == null) {
			cur = null;
			return null;
		}
		if (cur == null || cur.level != l) {
			cur = new World(l);
		}
		cur.playerEntities.clear();
		for (var p : l.players()) {
			cur.playerEntities.add(Entity.wrap(p));
		}
		return cur;
	}

	public void spawnParticle(net.plebmc.mc.util.EnumParticleTypes type, double x, double y, double z, double dx, double dy,
			double dz, int... args) {
		if (type.colored()) {
			level.addParticle(type.options(dx, dy, dz), x, y, z, 0, 0, 0);
		} else {
			level.addParticle(type.options(0, 0, 0), x, y, z, dx, dy, dz);
		}
	}

	public List<Entity> getLoadedEntityList() {
		List<Entity> out = new ArrayList<>();
		for (var e : level.entitiesForRendering()) {
			out.add(Entity.wrap(e));
		}
		return out;
	}

	public long getWorldTime() {
		return level.getDayTime();
	}

	public long getTotalWorldTime() {
		return level.getGameTime();
	}

	public boolean isRaining() {
		return level.isRaining();
	}

	public net.plebmc.mc.world.border.WorldBorder getWorldBorder() {
		return new net.plebmc.mc.world.border.WorldBorder(level.getWorldBorder());
	}

	public EntityPlayer getPlayerEntityByName(String name) {
		for (EntityPlayer p : playerEntities) {
			if (p.getName().equals(name)) {
				return p;
			}
		}
		return null;
	}

	public Entity getEntityByID(int id) {
		return Entity.wrap(level.getEntity(id));
	}
}
