package net.plebmc.mc.item;

/** 1.8's ItemStack over 1.21.1's (null stands for an empty stack, like 1.8). */
public class ItemStack {
	public final net.minecraft.world.item.ItemStack real;
	public int stackSize;

	public ItemStack(net.minecraft.world.item.ItemStack s) {
		real = s;
		stackSize = s.getCount();
	}

	public static ItemStack of(net.minecraft.world.item.ItemStack s) {
		return s == null || s.isEmpty() ? null : new ItemStack(s);
	}

	public Item getItem() {
		return Item.of(real.getItem());
	}

	public String getDisplayName() {
		return real.getHoverName().getString();
	}

	public int getItemDamage() {
		return real.getDamageValue();
	}

	public int getMetadata() {
		return real.getDamageValue();
	}

	public int getMaxDamage() {
		return real.getMaxDamage();
	}

	public boolean isItemStackDamageable() {
		return real.isDamageableItem();
	}

	public boolean isItemDamaged() {
		return real.isDamaged();
	}

	public boolean hasEffect() {
		return real.hasFoil();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof ItemStack && net.minecraft.world.item.ItemStack.matches(real, ((ItemStack) o).real);
	}

	@Override
	public int hashCode() {
		return real.getItem().hashCode();
	}
}
