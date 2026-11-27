package net.plebmc.colors;

import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Rewrites hex colors in a JSON text component so they survive
 * ViaBackwards on the way to a 1.8 PlebClient player.
 *
 * ViaBackwards rounds every {@code "color":"#RRGGBB"} to the nearest of the
 * 16 old colors. Text that is plain characters is left alone, so for every
 * text part with a hex color this removes the color and puts PlebClient's
 * compact RGB code ({@code §} plus one char {@code U+E000-U+EFFF}, 12-bit
 * color) and the part's bold/italic/etc. codes in front of its text. Parts
 * that have no text of their own (translations, scores, keybinds) keep their
 * hex color and Via rounds it as before.
 *
 * Only used for players whose client registered {@code plebmc:rgb}: a vanilla
 * 1.8 client would show these codes as plain white.
 */
public final class HexJson {

	public static final char SECTION = '§';
	private static final String[][] DECOS = { { "obfuscated", "k" }, { "bold", "l" }, { "strikethrough", "m" },
			{ "underlined", "n" }, { "italic", "o" } };

	private HexJson() {
	}

	/** The rewritten JSON, or null when it has no hex color to keep. */
	public static String convert(String json) {
		if (json == null || json.indexOf('#') < 0) {
			return null;
		}
		JsonElement root;
		try {
			root = new JsonParser().parse(json);
		} catch (RuntimeException e) {
			return null;
		}
		boolean[] changed = { false };
		JsonElement out = node(root, new Style(), changed);
		return changed[0] ? out.toString() : null;
	}

	/** {@code §} + one char holding a 12-bit color; the client's RgbText reads it. */
	public static String compactCode(int rgb) {
		int r = (((rgb >> 16) & 255) * 15 + 127) / 255;
		int g = (((rgb >> 8) & 255) * 15 + 127) / 255;
		int b = ((rgb & 255) * 15 + 127) / 255;
		return new String(new char[] { SECTION, (char) (0xE000 + (r << 8 | g << 4 | b)) });
	}

	static int parseHex(String color) {
		if (color == null || color.length() != 7 || color.charAt(0) != '#') {
			return -1;
		}
		try {
			return Integer.parseInt(color.substring(1), 16);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static final class Style {
		String color;
		final Boolean[] deco = new Boolean[DECOS.length];

		Style with(JsonObject o) {
			Style s = new Style();
			s.color = color;
			System.arraycopy(deco, 0, s.deco, 0, deco.length);
			JsonElement c = o.get("color");
			if (c != null && c.isJsonPrimitive()) {
				s.color = c.getAsString();
			}
			for (int k = 0; k < DECOS.length; ++k) {
				JsonElement d = o.get(DECOS[k][0]);
				if (d != null && d.isJsonPrimitive()) {
					s.deco[k] = d.getAsBoolean();
				}
			}
			return s;
		}

		int rgb() {
			return parseHex(color);
		}

		String codes() {
			StringBuilder sb = new StringBuilder(compactCode(rgb()));
			for (int k = 0; k < DECOS.length; ++k) {
				if (Boolean.TRUE.equals(deco[k])) {
					sb.append(SECTION).append(DECOS[k][1]);
				}
			}
			return sb.toString();
		}
	}

	private static JsonElement node(JsonElement e, Style parent, boolean[] changed) {
		if (e == null || e.isJsonNull()) {
			return e;
		}
		if (e.isJsonArray()) {
			// [first, rest...]: the rest inherit the first one's style
			JsonArray in = e.getAsJsonArray();
			JsonArray out = new JsonArray();
			Style first = parent;
			for (int i = 0; i < in.size(); ++i) {
				JsonElement el = in.get(i);
				if (i == 0 && el.isJsonObject()) {
					first = parent.with(el.getAsJsonObject());
				}
				out.add(node(el, i == 0 ? parent : first, changed));
			}
			return out;
		}
		if (e.isJsonPrimitive()) {
			if (parent.rgb() < 0) {
				return e;
			}
			changed[0] = true;
			JsonObject o = new JsonObject();
			o.addProperty("text", parent.codes() + e.getAsString());
			return o;
		}
		if (!e.isJsonObject()) {
			return e;
		}
		JsonObject o = e.getAsJsonObject();
		Style eff = parent.with(o);
		boolean hex = eff.rgb() >= 0;
		if (hex) {
			JsonElement text = o.get("text");
			if (text != null && text.isJsonPrimitive()) {
				o.addProperty("text", eff.codes() + text.getAsString());
				o.remove("color");
				changed[0] = true;
			} else if (!o.has("color")) {
				// its parent lost the color; keep it here so Via can still round it
				o.addProperty("color", eff.color);
			}
		}
		for (Map.Entry<String, JsonElement> en : o.entrySet()) {
			String k = en.getKey();
			if (("extra".equals(k) || "with".equals(k)) && en.getValue().isJsonArray()) {
				JsonArray in = en.getValue().getAsJsonArray();
				JsonArray out = new JsonArray();
				for (int i = 0; i < in.size(); ++i) {
					out.add(node(in.get(i), eff, changed));
				}
				en.setValue(out);
			}
		}
		return o;
	}

	/** For tests: a JSON string primitive. */
	static JsonPrimitive str(String s) {
		return new JsonPrimitive(s);
	}
}
