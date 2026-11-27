import net.plebmc.client.mods.ClientPrefs;
import net.plebmc.client.text.RgbText;

/** RGB text codes: Spigot hex, PlebClient compact codes, wrapping, stripping and the RGB setting. */
public class RgbTextTest {
	static int checks, failures;

	static void check(boolean ok, String what) {
		checks++;
		if (!ok) {
			System.out.println("FAIL: " + what);
			failures++;
		}
	}

	public static void main(String[] a) {
		String hex = "§x§f§f§8§0§0§a";
		check(RgbText.rgbAt(hex + "Hi", 0) == 0xFF800A, "spigot hex color");
		check(RgbText.codeLength(hex + "Hi", 0) == 14, "spigot hex length");
		check(RgbText.hexCode(0xFF800A).equals(hex), "hexCode round trip");
		check(RgbText.rgbAt("§x§f§fHi", 0) == -1, "short hex is not a code");
		check(RgbText.rgbAt("§cHi", 0) == -1, "legacy color is not RGB");

		String c = RgbText.compactCode(0xFF8800);
		check(c.length() == 2 && RgbText.codeLength(c + "x", 0) == 2, "compact length");
		check(RgbText.rgbAt(c, 0) == 0xFF8800, "compact color: " + Integer.toHexString(RgbText.rgbAt(c, 0)));
		check(RgbText.rgbAt(RgbText.compactCode(0x123456), 0) == 0x113355, "compact rounds to 12 bits");

		String s = "§lA" + c + "B§oC";
		check(RgbText.formatAtEnd(s).equals(c + "§o"), "format at end keeps RGB + italic");
		check(RgbText.formatAtEnd("§cA" + hex + "B").equals(hex), "format at end hex");
		check(RgbText.formatAtEnd("§cA§lB").equals("§c§l"), "vanilla format");
		check(RgbText.strip(c + "Hello " + hex + "World§r!").equals("Hello World!"), "strip");
		check(RgbText.strip("§").equals("§"), "lone section sign kept");

		int[] legacy = new int[16];
		for (int i = 0; i < 16; ++i) {
			int j = (i >> 3 & 1) * 85;
			legacy[i] = ((i >> 2 & 1) * 170 + j) << 16 | ((i >> 1 & 1) * 170 + j) << 8 | (i & 1) * 170 + j;
		}
		check(RgbText.displayColor(c, 0, legacy) == 0xFF8800, "RGB on");
		ClientPrefs.get().rgbText = false;
		check(RgbText.displayColor("§x§f§f§5§5§5§5", 0, legacy) == 0xFF5555,
				"RGB off picks nearest legacy (red)");
		ClientPrefs.get().rgbText = true;
		check(RgbText.shadow(0xFFFFFF) == 0x3F3F3F, "shadow");

		System.out.println(checks + " checks, " + failures + " failed");
		if (failures > 0) {
			System.exit(1);
		}
	}
}
