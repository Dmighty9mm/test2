package net.plebmc.client.hud;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.plebmc.mc.item.ItemStack;

/**
 * Apollo cooldown icons name items the way a modern server does
 * ("ENDER_PEARL", "minecraft:golden_axe"). On 1.21.1 those are the game's
 * own ids; a hint from the cooldown's name ("Grapple", "Quake Axe") is the
 * fallback when the icon is missing.
 */
final class ItemIcons {

	private ItemIcons() {
	}

	private static final Map<String, ItemStack> cache = new HashMap<>();

	/** 1.8 ids that a server written for 1.8 may still send. */
	private static final String[][] OLD = { { "fireworks", "firework_rocket" }, { "firework_charge", "firework_star" },
			{ "skull", "skeleton_skull" }, { "web", "cobweb" }, { "planks", "oak_planks" }, { "fence", "oak_fence" },
			{ "slime", "slime_block" }, { "red_flower", "poppy" }, { "yellow_flower", "dandelion" },
			{ "speckled_melon", "glistering_melon_slice" }, { "reeds", "sugar_cane" }, { "wooden_door", "oak_door" },
			{ "boat", "oak_boat" }, { "golden_rail", "powered_rail" }, { "dye", "red_dye" } };

	static ItemStack forCooldown(String icon, String name) {
		ItemStack s = forName(icon);
		if (s == null) {
			s = guess(name);
		}
		return s;
	}

	static ItemStack forName(String raw) {
		if (raw == null || raw.isEmpty() || raw.startsWith("#") && raw.length() == 1) {
			return null;
		}
		if (cache.containsKey(raw)) {
			return cache.get(raw);
		}
		ItemStack s;
		try {
			s = resolve(raw);
		} catch (Throwable t) {
			s = null;
		}
		if (cache.size() < 256) {
			cache.put(raw, s);
		}
		return s;
	}

	private static ItemStack stack(Item it) {
		return it == null || it == Items.AIR ? null : new ItemStack(new net.minecraft.world.item.ItemStack(it));
	}

	private static ItemStack resolve(String raw) {
		if (raw.startsWith("#")) {
			return stack(BuiltInRegistries.ITEM.byId(Integer.parseInt(raw.substring(1))));
		}
		String id = raw.trim().toLowerCase();
		String ns = "minecraft";
		int colon = id.indexOf(':');
		if (colon >= 0) {
			if (id.indexOf('/') >= 0 || id.endsWith(".png")) {
				id = id.substring(id.lastIndexOf('/') + 1);
				if (id.endsWith(".png")) {
					id = id.substring(0, id.length() - 4);
				}
			} else {
				ns = id.substring(0, colon);
				id = id.substring(colon + 1);
			}
		}
		for (String[] o : OLD) {
			if (o[0].equals(id)) {
				id = o[1];
				break;
			}
		}
		ResourceLocation rl = ResourceLocation.tryBuild(ns, id);
		Item it = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
		if (it == null && id.endsWith("_axe")) {
			it = Items.IRON_AXE;
		} else if (it == null && id.endsWith("_sword")) {
			it = Items.IRON_SWORD;
		}
		return stack(it);
	}

	private static ItemStack guess(String name) {
		String n = name == null ? "" : name.toLowerCase();
		if (n.contains("grapple") || n.contains("hook")) {
			return stack(Items.FISHING_ROD);
		}
		if (n.contains("axe")) {
			return stack(Items.GOLDEN_AXE);
		}
		if (n.contains("bow")) {
			return stack(Items.BOW);
		}
		if (n.contains("pearl") || n.contains("blink") || n.contains("warp")) {
			return stack(Items.ENDER_PEARL);
		}
		if (n.contains("tnt") || n.contains("bomb") || n.contains("grenade")) {
			return stack(Items.TNT);
		}
		if (n.contains("radar")) {
			return stack(Items.COMPASS);
		}
		if (n.contains("heal") || n.contains("bonfire") || n.contains("vitality") || n.contains("mending")) {
			return stack(Items.GOLDEN_APPLE);
		}
		if (n.contains("speed")) {
			return stack(Items.SUGAR);
		}
		return null;
	}
}
