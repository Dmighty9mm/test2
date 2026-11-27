package net.plebmc.shop;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;

/**
 * Turns a Blockbench model (.bbmodel, or a Java block/item model .json) into
 * what the PlebMc client draws on a player's head: a flat list of textured
 * quads in head pixels (x/z -4..4 is the head, y up from the neck, the top of
 * the head is y 8) with UVs as fractions of the texture.
 *
 * <p>
 * All the Blockbench details (cube and group rotations around their pivots,
 * inflate, per-face UV rotation, box UV with mirroring) are worked out here,
 * so the client only has to draw quads. The output is the "model blob" the
 * client downloads like a cloak texture:
 * {@code {"v":1,"quads":[x,y,z,u,v, x,y,z,u,v, x,y,z,u,v, x,y,z,u,v, ...]}}.
 */
final class HatModel {

	/** Limits that keep a hat cheap to send and draw. */
	static final int MAX_QUADS = 3000;
	static final int MAX_FILE_BYTES = 8 * 1024 * 1024;

	/** The quads: 20 floats each (4 corners of x, y, z, u, v). */
	final float[] quads;
	/** The PNG embedded in the .bbmodel, or null (then the config's texture is used). */
	final byte[] embeddedPng;
	final List<String> warnings = new ArrayList<>();

	private HatModel(float[] quads, byte[] png) {
		this.quads = quads;
		this.embeddedPng = png;
	}

	/** Where the hat sits: "auto" centers it on the head and stands it on top; "model" keeps its coordinates. */
	static final class Placement {
		String mode = "auto";
		float scale = 1f;
		float dx, dy, dz;
	}

	int quadCount() {
		return quads.length / 20;
	}

	/** The JSON the client downloads (numbers rounded to keep it small). */
	byte[] blob() {
		StringBuilder b = new StringBuilder(quads.length * 7 + 32);
		b.append("{\"v\":1,\"quads\":[");
		for (int i = 0; i < quads.length; i++) {
			if (i > 0) {
				b.append(',');
			}
			float f = quads[i];
			long r = Math.round(f * 10000.0);
			if (r == 0) {
				b.append('0');
			} else if (r % 10000 == 0) {
				b.append(r / 10000);
			} else {
				b.append(String.format(Locale.ROOT, "%.4f", r / 10000.0).replaceAll("0+$", ""));
			}
		}
		b.append("]}");
		return b.toString().getBytes(StandardCharsets.UTF_8);
	}

	// ==================== reading ====================

	private static final Gson GSON = new Gson();

	/** Parses a model file; throws IllegalArgumentException with a readable reason. */
	static HatModel read(String fileName, byte[] bytes, Placement place) {
		Object root;
		try {
			root = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), Object.class);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("is not valid JSON (" + e.getMessage() + ")");
		}
		if (!(root instanceof Map)) {
			throw new IllegalArgumentException("is not a Blockbench model");
		}
		Map<?, ?> m = (Map<?, ?>) root;
		if (!(m.get("elements") instanceof List)) {
			throw new IllegalArgumentException("has no elements (cubes)");
		}
		Reader r = new Reader(m, place);
		boolean javaModel = !(m.get("meta") instanceof Map) && !fileName.toLowerCase(Locale.ROOT).endsWith(".bbmodel");
		if (javaModel) {
			r.readJavaModel();
		} else {
			r.readBbModel();
		}
		if (r.out.isEmpty()) {
			throw new IllegalArgumentException("has no textured cube faces");
		}
		r.place();
		float[] q = new float[r.out.size()];
		for (int i = 0; i < q.length; i++) {
			q[i] = r.out.get(i);
		}
		HatModel hm = new HatModel(q, r.png);
		hm.warnings.addAll(r.warnings);
		if (hm.quadCount() > MAX_QUADS) {
			throw new IllegalArgumentException("has " + hm.quadCount() + " faces, the most a hat can have is " + MAX_QUADS);
		}
		return hm;
	}

	private static final class Reader {
		final Map<?, ?> root;
		final Placement place;
		final List<Float> out = new ArrayList<>();
		final List<String> warnings = new ArrayList<>();
		byte[] png;
		/** The texture index used (bbmodel), so faces on other textures are skipped. */
		int textureIndex = -1;
		float texW = 16, texH = 16;

		Reader(Map<?, ?> root, Placement place) {
			this.root = root;
			this.place = place;
		}

		// ---------- Blockbench .bbmodel ----------

		void readBbModel() {
			Map<?, ?> res = map(root.get("resolution"));
			float resW = res == null ? 16 : num(res.get("width"), 16);
			float resH = res == null ? 16 : num(res.get("height"), 16);
			List<?> textures = list(root.get("textures"));
			List<?> elements = list(root.get("elements"));

			// the texture most faces use
			Map<Integer, Integer> uses = new HashMap<>();
			for (Object e : elements) {
				Map<?, ?> faces = map(map(e) == null ? null : map(e).get("faces"));
				if (faces != null) {
					for (Object f : faces.values()) {
						Map<?, ?> fm = map(f);
						if (fm != null && fm.get("texture") instanceof Number) {
							uses.merge(((Number) fm.get("texture")).intValue(), 1, Integer::sum);
						}
					}
				}
			}
			int best = -1, bestCount = -1;
			for (Map.Entry<Integer, Integer> u : uses.entrySet()) {
				if (u.getValue() > bestCount) {
					best = u.getKey();
					bestCount = u.getValue();
				}
			}
			if (uses.size() > 1) {
				warnings.add("uses " + uses.size() + " textures, only the main one is drawn");
			}
			textureIndex = best;
			texW = resW;
			texH = resH;
			if (textures != null && best >= 0 && best < textures.size()) {
				Map<?, ?> t = map(textures.get(best));
				if (t != null) {
					texW = num(t.get("uv_width"), resW);
					texH = num(t.get("uv_height"), resH);
					String src = str(t.get("source"));
					if (src != null && src.startsWith("data:image/png;base64,")) {
						try {
							png = java.util.Base64.getDecoder().decode(src.substring(src.indexOf(',') + 1));
						} catch (IllegalArgumentException e) {
							warnings.add("its embedded texture is broken");
						}
					}
				}
			}

			Map<String, Map<?, ?>> byUuid = new HashMap<>();
			for (Object e : elements) {
				Map<?, ?> em = map(e);
				if (em != null && em.get("uuid") instanceof String) {
					byUuid.put((String) em.get("uuid"), em);
				}
			}
			Map<String, Map<?, ?>> groups = new HashMap<>();
			List<?> gl = list(root.get("groups"));
			if (gl != null) {
				for (Object g : gl) {
					Map<?, ?> gm = map(g);
					if (gm != null && gm.get("uuid") instanceof String) {
						groups.put((String) gm.get("uuid"), gm);
					}
				}
			}
			List<?> outliner = list(root.get("outliner"));
			java.util.Set<String> done = new java.util.HashSet<>();
			if (outliner != null) {
				walk(outliner, new ArrayList<>(), byUuid, groups, done);
			}
			// elements the outliner doesn't list are still drawn
			for (Object e : elements) {
				Map<?, ?> em = map(e);
				if (em != null && !done.contains(String.valueOf(em.get("uuid")))) {
					cube(em, new ArrayList<>());
				}
			}
		}

		/** Goes through the outliner, collecting the group transforms (innermost last). */
		private void walk(List<?> nodes, List<float[]> parents, Map<String, Map<?, ?>> byUuid,
				Map<String, Map<?, ?>> groups, java.util.Set<String> done) {
			for (Object n : nodes) {
				if (n instanceof String) {
					Map<?, ?> em = byUuid.get(n);
					if (em != null && done.add((String) n)) {
						cube(em, parents);
					}
				} else if (n instanceof Map) {
					Map<?, ?> g = (Map<?, ?>) n;
					Map<?, ?> props = g.get("uuid") instanceof String && groups.containsKey(g.get("uuid"))
							? groups.get(g.get("uuid"))
							: g;
					if (Boolean.FALSE.equals(props.get("visibility")) || Boolean.FALSE.equals(props.get("export"))) {
						markDone(list(g.get("children")), done);
						continue;
					}
					List<float[]> next = new ArrayList<>(parents);
					float[] rot = vec(props.get("rotation"), 0);
					if (rot[0] != 0 || rot[1] != 0 || rot[2] != 0) {
						float[] o = vec(props.get("origin"), 0);
						next.add(new float[] { o[0], o[1], o[2], rot[0], rot[1], rot[2] });
					}
					List<?> children = list(g.get("children"));
					if (children != null) {
						walk(children, next, byUuid, groups, done);
					}
				}
			}
		}

		private static void markDone(List<?> nodes, java.util.Set<String> done) {
			if (nodes == null) {
				return;
			}
			for (Object n : nodes) {
				if (n instanceof String) {
					done.add((String) n);
				} else if (n instanceof Map) {
					markDone(list(((Map<?, ?>) n).get("children")), done);
				}
			}
		}

		private void cube(Map<?, ?> e, List<float[]> parents) {
			String type = str(e.get("type"));
			if (type != null && !"cube".equals(type)) {
				return; // meshes, locators, null objects
			}
			if (Boolean.FALSE.equals(e.get("visibility")) || Boolean.FALSE.equals(e.get("export"))) {
				return;
			}
			float[] from = vec(e.get("from"), 0), to = vec(e.get("to"), 0);
			float inflate = num(e.get("inflate"), 0);
			for (int i = 0; i < 3; i++) {
				float a = Math.min(from[i], to[i]) - inflate, b = Math.max(from[i], to[i]) + inflate;
				from[i] = a;
				to[i] = b;
			}
			List<float[]> transforms = new ArrayList<>();
			float[] rot = vec(e.get("rotation"), 0);
			if (rot[0] != 0 || rot[1] != 0 || rot[2] != 0) {
				float[] o = vec(e.get("origin"), 0);
				transforms.add(new float[] { o[0], o[1], o[2], rot[0], rot[1], rot[2] });
			}
			// the cube's own rotation first, then its groups from the inside out
			for (int i = parents.size() - 1; i >= 0; i--) {
				transforms.add(parents.get(i));
			}
			Map<?, ?> faces = map(e.get("faces"));
			Map<String, float[]> boxUv = Boolean.TRUE.equals(e.get("box_uv")) ? boxUv(e, from, to) : null;
			for (int f = 0; f < 6; f++) {
				String name = FACES[f];
				Map<?, ?> fm = faces == null ? null : map(faces.get(name));
				if (fm == null) {
					continue;
				}
				if (!(fm.get("texture") instanceof Number) || ((Number) fm.get("texture")).intValue() != textureIndex) {
					continue; // no texture, or another texture
				}
				float[] uv = boxUv != null ? boxUv.get(name) : vec4(fm.get("uv"));
				if (uv == null) {
					continue;
				}
				emit(f, from, to, uv, (int) num(fm.get("rotation"), 0), transforms);
			}
		}

		/** Blockbench's box-UV layout (the Minecraft entity model unwrap). */
		private Map<String, float[]> boxUv(Map<?, ?> e, float[] from, float[] to) {
			float[] off = vec(e.get("uv_offset"), 0);
			float sx = to[0] - from[0], sy = to[1] - from[1], sz = to[2] - from[2];
			float inflate = num(e.get("inflate"), 0);
			sx -= 2 * inflate;
			sy -= 2 * inflate;
			sz -= 2 * inflate;
			sx = (float) Math.floor(sx + 1e-4);
			sy = (float) Math.floor(sy + 1e-4);
			sz = (float) Math.floor(sz + 1e-4);
			boolean mirror = Boolean.TRUE.equals(e.get("mirror_uv"));
			Map<String, float[]> m = new HashMap<>();
			// face -> from (u, v), size (u, v)
			float[][] spec = { { 0, sz, sz, sy }, { sz + sx, sz, sz, sy }, { sz + sx, sz, -sx, -sz },
					{ sz + sx * 2, 0, -sx, sz }, { sz * 2 + sx, sz, sx, sy }, { sz, sz, sx, sy } };
			String[] names = { "east", "west", "up", "down", "south", "north" };
			for (int i = 0; i < 6; i++) {
				float u = spec[i][0], v = spec[i][1], w = spec[i][2], h = spec[i][3];
				String name = names[i];
				if (mirror) {
					u += w;
					w = -w;
					if (name.equals("east")) {
						name = "west";
					} else if (name.equals("west")) {
						name = "east";
					}
				}
				m.put(name, new float[] { off[0] + u, off[1] + v, off[0] + u + w, off[1] + v + h });
			}
			return m;
		}

		// ---------- Java block/item model (.json) ----------

		void readJavaModel() {
			// UVs are in 0..16 for any texture size
			texW = 16;
			texH = 16;
			textureIndex = 0;
			for (Object o : list(root.get("elements"))) {
				Map<?, ?> e = map(o);
				if (e == null) {
					continue;
				}
				float[] from = vec(e.get("from"), 0), to = vec(e.get("to"), 0);
				List<float[]> transforms = new ArrayList<>();
				Map<?, ?> r = map(e.get("rotation"));
				if (r != null && num(r.get("angle"), 0) != 0) {
					float[] o2 = vec(r.get("origin"), 8);
					float a = num(r.get("angle"), 0);
					String axis = str(r.get("axis"));
					transforms.add(new float[] { o2[0], o2[1], o2[2], "x".equals(axis) ? a : 0, "y".equals(axis) ? a : 0,
							"z".equals(axis) ? a : 0 });
				}
				Map<?, ?> faces = map(e.get("faces"));
				for (int f = 0; f < 6; f++) {
					Map<?, ?> fm = faces == null ? null : map(faces.get(FACES[f]));
					if (fm == null) {
						continue;
					}
					float[] uv = vec4(fm.get("uv"));
					if (uv == null) {
						uv = autoUv(f, from, to);
					}
					emit(f, from, to, uv, (int) num(fm.get("rotation"), 0), transforms);
				}
			}
		}

		/** Minecraft's default UVs for a face without "uv": the face's own position in the 16x16 block. */
		private static float[] autoUv(int f, float[] a, float[] b) {
			switch (FACES[f]) {
			case "north":
				return new float[] { 16 - b[0], 16 - b[1], 16 - a[0], 16 - a[1] };
			case "south":
				return new float[] { a[0], 16 - b[1], b[0], 16 - a[1] };
			case "east":
				return new float[] { 16 - b[2], 16 - b[1], 16 - a[2], 16 - a[1] };
			case "west":
				return new float[] { a[2], 16 - b[1], b[2], 16 - a[1] };
			case "up":
				return new float[] { a[0], a[2], b[0], b[2] };
			default:
				return new float[] { a[0], 16 - b[2], b[0], 16 - a[2] };
			}
		}

		// ---------- quads ----------

		static final String[] FACES = { "north", "south", "east", "west", "up", "down" };

		/**
		 * One face as a quad: corners top-left, top-right, bottom-right,
		 * bottom-left as seen from outside, matched to the UV rectangle's
		 * corners (turned by the face's UV rotation).
		 */
		private void emit(int face, float[] a, float[] b, float[] uv, int uvRotation, List<float[]> transforms) {
			float x0 = a[0], y0 = a[1], z0 = a[2], x1 = b[0], y1 = b[1], z1 = b[2];
			float[][] c;
			switch (face) {
			case 0: // north (-z)
				c = new float[][] { { x1, y1, z0 }, { x0, y1, z0 }, { x0, y0, z0 }, { x1, y0, z0 } };
				break;
			case 1: // south (+z)
				c = new float[][] { { x0, y1, z1 }, { x1, y1, z1 }, { x1, y0, z1 }, { x0, y0, z1 } };
				break;
			case 2: // east (+x)
				c = new float[][] { { x1, y1, z1 }, { x1, y1, z0 }, { x1, y0, z0 }, { x1, y0, z1 } };
				break;
			case 3: // west (-x)
				c = new float[][] { { x0, y1, z0 }, { x0, y1, z1 }, { x0, y0, z1 }, { x0, y0, z0 } };
				break;
			case 4: // up
				c = new float[][] { { x0, y1, z0 }, { x1, y1, z0 }, { x1, y1, z1 }, { x0, y1, z1 } };
				break;
			default: // down
				c = new float[][] { { x0, y0, z1 }, { x1, y0, z1 }, { x1, y0, z0 }, { x0, y0, z0 } };
				break;
			}
			float u0 = uv[0] / texW, v0 = uv[1] / texH, u1 = uv[2] / texW, v1 = uv[3] / texH;
			float[][] t = { { u0, v0 }, { u1, v0 }, { u1, v1 }, { u0, v1 } };
			int turns = ((uvRotation / 90) % 4 + 4) % 4;
			for (int i = 0; i < 4; i++) {
				float[] p = c[i];
				for (float[] tr : transforms) {
					p = rotate(p, tr);
				}
				float[] tc = t[(i - turns + 4) % 4];
				out.add(p[0]);
				out.add(p[1]);
				out.add(p[2]);
				out.add(tc[0]);
				out.add(tc[1]);
			}
		}

		/** Rotates p around a pivot by Euler angles in degrees, Blockbench order (X, then Y, then Z). */
		private static float[] rotate(float[] p, float[] tr) {
			double x = p[0] - tr[0], y = p[1] - tr[1], z = p[2] - tr[2];
			double ax = Math.toRadians(tr[3]), ay = Math.toRadians(tr[4]), az = Math.toRadians(tr[5]);
			if (ax != 0) {
				double c = Math.cos(ax), s = Math.sin(ax);
				double ny = y * c - z * s, nz = y * s + z * c;
				y = ny;
				z = nz;
			}
			if (ay != 0) {
				double c = Math.cos(ay), s = Math.sin(ay);
				double nx = x * c + z * s, nz = -x * s + z * c;
				x = nx;
				z = nz;
			}
			if (az != 0) {
				double c = Math.cos(az), s = Math.sin(az);
				double nx = x * c - y * s, ny = x * s + y * c;
				x = nx;
				y = ny;
			}
			return new float[] { (float) (x + tr[0]), (float) (y + tr[1]), (float) (z + tr[2]) };
		}

		/**
		 * Moves the quads onto the head. "auto": centered over the head and
		 * standing just inside its top (like a crown); "model": the model's own
		 * coordinates, where Blockbench's 0,0,0 is the bottom middle of the head.
		 * Then scale (around the head's top middle) and the offset.
		 */
		void place() {
			float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
			float maxX = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
			for (int i = 0; i < out.size(); i += 5) {
				minX = Math.min(minX, out.get(i));
				maxX = Math.max(maxX, out.get(i));
				minY = Math.min(minY, out.get(i + 1));
				minZ = Math.min(minZ, out.get(i + 2));
				maxZ = Math.max(maxZ, out.get(i + 2));
			}
			float sx = 0, sy = 0, sz = 0;
			if (!"model".equalsIgnoreCase(place.mode)) {
				sx = -(minX + maxX) / 2;
				sy = 7.6f - minY;
				sz = -(minZ + maxZ) / 2;
			}
			float k = place.scale <= 0 ? 1f : place.scale;
			for (int i = 0; i < out.size(); i += 5) {
				float x = out.get(i) + sx, y = out.get(i + 1) + sy, z = out.get(i + 2) + sz;
				x = x * k + place.dx;
				y = 8 + (y - 8) * k + place.dy;
				z = z * k + place.dz;
				out.set(i, x);
				out.set(i + 1, y);
				out.set(i + 2, z);
			}
		}

		// ---------- json helpers ----------

		static Map<?, ?> map(Object o) {
			return o instanceof Map ? (Map<?, ?>) o : null;
		}

		static List<?> list(Object o) {
			return o instanceof List ? (List<?>) o : null;
		}

		static String str(Object o) {
			return o instanceof String ? (String) o : null;
		}

		static float num(Object o, float def) {
			return o instanceof Number ? ((Number) o).floatValue() : def;
		}

		static float[] vec(Object o, float def) {
			float[] v = { def, def, def };
			if (o instanceof List) {
				List<?> l = (List<?>) o;
				for (int i = 0; i < 3 && i < l.size(); i++) {
					v[i] = num(l.get(i), def);
				}
			}
			return v;
		}

		static float[] vec4(Object o) {
			if (!(o instanceof List) || ((List<?>) o).size() < 4) {
				return null;
			}
			List<?> l = (List<?>) o;
			return new float[] { num(l.get(0), 0), num(l.get(1), 0), num(l.get(2), 0), num(l.get(3), 0) };
		}
	}
}
