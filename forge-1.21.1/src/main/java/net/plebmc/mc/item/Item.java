package net.plebmc.mc.item;

/** 1.8's Item over 1.21.1's (identity matters: one wrapper per item). */
public class Item {
	public final net.minecraft.world.item.Item real;
	private static final java.util.Map<net.minecraft.world.item.Item, Item> ALL = new java.util.HashMap<>();

	Item(net.minecraft.world.item.Item i) {
		real = i;
	}

	public static Item of(net.minecraft.world.item.Item i) {
		return i == null ? null : ALL.computeIfAbsent(i, Item::new);
	}

	public String getRegistryName() {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(real).toString();
	}

	public String getUnlocalizedName() {
		return real.getDescriptionId();
	}
}
