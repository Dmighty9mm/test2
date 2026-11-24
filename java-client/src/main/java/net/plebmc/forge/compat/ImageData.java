package net.plebmc.forge.compat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

/** Decoded image (ARGB pixels), the Java Edition version of Eaglercraft's ImageData. */
public final class ImageData {
	public final int width;
	public final int height;
	public final int[] pixels;
	public final boolean alpha;

	public ImageData(int width, int height, int[] pixels, boolean alpha) {
		this.width = width;
		this.height = height;
		this.pixels = pixels;
		this.alpha = alpha;
	}

	public static ImageData loadImageFile(byte[] data) {
		try {
			BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
			if (img == null) {
				return null;
			}
			int w = img.getWidth(), h = img.getHeight();
			int[] px = img.getRGB(0, 0, w, h, null, 0, w);
			return new ImageData(w, h, px, true);
		} catch (Throwable t) {
			return null;
		}
	}

	public BufferedImage toBufferedImage() {
		BufferedImage b = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		b.setRGB(0, 0, width, height, pixels, 0, width);
		return b;
	}
}
