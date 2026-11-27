package net.plebmc.client.items;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.plebmc.client.apollo.proto.Json;

/**
 * Turns a 1.9+ item model (from a resource pack) into one model JSON that
 * 1.8 can load: the parent chain flattened (1.8 can't have elements and a
 * parent together), textures moved into PlebClient's own namespace so they
 * never replace a vanilla texture, element rotations snapped to the angles
 * 1.8 allows, and display transforms converted ({@link ItemTransforms}).
 * Returns null for anything it can't convert, so that item keeps its normal
 * look.
 */
public final class ModelConverter {

	/** Namespace that PlebClient serves converted models and pack textures from. */
	public static final String DOMAIN = "plebitems";
	/** Largest texture accepted (bigger ones could fail the whole texture atlas). */
	public static final int MAX_TEXTURE_W = 512, MAX_TEXTURE_H = 8192;
	private static final int MAX_PARENTS = 16;
	private static final int MAX_ELEMENTS = 1024;
	private static final String[] FACES = { "down", "up", "north", "south", "west", "east" };
	private static final double[] ANGLES = { -45, -22.5, 0, 22.5, 45 };

	public static final class Result {
		/** Model id inside {@link #DOMAIN} ("m/qualityarmory/item/ak47"). */
		public final String id;
		public final String json;
		/** Sprite names used ("plebitems:p/qualityarmory/item/ak47" or "minecraft:items/stick"). */
		public final Set<String> sprites;

		Result(String id, String json, Set<String> sprites) {
			this.id = id;
			this.json = json;
			this.sprites = sprites;
		}
	}

	/** Packs to read from, highest priority first. */
	private final List<PackFiles> packs;
	private final Map<String, Object> jsonCache = new LinkedHashMap<>();
	private final List<String> warnings = new ArrayList<>();
	/** Sprite path ("p/qa/item/ak47") to its file in the packs ("assets/qa/textures/item/ak47.png"). */
	private final Map<String, String> spriteFiles = new LinkedHashMap<>();

	public ModelConverter(List<PackFiles> packsHighFirst) {
		this.packs = packsHighFirst;
	}

	public List<String> warnings() {
		return warnings;
	}

	public Map<String, String> spriteFiles() {
		return spriteFiles;
	}

	/** "ns:path" with "minecraft" as the default namespace. */
	public static String[] split(String ref) {
		if (ref == null) {
			return null;
		}
		ref = ref.trim();
		int c = ref.indexOf(':');
		String ns = c < 0 ? "minecraft" : ref.substring(0, c);
		String path = c < 0 ? ref : ref.substring(c + 1);
		if (ns.isEmpty()) {
			ns = "minecraft";
		}
		return new String[] { ns.toLowerCase(Locale.ROOT), path };
	}

	/** A safe resource path piece. */
	public static String clean(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char ch = Character.toLowerCase(s.charAt(i));
			sb.append((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '/' || ch == '_' || ch == '-'
					|| ch == '.' ? ch : '_');
		}
		return sb.toString();
	}

	public static String modelId(String ref) {
		String[] p = split(ref);
		return "m/" + clean(p[0]) + "/" + clean(p[1]);
	}

	byte[] readFile(String path) {
		for (PackFiles p : packs) {
			try {
				byte[] b = p.read(path);
				if (b != null) {
					return b;
				}
			} catch (RuntimeException e) {
				// a broken pack file is the same as a missing one
			}
		}
		return null;
	}

	Object readJson(String path) {
		if (jsonCache.containsKey(path)) {
			return jsonCache.get(path);
		}
		Object o = null;
		byte[] b = readFile(path);
		if (b != null) {
			o = parse(b);
		}
		jsonCache.put(path, o);
		return o;
	}

	/** Lenient JSON from bytes (BOM and comments tolerated), or null. */
	public static Object parse(byte[] b) {
		try {
			String s = new String(b, StandardCharsets.UTF_8);
			if (!s.isEmpty() && s.charAt(0) == '﻿') {
				s = s.substring(1);
			}
			return Json.parse(stripComments(s));
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String stripComments(String s) {
		if (s.indexOf("//") < 0 && s.indexOf("/*") < 0) {
			return s;
		}
		StringBuilder sb = new StringBuilder(s.length());
		boolean str = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (str) {
				sb.append(c);
				if (c == '\\' && i + 1 < s.length()) {
					sb.append(s.charAt(++i));
				} else if (c == '"') {
					str = false;
				}
			} else if (c == '"') {
				str = true;
				sb.append(c);
			} else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
				while (i < s.length() && s.charAt(i) != '\n') {
					i++;
				}
				sb.append('\n');
			} else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
				int e = s.indexOf("*/", i + 2);
				i = e < 0 ? s.length() : e + 1;
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	/** Which vanilla parent ends a chain: "generated", "handheld", "block", or null. */
	static String vanillaKind(String ref) {
		String[] p = split(ref);
		if (!"minecraft".equals(p[0])) {
			return null;
		}
		switch (p[1]) {
		case "item/generated":
		case "builtin/generated":
			return "generated";
		case "item/handheld":
		case "item/handheld_rod":
		case "item/handheld_mace":
			return "handheld";
		case "block/block":
		case "block/thin_block":
			return "block";
		default:
			return null;
		}
	}

	/** Converts the model {@code ref}, or returns null (the item then keeps its normal model). */
	@SuppressWarnings("unchecked")
	public Result convert(String ref) {
		try {
			List<Map<String, Object>> chain = new ArrayList<>();
			String kind = null;
			String cur = ref;
			Set<String> seen = new HashSet<>();
			while (cur != null && chain.size() < MAX_PARENTS) {
				String k = vanillaKind(cur);
				if (k != null) {
					kind = k;
					break;
				}
				if (!seen.add(cur)) {
					warnings.add(ref + ": parent loop");
					return null;
				}
				String[] p = split(cur);
				if ("builtin".equals(p[1].split("/")[0]) && "minecraft".equals(p[0])) {
					warnings.add(ref + ": " + cur + " can't be drawn on 1.8");
					return null;
				}
				Object o = readJson("assets/" + p[0] + "/models/" + p[1] + ".json");
				if (!(o instanceof Map)) {
					if (chain.isEmpty()) {
						warnings.add(ref + ": model not found");
						return null;
					}
					break; // an unknown vanilla parent: use what the chain has
				}
				Map<String, Object> m = (Map<String, Object>) o;
				chain.add(m);
				Object parent = m.get("parent");
				cur = parent instanceof String ? (String) parent : null;
			}
			// merge: textures (parents first, children override), nearest elements, nearest display per slot
			Map<String, String> textures = new LinkedHashMap<>();
			List<Object> elements = null;
			Map<String, Object> display = new LinkedHashMap<>();
			Boolean ao = null;
			for (int i = chain.size() - 1; i >= 0; i--) {
				Map<String, Object> m = chain.get(i);
				Object t = m.get("textures");
				if (t instanceof Map) {
					for (Map.Entry<String, Object> e : ((Map<String, Object>) t).entrySet()) {
						if (e.getValue() instanceof String) {
							textures.put(e.getKey(), (String) e.getValue());
						}
					}
				}
				if (m.get("elements") instanceof List) {
					elements = (List<Object>) m.get("elements");
				}
				if (m.get("display") instanceof Map) {
					display.putAll((Map<String, Object>) m.get("display"));
				}
				if (m.get("ambientocclusion") instanceof Boolean) {
					ao = (Boolean) m.get("ambientocclusion");
				}
			}
			boolean generated = "generated".equals(kind) || "handheld".equals(kind);
			if (generated) {
				elements = null; // 1.9+: a generated parent always wins over elements
			} else if (elements == null || elements.isEmpty()) {
				warnings.add(ref + ": no elements and no item/generated parent");
				return null;
			}
			Set<String> sprites = new HashSet<>();
			Map<String, Object> outTex = new LinkedHashMap<>();
			for (Map.Entry<String, String> e : textures.entrySet()) {
				String v = e.getValue();
				if (v.startsWith("#")) {
					outTex.put(e.getKey(), v);
					continue;
				}
				String sprite = sprite(v);
				if (sprite == null) {
					warnings.add(ref + ": texture " + v + " missing or too big");
					return null;
				}
				outTex.put(e.getKey(), sprite);
				sprites.add(sprite);
			}
			Map<String, Object> out = new LinkedHashMap<>();
			if (generated) {
				out.put("parent", "builtin/generated");
				if (!outTex.containsKey("layer0")) {
					warnings.add(ref + ": generated model without layer0");
					return null;
				}
			} else {
				List<Object> els = new ArrayList<>();
				for (Object el : elements) {
					Map<String, Object> c = element(el, textures);
					if (c != null) {
						els.add(c);
					}
					if (els.size() >= MAX_ELEMENTS) {
						break;
					}
				}
				if (els.isEmpty()) {
					warnings.add(ref + ": no usable elements");
					return null;
				}
				out.put("elements", els);
				if (ao != null) {
					out.put("ambientocclusion", ao);
				}
			}
			if (!outTex.containsKey("particle")) {
				Object first = outTex.containsKey("layer0") ? outTex.get("layer0") : firstSprite(outTex);
				if (first != null) {
					outTex.put("particle", first);
				}
			}
			out.put("textures", outTex);
			Map<String, Object> disp = new LinkedHashMap<>();
			Map<String, ItemTransforms.T> t18s = new LinkedHashMap<>();
			for (String slot : ItemTransforms.SLOTS_18) {
				ItemTransforms.T t18;
				Object legacy = "thirdperson".equals(slot) || "firstperson".equals(slot) ? display.get(slot) : null;
				Object modern = display.get(modernKey(slot));
				if (modern == null && legacy != null) {
					t18 = ItemTransforms.read(legacy); // already 1.8 style
				} else {
					ItemTransforms.T t19 = modern != null ? ItemTransforms.read(modern)
							: ItemTransforms.default19(slot, kind);
					t18 = ItemTransforms.convert(slot, t19, !generated);
				}
				t18s.put(slot, t18 == null ? ItemTransforms.IDENTITY : t18);
			}
			if (!generated) {
				refit((List<Object>) out.get("elements"), t18s);
			}
			for (Map.Entry<String, ItemTransforms.T> e : t18s.entrySet()) {
				if (!e.getValue().isIdentity()) {
					disp.put(e.getKey(), ItemTransforms.write(e.getValue()));
				}
			}
			if (!disp.isEmpty()) {
				out.put("display", disp);
			}
			return new Result(modelId(ref), Json.write(out), sprites);
		} catch (RuntimeException e) {
			warnings.add(ref + ": " + e);
			return null;
		}
	}

	/**
	 * 1.8 refuses a display scale over 4 and a translation over 24 pixels, and
	 * small models made for 1.9+ (pistols drawn at 3x in the GUI and 7x in
	 * first person) need more. When a slot is over, the geometry is moved to
	 * the middle of the block and made bigger, and every slot's transform is
	 * changed to match, so each slot still draws exactly the same thing.
	 */
	@SuppressWarnings("unchecked")
	static void refit(List<Object> els, Map<String, ItemTransforms.T> t18s) {
		if (els == null || els.isEmpty() || !over(t18s.values())) {
			return;
		}
		double[] lo = { 1e9, 1e9, 1e9 }, hi = { -1e9, -1e9, -1e9 };
		for (Object o : els) {
			Map<String, Object> e = (Map<String, Object>) o;
			double[] f = vec3(e.get("from")), t = vec3(e.get("to"));
			for (int i = 0; i < 3; i++) {
				lo[i] = Math.min(lo[i], Math.min(f[i], t[i]));
				hi[i] = Math.max(hi[i], Math.max(f[i], t[i]));
			}
		}
		double half = 0;
		for (int i = 0; i < 3; i++) {
			half = Math.max(half, (hi[i] - lo[i]) / 2);
		}
		double need = 1;
		for (ItemTransforms.T t : t18s.values()) {
			for (double v : t.s) {
				need = Math.max(need, Math.abs(v) / 4);
			}
		}
		double k = Math.max(1, Math.min(need, half <= 0 ? 1 : 24 / half));
		double[] d = ItemTransforms.bestShift(t18s, lo, hi, k);
		for (Map.Entry<String, ItemTransforms.T> e : t18s.entrySet()) {
			e.setValue(ItemTransforms.refit(e.getValue(), d, k));
		}
		for (Object o : els) {
			Map<String, Object> e = (Map<String, Object>) o;
			double[] f = vec3(e.get("from")), t = vec3(e.get("to"));
			Object faces = e.get("faces");
			if (faces instanceof Map) {
				for (Map.Entry<String, Object> fe : ((Map<String, Object>) faces).entrySet()) {
					Map<String, Object> face = (Map<String, Object>) fe.getValue();
					if (!face.containsKey("uv")) {
						face.put("uv", autoUv(fe.getKey(), f, t)); // 1.8 works it out from the size, which changes
					}
				}
			}
			e.put("from", moved(f, d, k));
			e.put("to", moved(t, d, k));
			if (e.get("rotation") instanceof Map) {
				Map<String, Object> r = (Map<String, Object>) e.get("rotation");
				double[] origin = vec3(r.get("origin"));
				if (origin != null) {
					r.put("origin", moved(origin, d, k));
				}
			}
		}
	}

	private static boolean over(Iterable<ItemTransforms.T> ts) {
		for (ItemTransforms.T t : ts) {
			for (int i = 0; i < 3; i++) {
				if (Math.abs(t.s[i]) > 4 || Math.abs(t.t[i]) > 24) {
					return true;
				}
			}
		}
		return false;
	}

	private static List<Object> moved(double[] p, double[] d, double k) {
		double[] o = new double[3];
		for (int i = 0; i < 3; i++) {
			o[i] = Math.round((8 + k * (p[i] + d[i] - 8)) * 10000) / 10000.0;
		}
		return clampList(o, -16, 32);
	}

	/** The UV 1.8 (and 1.9+) give a face that has none. */
	static List<Object> autoUv(String side, double[] f, double[] t) {
		double[] uv;
		switch (side) {
		case "down":
			uv = new double[] { f[0], 16 - t[2], t[0], 16 - f[2] };
			break;
		case "up":
			uv = new double[] { f[0], f[2], t[0], t[2] };
			break;
		case "north":
			uv = new double[] { 16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1] };
			break;
		case "south":
			uv = new double[] { f[0], 16 - t[1], t[0], 16 - f[1] };
			break;
		case "west":
			uv = new double[] { f[2], 16 - t[1], t[2], 16 - f[1] };
			break;
		default:
			uv = new double[] { 16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1] };
		}
		List<Object> l = new ArrayList<>();
		for (double v : uv) {
			l.add(Math.max(0, Math.min(16, v)));
		}
		return l;
	}

	private static Object firstSprite(Map<String, Object> tex) {
		for (Object v : tex.values()) {
			if (v instanceof String && !((String) v).startsWith("#")) {
				return v;
			}
		}
		return null;
	}

	static String modernKey(String slot18) {
		switch (slot18) {
		case "thirdperson":
			return "thirdperson_righthand";
		case "firstperson":
			return "firstperson_righthand";
		default:
			return slot18;
		}
	}

	/**
	 * The 1.8 sprite for a texture reference: the pack's own file under
	 * PlebClient's namespace, or a 1.8 vanilla texture when the pack points at
	 * a vanilla one it doesn't include. Null when missing or too big.
	 */
	String sprite(String texRef) {
		String[] p = split(texRef);
		String path = "assets/" + p[0] + "/textures/" + p[1] + ".png";
		byte[] png = readFile(path);
		if (png != null) {
			int[] wh = pngSize(png);
			if (wh == null || wh[0] > MAX_TEXTURE_W || wh[1] > MAX_TEXTURE_H || wh[0] < 1) {
				return null;
			}
			String sp = textureSprite(p[0], p[1]);
			spriteFiles.put(sp, path);
			return DOMAIN + ":" + sp;
		}
		if ("minecraft".equals(p[0])) {
			String v = p[1];
			if (v.startsWith("item/")) {
				return "minecraft:items/" + VanillaNames.texture18(v.substring(5));
			}
			if (v.startsWith("block/")) {
				return "minecraft:blocks/" + v.substring(6);
			}
			if (v.startsWith("items/") || v.startsWith("blocks/")) {
				return "minecraft:" + v;
			}
		}
		return null;
	}

	/** Sprite path (inside {@link #DOMAIN}) of a pack texture. */
	public static String textureSprite(String ns, String path) {
		return "p/" + clean(ns) + "/" + clean(path);
	}

	/** Width and height from a PNG header, or null. */
	public static int[] pngSize(byte[] b) {
		if (b == null || b.length < 24 || (b[0] & 0xFF) != 0x89 || b[1] != 'P' || b[2] != 'N' || b[3] != 'G') {
			return null;
		}
		int w = ((b[16] & 0xFF) << 24) | ((b[17] & 0xFF) << 16) | ((b[18] & 0xFF) << 8) | (b[19] & 0xFF);
		int h = ((b[20] & 0xFF) << 24) | ((b[21] & 0xFF) << 16) | ((b[22] & 0xFF) << 8) | (b[23] & 0xFF);
		return new int[] { w, h };
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> element(Object o, Map<String, String> textures) {
		if (!(o instanceof Map)) {
			return null;
		}
		Map<String, Object> e = (Map<String, Object>) o;
		double[] from = vec3(e.get("from")), to = vec3(e.get("to"));
		if (from == null || to == null) {
			return null;
		}
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("from", clampList(from, -16, 32));
		out.put("to", clampList(to, -16, 32));
		if (e.get("rotation") instanceof Map) {
			Map<String, Object> r = (Map<String, Object>) e.get("rotation");
			Object axis = r.get("axis");
			double[] origin = vec3(r.get("origin"));
			if (axis instanceof String && ("x".equals(axis) || "y".equals(axis) || "z".equals(axis))
					&& r.get("angle") instanceof Number) {
				Map<String, Object> ro = new LinkedHashMap<>();
				ro.put("origin", origin == null ? list(8, 8, 8) : list(origin[0], origin[1], origin[2]));
				ro.put("axis", axis);
				ro.put("angle", snapAngle(((Number) r.get("angle")).doubleValue()));
				if (Boolean.TRUE.equals(r.get("rescale"))) {
					ro.put("rescale", true);
				}
				out.put("rotation", ro);
			}
		}
		if (Boolean.FALSE.equals(e.get("shade"))) {
			out.put("shade", false);
		}
		Map<String, Object> faces = new LinkedHashMap<>();
		if (e.get("faces") instanceof Map) {
			Map<String, Object> f = (Map<String, Object>) e.get("faces");
			for (String side : FACES) {
				if (!(f.get(side) instanceof Map)) {
					continue;
				}
				Map<String, Object> face = (Map<String, Object>) f.get(side);
				if (!(face.get("texture") instanceof String)) {
					continue;
				}
				String tex = (String) face.get("texture");
				if (tex.startsWith("#") && !textures.containsKey(tex.substring(1))) {
					continue; // points at nothing: drop the face rather than draw "missing"
				}
				Map<String, Object> fo = new LinkedHashMap<>();
				if (face.get("uv") instanceof List && ((List<?>) face.get("uv")).size() == 4) {
					List<Object> uv = new ArrayList<>();
					boolean ok = true;
					for (Object u : (List<?>) face.get("uv")) {
						if (!(u instanceof Number)) {
							ok = false;
							break;
						}
						uv.add(Math.max(0, Math.min(16, ((Number) u).doubleValue())));
					}
					if (ok) {
						fo.put("uv", uv);
					}
				}
				if (!tex.startsWith("#")) {
					continue; // modern models always use "#name"; anything else can't be resolved here
				}
				fo.put("texture", tex);
				if (face.get("rotation") instanceof Number) {
					int rot = ((Number) face.get("rotation")).intValue();
					rot = ((Math.round(rot / 90f) % 4) + 4) % 4 * 90;
					if (rot != 0) {
						fo.put("rotation", rot);
					}
				}
				if (face.get("cullface") instanceof String) {
					String cf = (String) face.get("cullface");
					if ("bottom".equals(cf)) {
						cf = "down";
					}
					for (String s : FACES) {
						if (s.equals(cf)) {
							fo.put("cullface", cf);
						}
					}
				}
				if (face.get("tintindex") instanceof Number) {
					fo.put("tintindex", ((Number) face.get("tintindex")).intValue());
				}
				faces.put(side, fo);
			}
		}
		if (faces.isEmpty()) {
			return null;
		}
		out.put("faces", faces);
		return out;
	}

	static double snapAngle(double a) {
		double best = 0, bd = Double.MAX_VALUE;
		for (double c : ANGLES) {
			double d = Math.abs(c - a);
			if (d < bd) {
				bd = d;
				best = c;
			}
		}
		return best;
	}

	private static double[] vec3(Object o) {
		if (!(o instanceof List) || ((List<?>) o).size() != 3) {
			return null;
		}
		double[] v = new double[3];
		for (int i = 0; i < 3; i++) {
			Object n = ((List<?>) o).get(i);
			if (!(n instanceof Number)) {
				return null;
			}
			v[i] = ((Number) n).doubleValue();
			if (Double.isNaN(v[i]) || Double.isInfinite(v[i])) {
				return null;
			}
		}
		return v;
	}

	private static List<Object> clampList(double[] v, double lo, double hi) {
		return list(Math.max(lo, Math.min(hi, v[0])), Math.max(lo, Math.min(hi, v[1])),
				Math.max(lo, Math.min(hi, v[2])));
	}

	private static List<Object> list(double a, double b, double c) {
		List<Object> l = new ArrayList<>();
		l.add(a);
		l.add(b);
		l.add(c);
		return l;
	}
}
