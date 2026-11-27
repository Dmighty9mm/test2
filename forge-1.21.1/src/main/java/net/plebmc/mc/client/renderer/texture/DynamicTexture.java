package net.plebmc.mc.client.renderer.texture;

import com.mojang.blaze3d.platform.NativeImage;

/** 1.8's DynamicTexture: an ARGB int[] you edit, then upload. */
public class DynamicTexture implements ITextureObject {
	private final int w, h;
	private final int[] data;
	private net.minecraft.client.renderer.texture.DynamicTexture tex;

	public DynamicTexture(int w, int h) {
		this.w = w;
		this.h = h;
		this.data = new int[w * h];
	}

	public DynamicTexture(java.awt.image.BufferedImage img) {
		this(img.getWidth(), img.getHeight());
		img.getRGB(0, 0, w, h, data, 0, w);
		updateDynamicTexture();
	}

	public int[] getTextureData() {
		return data;
	}

	public net.minecraft.client.renderer.texture.DynamicTexture real() {
		if (tex == null) {
			tex = new net.minecraft.client.renderer.texture.DynamicTexture(new NativeImage(w, h, true));
			copy();
		}
		return tex;
	}

	private void copy() {
		NativeImage img = tex.getPixels();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = data[y * w + x];
				// NativeImage stores ABGR
				img.setPixelRGBA(x, y, (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16));
			}
		}
	}

	public void updateDynamicTexture() {
		if (!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
			com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(this::updateDynamicTexture);
			return;
		}
		real();
		copy();
		tex.upload();
	}

	@Override
	public void setBlurMipmap(boolean blur, boolean mipmap) {
		real().setFilter(blur, mipmap);
	}

	@Override
	public int getGlTextureId() {
		return real().getId();
	}

	public void deleteGlTexture() {
		if (tex != null) {
			tex.close();
			tex = null;
		}
	}
}
