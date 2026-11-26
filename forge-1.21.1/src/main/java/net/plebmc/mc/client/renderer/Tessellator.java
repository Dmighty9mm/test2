package net.plebmc.mc.client.renderer;

/** 1.8's Tessellator: hands out the one {@link WorldRenderer}. */
public final class Tessellator {
	private static final Tessellator INSTANCE = new Tessellator();
	private final WorldRenderer wr = new WorldRenderer();
	/** Whether the next draw samples the bound texture (1.8's GL_TEXTURE_2D switch). */
	static boolean textured = true;

	public static Tessellator getInstance() {
		return INSTANCE;
	}

	public WorldRenderer getWorldRenderer() {
		return wr;
	}

	public void draw() {
		wr.finish(textured);
	}
}
