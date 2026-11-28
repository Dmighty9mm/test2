package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.lax1dude.eaglercraft.v1_8.opengl.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;

/**
 * Emoji in text. Newer versions can show the emoji players type; 1.8's font
 * stops at U+FFFF, so they came out as blank space. Each emoji (or emoji
 * sequence: flags, joined people) is one 16 pixel picture in
 * {@code textures/gui/emoji.png}, listed in {@code /plebclient/emoji.txt}
 * ("cell codepoint codepoint...", hex, variation selectors and skin tones
 * left out).
 * <p>
 * The font renderer works a character at a time, so {@link #fold} first
 * turns every known sequence into one private-use code point (U+F0000 + its
 * cell), which is still two chars: the first is 9 pixels wide and the second
 * 0. The pictures are collected while the line is drawn and drawn in one go
 * when it is done ({@link #flush}), so the text's own color isn't disturbed.
 */
public final class Emoji {

	private static final ResourceLocation TEXTURE = new ResourceLocation("plebitems", "textures/gui/emoji.png");
	private static final int COLS = 64, ROWS = 32, FIRST = 0xF0000;
	/** The advance of one emoji in pixels (an 8 pixel picture and a gap). */
	public static final int WIDTH = 9;

	private static Map<Integer, List<int[]>> byFirst;
	private static int count;
	private static final Map<String, String> folded = new HashMap<>();

	private static final int MAX = 256;
	private static final int[] cell = new int[MAX];
	private static final float[] px = new float[MAX], py = new float[MAX];
	private static int pending;
	private static char high;
	private static float highX, highY;

	private Emoji() {
	}

	private static boolean surrogate(char c) {
		return c >= 0xD800 && c <= 0xDFFF;
	}

	private static boolean skipped(int cp) {
		return cp == 0xFE0F || (cp >= 0x1F3FB && cp <= 0x1F3FF);
	}

	private static void load() {
		Map<Integer, List<int[]>> m = new HashMap<>();
		try {
			byte[] b = GlCompat.resource("/plebclient/emoji.txt");
			if (b != null) {
				for (String line : new String(b, java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
					String[] p = line.trim().split(" ");
					if (p.length < 2) {
						continue;
					}
					int[] seq = new int[p.length];
					seq[0] = Integer.parseInt(p[0]);
					for (int i = 1; i < p.length; i++) {
						seq[i] = Integer.parseInt(p[i], 16);
					}
					if (seq[0] < 0 || seq[0] >= COLS * ROWS) {
						continue;
					}
					List<int[]> l = m.get(seq[1]);
					if (l == null) {
						m.put(seq[1], l = new ArrayList<>());
					}
					// longest first, so a whole sequence wins over its first emoji
					int at = 0;
					while (at < l.size() && l.get(at).length >= seq.length) {
						at++;
					}
					l.add(at, seq);
					count++;
				}
			}
		} catch (Throwable t) {
			m.clear();
		}
		byFirst = m;
	}

	/** Known emoji sequences become one private-use code point each; other text is unchanged. */
	public static String fold(String s) {
		if (s == null) {
			return null;
		}
		int n = s.length(), first = -1;
		for (int i = 0; i < n; i++) {
			char c = s.charAt(i);
			if (c >= 0xD800 && c <= 0xDBFF) {
				first = i;
				break;
			}
		}
		if (first < 0) {
			return s;
		}
		String hit = folded.get(s);
		if (hit != null) {
			return hit;
		}
		if (byFirst == null) {
			load();
		}
		StringBuilder sb = new StringBuilder(n);
		sb.append(s, 0, first);
		int[] cps = new int[12], ends = new int[12];
		int i = first;
		while (i < n) {
			char c = s.charAt(i);
			if (c == 0xFE0F || c == 0x200D) {
				i++; // joiners and variation selectors on their own draw nothing
				continue;
			}
			if (!(c >= 0xD800 && c <= 0xDBFF) || i + 1 >= n || !Character.isLowSurrogate(s.charAt(i + 1))) {
				sb.append(c);
				i++;
				continue;
			}
			int cp = Character.toCodePoint(c, s.charAt(i + 1));
			List<int[]> list = byFirst.get(cp);
			int used = 0, idx = -1;
			if (list != null) {
				// the code points that follow, without variation selectors and skin tones
				int k = 0, j = i;
				while (k < cps.length && j < n) {
					int q = s.codePointAt(j);
					int len = Character.charCount(q);
					if (k > 0 && skipped(q)) {
						j += len;
						ends[k - 1] = j;
						continue;
					}
					cps[k] = q;
					j += len;
					ends[k] = j;
					k++;
				}
				for (int[] seq : list) {
					int len = seq.length - 1;
					if (len > k) {
						continue;
					}
					boolean same = true;
					for (int a = 1; a < len && same; a++) {
						same = cps[a] == seq[a + 1];
					}
					if (same) {
						idx = seq[0];
						used = ends[len - 1] - i;
						break;
					}
				}
			}
			if (idx >= 0) {
				sb.appendCodePoint(FIRST + idx);
				i += used;
			} else if (skipped(cp)) {
				i += 2; // a skin tone with nothing to color
			} else {
				sb.append(c).append(s.charAt(i + 1));
				i += 2;
			}
		}
		String out = sb.toString();
		if (folded.size() >= 512) {
			folded.clear();
		}
		folded.put(s, out);
		return out;
	}

	/** FontRenderer.getCharWidth for a surrogate: the first half is the emoji, the second nothing. */
	public static int width(char c) {
		return c <= 0xDBFF ? WIDTH : 0;
	}

	public static boolean isSurrogate(char c) {
		return surrogate(c);
	}

	/**
	 * FontRenderer drawing one char of a pair at (x, y): the picture is noted
	 * for {@link #flush} when the second half arrives. Returns the advance.
	 */
	public static float onChar(char c, float x, float y) {
		if (c <= 0xDBFF) {
			high = c;
			highX = x;
			highY = y;
			return WIDTH;
		}
		if (high != 0) {
			int idx = Character.toCodePoint(high, c) - FIRST;
			high = 0;
			if (idx >= 0 && idx < COLS * ROWS && pending < MAX) {
				cell[pending] = idx;
				px[pending] = highX;
				py[pending] = highY;
				pending++;
			}
		}
		return 0;
	}

	/** After a line was drawn: draws its emoji (not for the shadow pass, a picture has none). */
	public static void flush(float alpha, boolean shadow) {
		high = 0;
		if (pending == 0) {
			return;
		}
		int n = pending;
		pending = 0;
		if (shadow || alpha <= 0.02f) {
			return;
		}
		try {
			Minecraft mc = Minecraft.getMinecraft();
			mc.getTextureManager().bindTexture(TEXTURE);
			ITextureObject tex = mc.getTextureManager().getTexture(TEXTURE);
			if (tex != null) {
				tex.setBlurMipmap(true, false);
			}
			GlStateManager.enableTexture2D();
			GlStateManager.enableBlend();
			GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
			GlStateManager.color(1f, 1f, 1f, alpha);
			WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
			wr.begin(7, DefaultVertexFormats.POSITION_TEX);
			for (int i = 0; i < n; i++) {
				float x = px[i], y = py[i] - 0.5f;
				float u0 = (cell[i] % COLS) / (float) COLS, v0 = (cell[i] / COLS) / (float) ROWS;
				float u1 = u0 + 1f / COLS, v1 = v0 + 1f / ROWS;
				wr.pos(x, y + 8, 0).tex(u0, v1).endVertex();
				wr.pos(x + 8, y + 8, 0).tex(u1, v1).endVertex();
				wr.pos(x + 8, y, 0).tex(u1, v0).endVertex();
				wr.pos(x, y, 0).tex(u0, v0).endVertex();
			}
			Tessellator.getInstance().draw();
			GlStateManager.color(1f, 1f, 1f, 1f);
		} catch (Throwable t) {
			// text still draws without the pictures
		}
	}
}
