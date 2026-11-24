package net.plebmc.client.items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The custom item rules from resource packs, in the 1.14+ format: an item
 * model ("assets/minecraft/models/item/diamond_hoe.json") with
 * {@code "overrides": [{"predicate": {"custom_model_data": 5}, "model": "..."}]}.
 * Older packs that use {@code "damage"} / {@code "damaged"} predicates work
 * too. Every model an override points at is converted for 1.8 once, when the
 * packs load ({@link ModelConverter}).
 */
public final class ItemRules {

	public static final int MAX_MODELS = 4096;

	public static final class Override {
		final Map<String, Double> predicate;
		final String model;

		Override(Map<String, Double> predicate, String model) {
			this.predicate = predicate;
			this.model = model;
		}
	}

	/** Base item (modern id path) to its overrides, in file order. */
	private final Map<String, List<Override>> rules = new HashMap<>();
	/** Converted models by reference. */
	private final Map<String, ModelConverter.Result> models = new LinkedHashMap<>();
	/** Exact custom_model_data value to the models using it (for items Via renamed). */
	private final Map<Long, Set<String>> byCmd = new HashMap<>();
	private final List<String> warnings = new ArrayList<>();
	private Map<String, String> spriteFiles = Collections.emptyMap();
	private List<PackFiles> packsHigh = Collections.emptyList();

	public static final ItemRules EMPTY = new ItemRules();

	private ItemRules() {
	}

	/** Reads the rules of {@code packsLowFirst} (a later pack's file replaces an earlier one's). */
	@SuppressWarnings("unchecked")
	public static ItemRules build(List<PackFiles> packsLowFirst) {
		ItemRules r = new ItemRules();
		Map<String, List<Override>> found = new HashMap<>();
		for (PackFiles p : packsLowFirst) {
			List<String> names;
			try {
				names = p.list("assets/minecraft/models/item");
			} catch (RuntimeException e) {
				continue;
			}
			for (String n : names) {
				if (!n.endsWith(".json")) {
					continue;
				}
				String base = n.substring(0, n.length() - 5);
				Object o = ModelConverter.parse(p.read("assets/minecraft/models/item/" + n));
				if (!(o instanceof Map)) {
					continue;
				}
				Object ov = ((Map<String, Object>) o).get("overrides");
				List<Override> list = new ArrayList<>();
				if (ov instanceof List) {
					for (Object e : (List<Object>) ov) {
						Override x = override(e);
						if (x != null) {
							list.add(x);
						}
					}
				}
				if (list.isEmpty()) {
					found.remove(base); // this pack's plain model replaces a lower pack's rules
				} else {
					found.put(base, list);
				}
			}
		}
		List<PackFiles> high = new ArrayList<>(packsLowFirst);
		Collections.reverse(high);
		ModelConverter conv = new ModelConverter(high);
		for (Map.Entry<String, List<Override>> e : found.entrySet()) {
			String base = e.getKey();
			for (Override o : e.getValue()) {
				if (isOwnModel(base, o.model) || r.models.containsKey(o.model)) {
					continue;
				}
				if (r.models.size() >= MAX_MODELS) {
					r.warnings.add("too many custom models, the rest are ignored");
					break;
				}
				ModelConverter.Result res = conv.convert(o.model);
				r.models.put(o.model, res);
			}
			r.rules.put(base, e.getValue());
			for (Override o : e.getValue()) {
				Double cmd = o.predicate.get("custom_model_data");
				if (cmd != null && o.predicate.size() == 1 && r.models.get(o.model) != null) {
					long k = Math.round(cmd);
					Set<String> s = r.byCmd.get(k);
					if (s == null) {
						r.byCmd.put(k, s = new LinkedHashSet<>());
					}
					s.add(o.model);
				}
			}
		}
		r.warnings.addAll(conv.warnings());
		r.spriteFiles = conv.spriteFiles();
		r.packsHigh = high;
		return r;
	}

	@SuppressWarnings("unchecked")
	private static Override override(Object e) {
		if (!(e instanceof Map)) {
			return null;
		}
		Map<String, Object> m = (Map<String, Object>) e;
		if (!(m.get("model") instanceof String) || !(m.get("predicate") instanceof Map)) {
			return null;
		}
		Map<String, Double> pred = new HashMap<>();
		for (Map.Entry<String, Object> p : ((Map<String, Object>) m.get("predicate")).entrySet()) {
			if (p.getValue() instanceof Number) {
				String k = p.getKey();
				int c = k.indexOf(':');
				if (c >= 0) {
					k = k.substring(c + 1);
				}
				pred.put(k, ((Number) p.getValue()).doubleValue());
			}
		}
		return new Override(pred, (String) m.get("model"));
	}

	/** The override just points back at the item's normal model. */
	static boolean isOwnModel(String base, String ref) {
		String[] p = ModelConverter.split(ref);
		return "minecraft".equals(p[0]) && (("item/" + base).equals(p[1]) || ("items/" + base).equals(p[1]));
	}

	/**
	 * A texture file for a sprite these rules use: {@code spritePath} as in
	 * {@link ModelConverter#textureSprite}, {@code suffix} ".png" or ".png.mcmeta".
	 */
	public byte[] spriteFile(String spritePath, String suffix) {
		String f = spriteFiles.get(spritePath);
		if (f == null) {
			return null;
		}
		f = f.substring(0, f.length() - 4) + suffix;
		for (PackFiles p : packsHigh) {
			byte[] b = p.read(f);
			if (b != null) {
				return b;
			}
		}
		return null;
	}

	/** The converted model with this id, or null. */
	public ModelConverter.Result model(String id) {
		for (ModelConverter.Result r : models.values()) {
			if (r != null && r.id.equals(id)) {
				return r;
			}
		}
		return null;
	}

	public boolean isEmpty() {
		return rules.isEmpty();
	}

	public boolean hasRules(String base) {
		return rules.containsKey(base);
	}

	public Collection<ModelConverter.Result> models() {
		List<ModelConverter.Result> out = new ArrayList<>();
		for (ModelConverter.Result r : models.values()) {
			if (r != null) {
				out.add(r);
			}
		}
		return out;
	}

	public int ruleCount() {
		return rules.size();
	}

	public List<String> warnings() {
		return warnings;
	}

	/**
	 * The converted model for an item, or null for its normal look.
	 *
	 * @param bases      the item's possible modern ids, most likely first
	 * @param cmd        CustomModelData (0 when the item has none)
	 * @param hasCmd     the item had a CustomModelData value
	 * @param plainName  display name without color codes, or null
	 */
	public ModelConverter.Result match(List<String> bases, long cmd, boolean hasCmd, int damage, int maxDamage,
			boolean unbreakable, String plainName) {
		boolean anyRules = false;
		for (String base : bases) {
			List<Override> list = rules.get(base);
			if (list == null) {
				continue;
			}
			anyRules = true;
			String hit = null;
			for (Override o : list) {
				if (matches(o.predicate, cmd, damage, maxDamage, unbreakable)) {
					hit = o.model; // the last matching override wins, like vanilla
				}
			}
			if (hit != null) {
				return isOwnModel(base, hit) ? null : models.get(hit);
			}
		}
		if (hasCmd && cmd != 0) {
			// the base item was swapped on the way (Via): a value only one model uses is still safe
			Set<String> s = byCmd.get(cmd);
			if (s != null && s.size() == 1) {
				return models.get(s.iterator().next());
			}
		}
		if (anyRules && !hasCmd && plainName != null) {
			String want = norm(plainName);
			if (want.length() >= 3) {
				for (String base : bases) {
					List<Override> list = rules.get(base);
					if (list == null) {
						continue;
					}
					for (Override o : list) {
						String[] p = ModelConverter.split(o.model);
						String last = p[1].substring(p[1].lastIndexOf('/') + 1);
						if (want.equals(norm(last)) && models.get(o.model) != null) {
							return models.get(o.model);
						}
					}
				}
			}
		}
		return null;
	}

	static boolean matches(Map<String, Double> pred, long cmd, int damage, int maxDamage, boolean unbreakable) {
		for (Map.Entry<String, Double> p : pred.entrySet()) {
			double want = p.getValue();
			double have;
			switch (p.getKey()) {
			case "custom_model_data":
				have = cmd;
				break;
			case "damage":
				have = maxDamage <= 0 ? 0 : Math.max(0, Math.min(1, damage / (double) maxDamage));
				break;
			case "damaged":
				have = maxDamage > 0 && !unbreakable && damage > 0 ? 1 : 0;
				break;
			default:
				have = 0; // pulling, cast, blocking, charged...: never true on 1.8
			}
			if (have < want) {
				return false;
			}
		}
		return true;
	}

	/** Lowercase letters and digits only ("AK-47" and "ak47" are equal). */
	public static String norm(String s) {
		StringBuilder sb = new StringBuilder();
		String t = ItemNbtLog.strip(s).toLowerCase(Locale.ROOT);
		for (int i = 0; i < t.length(); i++) {
			char c = t.charAt(i);
			if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
				sb.append(c);
			}
		}
		return sb.toString();
	}
}
