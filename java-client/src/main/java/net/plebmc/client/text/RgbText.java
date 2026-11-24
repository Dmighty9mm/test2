package net.plebmc.client.text;

/**
 * RGB (hex) color codes inside legacy {@code §} text. 1.8 only knows the 16
 * {@code §0-§f} colors, so newer servers' hex colors and gradients reach a 1.8
 * player already rounded to the nearest one. PlebClient understands two ways
 * of carrying the real color in legacy text:
 *
 * <ul>
 * <li><b>Spigot/Bungee hex</b>: {@code §x§R§R§G§G§B§B} (14 chars, full
 * 24-bit color). This is what plugins use for 1.16+ legacy text.</li>
 * <li><b>Compact</b>: {@code §} followed by one private-use character
 * {@code U+E000-U+EFFF} that holds a 12-bit color ({@code 0xE000 | r<<8 |
 * g<<4 | b}, each 0-15). Two chars per color, so gradients still fit 1.8's
 * 16-char scoreboard prefixes. The PlebColors server plugin sends this.</li>
 * </ul>
 *
 * Both start with {@code §}, so code that skips {@code §} plus the next
 * char when measuring text keeps working. Like a normal color code, an RGB
 * code resets bold/italic/etc. A vanilla client shows these as plain white,
 * so the server plugin only sends them to PlebClient players.
 */
public final class RgbText {

	public static final char SECTION = '§';
	public static final char COMPACT_MIN = '';
	public static final char COMPACT_MAX = '';

	private RgbText() {
	}

	/**
	 * The 24-bit color of the RGB code starting at {@code i} (which must be a
	 * {@code §}), or -1 when there is no RGB code there.
	 */
	public static int rgbAt(String s, int i) {
		int n = s.length();
		if (i + 1 >= n || s.charAt(i) != SECTION) {
			return -1;
		}
		char c = s.charAt(i + 1);
		if (c >= COMPACT_MIN && c <= COMPACT_MAX) {
			int v = c - COMPACT_MIN;
			return ((v >> 8) & 15) * 0x110000 | ((v >> 4) & 15) * 0x1100 | (v & 15) * 0x11;
		}
		if ((c != 'x' && c != 'X') || i + 14 > n) {
			return -1;
		}
		int rgb = 0;
		for (int k = 0; k < 6; ++k) {
			int p = i + 2 + k * 2;
			if (s.charAt(p) != SECTION) {
				return -1;
			}
			int d = Character.digit(s.charAt(p + 1), 16);
			if (d < 0) {
				return -1;
			}
			rgb = (rgb << 4) | d;
		}
		return rgb;
	}

	/** How many chars the RGB code at {@code i} takes (14 or 2), or 0 for none. */
	public static int codeLength(String s, int i) {
		if (rgbAt(s, i) < 0) {
			return 0;
		}
		char c = s.charAt(i + 1);
		return (c == 'x' || c == 'X') ? 14 : 2;
	}

	/**
	 * The color to draw for the RGB code at {@code i}: the real color, or the
	 * nearest of the 16 legacy colors (from {@code legacy[0..15]}) when the
	 * player turned RGB text off.
	 */
	public static int displayColor(String s, int i, int[] legacy) {
		int rgb = rgbAt(s, i);
		if (rgb < 0) {
			return 0xFFFFFF;
		}
		if (net.plebmc.client.mods.ClientPrefs.get().rgbText || legacy == null || legacy.length < 16) {
			return rgb;
		}
		return legacy[nearestLegacy(rgb, legacy)] & 0xFFFFFF;
	}

	/** Index 0-15 of the legacy color closest to {@code rgb}. */
	public static int nearestLegacy(int rgb, int[] legacy) {
		int best = 15;
		long bestD = Long.MAX_VALUE;
		int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
		for (int k = 0; k < 16; ++k) {
			int c = legacy[k];
			long dr = r - ((c >> 16) & 255), dg = g - ((c >> 8) & 255), db = b - (c & 255);
			long d = dr * dr + dg * dg + db * db;
			if (d < bestD) {
				bestD = d;
				best = k;
			}
		}
		return best;
	}

	/** The shadow color vanilla uses for text: a quarter of the brightness. */
	public static int shadow(int rgb) {
		return (rgb & 0xFCFCFC) >> 2;
	}

	/** Spigot/Bungee {@code §x§R§R§G§G§B§B} for a 24-bit color. */
	public static String hexCode(int rgb) {
		StringBuilder sb = new StringBuilder(14).append(SECTION).append('x');
		String h = Integer.toHexString(0x1000000 | (rgb & 0xFFFFFF)).substring(1);
		for (int k = 0; k < 6; ++k) {
			sb.append(SECTION).append(h.charAt(k));
		}
		return sb.toString();
	}

	/** Compact two-char code for a 24-bit color (rounded to 12 bits). */
	public static String compactCode(int rgb) {
		int r = (((rgb >> 16) & 255) * 15 + 127) / 255;
		int g = (((rgb >> 8) & 255) * 15 + 127) / 255;
		int b = ((rgb & 255) * 15 + 127) / 255;
		return new String(new char[] { SECTION, (char) (COMPACT_MIN + (r << 8 | g << 4 | b)) });
	}

	/**
	 * The last color/format codes in {@code s}, so a wrapped line keeps its
	 * color: like vanilla {@code FontRenderer.getFormatFromString} but RGB
	 * codes count as colors.
	 */
	public static String formatAtEnd(String s) {
		String color = "";
		StringBuilder special = new StringBuilder();
		int n = s.length();
		for (int i = 0; i < n - 1; ++i) {
			if (s.charAt(i) != SECTION) {
				continue;
			}
			int len = codeLength(s, i);
			if (len > 0) {
				color = s.substring(i, i + len);
				special.setLength(0);
				i += len - 1;
				continue;
			}
			char c = Character.toLowerCase(s.charAt(i + 1));
			if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
				color = "" + SECTION + c;
				special.setLength(0);
			} else if (c == 'r') {
				color = "";
				special.setLength(0);
			} else if (c >= 'k' && c <= 'o') {
				special.append(SECTION).append(c);
			}
			++i;
		}
		return color + special;
	}

	/** {@code s} without any color/format codes, RGB ones included. */
	public static String strip(String s) {
		if (s == null || s.indexOf(SECTION) < 0) {
			return s;
		}
		StringBuilder sb = new StringBuilder(s.length());
		int n = s.length();
		for (int i = 0; i < n; ++i) {
			char c = s.charAt(i);
			if (c == SECTION && i + 1 < n) {
				int len = codeLength(s, i);
				if (len > 0) {
					i += len - 1;
					continue;
				}
				char d = Character.toLowerCase(s.charAt(i + 1));
				if ((d >= '0' && d <= '9') || (d >= 'a' && d <= 'f') || (d >= 'k' && d <= 'o') || d == 'r') {
					++i;
					continue;
				}
			}
			sb.append(c);
		}
		return sb.toString();
	}
}
