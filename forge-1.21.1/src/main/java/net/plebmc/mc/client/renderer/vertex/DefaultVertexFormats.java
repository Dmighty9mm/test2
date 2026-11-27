package net.plebmc.mc.client.renderer.vertex;

/** The 1.8 vertex formats PlebClient uses. */
public final class DefaultVertexFormats {
	public static final class Format {
		public final boolean tex, color, normal;

		Format(boolean tex, boolean color, boolean normal) {
			this.tex = tex;
			this.color = color;
			this.normal = normal;
		}
	}

	public static final Format POSITION = new Format(false, false, false);
	public static final Format POSITION_COLOR = new Format(false, true, false);
	public static final Format POSITION_TEX = new Format(true, false, false);
	public static final Format POSITION_TEX_COLOR = new Format(true, true, false);
	public static final Format POSITION_TEX_NORMAL = new Format(true, false, true);
	public static final Format POSITION_TEX_COLOR_NORMAL = new Format(true, true, true);
	public static final Format POSITION_NORMAL = new Format(false, false, true);
	public static final Format POSITION_TEX_LMAP_COLOR = new Format(true, true, false);
}
