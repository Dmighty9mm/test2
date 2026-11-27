package net.plebmc.mc.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.plebmc.mc.R;

/** 1.8's FontRenderer over 1.21.1's Font (legacy § codes work the same). */
public class FontRenderer {
	public int FONT_HEIGHT = 9;
	public final java.util.Random fontRandom = new java.util.Random();

	public Font font() {
		return R.mc().font;
	}

	public int getStringWidth(String s) {
		return s == null ? 0 : font().width(s);
	}

	public int getCharWidth(char c) {
		return c == '§' ? -1 : font().width(String.valueOf(c));
	}

	public int drawString(String s, int x, int y, int color) {
		return drawString(s, (float) x, (float) y, color, false);
	}

	public int drawStringWithShadow(String s, float x, float y, int color) {
		return drawString(s, x, y, color, true);
	}

	public int drawString(String s, int x, int y, int color, boolean shadow) {
		return drawString(s, (float) x, (float) y, color, shadow);
	}

	public int drawString(String s, float x, float y, int color, boolean shadow) {
		if (s == null) {
			return (int) x;
		}
		com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		if (R.gg != null && R.worldPose == null) {
			return R.gg.drawString(font(), s, x, y, color, shadow);
		}
		if (R.worldBuffers != null) {
			font().drawInBatch(s, x, y, color, shadow, R.pose().last().pose(), R.worldBuffers, Font.DisplayMode.NORMAL, 0,
					R.worldLight);
		}
		return (int) x + getStringWidth(s);
	}

	public String trimStringToWidth(String s, int w) {
		return font().plainSubstrByWidth(s, w);
	}

	public String trimStringToWidth(String s, int w, boolean reverse) {
		if (!reverse) {
			return trimStringToWidth(s, w);
		}
		String r = new StringBuilder(s).reverse().toString();
		return new StringBuilder(font().plainSubstrByWidth(r, w)).reverse().toString();
	}

	public int splitStringWidth(String s, int w) {
		return FONT_HEIGHT * listFormattedStringToWidth(s, w).size();
	}

	public boolean getUnicodeFlag() {
		return false;
	}

	/** 1.8's word wrap: color codes carry over to the next line. */
	public List<String> listFormattedStringToWidth(String s, int w) {
		List<String> out = new ArrayList<>();
		for (String para : s.split("\n", -1)) {
			wrap(para, w, out);
		}
		return out;
	}

	private void wrap(String s, int w, List<String> out) {
		String carry = "";
		while (true) {
			String line = carry + s;
			if (getStringWidth(line) <= w || s.isEmpty()) {
				out.add(line);
				return;
			}
			int cut = sizeFor(line, w);
			if (cut <= carry.length()) {
				cut = Math.min(line.length(), carry.length() + 1);
			}
			String head = line.substring(0, cut);
			char next = cut < line.length() ? line.charAt(cut) : ' ';
			int sp = head.lastIndexOf(' ');
			boolean brokeAtSpace = false;
			if (next != ' ' && sp > carry.length()) {
				head = head.substring(0, sp);
				cut = sp;
				brokeAtSpace = true;
			}
			out.add(head);
			String rest = line.substring(cut);
			if (brokeAtSpace || rest.startsWith(" ")) {
				rest = rest.startsWith(" ") ? rest.substring(1) : rest;
			}
			carry = formats(head);
			s = rest;
		}
	}

	private int sizeFor(String s, int w) {
		int i = 0;
		int width = 0;
		boolean bold = false;
		while (i < s.length()) {
			char c = s.charAt(i);
			if (c == '§' && i + 1 < s.length()) {
				char k = Character.toLowerCase(s.charAt(i + 1));
				if (k == 'l') {
					bold = true;
				} else if (k == 'r' || "0123456789abcdef".indexOf(k) >= 0) {
					bold = false;
				}
				i += 2;
				continue;
			}
			int cw = font().width(String.valueOf(c)) + (bold ? 1 : 0);
			if (width + cw > w) {
				break;
			}
			width += cw;
			i++;
		}
		return i;
	}

	private static String formats(String s) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < s.length() - 1; i++) {
			if (s.charAt(i) == '§') {
				char k = Character.toLowerCase(s.charAt(i + 1));
				if ("0123456789abcdefr".indexOf(k) >= 0) {
					b.setLength(0);
					if (k != 'r') {
						b.append('§').append(k);
					}
				} else if ("klmno".indexOf(k) >= 0) {
					b.append('§').append(k);
				}
				i++;
			}
		}
		return b.toString();
	}
}
