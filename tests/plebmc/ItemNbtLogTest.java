import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.plebmc.client.items.ItemNbtLog;
import net.plebmc.client.mods.ClientPrefs;
import net.plebmc.client.mods.SettingsStore;

/** Item NBT log (custom item models, step 1): hints, the size cap, the export and the saved setting. */
public class ItemNbtLogTest {
	static int checks, failures;

	static void check(boolean ok, String what) {
		checks++;
		if (!ok) {
			System.out.println("FAIL: " + what);
			failures++;
		}
	}

	public static void main(String[] a) {
		check(ItemNbtLog.hintFor("tag.CustomModelData", "CustomModelData", "12").contains("CustomModelData 12"),
				"top-level CustomModelData");
		check(ItemNbtLog.hintFor("tag.VB|Protocol1_14To1_13_2.CustomModelData", "CustomModelData", "7").contains("7"),
				"CustomModelData inside a backup tag");
		check(ItemNbtLog.hintFor("tag.VB|Protocol1_14To1_13_2", "VB|Protocol1_14To1_13_2", null).startsWith("Via backup"),
				"ViaBackwards tag");
		check(ItemNbtLog.hintFor("tag.ViaRewind1_8to1_9", "ViaRewind1_8to1_9", null).startsWith("Via backup"), "ViaRewind tag");
		check(ItemNbtLog.hintFor("tag.PublicBukkitValues", "PublicBukkitValues", null) != null, "plugin data");
		check(ItemNbtLog.hintFor("tag.VB|x.id", "id", "\"minecraft:totem_of_undying\"").contains("totem"), "original id");
		check(ItemNbtLog.hintFor("tag.id", "id", "\"minecraft:stone\"") == null, "plain id is not a hint");
		check(ItemNbtLog.hintFor("tag.display.Name", "Name", "\"x\"") == null, "name is not a hint");
		check(ItemNbtLog.hintFor("tag.x", null, null) == null, "null key");

		ItemNbtLog log = new ItemNbtLog();
		for (int i = 0; i < ItemNbtLog.MAX_ENTRIES + 5; i++) {
			log.add(new ItemNbtLog.Entry(i * 1000L, "hotbar 1", "minecraft:diamond_hoe", 293, i, 1, null, null, null, null));
		}
		check(log.size() == ItemNbtLog.MAX_ENTRIES && log.total() == ItemNbtLog.MAX_ENTRIES + 5, "cap: " + log.size());
		check(log.entries().get(0).damage == 5, "oldest dropped first");
		String ex = log.export("play.plebmc.top", "PlebClient");
		check(ex.startsWith("PlebClient item NBT log") && ex.contains("5 dropped") && ex.contains("play.plebmc.top"), "export header");
		log.clear();
		check(log.size() == 0 && log.total() == 0, "clear");

		StringBuilder big = new StringBuilder();
		for (int i = 0; i < ItemNbtLog.MAX_NBT_CHARS + 10; i++) {
			big.append('x');
		}
		ItemNbtLog.Entry e = new ItemNbtLog.Entry(0, "held", "minecraft:iron_horse_armor", 418, 0, 3,
				"§fAK-47", Arrays.asList("§7Ammo: 30"), big.toString(),
				Collections.singletonList("CustomModelData 5 at tag.CustomModelData"));
		check(e.nbt.endsWith("...(cut)") && e.nbt.length() < ItemNbtLog.MAX_NBT_CHARS + 20, "long NBT cut");
		check(e.summary().contains("x3") && e.summary().contains("AK-47"), "summary: " + e.summary());
		log.add(e);
		ex = log.export(null, "PlebClient");
		check(ex.contains("hint: CustomModelData 5") && ex.contains("lore: §7Ammo: 30") && ex.contains("Server: (none)")
				&& ex.contains("[00:00:00 UTC] held: minecraft:iron_horse_armor #418 x3"), "export entry");
		check("AK-47".equals(ItemNbtLog.strip("§f§lAK-47")), "strip codes");

		// the setting: off by default, saved and loaded
		final Map<String, String> m = new HashMap<>();
		SettingsStore st = new SettingsStore() {
			public String get(String k) {
				return m.get(k);
			}

			public void set(String k, String v) {
				m.put(k, v);
			}
		};
		ClientPrefs p = ClientPrefs.get();
		p.reset();
		check(!p.itemNbtLog, "log off by default");
		p.itemNbtLog = true;
		p.save(st);
		p.reset();
		check(!p.itemNbtLog, "reset turns it off");
		p.load(st);
		check(p.itemNbtLog, "saved and loaded");
		p.reset();

		System.out.println(checks + " checks, " + failures + " failures");
		if (failures > 0) {
			System.exit(1);
		}
	}
}
