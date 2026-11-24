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
	static final String CLOAK = "cloak";
	static final List<String> CATEGORIES = Collections.unmodifiableList(java.util.Arrays.asList(HAT, TRAIL, CLOAK));

	static boolean isCategory(String c) {
		return CATEGORIES.contains(c);
	}

	/** Cloak textures: a PNG in the standard cape layout (64x32) at any multiple, up to 4096x2048. */
	static final int MAX_TEXTURE_BYTES = 4 * 1024 * 1024;
	static final int MAX_TEXTURE_WIDTH = 4096;

	/** What the PlebMc client can draw. Items with another render are skipped. */
	static final List<String> HAT_RENDERS = Collections.unmodifiableList(java.util.Arrays.asList("tophat", "crown",
			"halo", "partyhat", "beanie", "horns"));
	/** A hat drawn from a model file (Blockbench) in the plugin folder. */
	static final String MODEL = "model";
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
		/** Cloak texture: the PNG bytes and a short content hash clients cache it by. */
		byte[] png;
		String textureHash;
		int textureWidth;
		/** Model hats: the quads the client draws, and their content hash. */
		byte[] modelBlob;
		String modelHash;

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
			if (modelHash != null) {
				m.put("model", modelHash);
			}
			if (textureHash != null) {
				m.put("texture", textureHash);
				m.put("textureWidth", textureWidth);
			}
			m.put("description", description);
			return m;
		}
	}

	private final Map<String, Item> items = new LinkedHashMap<>();
	private final List<String> warnings = new ArrayList<>();

	private final Map<String, Item> byTexture = new LinkedHashMap<>();

	Catalog(Cfg sec) {
		this(sec, null);
	}

	/** {@code folder}: where cloak texture paths are read from (the plugin's folder). */
	Catalog(Cfg sec, java.io.File folder) {
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
			String modelPath = s.str("model", null);
			String render = s.str("render", CLOAK.equals(category) ? CLOAK : modelPath != null ? MODEL : id).toLowerCase();
			if (!isCategory(category)) {
				warnings.add("item " + id + ": category must be hat, trail or cloak");
				continue;
			}
			if (modelPath != null && !HAT.equals(category)) {
				warnings.add("item " + id + ": only hats can use a model, ignoring it");
				modelPath = null;
			}
			if (modelPath != null) {
				render = MODEL;
			}
			List<String> renders = HAT.equals(category) ? HAT_RENDERS
					: TRAIL.equals(category) ? TRAIL_RENDERS : Collections.singletonList(CLOAK);
			if (!renders.contains(render) && !(MODEL.equals(render) && modelPath != null)) {
				warnings.add("item " + id + ": unknown " + category + " render '" + render + "' (use one of " + renders
						+ ")");
				continue;
			}
			String color = s.str("color", null);
			if (color != null && !color.matches("#[0-9a-fA-F]{6}")) {
				warnings.add("item " + id + ": color must look like #FFD700, ignoring it");
				color = null;
			}
			Item it = new Item(id, s.str("name", id), category, render, s.num("price", 0), color,
					s.str("description", ""), s.str("permission", null));
			String tex = s.str("texture", null);
			if (modelPath != null) {
				String problem = loadModel(it, folder, modelPath, tex, s);
				if (problem != null) {
					warnings.add("item " + id + ": " + problem + ", leaving it out of the shop");
					continue;
				}
				byTexture.put(it.textureHash, it);
				byTexture.put(it.modelHash, it);
			} else if (tex != null && CLOAK.equals(category) && folder != null) {
				String problem = loadTexture(it, folder, tex);
				if (problem != null) {
					warnings.add("item " + id + ": texture " + tex + " " + problem + ", showing a plain cloak");
				} else {
					byTexture.put(it.textureHash, it);
				}
			}
			items.put(id, it);
		}
	}

	/** Reads and checks a cloak PNG; returns what is wrong with it, or null. */
	static String loadTexture(Item it, java.io.File folder, String path) {
		if (folder == null) {
			return "can't be read (no plugin folder)";
		}
		java.io.File f = new java.io.File(folder, path);
		try {
			if (!f.getCanonicalPath().startsWith(folder.getCanonicalPath() + java.io.File.separator)) {
				return "must be inside the plugin folder";
			}
			if (!f.isFile()) {
				return "was not found at " + f.getPath();
			}
			if (f.length() > MAX_TEXTURE_BYTES) {
				return "is bigger than 4 MB";
			}
			byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
			int[] wh = pngSize(b);
			if (wh == null) {
				return "is not a PNG";
			}
			if (wh[0] != wh[1] * 2 || wh[0] % 64 != 0 || wh[0] > MAX_TEXTURE_WIDTH) {
				return "is " + wh[0] + "x" + wh[1] + ": use the cape layout at 64x32 or a multiple (128x64 ... 4096x2048)";
			}
			java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
			StringBuilder h = new StringBuilder();
			for (byte x : md.digest(b)) {
				h.append(String.format("%02x", x & 0xFF));
			}
			it.png = b;
			it.textureHash = h.substring(0, 16);
			it.textureWidth = wh[0];
			return null;
		} catch (java.io.IOException | java.security.NoSuchAlgorithmException e) {
			return "could not be read: " + e.getMessage();
		}
	}

	/**
	 * Reads a hat model (a Blockbench .bbmodel, or a Java model .json with a
	 * separate PNG) and its texture; returns what is wrong, or null.
	 */
	String loadModel(Item it, java.io.File folder, String path, String texPath, Cfg s) {
		if (folder == null) {
			return "model " + path + " can't be read (no plugin folder)";
		}
		java.io.File f = new java.io.File(folder, path);
		try {
			if (!f.getCanonicalPath().startsWith(folder.getCanonicalPath() + java.io.File.separator)) {
				return "model " + path + " must be inside the plugin folder";
			}
			if (!f.isFile()) {
				return "model was not found at " + f.getPath();
			}
			if (f.length() > HatModel.MAX_FILE_BYTES) {
				return "model " + path + " is bigger than 8 MB";
			}
			HatModel.Placement place = new HatModel.Placement();
			place.mode = s.str("position", "auto");
			place.scale = (float) s.dbl("scale", 1.0);
			List<String> off = s.list("offset");
			if (off.size() == 3) {
				try {
					place.dx = Float.parseFloat(off.get(0));
					place.dy = Float.parseFloat(off.get(1));
					place.dz = Float.parseFloat(off.get(2));
				} catch (NumberFormatException e) {
					warnings.add("item " + it.id + ": offset must be three numbers like [0, 1.5, 0]");
				}
			}
			HatModel hm;
			try {
				hm = HatModel.read(f.getName(), java.nio.file.Files.readAllBytes(f.toPath()), place);
			} catch (IllegalArgumentException e) {
				return "model " + path + " " + e.getMessage();
			}
			for (String w : hm.warnings) {
				warnings.add("item " + it.id + ": model " + path + " " + w);
			}
			byte[] png;
			if (texPath != null) {
				java.io.File tf = new java.io.File(folder, texPath);
				if (!tf.getCanonicalPath().startsWith(folder.getCanonicalPath() + java.io.File.separator)) {
					return "texture " + texPath + " must be inside the plugin folder";
				}
				if (!tf.isFile()) {
					return "texture was not found at " + tf.getPath();
				}
				png = java.nio.file.Files.readAllBytes(tf.toPath());
			} else if (hm.embeddedPng != null) {
				png = hm.embeddedPng;
			} else {
				return "model " + path + " has no texture inside it: add texture: 'hats/name.png'";
			}
			if (png.length > MAX_TEXTURE_BYTES) {
				return "texture is bigger than 4 MB";
			}
			int[] wh = pngSize(png);
			if (wh == null) {
				return "texture is not a PNG";
			}
			if (wh[0] > MAX_TEXTURE_WIDTH || wh[1] > MAX_TEXTURE_WIDTH) {
				return "texture is " + wh[0] + "x" + wh[1] + ", the biggest is 4096x4096";
			}
			it.png = png;
			it.textureHash = hash(png);
			it.textureWidth = wh[0];
			it.modelBlob = hm.blob();
			it.modelHash = hash(it.modelBlob);
			return null;
		} catch (java.io.IOException e) {
			return "model " + path + " could not be read: " + e.getMessage();
		}
	}

	static String hash(byte[] b) {
		try {
			java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
			StringBuilder h = new StringBuilder();
			for (byte x : md.digest(b)) {
				h.append(String.format("%02x", x & 0xFF));
			}
			return h.substring(0, 16);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The bytes a client downloads for an id: a texture PNG or a model blob. */
	byte[] download(String hash) {
		Item it = byTexture(hash);
		if (it == null) {
			return null;
		}
		return hash.equals(it.modelHash) ? it.modelBlob : hash.equals(it.textureHash) ? it.png : null;
	}

	/** Width and height from a PNG's IHDR, or null when it isn't a PNG. */
	static int[] pngSize(byte[] b) {
		byte[] sig = { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10 };
		if (b.length < 24) {
			return null;
		}
		for (int i = 0; i < 8; i++) {
			if (b[i] != sig[i]) {
				return null;
			}
		}
		if (b[12] != 'I' || b[13] != 'H' || b[14] != 'D' || b[15] != 'R') {
			return null;
		}
		int w = (b[16] & 0xFF) << 24 | (b[17] & 0xFF) << 16 | (b[18] & 0xFF) << 8 | (b[19] & 0xFF);
		int h = (b[20] & 0xFF) << 24 | (b[21] & 0xFF) << 16 | (b[22] & 0xFF) << 8 | (b[23] & 0xFF);
		return w > 0 && h > 0 ? new int[] { w, h } : null;
	}

	/** The item whose texture or model has this hash, or null. */
	Item byTexture(String hash) {
		return hash == null ? null : byTexture.get(hash);
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
