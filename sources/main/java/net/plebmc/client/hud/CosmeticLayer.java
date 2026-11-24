package net.plebmc.client.hud;

import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.lax1dude.eaglercraft.v1_8.opengl.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.plebmc.client.cosmetics.Cosmetics;
import net.plebmc.client.cosmetics.CrownModel;

/**
 * Draws a player's equipped hat on their head (a RenderPlayer layer, so it
 * follows the head, sneaking, the inventory preview and third person). The
 * hats are built from flat-shaded boxes; the crown is a textured model
 * (CrownModel).
 */
public final class CosmeticLayer implements LayerRenderer<AbstractClientPlayer> {

	private final RenderPlayer renderer;

	public CosmeticLayer(RenderPlayer renderer) {
		this.renderer = renderer;
	}

	@Override
	public void doRenderLayer(AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTicks, float ageInTicks, float headYaw, float headPitch, float scale) {
		if (Cosmetics.nothingToDraw() || player.isInvisible()) {
			return;
		}
		Cosmetics.Item hat = Cosmetics.shown(PlebEntities.uuid(player), Cosmetics.HAT);
		if (hat == null) {
			return;
		}
		GlStateManager.pushMatrix();
		try {
			if (player.isSneaking()) {
				GlStateManager.translate(0.0F, 0.2F, 0.0F);
			}
			renderer.getMainModel().bipedHead.postRender(0.0625F);
			// head space in pixels: the head is x/z -4..4, y -8 (top) .. 0 (neck); y grows downward
			GlStateManager.scale(0.0625F, 0.0625F, 0.0625F);
			if (player.getCurrentArmor(3) != null) {
				// sit on top of a helmet
				GlStateManager.translate(0.0F, -1.0F, 0.0F);
				GlStateManager.scale(1.12F, 1.12F, 1.12F);
			}
			if (Cosmetics.MODEL.equals(hat.render)) {
				model(hat);
			} else {
				draw(hat.render, hat.color, ageInTicks + partialTicks);
			}
		} finally {
			GlStateManager.popMatrix();
		}
	}

	@Override
	public boolean shouldCombineTextures() {
		return false;
	}

	// ==================== hat models ====================

	private static WorldRenderer wr;

	/** Draws one hat in head space (pixels, y down, origin at the neck). */
	static void draw(String render, int tint, float time) {
		if ("crown".equals(render) && texturedCrown()) {
			return;
		}
		GlStateManager.disableTexture2D();
		GlStateManager.disableLighting();
		GlStateManager.disableCull();
		GlStateManager.color(1f, 1f, 1f, 1f);
		Tessellator t = Tessellator.getInstance();
		wr = t.getWorldRenderer();
		wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
		switch (render) {
		case "tophat":
			topHat(tint < 0 ? 0x1C1C1C : tint);
			break;
		case "crown":
			crown(tint < 0 ? 0xF2C230 : tint);
			break;
		case "halo":
			halo(tint < 0 ? 0xFFE66B : tint, time);
			break;
		case "partyhat":
			partyHat(tint);
			break;
		case "beanie":
			beanie(tint < 0 ? 0x3A7BD5 : tint);
			break;
		case "horns":
			horns(tint < 0 ? 0xB3201E : tint);
			break;
		default:
			break;
		}
		t.draw();
		GlStateManager.enableCull();
		GlStateManager.enableLighting();
		GlStateManager.enableTexture2D();
	}

	// ==================== model hats (from the shop plugin's folder) ====================

	private static final java.util.Map<String, ResourceLocation> modelTextures = new java.util.HashMap<>();
	private static final java.util.Set<String> badTextures = new java.util.HashSet<>();

	/** The model hat's texture once downloaded, or null. */
	private static ResourceLocation modelTexture(Cosmetics.Item it) {
		ResourceLocation rl = modelTextures.get(it.texture);
		if (rl != null || badTextures.contains(it.texture)) {
			return rl;
		}
		byte[] png = Cosmetics.texture(it.texture);
		if (png == null) {
			return null;
		}
		try {
			net.lax1dude.eaglercraft.v1_8.opengl.ImageData img = net.lax1dude.eaglercraft.v1_8.opengl.ImageData
					.loadImageFile(png);
			if (img != null && img.width > 0 && img.height > 0) {
				rl = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("plebhat_" + it.texture,
						new net.minecraft.client.renderer.texture.DynamicTexture(img));
				modelTextures.put(it.texture, rl);
				return rl;
			}
		} catch (Throwable t) {
			// broken image: never try again
		}
		badTextures.add(it.texture);
		return null;
	}

	/**
	 * Draws a model hat: the quads the server converted from a Blockbench
	 * model, in head pixels (y up from the neck). Nothing until the model and
	 * its texture have downloaded.
	 */
	static boolean model(Cosmetics.Item it) {
		float[] q = Cosmetics.model(it.model);
		ResourceLocation tex = q == null ? null : modelTexture(it);
		if (tex == null) {
			return false;
		}
		Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
		GlStateManager.enableTexture2D();
		GlStateManager.disableLighting();
		GlStateManager.disableCull();
		GlStateManager.enableAlpha();
		GlStateManager.enableBlend();
		GlStateManager.blendFunc(770, 771);
		GlStateManager.color(1f, 1f, 1f, 1f);
		GlStateManager.pushMatrix();
		GlStateManager.scale(1.0F, -1.0F, 1.0F); // the model is y-up
		Tessellator t = Tessellator.getInstance();
		wr = t.getWorldRenderer();
		wr.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
		for (int i = 0; i + 20 <= q.length; i += 20) {
			float shade = shade(q, i);
			for (int c = 0; c < 4; c++) {
				int k = i + c * 5;
				wr.pos(q[k], q[k + 1], q[k + 2]).tex(q[k + 3], q[k + 4]).color(shade, shade, shade, 1f).endVertex();
			}
		}
		t.draw();
		GlStateManager.popMatrix();
		GlStateManager.disableBlend();
		GlStateManager.enableCull();
		GlStateManager.enableLighting();
		return true;
	}

	/** Minecraft-style face shading from the quad's normal: top 1.0, sides 0.8/0.62, bottom 0.5. */
	private static float shade(float[] q, int i) {
		float ax = q[i + 5] - q[i], ay = q[i + 6] - q[i + 1], az = q[i + 7] - q[i + 2];
		float bx = q[i + 15] - q[i], by = q[i + 16] - q[i + 1], bz = q[i + 17] - q[i + 2];
		float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1e-6f) {
			return 0.8f;
		}
		nx /= len;
		ny /= len;
		nz /= len;
		// the corners go clockwise seen from outside, so the cross product points inward
		ny = -ny;
		nx = -nx;
		nz = -nz;
		float up = Math.max(0f, ny), down = Math.max(0f, -ny);
		return up * 1.0f + down * 0.5f + Math.abs(nx) * 0.62f + Math.abs(nz) * 0.8f;
	}

	private static final ResourceLocation CROWN = new ResourceLocation("plebitems", CrownModel.TEXTURE);
	private static boolean crownMissing;

	/** The textured crown (CrownModel); false when its texture can't load, for the plain one. */
	private static boolean texturedCrown() {
		if (crownMissing) {
			return false;
		}
		try {
			Minecraft.getMinecraft().getTextureManager().bindTexture(CROWN);
		} catch (Throwable t) {
			crownMissing = true;
			return false;
		}
		GlStateManager.enableTexture2D();
		GlStateManager.disableLighting();
		GlStateManager.disableCull();
		GlStateManager.enableAlpha();
		GlStateManager.color(1f, 1f, 1f, 1f);
		GlStateManager.pushMatrix();
		GlStateManager.scale(1.0F, -1.0F, 1.0F); // the model is y-up
		Tessellator t = Tessellator.getInstance();
		wr = t.getWorldRenderer();
		wr.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
		float k = 1f / CrownModel.TEXTURE_SIZE;
		for (float[] b : CrownModel.BOXES) {
			float x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
			// north (-z): u runs from +x to -x
			tface(b, 6, k, 0.8f, x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0);
			// south (+z)
			tface(b, 10, k, 0.8f, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1);
			// east (+x): u runs from +z to -z
			tface(b, 14, k, 0.62f, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1);
			// west (-x)
			tface(b, 18, k, 0.62f, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0);
			// up: u along x, v from -z to +z
			tface(b, 22, k, 1.0f, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
			// down: u along x, v from +z to -z
			tface(b, 26, k, 0.5f, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
		}
		t.draw();
		GlStateManager.popMatrix();
		GlStateManager.enableCull();
		GlStateManager.enableLighting();
		return true;
	}

	/** One textured quad: corners in order top-left, top-right, bottom-right, bottom-left of the texture rectangle. */
	private static void tface(float[] b, int i, float k, float shade, float ax, float ay, float az, float bx, float by,
			float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
		if (b[i] < 0) {
			return;
		}
		float u0 = b[i] * k, v0 = b[i + 1] * k, u1 = b[i + 2] * k, v1 = b[i + 3] * k;
		wr.pos(ax, ay, az).tex(u0, v0).color(shade, shade, shade, 1f).endVertex();
		wr.pos(bx, by, bz).tex(u1, v0).color(shade, shade, shade, 1f).endVertex();
		wr.pos(cx, cy, cz).tex(u1, v1).color(shade, shade, shade, 1f).endVertex();
		wr.pos(dx, dy, dz).tex(u0, v1).color(shade, shade, shade, 1f).endVertex();
	}

	private static void topHat(int c) {
		box(-5.6f, -8.7f, -5.6f, 5.6f, -8.0f, 5.6f, c); // brim
		box(-3.6f, -10.2f, -3.6f, 3.6f, -8.7f, 3.6f, 0xA01C1C); // band
		box(-3.5f, -15.5f, -3.5f, 3.5f, -10.2f, 3.5f, c); // crown
	}

	private static void crown(int gold) {
		float r = 4.4f;
		float w = 0.8f;
		float y0 = -10.6f;
		float y1 = -7.9f;
		box(-r, y0, -r, r, y1, -r + w, gold);
		box(-r, y0, r - w, r, y1, r, gold);
		box(-r, y0, -r, -r + w, y1, r, gold);
		box(r - w, y0, -r, r, y1, r, gold);
		// spikes: corners and middles
		float[] p = { -r, -0.5f, r - 1.0f };
		for (float a : p) {
			for (float b : p) {
				if (a != -0.5f || b != -0.5f) {
					boolean edge = a == -r || a == r - 1.0f || b == -r || b == r - 1.0f;
					if (edge) {
						box(a, y0 - 1.8f, b, a + 1.0f, y0, b + 1.0f, gold);
					}
				}
			}
		}
		// gems on the front and back
		box(-0.7f, -9.8f, -r - 0.25f, 0.7f, -8.6f, -r + 0.1f, 0xD3202A);
		box(-0.7f, -9.8f, r - 0.1f, 0.7f, -8.6f, r + 0.25f, 0x2A6BD3);
	}

	private static void halo(int c, float time) {
		float y = -11.0f + (float) Math.sin(time * 0.08) * 0.35f;
		float r = 4.6f;
		int n = 20;
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n;
			float x = (float) (Math.cos(a) * r);
			float z = (float) (Math.sin(a) * r);
			box(x - 0.75f, y - 0.35f, z - 0.75f, x + 0.75f, y + 0.35f, z + 0.75f, c);
		}
	}

	private static void partyHat(int tint) {
		int[] stripes = tint < 0 ? new int[] { 0xE84A5F, 0xFFD166, 0x06D6A0, 0x118AB2 }
				: new int[] { tint, 0xFFFFFF };
		int layers = 7;
		for (int i = 0; i < layers; i++) {
			float half = 3.6f - i * 0.48f;
			float y1 = -8.0f - i * 1.1f;
			box(-half, y1 - 1.1f, -half, half, y1, half, stripes[i % stripes.length]);
		}
		box(-0.8f, -8.0f - layers * 1.1f - 1.4f, -0.8f, 0.8f, -8.0f - layers * 1.1f, 0.8f, 0xFFFFFF); // pom-pom
	}

	private static void beanie(int c) {
		int dark = shade(c, 0.75f);
		box(-4.4f, -9.4f, -4.4f, 4.4f, -6.2f, 4.4f, c);
		box(-4.7f, -6.6f, -4.7f, 4.7f, -5.4f, 4.7f, dark); // folded edge
		box(-1.2f, -10.8f, -1.2f, 1.2f, -9.4f, 1.2f, 0xF2F2F2); // pom-pom
	}

	private static void horns(int c) {
		int tip = shade(c, 0.7f);
		for (int s = -1; s <= 1; s += 2) {
			float x = s * 2.6f;
			box(x - 0.9f, -9.4f, -1.0f, x + 0.9f, -7.8f, 0.8f, c);
			box(x + s * 0.4f - 0.7f, -10.8f, -0.8f, x + s * 0.4f + 0.7f, -9.4f, 0.6f, c);
			box(x + s * 0.9f - 0.45f, -12.0f, -0.6f, x + s * 0.9f + 0.45f, -10.8f, 0.3f, tip);
		}
	}

	private static int shade(int rgb, float k) {
		int r = (int) (((rgb >> 16) & 0xFF) * k);
		int g = (int) (((rgb >> 8) & 0xFF) * k);
		int b = (int) ((rgb & 0xFF) * k);
		return (r << 16) | (g << 8) | b;
	}

	/** An axis-aligned box with simple per-face shading (top bright, sides darker). */
	private static void box(float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
		float r = ((rgb >> 16) & 0xFF) / 255f;
		float g = ((rgb >> 8) & 0xFF) / 255f;
		float b = (rgb & 0xFF) / 255f;
		// y is down in head space: y0 is the top face
		face(r, g, b, 1.0f, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		face(r, g, b, 0.5f, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		face(r, g, b, 0.8f, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
		face(r, g, b, 0.8f, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		face(r, g, b, 0.62f, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		face(r, g, b, 0.62f, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
	}

	private static void face(float r, float g, float b, float k, float ax, float ay, float az, float bx, float by,
			float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
		wr.pos(ax, ay, az).color(r * k, g * k, b * k, 1f).endVertex();
		wr.pos(bx, by, bz).color(r * k, g * k, b * k, 1f).endVertex();
		wr.pos(cx, cy, cz).color(r * k, g * k, b * k, 1f).endVertex();
		wr.pos(dx, dy, dz).color(r * k, g * k, b * k, 1f).endVertex();
	}
}
