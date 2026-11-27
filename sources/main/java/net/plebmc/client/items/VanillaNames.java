package net.plebmc.client.items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Item names that changed between 1.8 and 1.13+ (the "flattening"), in both
 * directions that custom items need: a 1.8 item's modern id (resource packs
 * name their override files by modern id) and a modern texture's 1.8 file.
 */
public final class VanillaNames {

	private static final Map<String, String> MODERN_OF_18 = new HashMap<>();
	private static final Map<String, String> TEXTURE_18 = new HashMap<>();

	static {
		String[] reg = { "reeds", "sugar_cane", "speckled_melon", "glistering_melon_slice", "melon", "melon_slice",
				"boat", "oak_boat", "wooden_door", "oak_door", "netherbrick", "nether_brick", "fireworks",
				"firework_rocket", "firework_charge", "firework_star", "fish", "cod", "cooked_fish", "cooked_cod",
				"bed", "red_bed", "sign", "oak_sign", "skull", "skeleton_skull", "golden_rail", "powered_rail",
				"web", "cobweb", "lit_pumpkin", "jack_o_lantern", "waterlily", "lily_pad", "snow_layer", "snow",
				"noteblock", "note_block", "brick_block", "bricks", "quartz_ore", "nether_quartz_ore",
				"mob_spawner", "spawner", "monster_egg", "infested_stone", "red_flower", "poppy", "yellow_flower",
				"dandelion", "deadbush", "dead_bush", "tallgrass", "grass", "stonebrick", "stone_bricks",
				"wooden_slab", "oak_slab", "fence", "oak_fence", "fence_gate", "oak_fence_gate", "trapdoor",
				"oak_trapdoor", "wooden_pressure_plate", "oak_pressure_plate", "wooden_button", "oak_button",
				"hardened_clay", "terracotta", "stained_hardened_clay", "white_terracotta", "carpet", "white_carpet",
				"wool", "white_wool", "planks", "oak_planks", "log", "oak_log", "leaves", "oak_leaves", "sapling",
				"oak_sapling", "stained_glass", "white_stained_glass", "stained_glass_pane",
				"white_stained_glass_pane", "slime", "slime_block", "end_bricks", "end_stone_bricks",
				"record_13", "music_disc_13", "record_cat", "music_disc_cat", "record_blocks", "music_disc_blocks",
				"record_chirp", "music_disc_chirp", "record_far", "music_disc_far", "record_mall",
				"music_disc_mall", "record_mellohi", "music_disc_mellohi", "record_stal", "music_disc_stal",
				"record_strad", "music_disc_strad", "record_ward", "music_disc_ward", "record_11", "music_disc_11",
				"record_wait", "music_disc_wait", "dye", "ink_sac", "golden_apple", "golden_apple",
				"spawn_egg", "pig_spawn_egg",
				// ViaBackwards turns a 1.14+ crossbow into a bow for 1.8 (QualityArmory guns are crossbows)
				"bow", "crossbow" };
		for (int i = 0; i + 1 < reg.length; i += 2) {
			MODERN_OF_18.put(reg[i], reg[i + 1]);
		}
		String[] tex = { "wooden_sword", "wood_sword", "wooden_shovel", "wood_shovel", "wooden_pickaxe",
				"wood_pickaxe", "wooden_axe", "wood_axe", "wooden_hoe", "wood_hoe", "golden_sword", "gold_sword",
				"golden_shovel", "gold_shovel", "golden_pickaxe", "gold_pickaxe", "golden_axe", "gold_axe",
				"golden_hoe", "gold_hoe", "golden_helmet", "gold_helmet", "golden_chestplate", "gold_chestplate",
				"golden_leggings", "gold_leggings", "golden_boots", "gold_boots", "golden_horse_armor",
				"gold_horse_armor", "golden_apple", "apple_golden", "golden_carrot", "carrot_golden", "bow",
				"bow_standby", "fishing_rod", "fishing_rod_uncast", "cod", "fish_cod_raw", "cooked_cod",
				"fish_cod_cooked", "salmon", "fish_salmon_raw", "cooked_salmon", "fish_salmon_cooked", "pufferfish",
				"fish_pufferfish_raw", "tropical_fish", "fish_clownfish_raw", "beef", "beef_raw", "cooked_beef",
				"beef_cooked", "porkchop", "porkchop_raw", "cooked_porkchop", "porkchop_cooked", "chicken",
				"chicken_raw", "cooked_chicken", "chicken_cooked", "rabbit", "rabbit_raw", "cooked_rabbit",
				"rabbit_cooked", "mutton", "mutton_raw", "cooked_mutton", "mutton_cooked", "potion",
				"potion_bottle_drinkable", "splash_potion", "potion_bottle_splash", "glass_bottle",
				"potion_bottle_empty", "map", "map_empty", "filled_map", "map_filled", "fire_charge", "fireball",
				"writable_book", "book_writable", "written_book", "book_written", "enchanted_book",
				"book_enchanted", "book", "book_normal", "melon_slice", "melon", "glistering_melon_slice",
				"melon_speckled", "sugar_cane", "reeds", "oak_door", "door_wood", "iron_door", "door_iron",
				"wheat_seeds", "seeds_wheat", "pumpkin_seeds", "seeds_pumpkin", "melon_seeds", "seeds_melon",
				"bucket", "bucket_empty", "water_bucket", "bucket_water", "lava_bucket", "bucket_lava",
				"milk_bucket", "bucket_milk", "firework_rocket", "fireworks", "firework_star", "fireworks_charge",
				"slime_ball", "slimeball", "redstone", "redstone_dust", "ink_sac", "dye_powder_black", "bone_meal",
				"dye_powder_white", "lapis_lazuli", "dye_powder_blue", "cocoa_beans", "dye_powder_brown",
				"minecart", "minecart_normal", "chest_minecart", "minecart_chest", "furnace_minecart",
				"minecart_furnace", "tnt_minecart", "minecart_tnt", "hopper_minecart", "minecart_hopper",
				"command_block_minecart", "minecart_command_block", "oak_boat", "boat", "armor_stand",
				"wooden_armorstand", "fermented_spider_eye", "spider_eye_fermented", "poisonous_potato",
				"potato_poisonous", "baked_potato", "potato_baked", "experience_bottle", "experience_bottle",
				"clock", "clock", "compass", "compass", "nether_brick", "netherbrick", "music_disc_13", "record_13",
				"music_disc_cat", "record_cat", "music_disc_blocks", "record_blocks", "music_disc_chirp",
				"record_chirp", "music_disc_far", "record_far", "music_disc_mall", "record_mall",
				"music_disc_mellohi", "record_mellohi", "music_disc_stal", "record_stal", "music_disc_strad",
				"record_strad", "music_disc_ward", "record_ward", "music_disc_11", "record_11", "music_disc_wait",
				"record_wait", "iron_nugget", "gold_nugget", "crossbow", "bow_standby" };
		for (int i = 0; i + 1 < tex.length; i += 2) {
			TEXTURE_18.put(tex[i], tex[i + 1]);
		}
	}

	private VanillaNames() {
	}

	/** The 1.8 texture file name for a modern item texture name. */
	public static String texture18(String modern) {
		String t = TEXTURE_18.get(modern);
		return t == null ? modern : t;
	}

	/** Every name a 1.8 item (registry path, e.g. "reeds") can have in a modern pack, own name first. */
	public static List<String> modernNames(String registry18) {
		List<String> out = new ArrayList<>(2);
		out.add(registry18);
		String m = MODERN_OF_18.get(registry18);
		if (m != null && !m.equals(registry18)) {
			out.add(m);
		}
		return out;
	}
}
