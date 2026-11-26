package net.plebmc.mc.entity.item;

/** 1.8's EntityArmorStand. */
public class EntityArmorStand extends net.plebmc.mc.entity.EntityLivingBase {
	public final net.minecraft.world.entity.decoration.ArmorStand as;

	public EntityArmorStand(net.minecraft.world.entity.decoration.ArmorStand a) {
		super(a);
		as = a;
	}

	public boolean hasMarker() {
		return as.isMarker();
	}

	public boolean hasNoGravity() {
		return as.isNoGravity();
	}
}
