package net.plebmc.mc.entity;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 1.8's Entity, as a snapshot of a 1.21.1 entity: the fields are copied
 * from the real entity each time it is looked up (once per frame at most).
 */
public class Entity {
	public final net.minecraft.world.entity.Entity e;
	public double posX, posY, posZ, prevPosX, prevPosY, prevPosZ, lastTickPosX, lastTickPosY, lastTickPosZ;
	public double motionX, motionY, motionZ;
	public float rotationYaw, rotationPitch, prevRotationYaw, prevRotationPitch;
	public float width, height;
	public boolean onGround, isDead;
	public int ticksExisted;
	public float distanceWalkedModified, prevDistanceWalkedModified;
	public int dimension;
	public net.plebmc.mc.world.World worldObj;

	protected Entity(net.minecraft.world.entity.Entity e) {
		this.e = e;
	}

	private static final Map<net.minecraft.world.entity.Entity, Entity> CACHE = new WeakHashMap<>();
	private static long frame;
	private static final Map<Entity, Long> SYNCED = new WeakHashMap<>();

	/** Bumps the frame so wrappers refresh their fields on next lookup. */
	public static void nextFrame() {
		frame++;
	}

	@SuppressWarnings("unchecked")
	public static <T extends Entity> T wrap(net.minecraft.world.entity.Entity e) {
		if (e == null) {
			return null;
		}
		Entity w = CACHE.get(e);
		if (w == null) {
			if (e instanceof net.minecraft.client.player.LocalPlayer) {
				w = new net.plebmc.mc.client.entity.EntityPlayerSP((net.minecraft.client.player.LocalPlayer) e);
			} else if (e instanceof net.minecraft.client.player.AbstractClientPlayer) {
				w = new net.plebmc.mc.client.entity.AbstractClientPlayer((net.minecraft.client.player.AbstractClientPlayer) e);
			} else if (e instanceof net.minecraft.world.entity.player.Player) {
				w = new net.plebmc.mc.entity.player.EntityPlayer((net.minecraft.world.entity.player.Player) e);
			} else if (e instanceof net.minecraft.world.entity.decoration.ArmorStand) {
				w = new net.plebmc.mc.entity.item.EntityArmorStand((net.minecraft.world.entity.decoration.ArmorStand) e);
			} else if (e instanceof net.minecraft.world.entity.item.PrimedTnt) {
				w = new net.plebmc.mc.entity.item.EntityTNTPrimed((net.minecraft.world.entity.item.PrimedTnt) e);
			} else if (e instanceof net.minecraft.world.entity.LivingEntity) {
				w = new EntityLivingBase((net.minecraft.world.entity.LivingEntity) e);
			} else {
				w = new Entity(e);
			}
			CACHE.put(e, w);
		}
		Long at = SYNCED.get(w);
		if (at == null || at != frame) {
			SYNCED.put(w, frame);
			w.sync();
		}
		return (T) w;
	}

	protected void sync() {
		posX = e.getX();
		posY = e.getY();
		posZ = e.getZ();
		prevPosX = e.xo;
		prevPosY = e.yo;
		prevPosZ = e.zo;
		lastTickPosX = e.xOld;
		lastTickPosY = e.yOld;
		lastTickPosZ = e.zOld;
		var m = e.getDeltaMovement();
		motionX = m.x;
		motionY = m.y;
		motionZ = m.z;
		rotationYaw = e.getYRot();
		rotationPitch = e.getXRot();
		prevRotationYaw = e.yRotO;
		prevRotationPitch = e.xRotO;
		width = e.getBbWidth();
		height = e.getBbHeight();
		onGround = e.onGround();
		isDead = e.isRemoved();
		ticksExisted = e.tickCount;
		distanceWalkedModified = e.walkDist;
		prevDistanceWalkedModified = e.walkDistO;
		var lvl = e.level();
		dimension = lvl == null ? 0 : lvl.dimension() == net.minecraft.world.level.Level.NETHER ? -1
				: lvl.dimension() == net.minecraft.world.level.Level.END ? 1 : 0;
		worldObj = net.plebmc.mc.world.World.current();
	}

	public int getEntityId() {
		return e.getId();
	}

	public UUID getUniqueID() {
		return e.getUUID();
	}

	public String getName() {
		return e.getName().getString();
	}

	public float getEyeHeight() {
		return e.getEyeHeight();
	}

	public boolean isInvisible() {
		return e.isInvisible();
	}

	public boolean isSneaking() {
		return e.isShiftKeyDown();
	}

	public boolean isSprinting() {
		return e.isSprinting();
	}

	public boolean isBurning() {
		return e.isOnFire();
	}

	public boolean isRiding() {
		return e.isPassenger();
	}

	public boolean hasCustomName() {
		return e.hasCustomName();
	}

	public String getCustomNameTag() {
		return e.getCustomName() == null ? "" : e.getCustomName().getString();
	}

	public boolean getAlwaysRenderNameTag() {
		return e.isCustomNameVisible();
	}

	public double getDistanceSqToEntity(Entity o) {
		return e.distanceToSqr(o.e);
	}

	public double getDistanceSq(double x, double y, double z) {
		return e.distanceToSqr(x, y, z);
	}

	public float getDistanceToEntity(Entity o) {
		return e.distanceTo(o.e);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Entity && ((Entity) o).e == e;
	}

	@Override
	public int hashCode() {
		return System.identityHashCode(e);
	}
}
