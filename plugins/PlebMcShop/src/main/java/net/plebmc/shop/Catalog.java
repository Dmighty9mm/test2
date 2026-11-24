package net.plebmc.shop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/** The items for sale, read from config.yml ("items"). */
final class Catalog {

	static final String HAT = "hat";
	static final String TRAIL = "trail";

	/** What the PlebMc client can draw. Items with another render are skipped. */
	static final List<String> HAT_RENDERS = Collections.unmodifiableList(java.util.Arrays.asList("tophat", "crown",
			"halo", "partyhat", "beanie", "horns"));
	static final List<String> TRAIL_RENDERS = Collections.unmodifiableList(java.util.Arrays.asList("flame", "hearts",
			"notes", "rainbow", "cloud", "portal", "emerald", "snow"));

	static final class Item {
		final String id;
		final String name;
		final String category;
		final String render;
		final long price;
		final String color; // "#RRGGBB" or null
		final String description;
		final String permission; // null = anyone can buy

		Item(String id, String name, String category, String render, long price, String color, String description,
				String permission) {
			this.id = id;
			this.name = name;
			this.category = category;
			this.render = render;
			this.price = price;
			this.color = color;
			this.description = description;
			this.permission = permission;
		}

		/** Not for sale: only given by a command (e.g. a Battle Pass reward). */
		boolean notForSale() {
			return price < 0;
		}

		Map<String, Object> toJson() {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", id);
			m.put("name", name);
			m.put("category", category);
			m.put("render", render);
			// older clients have no "not for sale" state: they get a price nobody can reach
			m.put("price", notForSale() ? 999_999_999L : price);
			if (notForSale()) {
				m.put("forSale", false);
			}
			if (color != null) {
				m.put("color", color);
			}
			m.put("description", description);
			return m;
		}
	}

	private final Map<String, Item> items = new LinkedHashMap<>();
	private final List<String> warnings = new ArrayList<>();

	Catalog(Cfg sec) {
		if (sec == null) {
			warnings.add("config.yml has no 'items' section, the shop is empty");
			return;
		}
		for (String id : sec.keys()) {
			Cfg s = sec.section(id);
			if (s == null) {
				continue;
			}
			String category = s.str("category", "").toLowerCase();
			String render = s.str("render", id).toLowerCase();
			if (!HAT.equals(category) && !TRAIL.equals(category)) {
				warnings.add("item " + id + ": category must be hat or trail");
				continue;
			}
			List<String> renders = HAT.equals(category) ? HAT_RENDERS : TRAIL_RENDERS;
			if (!renders.contains(render)) {
				warnings.add("item " + id + ": unknown " + category + " render '" + render + "' (use one of " + renders
						+ ")");
				continue;
			}
			String color = s.str("color", null);
			if (color != null && !color.matches("#[0-9a-fA-F]{6}")) {
				warnings.add("item " + id + ": color must look like #FFD700, ignoring it");
				color = null;
			}
			items.put(id, new Item(id, s.str("name", id), category, render, s.num("price", 0), color,
					s.str("description", ""), s.str("permission", null)));
		}
	}

	Item get(String id) {
		return id == null ? null : items.get(id);
	}

	java.util.Collection<Item> all() {
		return items.values();
	}

	List<String> warnings() {
		return warnings;
	}

	List<Object> toJson() {
		List<Object> out = new ArrayList<>();
		for (Item it : items.values()) {
			out.add(it.toJson());
		}
		return out;
	}
}
