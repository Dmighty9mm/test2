package net.plebmc.mc.client.renderer.texture;

import com.mojang.blaze3d.systems.RenderSystem;

import net.plebmc.mc.util.ResourceLocation;

/** 1.8's TextureManager over 1.21.1's. */
public class TextureManager {
	private net.minecraft.client.renderer.texture.TextureManager real() {
		return net.minecraft.client.Minecraft.getInstance().getTextureManager();
	}

	private static final java.util.Set<net.minecraft.resources.ResourceLocation> JPGS = new java.util.HashSet<>();

	/** 1.21 only reads PNG textures; a .jpg (the PlebMc wallpaper) is decoded here once. */
	private void loadJpg(net.minecraft.resources.ResourceLocation rl) {
		if (!JPGS.add(rl)) {
			return;
		}
		try (var in = net.minecraft.client.Minecraft.getInstance().getResourceManager().open(rl)) {
			java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
			if (img == null) {
				return;
			}
			int w = img.getWidth(), h = img.getHeight();
			com.mojang.blaze3d.platform.NativeImage ni = new com.mojang.blaze3d.platform.NativeImage(w, h, false);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int c = img.getRGB(x, y);
					// ARGB -> ABGR
					ni.setPixelRGBA(x, y, (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16));
				}
			}
			real().register(rl, new net.minecraft.client.renderer.texture.DynamicTexture(ni));
		} catch (Throwable ignored) {
		}
	}

	public void bindTexture(ResourceLocation rl) {
		if (rl.rl.getPath().endsWith(".jpg")) {
			loadJpg(rl.rl);
		}
		RenderSystem.setShaderTexture(0, rl.rl);
		net.plebmc.mc.R.tex = rl.rl;
		net.plebmc.mc.client.renderer.GlStateManager.enableTexture2D();
	}

	public ITextureObject getTexture(ResourceLocation rl) {
		var t = real().getTexture(rl.rl, null);
		return t == null ? null : new ITextureObject.Wrap(t);
	}

	public boolean loadTexture(ResourceLocation rl, ITextureObject t) {
		if (t instanceof DynamicTexture) {
			real().register(rl.rl, ((DynamicTexture) t).real());
		}
		return true;
	}

	private int dyn;

	public ResourceLocation getDynamicTextureLocation(String name, DynamicTexture t) {
		ResourceLocation rl = new ResourceLocation("plebmc", "dynamic/" + name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_") + "_" + (++dyn));
		real().register(rl.rl, t.real());
		return rl;
	}

	public void deleteTexture(ResourceLocation rl) {
		real().release(rl.rl);
	}
}
