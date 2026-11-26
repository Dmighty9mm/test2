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

	/** Per player: last ageInTicks, flap time, speed, amount. */
	private static final java.util.Map<Integer, float[]> flaps = new java.util.HashMap<>();

	@Override
	public void doRenderLayer(AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTicks, float ageInTicks, float headYaw, float headPitch, float scale) {
		if (Cosmetics.nothingToDraw() || player.isInvisible()) {
			return;
		}
		String uuid = PlebEntities.uuid(player);
		Cosmetics.Item wings = Cosmetics.shown(uuid, Cosmetics.WINGS);
		if (wings != null) {
			GlStateManager.pushMatrix();
			try {
				if (player.isSneaking()) {
					GlStateManager.translate(0.0F, 0.2F, 0.0F);
				}
				renderer.getMainModel().bipedBody.postRender(0.0625F);
				GlStateManager.scale(0.0625F, 0.0625F, 0.0625F);
				// flap faster while flying or falling, slowly while standing; the beat keeps
				// going smoothly when that changes (ageInTicks already counts partial ticks)
				boolean air = !player.onGround;
				float[] st = flaps.get(player.getEntityId());
				if (st == null) {
					if (flaps.size() > 256) {
						flaps.clear();
					}
					st = new float[] { ageInTicks, 0f, 1f, 1f };
					flaps.put(player.getEntityId(), st);
				}
				float dt = ageInTicks - st[0];
				if (dt < 0f || dt > 20f) {
					dt = 0f;
				}
				st[0] = ageInTicks;
				float k = Math.min(1f, dt * 0.12f);
				st[2] += ((air ? 2.4f : 1f) - st[2]) * k;
				st[3] += ((air ? 1.6f : 1f) - st[3]) * k;
				st[1] = (st[1] + dt * st[2]) % 100000f;
				wings(wings, st[1], 1f, st[3]);
			} finally {
				GlStateManager.popMatrix();
			}
		}
		Cosmetics.Item hat = Cosmetics.shown(uuid, Cosmetics.HAT);
		if (hat == null) {
			return;
		}
		GlStateManager.pushMatrix();
		try {
			if (player.isSneaking()) {
				GlStateManager.translate(0.0F, 0.2F, 0.0F);
			}
			renderer.getMainModel().bipedHead.postRender(0.0625F);
			float big = SecretMods.headScale(player);
			GlStateManager.scale(big, big, big);
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

	/**
	 * A hat on its own, for the shop: centered on (x, y) in GUI pixels,
	 * {@code scale} GUI pixels per hat pixel, turned {@code yaw} degrees and
	 * tipped toward the viewer.
	 */
	public static void thumb(Cosmetics.Item it, float x, float y, float scale, float yaw, float time) {
		boolean w = Cosmetics.WINGS.equals(it.category);
		GlStateManager.pushMatrix();
		GlStateManager.translate(x, y, 150f);
		GlStateManager.scale(scale, scale, scale);
		GlStateManager.rotate(-18f, 1f, 0f, 0f);
		if (w) {
			// seen from behind, a little smaller (wings are wide)
			GlStateManager.scale(0.62f, 0.62f, 0.62f);
			GlStateManager.rotate(180f + (yaw - 35f) * 0.4f, 0f, 1f, 0f);
			GlStateManager.translate(0f, -2f, -2f);
		} else {
			GlStateManager.rotate(yaw, 0f, 1f, 0f);
			GlStateManager.translate(0f, 10.5f, 0f);
		}
		GlStateManager.clear(256);
		GlStateManager.enableDepth();
		GlStateManager.depthMask(true);
		try {
			if (w) {
				wings(it, time, 1f, 1f);
			} else if (Cosmetics.MODEL.equals(it.render)) {
				model(it);
			} else {
				draw(it.render, it.color, time);
			}
		} finally {
			GlStateManager.disableLighting();
			GlStateManager.disableDepth();
			GlStateManager.popMatrix();
			GlStateManager.enableTexture2D();
			GlStateManager.color(1f, 1f, 1f, 1f);
		}
	}

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
		case "cap":
			cap(tint < 0 ? 0x2D9CDB : tint);
			break;
		case "headphones":
			headphones(tint < 0 ? 0x3FD8E6 : tint);
			break;
		case "bunny":
			bunny(tint < 0 ? 0xF4F4F4 : tint);
			break;
		case "wizard":
			wizard(tint < 0 ? 0x3B2A8C : tint);
			break;
		case "viking":
			viking(tint < 0 ? 0x8A929B : tint);
			break;
		case "cowboy":
			cowboy(tint < 0 ? 0x8B5A2B : tint);
			break;
		case "propeller":
			propeller(time);
			break;
		case "chef":
			chef(tint < 0 ? 0xFFFFFF : tint);
			break;
		default:
			break;
		}
		t.draw();
		GlStateManager.enableCull();
		GlStateManager.enableLighting();
		GlStateManager.enableTexture2D();
	}

	// ==================== wings ====================

	/**
	 * Draws a pair of wings in body space (pixels, y down from the neck, +z
	 * behind the back). Built-in looks are pixel shapes; a model is the right
	 * wing from the server (y up from the waist, z 0 at the back), mirrored.
	 * {@code speed} and {@code amount} make the flap faster and wider.
	 */
	public static void wings(Cosmetics.Item it, float time, float speed, float amount) {
		boolean model = Cosmetics.MODEL.equals(it.render);
		// a real flap: the wings sweep back and forth (about a second and a half per beat,
		// faster in the air) and the tips rise and dip with it
		float beat = (float) Math.sin(time * 0.22f * speed);
		float flap = 30f + 24f * amount * beat;
		float lift = 9f * amount * beat;
		for (int side = -1; side <= 1; side += 2) {
			GlStateManager.pushMatrix();
			try {
				if (model) {
					GlStateManager.translate(0f, 12f, 2f);
				} else {
					GlStateManager.translate(0f, 0f, 2.1f);
				}
				GlStateManager.scale(side, 1f, 1f);
				if (!model) {
					GlStateManager.translate(0.6f, 0f, 0f);
				}
				GlStateManager.rotate(-flap, 0f, 1f, 0f);
				GlStateManager.rotate(model ? lift : -lift, 0f, 0f, 1f);
				if (model) {
					model(it);
				} else {
					wing(it.render, it.color);
				}
			} finally {
				GlStateManager.popMatrix();
			}
		}
	}

	/** Width of each built-in wing in pixels. */
	static int wingWidth(String render) {
		return "butterfly".equals(render) || "bat".equals(render) ? 11 : 13;
	}

	/**
	 * One pixel of a built-in wing: u goes out from the root (0..width-1), v
	 * is the body's y (negative is above the shoulders). 0 is empty, then
	 * 1 main color, 2 edge/bone, 3 accent, 4 shade.
	 */
	static int wingCell(String render, int u, int v) {
		int w = wingWidth(render);
		if (u < 0 || u >= w) {
			return 0;
		}
		switch (render) {
		case "angel": {
			int top = 2 - Math.round(5f * (float) Math.sin(Math.min(1f, u / (float) (w - 2)) * Math.PI / 2));
			if (u == w - 1) {
				top += 2;
			}
			int len = Math.round(12f - 0.62f * u) + (u % 2 == 0 ? 1 : 0);
			int d = v - top;
			if (d < 0 || d >= len) {
				return 0;
			}
			if (d == 0) {
				return 2;
			}
			return u % 3 == 2 && d > len * 0.4f ? 4 : d > len - 2 ? 4 : 1;
		}
		case "butterfly": {
			float x = u + 0.5f, y = v + 0.5f;
			boolean upper = sq((x - 5.6f) / 5.6f) + sq((y + 1.8f) / 5.2f) <= 1f;
			boolean lower = sq((x - 3.6f) / 3.8f) + sq((y - 6.5f) / 4.2f) <= 1f;
			if (!upper && !lower) {
				return 0;
			}
			boolean edge = false;
			for (int k = 0; k < 4 && !edge; k++) {
				int nu = u + (k == 0 ? 1 : k == 1 ? -1 : 0), nv = v + (k == 2 ? 1 : k == 3 ? -1 : 0);
				float nx = nu + 0.5f, ny = nv + 0.5f;
				edge = nu >= 0 && !(sq((nx - 5.6f) / 5.6f) + sq((ny + 1.8f) / 5.2f) <= 1f
						|| sq((nx - 3.6f) / 3.8f) + sq((ny - 6.5f) / 4.2f) <= 1f);
			}
			if (edge) {
				return 2;
			}
			if (sq(x - 7.2f) + sq(y + 2.2f) <= 2.3f || sq(x - 3.5f) + sq(y - 7.5f) <= 1.2f) {
				return 3;
			}
			return upper ? 1 : 4;
		}
		default: { // demon, dragon, bat: bones with skin between
			int top = 2 - Math.round(u * ("bat".equals(render) ? 0.45f : 0.6f));
			int period = Math.max(3, (w + 1) / 3);
			int f = u % period;
			boolean finger = f == period - 1 || u == w - 1;
			float dip = finger ? 0f : (float) Math.sin(Math.PI * (f + 1) / period) * ("bat".equals(render) ? 3.2f : 4f);
			int bottom = Math.round(("bat".equals(render) ? 8.5f : 10.5f) - 0.3f * u - dip);
			if ("demon".equals(render) && finger && v == top - 1) {
				return 2; // spikes
			}
			if ("dragon".equals(render) && u == w - 1 && v >= top - 2 && v < top) {
				return 3; // claw
			}
			if (v < top || v > bottom) {
				return 0;
			}
			if (v == top || finger) {
				return 2;
			}
			return "dragon".equals(render) && (u + v) % 4 == 0 ? 4 : 1;
		}
		}
	}

	private static float sq(float f) {
		return f * f;
	}

	/** The colors of a built-in wing: main, edge, accent, shade (the tint replaces main). */
	static int[] wingColors(String render, int tint) {
		int[] c;
		switch (render) {
		case "angel":
			c = new int[] { 0xF4F4F4, 0xFFFFFF, 0xFFFFFF, 0xC9CFD8 };
			break;
		case "demon":
			c = new int[] { 0x8A1414, 0x2A0808, 0xFF5A2A, 0x5E0D0D };
			break;
		case "dragon":
			c = new int[] { 0x2F7D3A, 0x173F1D, 0xEDE6CF, 0x256530 };
			break;
		case "butterfly":
			c = new int[] { 0x3FA9F5, 0x15182B, 0xFFFFFF, 0xFF8A3D };
			break;
		default: // bat
			c = new int[] { 0x34343F, 0x16161C, 0x9A2A2A, 0x2A2A33 };
			break;
		}
		if (tint >= 0) {
			c[0] = tint;
			c[3] = "butterfly".equals(render) ? mixRgb(tint, 0xFF8A3D, 0.5f) : mixRgb(tint, 0x000000, 0.22f);
			if ("angel".equals(render)) {
				c[1] = mixRgb(tint, 0xFFFFFF, 0.45f);
			}
		}
		return c;
	}

	static int mixRgb(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bl;
	}

	/** One built-in wing going out along +x from the root, a pixel-thick slab. */
	static void wing(String render, int tint) {
		int[] colors = wingColors(render, tint);
		GlStateManager.disableTexture2D();
		GlStateManager.disableLighting();
		GlStateManager.disableCull();
		GlStateManager.color(1f, 1f, 1f, 1f);
		Tessellator t = Tessellator.getInstance();
		wr = t.getWorldRenderer();
		wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
		int w = wingWidth(render);
		for (int u = 0; u < w; u++) {
			// one box per run of the same color down the column
			int start = -12, cur = 0;
			for (int v = -12; v <= 16; v++) {
				int c = v == 16 ? 0 : wingCell(render, u, v);
				if (c != cur) {
					if (cur != 0) {
						box(u, start, 0f, u + 1, v, 0.7f, colors[cur - 1]);
					}
					cur = c;
					start = v;
				}
			}
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
			// push each face a hair outward (a little more for later cubes), so the two
			// sides of a flat plane (a brim) and faces of touching cubes never z-fight
			float[] n = outward(q, i);
			float push = 0.012f + Math.min(40, i / 120) * 0.003f;
			float ox = n[0] * push, oy = n[1] * push, oz = n[2] * push;
			for (int c = 0; c < 4; c++) {
				int k = i + c * 5;
				wr.pos(q[k] + ox, q[k + 1] + oy, q[k + 2] + oz).tex(q[k + 3], q[k + 4]).color(shade, shade, shade, 1f).endVertex();
			}
		}
		t.draw();
		GlStateManager.popMatrix();
		GlStateManager.disableBlend();
		GlStateManager.enableCull();
		GlStateManager.enableLighting();
		return true;
	}

	/** The quad's outward unit normal (corners go clockwise seen from outside). */
	private static float[] outward(float[] q, int i) {
		float ax = q[i + 5] - q[i], ay = q[i + 6] - q[i + 1], az = q[i + 7] - q[i + 2];
		float bx = q[i + 15] - q[i], by = q[i + 16] - q[i + 1], bz = q[i + 17] - q[i + 2];
		float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1e-6f) {
			return new float[3];
		}
		return new float[] { -nx / len, -ny / len, -nz / len };
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

	// the front of the head is -z

	private static void cap(int c) {
		box(-4.3f, -9.4f, -4.3f, 4.3f, -7.6f, 4.3f, c);
		box(-1.6f, -9.9f, -1.6f, 1.6f, -9.4f, 1.6f, shade(c, 0.85f)); // top
		box(-4.1f, -7.9f, -8.2f, 4.1f, -7.3f, -4.3f, shade(c, 0.8f)); // brim
		box(-1.2f, -9.0f, -4.5f, 1.2f, -8.0f, -4.3f, 0xFFFFFF); // logo
	}

	private static void headphones(int c) {
		int band = 0x23272B;
		box(-4.7f, -9.1f, -0.8f, 4.7f, -8.3f, 0.8f, band);
		for (int s = -1; s <= 1; s += 2) {
			float x = s * 4.5f;
			box(Math.min(x, x + s * 0.4f), -8.3f, -0.8f, Math.max(x, x + s * 0.4f), -5.6f, 0.8f, band);
			box(s < 0 ? -5.8f : 4.3f, -6.2f, -2.0f, s < 0 ? -4.3f : 5.8f, -2.4f, 2.0f, c);
			box(s < 0 ? -6.1f : 5.8f, -5.6f, -1.4f, s < 0 ? -5.8f : 6.1f, -3.0f, 1.4f, shade(c, 0.7f));
		}
	}

	private static void bunny(int c) {
		int inner = 0xF7A1C4;
		for (int s = -1; s <= 1; s += 2) {
			float x = s * 2.0f;
			box(x - 1.0f, -12.0f, -0.4f, x + 1.0f, -8.0f, 0.6f, c);
			box(x + s * 0.4f - 0.9f, -15.5f, -0.4f, x + s * 0.4f + 0.9f, -12.0f, 0.6f, c);
			box(x - 0.5f, -11.6f, -0.6f, x + 0.5f, -8.4f, -0.4f, inner);
			box(x + s * 0.4f - 0.45f, -15.0f, -0.6f, x + s * 0.4f + 0.45f, -12.0f, -0.4f, inner);
		}
	}

	private static void wizard(int c) {
		box(-6.2f, -8.6f, -6.2f, 6.2f, -7.9f, 6.2f, c); // brim
		int layers = 8;
		for (int i = 0; i < layers; i++) {
			float half = 3.9f - i * 0.45f;
			float lean = i > 4 ? (i - 4) * 0.55f : 0f; // the tip flops back
			float y1 = -8.6f - i * 1.15f;
			box(-half, y1 - 1.15f, -half + lean, half, y1, half + lean, i == 1 ? 0xE8C547 : c);
		}
		int star = 0xFFE066;
		box(-2.2f, -12.0f, -3.2f, -1.4f, -11.2f, -3.0f, star);
		box(1.0f, -10.6f, -3.6f, 1.8f, -9.8f, -3.4f, star);
		box(-0.6f, -14.0f, -2.4f, 0.2f, -13.2f, -2.2f, star);
	}

	private static void viking(int metal) {
		int horn = 0xEDE4CC;
		box(-4.6f, -9.4f, -4.6f, 4.6f, -6.2f, 4.6f, metal);
		box(-3.4f, -10.2f, -3.4f, 3.4f, -9.4f, 3.4f, shade(metal, 1.1f));
		box(-4.8f, -6.8f, -4.8f, 4.8f, -5.8f, 4.8f, 0x6B4A2B); // leather rim
		box(-0.5f, -6.0f, -4.9f, 0.5f, -3.6f, -4.6f, metal); // nose guard
		box(-0.4f, -10.3f, -3.6f, 0.4f, -6.2f, 3.6f, shade(metal, 0.8f)); // ridge
		for (int s = -1; s <= 1; s += 2) {
			box(s < 0 ? -6.4f : 4.6f, -8.6f, -0.8f, s < 0 ? -4.6f : 6.4f, -7.2f, 0.8f, horn);
			box(s < 0 ? -7.4f : 6.0f, -10.6f, -0.6f, s < 0 ? -6.0f : 7.4f, -8.6f, 0.6f, horn);
			box(s < 0 ? -7.8f : 6.8f, -12.0f, -0.4f, s < 0 ? -6.8f : 7.8f, -10.6f, 0.4f, shade(horn, 0.85f));
		}
	}

	private static void cowboy(int c) {
		int band = 0x3A2414;
		box(-7.0f, -8.4f, -6.0f, 7.0f, -7.8f, 6.0f, c); // brim
		box(-7.0f, -9.4f, -6.0f, -6.0f, -8.4f, 6.0f, c); // curled sides
		box(6.0f, -9.4f, -6.0f, 7.0f, -8.4f, 6.0f, c);
		box(-3.8f, -12.2f, -3.8f, 3.8f, -8.4f, 3.8f, c);
		box(-3.9f, -9.4f, -3.9f, 3.9f, -8.4f, 3.9f, band);
		box(-0.9f, -12.3f, -3.9f, 0.9f, -11.6f, 3.9f, shade(c, 0.75f)); // the dent
	}

	private static void propeller(float time) {
		int[] q = { 0xE84A5F, 0xFFD166, 0x118AB2, 0x06D6A0 };
		box(-4.4f, -9.3f, -4.4f, 0f, -7.6f, 0f, q[0]);
		box(0f, -9.3f, -4.4f, 4.4f, -7.6f, 0f, q[1]);
		box(0f, -9.3f, 0f, 4.4f, -7.6f, 4.4f, q[2]);
		box(-4.4f, -9.3f, 0f, 0f, -7.6f, 4.4f, q[3]);
		box(-4.6f, -7.9f, -4.6f, 4.6f, -7.3f, 4.6f, 0x2A2A2A); // band
		box(-0.3f, -11.0f, -0.3f, 0.3f, -9.3f, 0.3f, 0x333333); // stem
		double a = time * 0.6;
		for (int b = 0; b < 2; b++) {
			blade(a + b * Math.PI, -11.0f, 4.8f, 0.9f, b == 0 ? 0xE84A5F : 0x118AB2);
		}
	}

	/** A flat spinning blade from the middle, at height y. */
	private static void blade(double a, float y, float len, float half, int rgb) {
		float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
		float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
		float px = -sa * half, pz = ca * half; // across the blade
		float ex = ca * len, ez = sa * len; // along it
		float y0 = y - 0.25f, y1 = y + 0.25f;
		face(r, g, b, 1.0f, px, y0, pz, ex + px, y0, ez + pz, ex - px, y0, ez - pz, -px, y0, -pz);
		face(r, g, b, 0.5f, -px, y1, -pz, ex - px, y1, ez - pz, ex + px, y1, ez + pz, px, y1, pz);
	}

	private static void chef(int c) {
		box(-4.4f, -9.8f, -4.4f, 4.4f, -7.6f, 4.4f, shade(c, 0.95f)); // band
		box(-5.2f, -13.8f, -5.2f, 5.2f, -9.8f, 5.2f, c);
		box(-3.6f, -14.8f, -3.6f, 3.6f, -13.8f, 3.6f, c);
		box(-5.4f, -12.6f, -1.6f, 5.4f, -10.6f, 1.6f, c); // puffs
		box(-1.6f, -12.6f, -5.4f, 1.6f, -10.6f, 5.4f, c);
	}

	private static int shade(int rgb, float k) {
		int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * k));
		int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * k));
		int b = Math.min(255, (int) ((rgb & 0xFF) * k));
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
