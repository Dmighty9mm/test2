package net.plebmc.mc.entity.item;

/** 1.8's EntityTNTPrimed. */
public class EntityTNTPrimed extends net.plebmc.mc.entity.Entity {
	public final net.minecraft.world.entity.item.PrimedTnt tnt;
	public int fuse;

	public EntityTNTPrimed(net.minecraft.world.entity.item.PrimedTnt t) {
		super(t);
		tnt = t;
	}

	@Override
	protected void sync() {
		super.sync();
		fuse = tnt.getFuse();
	}
}
