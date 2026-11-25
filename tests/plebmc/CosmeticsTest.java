import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.plebmc.client.cosmetics.Cosmetics;
import net.plebmc.client.network.PluginChannels;

/**
 * JVM test for the cosmetics shop protocol: state/players/result messages
 * from the server, buy/equip requests to it, the try-on preview, and that
 * bad input and unknown looks are ignored.
 *
 * Exit 0 = holds; 1 = violation.
 */
public class CosmeticsTest {

	static int failures = 0;
	static int checks = 0;

	static void check(boolean ok, String what) {
		checks++;
		if (!ok) {
			System.out.println("FAIL: " + what);
			failures++;
		}
	}

	static final List<String> sent = new ArrayList<>();

	public static void main(String[] args) {
		PluginChannels.reset();
		PluginChannels.attach((channel, data) -> {
			if (Cosmetics.CHANNEL.equals(channel)) {
				sent.add(new String(data, StandardCharsets.UTF_8));
			}
			return true;
		});
		Cosmetics.init(null);
		PluginChannels.onJoin();
		check(sent.size() == 1 && sent.get(0).contains("\"open\""), "join asks the server for the shop state");
		check(!Cosmetics.serverSupported() && Cosmetics.coins() == -1, "no server support before a message");
		check(Cosmetics.catalog("hat").size() > 0 && Cosmetics.catalog("cloak").size() > 0 && Cosmetics.catalog("trail").isEmpty(), "default catalog (no trails)");
		check(Cosmetics.collections().size() == 2 && Cosmetics.catalog("hat", "plebmc").size() == Cosmetics.catalog("hat").size()
				&& Cosmetics.catalog("hat", "partners").isEmpty(), "default collections");

		String me = "00000000-0000-0000-0000-000000000001";
		String other = "00000000-0000-0000-0000-000000000002";
		Cosmetics.setSelf(me);
		check(Cosmetics.nothingToDraw(), "nothing equipped yet");

		Cosmetics.handle("{\"type\":\"state\",\"coins\":1250,\"owned\":[\"tophat\",\"halo\"],"
				+ "\"equipped\":{\"hat\":\"tophat\"}}");
		check(Cosmetics.serverSupported(), "server support after state");
		check(Cosmetics.coins() == 1250, "coins");
		check(Cosmetics.owns("tophat") && !Cosmetics.owns("crown"), "owned");
		check("tophat".equals(Cosmetics.equippedId("hat")), "equipped hat");
		check(Cosmetics.shown(me, "hat") != null && "tophat".equals(Cosmetics.shown(me, "hat").render), "self shows hat");
		check(Cosmetics.shown(other, "hat") == null, "others show nothing until told");

		Cosmetics.setPreview("hat", "halo");
		check("halo".equals(Cosmetics.shown(me, "hat").id), "try-on wins for self");
		Cosmetics.clearPreview();
		check("tophat".equals(Cosmetics.shown(me, "hat").id), "try-on cleared");

		Cosmetics.handle("{\"type\":\"players\",\"players\":{\"" + other.toUpperCase() + "\":{\"hat\":\"halo\"}}}");
		check(Cosmetics.shown(other, "hat") != null && "halo".equals(Cosmetics.shown(other, "hat").render),
				"other player's hat (uuid case-insensitive)");
		Cosmetics.handle("{\"type\":\"players\",\"players\":{\"" + other + "\":null}}");
		check(Cosmetics.shown(other, "hat") == null, "other player's cosmetics removed");

		Cosmetics.handle("{\"type\":\"state\",\"catalog\":[{\"id\":\"gold_tophat\",\"name\":\"Gold Top Hat\","
				+ "\"category\":\"hat\",\"render\":\"tophat\",\"price\":900,\"color\":\"#FFD700\"},"
				+ "{\"id\":\"jetpack\",\"name\":\"Jetpack\",\"category\":\"hat\",\"render\":\"jetpack\",\"price\":5}]}");
		check(Cosmetics.hasServerCatalog() && Cosmetics.catalog("hat").size() == 1, "server catalog, unknown look hidden");
		Cosmetics.Item g = Cosmetics.catalog("hat").get(0);
		check(g.price == 900 && g.color == 0xFFD700 && "tophat".equals(g.render), "catalog item fields");
		check(Cosmetics.coins() == 1250, "state without coins keeps the balance");

		sent.clear();
		Cosmetics.buy("gold_tophat");
		Cosmetics.equip("hat", "gold_tophat");
		Cosmetics.equip("hat", null);
		check(sent.size() == 3, "three requests sent");
		check(sent.get(0).equals("{\"type\":\"buy\",\"id\":\"gold_tophat\"}"), "buy request: " + sent.get(0));
		check(sent.get(1).equals("{\"type\":\"equip\",\"category\":\"hat\",\"id\":\"gold_tophat\"}"),
				"equip request: " + sent.get(1));
		check(sent.get(2).equals("{\"type\":\"equip\",\"category\":\"hat\",\"id\":null}"), "unequip: " + sent.get(2));

		Cosmetics.handle("{\"type\":\"result\",\"ok\":false,\"message\":\"Not enough coins\",\"coins\":100}");
		check("Not enough coins".equals(Cosmetics.message()) && !Cosmetics.messageOk(), "result message");
		check(Cosmetics.coins() == 100, "result updates coins");

		int v = Cosmetics.version();
		Cosmetics.handle("not json");
		Cosmetics.handle("[1,2]");
		check(Cosmetics.version() == v && Cosmetics.coins() == 100, "bad input ignored");

		PluginChannels.reset();
		check(!Cosmetics.serverSupported() && Cosmetics.coins() == -1 && Cosmetics.nothingToDraw(),
				"disconnect forgets the server's state");

		// quest categories (General / PlebNite), the All view, NEW tags
		Cosmetics.handle("{\"type\":\"state\",\"questCategories\":[{\"id\":\"general\",\"name\":\"General\",\"icon\":\"general\",\"color\":\"#3FD8E6\"},"
				+ "{\"id\":\"plebnite\",\"name\":\"PlebNite\",\"color\":\"bad\"}],\"quests\":["
				+ "{\"id\":\"login\",\"category\":\"general\",\"period\":\"login\",\"streak\":3,\"streakLength\":7,\"cycle\":\"d1\",\"goal\":1,\"done\":true},"
				+ "{\"id\":\"general_chat_10\",\"category\":\"general\",\"period\":\"daily\",\"cycle\":\"d1\",\"goal\":10,\"progress\":2},"
				+ "{\"id\":\"kills_3\",\"category\":\"plebnite\",\"period\":\"daily\",\"cycle\":\"d1\",\"goal\":3}]}");
		check(Cosmetics.questCategories().get(0).color == 0xFF3FD8E6 && Cosmetics.questCategories().get(1).color == 0
				&& "quests".equals(Cosmetics.questCategories().get(1).icon), "category colors and icons");
		check(Cosmetics.quests(null).size() == 3 && Cosmetics.quests("general").size() == 2 && Cosmetics.quests("plebnite").size() == 1,
				"All / General / PlebNite");
		check(Cosmetics.quests("general").get(0).streak == 3, "login streak on the row");
		Cosmetics.loadSeen(null);
		check(Cosmetics.newCount(null) == 2 && Cosmetics.newCount("plebnite") == 1, "unseen quests are new (not the login)");
		check(Cosmetics.markSeen(Cosmetics.quests(null)) && Cosmetics.newCount(null) == 0 && !Cosmetics.markSeen(Cosmetics.quests(null)), "seen");
		String saved = Cosmetics.seenJson();
		Cosmetics.loadSeen(saved);
		check(Cosmetics.newCount(null) == 0, "seen survives a restart");
		Cosmetics.handle("{\"type\":\"state\",\"quests\":[{\"id\":\"kills_3\",\"category\":\"plebnite\",\"period\":\"daily\",\"cycle\":\"d2\",\"goal\":3}]}");
		check(Cosmetics.newCount(null) == 1, "the same quest on a new day is new again");
		Cosmetics.loadSeen("not json");
		check(Cosmetics.newCount(null) == 1, "broken seen data is ignored");
		Cosmetics.handle("{\"type\":\"state\",\"catalog\":[{\"id\":\"vip\",\"name\":\"VIP\",\"category\":\"hat\",\"render\":\"crown\",\"price\":999999999,\"forSale\":false},"
				+ "{\"id\":\"cap\",\"name\":\"Cap\",\"category\":\"hat\",\"render\":\"beanie\",\"price\":50}]}");
		check(Cosmetics.item("vip") != null && !Cosmetics.item("vip").forSale && Cosmetics.item("cap").forSale, "not-for-sale items");

		// model hats: listed with their model and texture ids; without them they are hidden
		Cosmetics.handle("{\"type\":\"state\",\"catalog\":[{\"id\":\"king\",\"name\":\"King\",\"category\":\"hat\",\"render\":\"model\",\"model\":\"0123456789abcdef\",\"texture\":\"fedcba9876543210\",\"textureWidth\":256,\"price\":10},"
				+ "{\"id\":\"broken\",\"category\":\"hat\",\"render\":\"model\",\"price\":10}]}");
		check(Cosmetics.item("king") != null && "0123456789abcdef".equals(Cosmetics.item("king").model)
				&& Cosmetics.item("broken") == null, "model hats in the catalog");
		float[] q = Cosmetics.parseModel("{\"v\":1,\"quads\":[1,2,3,0,0, 1,2,3,1,0, 1,2,3,1,1, 1,2,3,0,1]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		check(q != null && q.length == 20 && q[19] == 1f, "model blob parsed");
		check(Cosmetics.parseModel("{\"quads\":[1,2,3]}".getBytes(java.nio.charset.StandardCharsets.UTF_8)) == null
				&& Cosmetics.parseModel("nope".getBytes(java.nio.charset.StandardCharsets.UTF_8)) == null, "bad model blobs refused");
		// collections: PlebMc / Partners, a creator, unknown collections fall back to the first, trails ignored
		Cosmetics.handle("{\"type\":\"state\",\"collections\":[{\"id\":\"plebmc\",\"name\":\"PlebMc\"},{\"id\":\"Partners\",\"name\":\"Partners\",\"icon\":\"partner\",\"color\":\"#FF7AC6\"}],"
				+ "\"catalog\":[{\"id\":\"a\",\"category\":\"hat\",\"render\":\"cap\",\"price\":1},"
				+ "{\"id\":\"b\",\"category\":\"hat\",\"render\":\"wizard\",\"collection\":\"partners\",\"creator\":\"Streamer\",\"price\":1},"
				+ "{\"id\":\"c\",\"category\":\"hat\",\"render\":\"bunny\",\"collection\":\"gone\",\"price\":1},"
				+ "{\"id\":\"d\",\"category\":\"trail\",\"render\":\"flame\",\"price\":1}]}");
		check(Cosmetics.collections().size() == 2 && "partners".equals(Cosmetics.collections().get(1).id)
				&& Cosmetics.collections().get(1).color == 0xFFFF7AC6, "collections read");
		check(Cosmetics.catalog("hat", "plebmc").size() == 2 && Cosmetics.catalog("hat", "partners").size() == 1
				&& "Streamer".equals(Cosmetics.item("b").creator) && Cosmetics.item("d") == null, "items split by collection, trails ignored");

		// admin flag and admin data
		Cosmetics.handle("{\"type\":\"account\",\"linked\":true,\"username\":\"Master\",\"groups\":[\"Owner\"],\"admin\":true}");
		check(Cosmetics.account().admin, "account says admin");
		Cosmetics.handle("{\"type\":\"admin\",\"allowed\":true,\"players\":[{\"uuid\":\"u2\",\"name\":\"zed\",\"coins\":5},{\"uuid\":\"u1\",\"name\":\"Amy\",\"coins\":7,\"owned\":[\"halo\"]}],\"servers\":[\"lobby\",\"plebnite\"],\"items\":[{\"id\":\"halo\",\"name\":\"Halo\",\"category\":\"hat\"}],\"message\":\"ok\",\"ok\":true}");
		check(Cosmetics.admin().allowed && Cosmetics.admin().players.size() == 2 && "Amy".equals(Cosmetics.admin().players.get(0).name)
				&& Cosmetics.admin().player("u1").owned.contains("halo") && Cosmetics.admin().servers.size() == 2, "admin data read");

		System.out.println("CosmeticsTest: " + checks + " checks, failures=" + failures);
		System.exit(failures == 0 ? 0 : 1);
	}
}
