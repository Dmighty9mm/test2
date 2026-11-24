package net.plebmc.client.items;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Items newer than 1.8 (totems, netherite) that a server's Via plugins turn
 * into an older item for 1.8 players. ViaBackwards names such an item after
 * what it really is ("1.16 Netherite Sword") and may keep the original id in
 * its NBT; either one is enough to draw the right texture again. Textures are
 * PlebClient's own (in {@code assets/plebitems/textures/new/}).
 */
public final class NewItems {

	/** Modern id to its English name. */
	static final Map<String, String> NAMES = new LinkedHashMap<>();
	/** English name (normalized) to modern id. */
	private static final Map<String, String> BY_NAME = new LinkedHashMap<>();

	static {
		String[] n = { "totem_of_undying", "Totem of Undying", "netherite_sword", "Netherite Sword",
				"netherite_pickaxe", "Netherite Pickaxe", "netherite_axe", "Netherite Axe", "netherite_shovel",
				"Netherite Shovel", "netherite_hoe", "Netherite Hoe", "netherite_helmet", "Netherite Helmet",
				"netherite_chestplate", "Netherite Chestplate", "netherite_leggings", "Netherite Leggings",
				"netherite_boots", "Netherite Boots", "netherite_ingot", "Netherite Ingot", "netherite_scrap",
				"Netherite Scrap" };
		for (int i = 0; i + 1 < n.length; i += 2) {
			NAMES.put(n[i], n[i + 1]);
			BY_NAME.put(ItemRules.norm(n[i + 1]), n[i]);
		}
	}

	private NewItems() {
	}

	public static Iterable<String> ids() {
		return NAMES.keySet();
	}

	/** Tools are held like a sword; the rest like a flat item. */
	public static boolean isTool(String id) {
		return id.endsWith("_sword") || id.endsWith("_pickaxe") || id.endsWith("_axe") || id.endsWith("_shovel")
				|| id.endsWith("_hoe");
	}

	/** "head", "chest", "legs", "feet" for armor, else null. */
	public static String armorSlot(String id) {
		if (id.endsWith("_helmet")) {
			return "head";
		}
		if (id.endsWith("_chestplate")) {
			return "chest";
		}
		if (id.endsWith("_leggings")) {
			return "legs";
		}
		if (id.endsWith("_boots")) {
			return "feet";
		}
		return null;
	}

	/**
	 * Which new item a 1.8 item really is: from its display name (Via's "1.16
	 * Netherite Sword", without color codes) or an original id found in its NBT
	 * ("minecraft:netherite_sword"). Null for anything else.
	 */
	public static String identify(String plainName, String originalId) {
		if (originalId != null) {
			String id = originalId.toLowerCase(Locale.ROOT).replace("\"", "").trim();
			if (id.startsWith("minecraft:")) {
				id = id.substring(10);
			}
			if (NAMES.containsKey(id)) {
				return id;
			}
		}
		if (plainName == null) {
			return null;
		}
		String s = ItemNbtLog.strip(plainName).trim();
		// "1.16 Netherite Sword" / "1.11 Totem of Undying"
		int sp = s.indexOf(' ');
		if (sp > 2 && s.startsWith("1.")) {
			boolean version = true;
			for (int i = 0; i < sp; i++) {
				char c = s.charAt(i);
				if (!(c == '.' || (c >= '0' && c <= '9'))) {
					version = false;
					break;
				}
			}
			if (version) {
				return BY_NAME.get(ItemRules.norm(s.substring(sp + 1)));
			}
		}
		return null;
	}

	/** The 1.8 model JSON for a new item (a flat item with PlebClient's texture). */
	public static String modelJson(String id) {
		String tex = ModelConverter.DOMAIN + ":new/" + id;
		String disp = isTool(id)
				? "\"thirdperson\":{\"rotation\":[0,90,-35],\"translation\":[0,1.25,-3.5],\"scale\":[0.85,0.85,0.85]}"
				: "\"thirdperson\":{\"rotation\":[-90,0,0],\"translation\":[0,1,-3],\"scale\":[0.55,0.55,0.55]}";
		return "{\"parent\":\"builtin/generated\",\"textures\":{\"layer0\":\"" + tex + "\"},\"display\":{" + disp
				+ ",\"firstperson\":{\"rotation\":[0,-135,25],\"translation\":[0,4,2],\"scale\":[1.7,1.7,1.7]}}}";
	}

	/** Model id inside {@link ModelConverter#DOMAIN}. */
	public static String modelId(String id) {
		return "new/" + id;
	}
}
