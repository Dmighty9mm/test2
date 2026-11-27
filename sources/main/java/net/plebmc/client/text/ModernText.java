package net.plebmc.client.text;

/**
 * Letters newer servers use that 1.8's font can't draw well.
 * <p>
 * Scoreboards and chat on newer versions are full of "small caps" (ᴛʜɪꜱ
 * ꜱᴛʏʟᴇ). Newer Minecraft draws them from its main font as short capitals;
 * 1.8 only has them in its tiny unicode pages, so they came out thin and
 * uneven. PlebClient draws them the newer way: {@link #glyph} gives each one a
 * short capital in the 1.8 font's style (5 pixels tall, sitting on the line
 * like a lowercase letter). Typographic quotes and dashes the 1.8 font lacks
 * are drawn as their plain ASCII versions ({@link #fold}).
 */
public final class ModernText {

	private ModernText() {
	}

	private static final char[] TABLE;

	static {
		String[] pairs = {
				"ᴁÆ",
				// typographic punctuation the 1.8 ASCII font lacks
				"‘'", "’'", "“\"", "”\"", "–-", "—-", "•·", "│|",
				"┃|", "‐-", "‑-", "‒-", "  " };
		TABLE = new char[0x10000];
		for (String p : pairs) {
			TABLE[p.charAt(0)] = p.charAt(1);
		}
	}

	// ==================== small caps ====================

	/** Small capitals, then look-alikes servers use for them (each maps to a glyph below). */
	private static final String SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡʏᴢғѕᴩ";
	private static final String SMALL_OF = "ABCDEFGHIJKLMNOPQRSTUVWYZFSP";
	/** 5 rows each, '#' = a pixel. */
	private static final String[][] GLYPHS = {
			{ ".###.", "#...#", "#####", "#...#", "#...#" }, // A
			{ "####.", "#...#", "####.", "#...#", "####." }, // B
			{ ".###.", "#...#", "#....", "#...#", ".###." }, // C
			{ "####.", "#...#", "#...#", "#...#", "####." }, // D
			{ "#####", "#....", "####.", "#....", "#####" }, // E
			{ "#####", "#....", "####.", "#....", "#...." }, // F
			{ ".####", "#....", "#..##", "#...#", ".###." }, // G
			{ "#...#", "#...#", "#####", "#...#", "#...#" }, // H
			{ "###", ".#.", ".#.", ".#.", "###" }, // I
			{ "....#", "....#", "....#", "#...#", ".###." }, // J
			{ "#...#", "#..#.", "###..", "#..#.", "#...#" }, // K
			{ "#....", "#....", "#....", "#....", "#####" }, // L
			{ "#...#", "##.##", "#.#.#", "#...#", "#...#" }, // M
			{ "#...#", "##..#", "#.#.#", "#..##", "#...#" }, // N
			{ ".###.", "#...#", "#...#", "#...#", ".###." }, // O
			{ "####.", "#...#", "####.", "#....", "#...." }, // P
			{ ".###.", "#...#", "#...#", "#..#.", ".##.#" }, // Q
			{ "####.", "#...#", "####.", "#..#.", "#...#" }, // R
			{ ".####", "#....", ".###.", "....#", "####." }, // S
			{ "#####", "..#..", "..#..", "..#..", "..#.." }, // T
			{ "#...#", "#...#", "#...#", "#...#", ".###." }, // U
			{ "#...#", "#...#", ".#.#.", ".#.#.", "..#.." }, // V
			{ "#...#", "#...#", "#.#.#", "##.##", "#...#" }, // W
			{ "#...#", ".#.#.", "..#..", "..#..", "..#.." }, // Y
			{ "#####", "...#.", "..#..", ".#...", "#####" }, // Z
	};
	private static final String GLYPH_OF = "ABCDEFGHIJKLMNOPQRSTUVWYZ";
	/** The top row of a glyph in an 8 pixel cell (rows 2-6, like a lowercase letter). */
	public static final int GLYPH_TOP = 2;

	/** How many glyphs {@link #glyphPixels} has. */
	public static int glyphCount() {
		return GLYPHS.length;
	}

	/** The glyph to draw for {@code c}, or -1 when it's not a small capital. */
	public static int glyph(char c) {
		if (c < 0x0100) {
			return -1;
		}
		int i = SMALL.indexOf(c);
		return i < 0 ? -1 : GLYPH_OF.indexOf(SMALL_OF.charAt(i));
	}

	/** A glyph's width in pixels (the space after it not counted). */
	public static int glyphWidth(int glyph) {
		return GLYPHS[glyph][0].length();
	}

	/** True when glyph {@code g} has a pixel at column {@code x}, row {@code y} (0-4). */
	public static boolean glyphPixel(int g, int x, int y) {
		String row = GLYPHS[g][y];
		return x < row.length() && row.charAt(x) == '#';
	}

	/** The character to draw in place of {@code c} (itself when it draws fine). */
	public static char fold(char c) {
		if (c < 0x00A0) {
			return c;
		}
		char m = TABLE[c];
		return m == 0 ? c : m;
	}

	/** {@code s} with every such character replaced (the same string when there are none). */
	public static String fold(String s) {
		if (s == null) {
			return null;
		}
		int n = s.length();
		for (int i = 0; i < n; i++) {
			char c = s.charAt(i);
			if (c >= 0x00A0 && TABLE[c] != 0) {
				// RGB codes (§ + private-use char) are never in the table, so they pass through
				char[] out = s.toCharArray();
				for (int j = i; j < n; j++) {
					char m = out[j] >= 0x00A0 ? TABLE[out[j]] : 0;
					if (m != 0) {
						out[j] = m;
					}
				}
				return new String(out);
			}
		}
		return s;
	}
}
