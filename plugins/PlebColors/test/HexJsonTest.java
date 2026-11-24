import net.plebmc.colors.HexJson;

/** HexJson: hex colors move into the text as PlebClient RGB codes; everything else is untouched. */
public class HexJsonTest {
	static int checks, failures;

	static void check(boolean ok, String what) {
		checks++;
		if (!ok) {
			System.out.println("FAIL: " + what);
			failures++;
		}
	}

	static String code(int rgb) {
		return HexJson.compactCode(rgb);
	}

	static String esc(String s) {
		StringBuilder sb = new StringBuilder();
		for (char c : s.toCharArray()) {
			if (c > 126) {
				sb.append(String.format("\\u%04x", (int) c));
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	public static void main(String[] a) {
		check(HexJson.convert("{\"text\":\"hi\",\"color\":\"red\"}") == null, "named color unchanged");
		check(HexJson.convert("{\"text\":\"#1 place\"}") == null, "# in text only is unchanged");
		check(HexJson.convert("not json {") == null, "bad json");

		String out = HexJson.convert("{\"text\":\"Hi\",\"color\":\"#FF8800\",\"bold\":true}");
		check(out != null, "converted");
		check(out.contains("\"text\":\"" + code(0xFF8800) + "§lHi\"") && !out.contains("color"),
				"color moves into text with bold: " + esc(out));

		// a MiniMessage-style gradient: parent with per-letter children
		String g = HexJson.convert("{\"text\":\"\",\"extra\":[{\"text\":\"A\",\"color\":\"#FF0000\"},"
				+ "{\"text\":\"B\",\"color\":\"#00FF00\"},\"C\"]}");
		check(g.contains(code(0xFF0000) + "A") && g.contains(code(0x00FF00) + "B"), "gradient letters: " + esc(g));
		check(g.contains("\"C\""), "plain child without color untouched");

		// children inherit the hex color, including plain strings
		String inh = HexJson.convert("{\"text\":\"P\",\"color\":\"#112233\",\"extra\":[\"x\",{\"text\":\"y\",\"italic\":true}]}");
		check(inh.contains(code(0x112233) + "x") && inh.contains(code(0x112233) + "§oy"), "inherit: " + esc(inh));

		// a named child color wins over the parent's hex
		String named = HexJson.convert("{\"text\":\"P\",\"color\":\"#112233\",\"extra\":[{\"text\":\"r\",\"color\":\"red\"}]}");
		check(named.contains("{\"text\":\"r\",\"color\":\"red\"}"), "named child: " + esc(named));

		// translation with hex: keeps its color (Via rounds it), args get codes
		String tr = HexJson.convert("{\"translate\":\"chat.type.text\",\"color\":\"#AABBCC\",\"with\":[\"Bob\",{\"text\":\"hi\"}]}");
		check(tr.contains("\"color\":\"#AABBCC\"") && tr.contains(code(0xAABBCC) + "Bob")
				&& tr.contains(code(0xAABBCC) + "hi"), "translate: " + esc(tr));

		// top-level array: the rest inherit the first element's style
		String arr = HexJson.convert("[{\"text\":\"a\",\"color\":\"#010203\"},\"b\"]");
		check(arr.contains(code(0x010203) + "b"), "array: " + esc(arr));

		check(code(0xFFFFFF).equals("§") && code(0).equals("§"), "compact range");
		System.out.println(checks + " checks, " + failures + " failed");
		if (failures > 0) {
			System.exit(1);
		}
	}
}
