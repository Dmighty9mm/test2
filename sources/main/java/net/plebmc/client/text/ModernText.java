package net.plebmc.client.text;

/**
 * Letters newer servers use that 1.8's font can't draw well. Scoreboards and
 * chat in newer versions are full of "small caps" (ᴛʜɪꜱ ꜱᴛʏʟᴇ), which newer
 * Minecraft draws from its main font; 1.8 only has them in its tiny unicode
 * pages (or not at all), so they come out small, blurry and uneven. They are
 * drawn here as the font's own capital letters instead, which is how they read.
 */
public final class ModernText {

	private ModernText() {
	}

	private static final char[] TABLE;

	static {
		String[] pairs = {
				// IPA and phonetic extension small capitals
				"ᴀA", "ʙB", "ᴄC", "ᴅD", "ᴇE", "ꜰF", "ɢG", "ʜH", "ɪI", "ᴊJ",
				"ᴋK", "ʟL", "ᴍM", "ɴN", "ᴏO", "ᴘP", "ǫQ", "ʀR", "ꜱS", "ᴛT",
				"ᴜU", "ᴠV", "ᴡW", "ʏY", "ᴢZ", "ᴁÆ",
				// other look-alikes servers use for small caps
				"ғF", "ѕS", "ᴩP",
				// typographic punctuation the 1.8 ASCII font lacks
				"‘'", "’'", "“\"", "”\"", "–-", "—-", "•·", "│|",
				"┃|", "‐-", "‑-", "‒-", "  " };
		TABLE = new char[0x10000];
		for (String p : pairs) {
			TABLE[p.charAt(0)] = p.charAt(1);
		}
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
