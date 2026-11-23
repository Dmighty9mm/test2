package net.plebmc.client.hud;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Apollo cooldown icons name items the way a modern server does
 * ("ENDER_PEARL", "minecraft:golden_axe", "FIREWORK_ROCKET"). This turns
 * those names into a 1.8 ItemStack, including the items that were renamed
 * after 1.8, and falls back to a hint from the cooldown's own name
 * ("Grapple", "Quake Axe", "Phase Bow") when the icon is missing.
 */
final class ItemIcons {

	private ItemIcons() {
	}

	private static final Map<String, ItemStack> cache = new HashMap<>();

	/** Modern id to 1.8 {id, meta}. Only names that changed are listed. */
	private static final String[][] RENAMED = { { "golden_axe", "golden_axe" }, { "golden_sword", "golden_sword" },
			{ "golden_pickaxe", "golden_pickaxe" }, { "golden_shovel", "golden_shovel" }, { "golden_hoe", "golden_hoe" },
			{ "wooden_axe", "wooden_axe" }, { "wooden_sword", "wooden_sword" }, { "firework_rocket", "fireworks" },
			{ "firework_star", "firework_charge" }, { "experience_bottle", "experience_bottle" },
			{ "ender_eye", "ender_eye" }, { "fire_charge", "fire_charge" }, { "clock", "clock" },
			{ "snowball", "snowball" }, { "spyglass", "spider_eye" }, { "trident", "iron_sword" },
			{ "crossbow", "bow" }, { "totem_of_undying", "golden_apple" }, { "shield", "iron_chestplate" },
			{ "elytra", "feather" }, { "end_crystal", "nether_star" }, { "lead", "lead" }, { "rabbit_foot", "rabbit_foot" },
			{ "player_head", "skull" }, { "skeleton_skull", "skull" }, { "lime_dye", "dye" }, { "red_dye", "dye" },
			{ "blaze_powder", "blaze_powder" }, { "heart_of_the_sea", "prismarine_crystals" },
			{ "netherite_sword", "diamond_sword" }, { "netherite_axe", "diamond_axe" }, { "honey_bottle", "potion" },
			{ "splash_potion", "potion" }, { "lingering_potion", "potion" }, { "cobweb", "web" },
			{ "oak_planks", "planks" }, { "oak_fence", "fence" }, { "campfire", "torch" }, { "soul_torch", "torch" },
			{ "slime_block", "slime" }, { "iron_bars", "iron_bars" }, { "gunpowder", "gunpowder" },
			{ "tnt", "tnt" }, { "fishing_rod", "fishing_rod" }, { "carrot_on_a_stick", "carrot_on_a_stick" },
			{ "nether_star", "nether_star" }, { "compass", "compass" }, { "recovery_compass", "compass" },
			{ "wither_rose", "red_flower" }, { "glowstone_dust", "glowstone_dust" }, { "milk_bucket", "milk_bucket" },
			{ "sugar", "sugar" }, { "ender_pearl", "ender_pearl" }, { "golden_apple", "golden_apple" },
			{ "enchanted_golden_apple", "golden_apple" } };

	/** The icon for a cooldown: its icon name if it has one, else a guess from its display name. */
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
		ItemStack s = null;
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

	private static ItemStack resolve(String raw) {
		if (raw.startsWith("#")) {
			Item it = Item.getItemById(Integer.parseInt(raw.substring(1)));
			return it == null ? null : new ItemStack(it);
		}
		String id = raw.trim().toLowerCase();
		int colon = id.indexOf(':');
		if (colon >= 0) {
			if (id.indexOf('/') >= 0 || id.endsWith(".png")) {
				// a texture path such as "minecraft:textures/items/ender_pearl.png"
				id = id.substring(id.lastIndexOf('/') + 1);
				if (id.endsWith(".png")) {
					id = id.substring(0, id.length() - 4);
				}
			} else {
				id = id.substring(colon + 1);
			}
		}
		for (int i = 0; i < RENAMED.length; i++) {
			if (RENAMED[i][0].equals(id)) {
				id = RENAMED[i][1];
				break;
			}
		}
		Item it = Item.getByNameOrId(id);
		if (it == null) {
			Block b = Block.getBlockFromName(id);
			if (b != null) {
				it = Item.getItemFromBlock(b);
			}
		}
		if (it == null && id.endsWith("_axe")) {
			it = Items.iron_axe;
		} else if (it == null && id.endsWith("_sword")) {
			it = Items.iron_sword;
		}
		return it == null ? null : new ItemStack(it);
	}

	private static ItemStack guess(String name) {
		String n = name == null ? "" : name.toLowerCase();
		if (n.contains("grapple") || n.contains("hook")) {
			return new ItemStack(Items.fishing_rod);
		}
		if (n.contains("axe")) {
			return new ItemStack(Items.golden_axe);
		}
		if (n.contains("bow")) {
			return new ItemStack(Items.bow);
		}
		if (n.contains("pearl") || n.contains("blink") || n.contains("warp")) {
			return new ItemStack(Items.ender_pearl);
		}
		if (n.contains("tnt") || n.contains("bomb") || n.contains("grenade")) {
			return new ItemStack(Item.getItemFromBlock(net.minecraft.init.Blocks.tnt));
		}
		if (n.contains("radar")) {
			return new ItemStack(Items.compass);
		}
		if (n.contains("heal") || n.contains("bonfire") || n.contains("vitality") || n.contains("mending")) {
			return new ItemStack(Items.golden_apple);
		}
		if (n.contains("speed")) {
			return new ItemStack(Items.sugar);
		}
		return null;
	}
}
