package net.plebmc.client.items;

/**
 * Converts 1.9+ item display transforms ("firstperson_righthand", ...) to
 * the 1.8 ones ("firstperson", ...).
 * <p>
 * The two versions apply a transform differently (1.9+: translate, rotate
 * X then Y then Z, scale; 1.8: translate, rotate Y then X then Z, scale) and
 * their renderers hold items differently. For each slot, the difference in
 * the renderer is taken from a vanilla item that looks the same in both
 * versions (a sword for the hands, a flat item or a block for the GUI, the
 * ground and frames): {@code C = V18 * inverse(V19)}. A custom transform
 * {@code M19} then becomes {@code C * M19}, written back in 1.8's order.
 * Values are JSON units (translation in pixels, rotation in degrees).
 */
public final class ItemTransforms {

	public static final String[] SLOTS_18 = { "thirdperson", "firstperson", "gui", "ground", "fixed", "head" };

	/** A transform: rotation (deg), translation (px), scale. */
	public static final class T {
		public final double[] r, t, s;

		public T(double[] r, double[] t, double[] s) {
			this.r = r;
			this.t = t;
			this.s = s;
		}

		static T of(double rx, double ry, double rz, double tx, double ty, double tz, double sc) {
			return new T(new double[] { rx, ry, rz }, new double[] { tx, ty, tz }, new double[] { sc, sc, sc });
		}

		public boolean isIdentity() {
			for (int i = 0; i < 3; i++) {
				if (Math.abs(r[i]) > 1e-4 || Math.abs(t[i]) > 1e-4 || Math.abs(s[i] - 1) > 1e-4) {
					return false;
				}
			}
			return true;
		}

		@Override
		public String toString() {
			return "r" + java.util.Arrays.toString(r) + " t" + java.util.Arrays.toString(t) + " s"
					+ java.util.Arrays.toString(s);
		}
	}

	public static final T IDENTITY = T.of(0, 0, 0, 0, 0, 0, 1);

	// vanilla reference transforms
	static final T V18_HAND_FP = T.of(0, -135, 25, 0, 4, 2, 1.7);
	static final T V19_HAND_FP = T.of(0, -90, 25, 1.13, 3.2, 1.13, 0.68);
	static final T V18_HAND_TP = T.of(0, 90, -35, 0, 1.25, -3.5, 0.85);
	static final T V19_HAND_TP = T.of(0, -90, 55, 0, 4, 0.5, 0.85);
	/** 1.9+ item/generated defaults (by 1.9 slot). */
	static final T V19_GEN_TP = T.of(0, 0, 0, 0, 3, 1, 0.55);
	static final T V19_GEN_GROUND = T.of(0, 0, 0, 0, 2, 0, 0.5);
	static final T V19_GEN_FIXED = T.of(0, 180, 0, 0, 0, 0, 1);
	static final T V19_GEN_HEAD = T.of(0, 180, 0, 0, 13, 7, 1);
	/** 1.9+ block/block defaults. */
	static final T V19_BLOCK_GUI = T.of(30, 225, 0, 0, 0, 0, 0.625);
	static final T V19_BLOCK_GROUND = T.of(0, 0, 0, 0, 3, 0, 0.25);
	static final T V19_BLOCK_FIXED = T.of(0, 0, 0, 0, 0, 0, 0.5);

	private ItemTransforms() {
	}

	/**
	 * The 1.9+ transform a model has for a slot when it doesn't set one:
	 * {@code parentKind} is "generated", "handheld" or null (a model with its own
	 * elements and no vanilla parent).
	 */
	public static T default19(String slot18, String parentKind) {
		if (parentKind == null) {
			return IDENTITY;
		}
		if ("block".equals(parentKind)) {
			switch (slot18) {
			case "firstperson":
				return T.of(0, 45, 0, 0, 0, 0, 0.4);
			case "thirdperson":
				return T.of(75, 45, 0, 0, 2.5, 0, 0.375);
			case "gui":
				return V19_BLOCK_GUI;
			case "ground":
				return V19_BLOCK_GROUND;
			case "fixed":
				return V19_BLOCK_FIXED;
			default:
				return IDENTITY;
			}
		}
		boolean hand = "handheld".equals(parentKind);
		switch (slot18) {
		case "firstperson":
			return V19_HAND_FP;
		case "thirdperson":
			return hand ? V19_HAND_TP : V19_GEN_TP;
		case "ground":
			return V19_GEN_GROUND;
		case "fixed":
			return V19_GEN_FIXED;
		case "head":
			return V19_GEN_HEAD;
		default:
			return IDENTITY;
		}
	}

	/** {@code m19} for 1.8's {@code slot18}; {@code model3d} = the model has its own elements (drawn in 3D). */
	public static T convert(String slot18, T m19, boolean model3d) {
		Mat c;
		switch (slot18) {
		case "firstperson":
			c = Mat.of18(V18_HAND_FP).mul(Mat.of19(V19_HAND_FP).inverse());
			break;
		case "thirdperson":
			c = Mat.of18(V18_HAND_TP).mul(Mat.of19(V19_HAND_TP).inverse());
			break;
		case "gui":
			c = model3d ? Mat.of19(V19_BLOCK_GUI).inverse() : Mat.identity();
			break;
		case "ground":
			c = Mat.of19(model3d ? V19_BLOCK_GROUND : V19_GEN_GROUND).inverse();
			break;
		case "fixed":
			c = Mat.of19(model3d ? V19_BLOCK_FIXED : V19_GEN_FIXED).inverse();
			break;
		case "head":
			c = model3d ? Mat.identity() : Mat.of19(V19_GEN_HEAD).inverse();
			break;
		default:
			c = Mat.identity();
		}
		return c.mul(Mat.of19(m19)).to18();
	}

	/** Reads a {@code {"rotation":[..],"translation":[..],"scale":[..]}} map (missing parts default). */
	public static T read(Object o) {
		if (!(o instanceof java.util.Map)) {
			return null;
		}
		java.util.Map<?, ?> m = (java.util.Map<?, ?>) o;
		return new T(vec(m.get("rotation"), 0), vec(m.get("translation"), 0), vec(m.get("scale"), 1));
	}

	private static double[] vec(Object o, double def) {
		double[] v = { def, def, def };
		if (o instanceof java.util.List) {
			java.util.List<?> l = (java.util.List<?>) o;
			for (int i = 0; i < 3 && i < l.size(); i++) {
				if (l.get(i) instanceof Number) {
					double d = ((Number) l.get(i)).doubleValue();
					if (!Double.isNaN(d) && !Double.isInfinite(d)) {
						v[i] = d;
					}
				}
			}
		}
		return v;
	}

	/** As 1.8 JSON (translation and scale clamped to what 1.8 accepts). */
	public static java.util.Map<String, Object> write(T t) {
		java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
		m.put("rotation", list(t.r, -1e9, 1e9));
		m.put("translation", list(t.t, -24, 24));
		m.put("scale", list(t.s, -4, 4));
		return m;
	}

	private static java.util.List<Object> list(double[] v, double lo, double hi) {
		java.util.List<Object> l = new java.util.ArrayList<>();
		for (double d : v) {
			double c = Math.max(lo, Math.min(hi, d));
			l.add(Math.round(c * 10000) / 10000.0);
		}
		return l;
	}

	/** 4x4 affine matrix (row major), translation in blocks. */
	static final class Mat {
		final double[] m = new double[16];

		static Mat identity() {
			Mat r = new Mat();
			r.m[0] = r.m[5] = r.m[10] = r.m[15] = 1;
			return r;
		}

		static Mat rot(int axis, double deg) {
			Mat r = identity();
			double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
			switch (axis) {
			case 0:
				r.m[5] = c;
				r.m[6] = -s;
				r.m[9] = s;
				r.m[10] = c;
				break;
			case 1:
				r.m[0] = c;
				r.m[2] = s;
				r.m[8] = -s;
				r.m[10] = c;
				break;
			default:
				r.m[0] = c;
				r.m[1] = -s;
				r.m[4] = s;
				r.m[5] = c;
			}
			return r;
		}

		static Mat trans(double[] t) {
			Mat r = identity();
			r.m[3] = t[0] / 16;
			r.m[7] = t[1] / 16;
			r.m[11] = t[2] / 16;
			return r;
		}

		static Mat scale(double[] s) {
			Mat r = identity();
			r.m[0] = s[0];
			r.m[5] = s[1];
			r.m[10] = s[2];
			return r;
		}

		/** 1.9+: T * Rx * Ry * Rz * S. */
		static Mat of19(T t) {
			return trans(t.t).mul(rot(0, t.r[0])).mul(rot(1, t.r[1])).mul(rot(2, t.r[2])).mul(scale(t.s));
		}

		/** 1.8: T * Ry * Rx * Rz * S. */
		static Mat of18(T t) {
			return trans(t.t).mul(rot(1, t.r[1])).mul(rot(0, t.r[0])).mul(rot(2, t.r[2])).mul(scale(t.s));
		}

		Mat mul(Mat o) {
			Mat r = new Mat();
			for (int i = 0; i < 4; i++) {
				for (int j = 0; j < 4; j++) {
					double v = 0;
					for (int k = 0; k < 4; k++) {
						v += m[i * 4 + k] * o.m[k * 4 + j];
					}
					r.m[i * 4 + j] = v;
				}
			}
			return r;
		}

		/** Inverse of an affine matrix. */
		Mat inverse() {
			double a = m[0], b = m[1], c = m[2], d = m[4], e = m[5], f = m[6], g = m[8], h = m[9], i = m[10];
			double det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
			Mat r = identity();
			if (Math.abs(det) < 1e-12) {
				return r;
			}
			double[] inv = { (e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det,
					(f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det, (d * h - e * g) / det,
					(b * g - a * h) / det, (a * e - b * d) / det };
			for (int row = 0; row < 3; row++) {
				for (int col = 0; col < 3; col++) {
					r.m[row * 4 + col] = inv[row * 3 + col];
				}
			}
			for (int row = 0; row < 3; row++) {
				r.m[row * 4 + 3] = -(inv[row * 3] * m[3] + inv[row * 3 + 1] * m[7] + inv[row * 3 + 2] * m[11]);
			}
			return r;
		}

		/** Splits into 1.8's T * Ry * Rx * Rz * S (scale per axis, a mirror goes on X). */
		T to18() {
			double[] s = new double[3];
			double[][] col = new double[3][3];
			for (int j = 0; j < 3; j++) {
				double len = Math.sqrt(m[j] * m[j] + m[4 + j] * m[4 + j] + m[8 + j] * m[8 + j]);
				s[j] = len;
				for (int i = 0; i < 3; i++) {
					col[j][i] = len < 1e-12 ? 0 : m[i * 4 + j] / len;
				}
			}
			double det = col[0][0] * (col[1][1] * col[2][2] - col[1][2] * col[2][1])
					- col[1][0] * (col[0][1] * col[2][2] - col[0][2] * col[2][1])
					+ col[2][0] * (col[0][1] * col[1][2] - col[0][2] * col[1][1]);
			if (det < 0) {
				s[0] = -s[0];
				for (int i = 0; i < 3; i++) {
					col[0][i] = -col[0][i];
				}
			}
			// R[i][j] = col[j][i]; R = Ry(b) Rx(a) Rz(c)
			double r12 = col[2][1];
			double a = Math.asin(Math.max(-1, Math.min(1, -r12)));
			double b, c;
			if (Math.abs(Math.cos(a)) > 1e-6) {
				b = Math.atan2(col[2][0], col[2][2]);
				c = Math.atan2(col[0][1], col[1][1]);
			} else {
				b = Math.atan2(-col[0][2], col[0][0]);
				c = 0;
			}
			double[] r = { clean(Math.toDegrees(a)), clean(Math.toDegrees(b)), clean(Math.toDegrees(c)) };
			double[] t = { clean(m[3] * 16), clean(m[7] * 16), clean(m[11] * 16) };
			return new T(r, t, new double[] { clean(s[0]), clean(s[1]), clean(s[2]) });
		}

		private static double clean(double d) {
			double r = Math.round(d * 10000) / 10000.0;
			return r == -0.0 ? 0.0 : r;
		}
	}
}
