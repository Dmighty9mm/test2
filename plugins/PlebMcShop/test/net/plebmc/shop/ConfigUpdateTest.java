package net.plebmc.shop;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** An old config.yml gets the new settings; nothing in it changes or disappears. */
public class ConfigUpdateTest {

	static int checks, failures;

	static void check(boolean ok, String what) {
		checks++;
		if (!ok) {
			failures++;
			System.out.println("FAIL: " + what);
		}
	}

	static Cfg cfg(String text) throws Exception {
		return Cfg.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
	}

	public static void run() throws Exception {
		String old = new String(Files.readAllBytes(Paths.get("test/fixtures/old-config.yml")), StandardCharsets.UTF_8);
		String def = new String(Files.readAllBytes(Paths.get("src/main/resources/config.yml")), StandardCharsets.UTF_8);
		ConfigUpdate.Result r = ConfigUpdate.merge(old, def);
		Cfg c = cfg(r.text);
		check(r.text.startsWith(old.trim().substring(0, 40)), "the file still starts the same");
		check("Pleb Coins".equals(c.str("currency-name", "")), "your currency name kept");
		check("test-key".equals(c.section("website").str("api-key", "")), "your website key kept");
		check(c.section("accounts") != null && "https://plebmc.top".equals(c.section("accounts").str("url", "")),
				"accounts section added");
		check(c.section("admin-menu") != null && c.section("staff-lock") != null, "admin-menu and staff-lock added");
		check(c.bool("plebclient-only", false), "plebclient-only added");
		check(r.added.contains("messages.not-enough") && c.section("messages").str("not-enough", null) != null,
				"missing messages filled in");
		check("Bought %item%!".equals(c.section("messages").str("bought", "")), "your message kept");
		java.util.List<String> keys = new java.util.ArrayList<>(c.section("items").keys());
		check(keys.size() == 8 && keys.contains("angel_wings") && keys.contains("bat_wings") && !keys.contains("halo"),
				"only the new wings are added to your items (no default hats come back): " + keys);
		check("wings".equals(c.section("items").section("dragon_wings").str("category", "")), "wings items load");
		check(r.text.contains("added-item-packs"), "the wings pack is remembered");
		String noWings = r.text.replaceAll("(?m)^  [a-z_]+_wings:.*\\n", "");
		check(ConfigUpdate.merge(noWings, def).added.isEmpty(), "wings you delete don't come back");
		check(c.section("quests").section("categories").keys().size() == 1, "quests untouched");
		check(r.text.contains("# Turn on with a PlebMc Panel") || r.text.contains("accounts:"), "comments come with the new settings");
		check(ConfigUpdate.merge(r.text, def).added.isEmpty(), "running it again adds nothing");
		check(ConfigUpdate.merge(def, def).added.isEmpty(), "a current config is left alone");

		// in place, with a backup
		File dir = Files.createTempDirectory("cfgupd").toFile();
		File f = new File(dir, "config.yml");
		Files.write(f.toPath(), old.getBytes(StandardCharsets.UTF_8));
		ConfigUpdate.update(f, def.getBytes(StandardCharsets.UTF_8), java.util.logging.Logger.getLogger("t"));
		File[] b = dir.listFiles((d, n) -> n.startsWith("config.yml.before-update-"));
		check(b != null && b.length == 1 && new String(Files.readAllBytes(b[0].toPath()), StandardCharsets.UTF_8).equals(old), "old file backed up");
		check(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).contains("accounts:"), "file updated");

		System.out.println((checks - failures) + "/" + checks + " config update checks passed");
		if (failures > 0) {
			System.exit(1);
		}
	}
}
