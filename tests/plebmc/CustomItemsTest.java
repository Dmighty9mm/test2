import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.plebmc.client.apollo.proto.Json;
import net.plebmc.client.items.ItemRules;
import net.plebmc.client.items.ItemTransforms;
import net.plebmc.client.items.ModelConverter;
import net.plebmc.client.items.NewItems;
import net.plebmc.client.items.PackFiles;
import net.plebmc.client.items.VanillaNames;

/** Custom item models: pack rules, matching, model conversion, display transforms, new items. */
public class CustomItemsTest {
	static int checks, failures;

	static void check(boolean ok, String what) {
		checks++;
		if (!ok) {
			System.out.println("FAIL: " + what);
			failures++;
		}
	}

	static byte[] png(int w, int h) {
		byte[] b = new byte[32];
		b[0] = (byte) 0x89;
		b[1] = 'P';
		b[2] = 'N';
		b[3] = 'G';
		b[16] = (byte) (w >> 24);
		b[17] = (byte) (w >> 16);
		b[18] = (byte) (w >> 8);
		b[19] = (byte) w;
		b[20] = (byte) (h >> 24);
		b[21] = (byte) (h >> 16);
		b[22] = (byte) (h >> 8);
		b[23] = (byte) h;
		return b;
	}

	static PackFiles pack(final String name, final Map<String, byte[]> files) {
		return new PackFiles() {
			public byte[] read(String path) {
				return files.get(path);
			}

			public List<String> list(String folder) {
				List<String> out = new ArrayList<>();
				String pre = folder + "/";
				for (String k : files.keySet()) {
					if (k.startsWith(pre) && k.indexOf('/', pre.length()) < 0) {
						out.add(k.substring(pre.length()));
					}
				}
				return out;
			}

			public String name() {
				return name;
			}
		};
	}

	static byte[] s(String json) {
		return json.getBytes(StandardCharsets.UTF_8);
	}

	static boolean near(double[] a, double[] b) {
		for (int i = 0; i < 3; i++) {
			if (Math.abs(a[i] - b[i]) > 1e-3) {
				return false;
			}
		}
		return true;
	}

	public static void main(String[] a) {
		// ---- display transforms: vanilla items look the same after conversion
		ItemTransforms.T fp = ItemTransforms.convert("firstperson",
				ItemTransforms.read(Json.parse("{\"rotation\":[0,-90,25],\"translation\":[1.13,3.2,1.13],\"scale\":[0.68,0.68,0.68]}")), false);
		check(near(fp.r, new double[] { 0, -135, 25 }) && near(fp.t, new double[] { 0, 4, 2 })
				&& near(fp.s, new double[] { 1.7, 1.7, 1.7 }), "handheld first person -> 1.8 sword: " + fp);
		ItemTransforms.T tp = ItemTransforms.convert("thirdperson",
				ItemTransforms.read(Json.parse("{\"rotation\":[0,-90,55],\"translation\":[0,4,0.5],\"scale\":[0.85,0.85,0.85]}")), false);
		check(near(tp.r, new double[] { 0, 90, -35 }) && near(tp.t, new double[] { 0, 1.25, -3.5 }), "handheld third person: " + tp);
		ItemTransforms.T g = ItemTransforms.convert("ground", ItemTransforms.default19("ground", "generated"), false);
		check(g.isIdentity(), "generated ground -> 1.8 default: " + g);
		ItemTransforms.T gui = ItemTransforms.convert("gui", ItemTransforms.default19("gui", "block"), true);
		check(gui.isIdentity(), "block gui -> 1.8 default: " + gui);
		ItemTransforms.T mirrored = ItemTransforms.convert("gui", ItemTransforms.read(Json.parse("{\"scale\":[-1,1,1]}")), false);
		check(near(mirrored.s, new double[] { -1, 1, 1 }), "mirror kept: " + mirrored);

		// ---- a QualityArmory-style pack
		Map<String, byte[]> f = new HashMap<>();
		f.put("pack.mcmeta", s("{\"pack\":{\"pack_format\":6,\"description\":\"qa\"}}"));
		f.put("assets/minecraft/models/item/diamond_hoe.json", s("{\"parent\":\"item/handheld\",\"textures\":{\"layer0\":\"item/diamond_hoe\"},"
				+ "\"overrides\":[{\"predicate\":{\"custom_model_data\":1},\"model\":\"qualityarmory:item/ak47\"},"
				+ "{\"predicate\":{\"custom_model_data\":2},\"model\":\"item/pistol\"},"
				+ "{\"predicate\":{\"custom_model_data\":3},\"model\":\"item/broken\"}]}"));
		f.put("assets/minecraft/models/item/iron_hoe.json", s("{\"parent\":\"item/handheld\",\"textures\":{\"layer0\":\"item/iron_hoe\"},"
				+ "\"overrides\":[{\"predicate\":{\"damaged\":0,\"damage\":0.1},\"model\":\"item/medkit\"},"
				+ "{\"predicate\":{\"damaged\":1},\"model\":\"item/iron_hoe\"}]}"));
		f.put("assets/minecraft/models/item/stone.json", s("{\"parent\":\"block/stone\"}"));
		f.put("assets/qualityarmory/models/item/ak47.json", s("// made in blockbench\n{\"credit\":\"x\",\"texture_size\":[32,32],"
				+ "\"textures\":{\"0\":\"qualityarmory:item/ak47\",\"particle\":\"qualityarmory:item/ak47\"},"
				+ "\"elements\":[{\"from\":[7,0,-20],\"to\":[9,3,30],\"rotation\":{\"angle\":30,\"axis\":\"x\",\"origin\":[8,8,8]},"
				+ "\"faces\":{\"north\":{\"uv\":[0,0,1,1],\"texture\":\"#0\"},\"up\":{\"uv\":[0,0,1,1],\"texture\":\"#missing\"}}},"
				+ "{\"from\":[0,0,0],\"to\":[1,1,1],\"faces\":{}}],"
				+ "\"display\":{\"firstperson_righthand\":{\"rotation\":[0,-90,25],\"translation\":[1.13,3.2,1.13],\"scale\":[0.68,0.68,0.68]},"
				+ "\"gui\":{\"rotation\":[30,225,0],\"scale\":[0.625,0.625,0.625]}}}"));
		f.put("assets/qualityarmory/textures/item/ak47.png", png(64, 64));
		f.put("assets/minecraft/models/item/pistol.json", s("{\"parent\":\"item/handheld\",\"textures\":{\"layer0\":\"item/pistol\"}}"));
		f.put("assets/minecraft/textures/item/pistol.png", png(16, 16));
		f.put("assets/minecraft/models/item/medkit.json", s("{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"item/medkit\",\"layer1\":\"item/paper\"}}"));
		f.put("assets/minecraft/textures/item/medkit.png", png(16, 16));
		f.put("assets/minecraft/models/item/broken.json", s("{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"item/huge\"}}"));
		f.put("assets/minecraft/textures/item/huge.png", png(4096, 4096));
		ItemRules r = ItemRules.build(Arrays.asList(pack("qa", f)));
		check(r.ruleCount() == 2, "two items with rules: " + r.ruleCount());
		List<String> hoe = Arrays.asList("diamond_hoe");
		ModelConverter.Result ak = r.match(hoe, 1, true, 0, 1561, false, null);
		check(ak != null && ak.id.equals("m/qualityarmory/item/ak47"), "cmd 1 -> ak47");
		check(r.match(hoe, 0, false, 0, 1561, false, null) == null, "no cmd -> normal hoe");
		ModelConverter.Result pistol = r.match(hoe, 2, true, 0, 1561, false, null);
		check(pistol != null && pistol.id.equals("m/minecraft/item/pistol"), "cmd 2 -> pistol");
		check(r.match(hoe, 3, true, 0, 1561, false, null) == null, "texture too big -> normal hoe, no crash");
		check(r.match(hoe, 9, true, 0, 1561, false, null) == null, "last match wins (broken) -> normal");
		check(r.match(Arrays.asList("gold_nugget"), 1, true, 0, 0, false, null) == ak, "unique cmd on another base item");
		check(r.match(Arrays.asList("gold_nugget"), 0, false, 0, 0, false, "AK-47") == null, "name fallback needs the base's rules");
		check(r.match(Arrays.asList("diamond_hoe"), 0, false, 0, 1561, false, "§cAK-47") == ak, "name fallback on the base");
		check(r.match(Arrays.asList("diamond_sword"), 1, false, 0, 0, false, null) == null, "vanilla sword untouched");
		// damage predicates (older packs)
		ModelConverter.Result med = r.match(Arrays.asList("iron_hoe"), 0, false, 26, 250, true, null);
		check(med != null && med.id.equals("m/minecraft/item/medkit"), "unbreakable + damage 26/250 -> medkit");
		check(r.match(Arrays.asList("iron_hoe"), 0, false, 26, 250, false, null) == null, "really damaged hoe -> normal");
		check(r.match(Arrays.asList("iron_hoe"), 0, false, 0, 250, true, null) == null, "undamaged hoe -> normal");

		// converted model JSON
		Map<?, ?> akj = (Map<?, ?>) Json.parse(ak.json);
		List<?> els = (List<?>) akj.get("elements");
		check(els.size() == 1, "element without faces dropped");
		Map<?, ?> el = (Map<?, ?>) els.get(0);
		check(((Number) ((List<?>) el.get("from")).get(2)).doubleValue() == -16 && ((Number) ((List<?>) el.get("to")).get(2)).doubleValue() == 30, "from/to clamped: " + el);
		check(((Number) ((Map<?, ?>) el.get("rotation")).get("angle")).doubleValue() == 22.5, "30 deg snapped to 22.5");
		check(((Map<?, ?>) el.get("faces")).size() == 1, "face with a missing texture dropped");
		check("plebitems:p/qualityarmory/item/ak47".equals(((Map<?, ?>) akj.get("textures")).get("0")), "texture moved to plebitems");
		check(!akj.containsKey("parent"), "no parent next to elements");
		Map<?, ?> disp = (Map<?, ?>) akj.get("display");
		check(disp.containsKey("firstperson") && !disp.containsKey("gui"), "display converted (gui = 1.8 default): " + disp.keySet());
		check(ak.sprites.contains("plebitems:p/qualityarmory/item/ak47"), "sprite listed");
		check(r.spriteFile("p/qualityarmory/item/ak47", ".png") != null, "sprite file found");
		Map<?, ?> medj = (Map<?, ?>) Json.parse(med.json);
		check("builtin/generated".equals(medj.get("parent")), "generated parent");
		check("minecraft:items/paper".equals(((Map<?, ?>) medj.get("textures")).get("layer1")), "vanilla texture -> 1.8 name");

		// a higher pack replacing the hoe with a plain model removes its rules
		Map<String, byte[]> f2 = new HashMap<>();
		f2.put("assets/minecraft/models/item/diamond_hoe.json", s("{\"parent\":\"item/handheld\",\"textures\":{\"layer0\":\"item/diamond_hoe\"}}"));
		ItemRules r2 = ItemRules.build(Arrays.asList(pack("qa", f), pack("plain", f2)));
		check(!r2.hasRules("diamond_hoe") && r2.hasRules("iron_hoe"), "higher pack wins per file");
		check(ItemRules.build(new ArrayList<PackFiles>()).isEmpty(), "no packs, no rules");
		Map<String, byte[]> bad = new HashMap<>();
		bad.put("assets/minecraft/models/item/diamond_hoe.json", s("{not json"));
		check(ItemRules.build(Arrays.asList(pack("bad", bad))).isEmpty(), "broken json ignored");

		// ---- new items through Via
		check("netherite_sword".equals(NewItems.identify("§f1.16 Netherite Sword", null)), "Via name -> netherite sword");
		check("totem_of_undying".equals(NewItems.identify("1.11 Totem of Undying", null)), "totem");
		check("netherite_ingot".equals(NewItems.identify(null, "\"minecraft:netherite_ingot\"")), "original id");
		check(NewItems.identify("Netherite Sword", null) == null, "a plain rename isn't Via's");
		check(NewItems.identify("1.16 Diamond Sword", null) == null, "only known new items");
		check("chest".equals(NewItems.armorSlot("netherite_chestplate")) && NewItems.isTool("netherite_axe"), "slots");
		check(Json.parse(NewItems.modelJson("netherite_sword")) instanceof Map, "builtin model is valid json");

		check(VanillaNames.modernNames("reeds").contains("sugar_cane") && "gold_sword".equals(VanillaNames.texture18("golden_sword")), "vanilla names");

		System.out.println("CustomItemsTest: " + checks + " checks, " + failures + " failures");
		if (failures > 0) {
			System.exit(1);
		}
	}
}
