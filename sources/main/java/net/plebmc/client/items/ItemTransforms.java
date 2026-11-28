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
			return hand(V18_HAND_FP, V19_HAND_FP, m19);
		case "thirdperson":
			return hand(V18_HAND_TP, V19_HAND_TP, m19);
		case "gui": {
			// 1.8 draws a slot icon as S(2) * M18 * S(0.5) too (the 40 and 64 in
			// RenderItem.setupGuiTransform are twice 1.9's sizes, and renderItem halves
			// the model), so the model's own translation counts double there: halve it,
			// or a gun that is moved over in its slot ends up twice as far off-center.
			double[] half = { 0.5, 0.5, 0.5 }, twice = { 2, 2, 2 };
			c = model3d ? Mat.of19(V19_BLOCK_GUI).inverse() : Mat.identity();
			return Mat.scale(half).mul(c).mul(Mat.of19(m19)).mul(Mat.scale(twice)).to18();
		}
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

	/**
	 * Where a model point (pixels, 0..16) lands in an inventory slot on 1.8, in
	 * slot pixels from the slot's middle (x right, y down), the way
	 * RenderItem.setupGuiTransform and renderItem draw it.
	 */
	public static double[] guiPoint18(T t18, double[] p, boolean model3d) {
		Mat m = model3d
				? Mat.scale(new double[] { 20, 20, -20 }).mul(Mat.rot(0, 210)).mul(Mat.rot(1, -135))
				: Mat.scale(new double[] { 32, 32, -32 }).mul(Mat.rot(0, 180));
		m = m.mul(Mat.of18(t18)).mul(Mat.scale(new double[] { 0.5, 0.5, 0.5 })).mul(Mat.trans(new double[] { -8, -8, -8 }));
		return m.apply(p);
	}

	/** The same on 1.9+ (ItemRenderer.renderGuiItem), which is what a pack was made for. */
	public static double[] guiPoint19(T t19, double[] p) {
		Mat m = Mat.scale(new double[] { 16, -16, 16 }).mul(Mat.of19(t19)).mul(Mat.trans(new double[] { -8, -8, -8 }));
		return m.apply(p);
	}

	/**
	 * A hand slot. 1.8 draws a held item as {@code S(2) * M18 * S(0.5)} (twice as
	 * big before the transform, half after), so the sword reference really is
	 * {@code V18 * S(0.5)} and the model's own transform gets the 2 back.
	 */
	private static T hand(T v18, T v19, T m19) {
		double[] half = { 0.5, 0.5, 0.5 }, twice = { 2, 2, 2 };
		return Mat.of18(v18).mul(Mat.scale(half)).mul(Mat.of19(v19).inverse()).mul(Mat.of19(m19))
				.mul(Mat.scale(twice)).to18();
	}

	/**
	 * First person for a gun being aimed: 1.9+ gun plugins move the gun to the
	 * off hand, so this is {@code left19} ("firstperson_lefthand") drawn where
	 * the left hand would be (1.12 blocks to the left, mirrored the way 1.9+
	 * applies a left hand transform).
	 */
	public static T convertAim(T left19) {
		T m = new T(new double[] { left19.r[0], -left19.r[1], -left19.r[2] },
				new double[] { -left19.t[0] - 1.12 * 16, left19.t[1], left19.t[2] }, left19.s);
		return hand(V18_HAND_FP, V19_HAND_FP, m);
	}

	/**
	 * The same slot after the geometry was moved by {@code d} pixels and then
	 * scaled {@code k} times around the middle of the block.
	 */
	public static T refit(T t18, double[] d, double k) {
		Mat rs = Mat.of18(new T(t18.r, new double[3], t18.s));
		double[] t = new double[3], s = new double[3];
		for (int i = 0; i < 3; i++) {
			t[i] = Mat.clean(t18.t[i] - (rs.m[i * 4] * d[0] + rs.m[i * 4 + 1] * d[1] + rs.m[i * 4 + 2] * d[2]));
			s[i] = Mat.clean(t18.s[i] / k);
		}
		return new T(t18.r.clone(), t, s);
	}

	/**
	 * How far to move a model's geometry (pixels) so every slot's translation
	 * fits in 1.8's 24 pixels, or as close as it gets (the hands and the GUI
	 * count most). The geometry, made {@code k} times bigger around the middle
	 * of the block afterwards, has to stay inside 1.8's -16..32 box.
	 */
	public static double[] bestShift(java.util.Map<String, T> t18s, double[] lo, double[] hi, double k) {
		double[] dlo = new double[3], dhi = new double[3], d = new double[3];
		for (int i = 0; i < 3; i++) {
			dlo[i] = 8 - 24 / k - lo[i];
			dhi[i] = 8 + 24 / k - hi[i];
			if (dlo[i] > dhi[i]) {
				dlo[i] = dhi[i] = 8 - (lo[i] + hi[i]) / 2;
			}
			d[i] = Math.max(dlo[i], Math.min(dhi[i], 8 - (lo[i] + hi[i]) / 2));
		}
		java.util.List<double[]> as = new java.util.ArrayList<>();
		java.util.List<double[]> ts = new java.util.ArrayList<>();
		java.util.List<Double> ws = new java.util.ArrayList<>();
		double norm = 0;
		for (java.util.Map.Entry<String, T> e : t18s.entrySet()) {
			T t = e.getValue();
			Mat rs = Mat.of18(new T(t.r, new double[3], t.s));
			double w = "gui".equals(e.getKey()) || e.getKey().endsWith("person") ? 10 : 1;
			as.add(rs.m);
			ts.add(t.t);
			ws.add(w);
			double s = Math.max(Math.abs(t.s[0]), Math.max(Math.abs(t.s[1]), Math.abs(t.s[2])));
			norm += w * s * s;
		}
		double step = norm <= 0 ? 0 : 0.4 / norm;
		for (int it = 0; it < 4000; it++) {
			double[] g = new double[3];
			boolean any = false;
			for (int n = 0; n < as.size(); n++) {
				double[] m = as.get(n), t = ts.get(n);
				for (int i = 0; i < 3; i++) {
					double r = t[i] - (m[i * 4] * d[0] + m[i * 4 + 1] * d[1] + m[i * 4 + 2] * d[2]);
					double over = Math.abs(r) - 23.9;
					if (over > 0) {
						any = true;
						double o = Math.signum(r) * over * ws.get(n);
						for (int j = 0; j < 3; j++) {
							g[j] -= o * m[i * 4 + j];
						}
					}
				}
			}
			if (!any) {
				break;
			}
			for (int j = 0; j < 3; j++) {
				d[j] = Math.max(dlo[j], Math.min(dhi[j], d[j] - step * g[j]));
			}
		}
		return d;
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

	/** As 1.8 JSON, marked so the client reads it without 1.8's limits (24 pixels, 4x). */
	public static java.util.Map<String, Object> write(T t) {
		java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
		m.put("rotation", list(t.r, -1e9, 1e9));
		m.put("translation", list(t.t, -1e9, 1e9));
		m.put("scale", list(t.s, -1e9, 1e9));
		m.put("pleb_unclamped", Boolean.TRUE); // read by the client's ItemTransformVec3f
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

		/** This matrix applied to a point given in pixels. */
		double[] apply(double[] px) {
			double[] o = new double[3];
			for (int i = 0; i < 3; i++) {
				o[i] = m[i * 4] * px[0] / 16 + m[i * 4 + 1] * px[1] / 16 + m[i * 4 + 2] * px[2] / 16 + m[i * 4 + 3];
			}
			return o;
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

		static double clean(double d) {
			double r = Math.round(d * 10000) / 10000.0;
			return r == -0.0 ? 0.0 : r;
		}
	}
}
