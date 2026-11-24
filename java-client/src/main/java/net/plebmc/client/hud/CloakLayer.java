package net.plebmc.client.hud;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.renderer.GlStateManager;
import net.plebmc.forge.compat.ImageData;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.plebmc.client.cosmetics.Cosmetics;

/**
 * Cloak cosmetics: the cape model with the server's texture (any multiple of
 * the 64x32 cape layout, so 512x256 and up draw in full detail because the
 * model's UVs are fractions of the image) or a plain colored cloak. Moves
 * exactly like a vanilla cape, and replaces one while it is worn.
 */
public final class CloakLayer implements LayerRenderer<AbstractClientPlayer> {

	private final RenderPlayer renderer;

	public CloakLayer(RenderPlayer renderer) {
		this.renderer = renderer;
	}

	/** True when a cloak cosmetic draws for this player (the vanilla cape layer then stays out of the way). */
	public static boolean covers(AbstractClientPlayer p) {
		try {
			return !Cosmetics.nothingToDraw() && Cosmetics.shown(PlebEntities.uuid(p), Cosmetics.CLOAK) != null;
		} catch (Throwable t) {
			return false;
		}
	}

	// ==================== textures ====================

	private static final Map<String, ResourceLocation> loaded = new HashMap<>();
	private static final Map<String, Boolean> failed = new HashMap<>();

	/** The texture to bind for a cloak (its HD image once downloaded, a plain one meanwhile). */
	static ResourceLocation texture(Cosmetics.Item it) {
		if (it.texture != null) {
			ResourceLocation rl = loaded.get(it.texture);
			if (rl != null) {
				return rl;
			}
			byte[] png = failed.containsKey(it.texture) ? null : Cosmetics.texture(it.texture);
			if (png != null) {
				try {
					ImageData img = ImageData.loadImageFile(png);
					if (img != null && img.width == img.height * 2 && img.width >= 64) {
						rl = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("plebcloak_" + it.texture,
								new DynamicTexture(img.toBufferedImage()));
						loaded.put(it.texture, rl);
						return rl;
					}
				} catch (Throwable t) {
					// fall through to the plain look
				}
				failed.put(it.texture, Boolean.TRUE);
			}
		}
		return plain(it.color < 0 ? 0x5A6B7A : it.color);
	}

	/** A 64x32 cape in one color with a darker rim, made once per color. */
	private static ResourceLocation plain(int rgb) {
		String key = "plain_" + Integer.toHexString(rgb);
		ResourceLocation rl = loaded.get(key);
		if (rl != null) {
			return rl;
		}
		DynamicTexture t = new DynamicTexture(64, 32);
		int[] px = t.getTextureData();
		int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 64; x++) {
				// the main face is x 1..10, y 1..16: a subtle top-to-bottom shade with a rim
				boolean face = x >= 1 && x <= 10 && y >= 1 && y <= 16;
				boolean rim = face && (x == 1 || x == 10 || y == 16);
				float k = rim ? 0.7f : face ? 1.08f - y * 0.018f : 0.75f;
				px[y * 64 + x] = abgr(Math.min(255, (int) (r * k)), Math.min(255, (int) (g * k)), Math.min(255, (int) (b * k)));
			}
		}
		t.updateDynamicTexture();
		rl = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("plebcloak_" + key, t);
		loaded.put(key, rl);
		return rl;
	}

	/** Texture data is uploaded as RGBA bytes, so one int is 0xAABBGGRR. */
	private static int abgr(int r, int g, int b) {
		return 0xFF000000 | b << 16 | g << 8 | r;
	}

	/** The cloak's art (the back face) in a GUI rect, for shop cards. */
	static void thumb(Cosmetics.Item it, int x, int y, int w, int h) {
		Minecraft.getMinecraft().getTextureManager().bindTexture(texture(it));
		GlStateManager.enableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.color(1f, 1f, 1f, 1f);
		Tessellator t = Tessellator.getInstance();
		WorldRenderer wr = t.getWorldRenderer();
		float u0 = 1f / 64f, u1 = 11f / 64f, v0 = 1f / 32f, v1 = 17f / 32f;
		wr.begin(7, DefaultVertexFormats.POSITION_TEX);
		wr.pos(x, y + h, 0).tex(u0, v1).endVertex();
		wr.pos(x + w, y + h, 0).tex(u1, v1).endVertex();
		wr.pos(x + w, y, 0).tex(u1, v0).endVertex();
		wr.pos(x, y, 0).tex(u0, v0).endVertex();
		t.draw();
	}

	// ==================== the layer ====================

	@Override
	public void doRenderLayer(AbstractClientPlayer p, float limbSwing, float limbSwingAmount, float f, float ageInTicks,
			float headYaw, float headPitch, float scale) {
		if (Cosmetics.nothingToDraw() || p.isInvisible() || !(renderer.getMainModel() instanceof ModelPlayer)) {
			return;
		}
		Cosmetics.Item it = Cosmetics.shown(PlebEntities.uuid(p), Cosmetics.CLOAK);
		if (it == null) {
			return;
		}
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		renderer.bindTexture(texture(it));
		GlStateManager.pushMatrix();
		try {
			// the vanilla cape's swing, so a cloak moves like any cape
			GlStateManager.translate(0.0F, 0.0F, 0.125F);
			double d0 = p.prevChasingPosX + (p.chasingPosX - p.prevChasingPosX) * (double) f
					- (p.prevPosX + (p.posX - p.prevPosX) * (double) f);
			double d1 = p.prevChasingPosY + (p.chasingPosY - p.prevChasingPosY) * (double) f
					- (p.prevPosY + (p.posY - p.prevPosY) * (double) f);
			double d2 = p.prevChasingPosZ + (p.chasingPosZ - p.prevChasingPosZ) * (double) f
					- (p.prevPosZ + (p.posZ - p.prevPosZ) * (double) f);
			float f1 = p.prevRenderYawOffset + (p.renderYawOffset - p.prevRenderYawOffset) * f;
			double d3 = (double) MathHelper.sin(f1 * 3.1415927F / 180.0F);
			double d4 = (double) (-MathHelper.cos(f1 * 3.1415927F / 180.0F));
			float f2 = (float) d1 * 10.0F;
			f2 = MathHelper.clamp_float(f2, -6.0F, 32.0F);
			float f3 = (float) (d0 * d3 + d2 * d4) * 100.0F;
			float f4 = (float) (d0 * d4 - d2 * d3) * 100.0F;
			if (f3 < 0.0F) {
				f3 = 0.0F;
			}
			float f5 = p.prevCameraYaw + (p.cameraYaw - p.prevCameraYaw) * f;
			f2 = f2 + MathHelper.sin((p.prevDistanceWalkedModified
					+ (p.distanceWalkedModified - p.prevDistanceWalkedModified) * f) * 6.0F) * 32.0F * f5;
			if (p.isSneaking()) {
				f2 += 25.0F;
			}
			GlStateManager.rotate(6.0F + f3 / 2.0F + f2, 1.0F, 0.0F, 0.0F);
			GlStateManager.rotate(f4 / 2.0F, 0.0F, 0.0F, 1.0F);
			GlStateManager.rotate(-f4 / 2.0F, 0.0F, 1.0F, 0.0F);
			GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
			((ModelPlayer) renderer.getMainModel()).renderCape(0.0625F);
		} finally {
			GlStateManager.popMatrix();
		}
	}

	@Override
	public boolean shouldCombineTextures() {
		return false;
	}
}
