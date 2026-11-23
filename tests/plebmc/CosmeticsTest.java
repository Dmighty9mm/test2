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
		check(Cosmetics.catalog("hat").size() > 0 && Cosmetics.catalog("trail").size() > 0, "default catalog");

		String me = "00000000-0000-0000-0000-000000000001";
		String other = "00000000-0000-0000-0000-000000000002";
		Cosmetics.setSelf(me);
		check(Cosmetics.nothingToDraw(), "nothing equipped yet");

		Cosmetics.handle("{\"type\":\"state\",\"coins\":1250,\"owned\":[\"tophat\",\"flame\"],"
				+ "\"equipped\":{\"hat\":\"tophat\",\"trail\":\"flame\"}}");
		check(Cosmetics.serverSupported(), "server support after state");
		check(Cosmetics.coins() == 1250, "coins");
		check(Cosmetics.owns("tophat") && !Cosmetics.owns("crown"), "owned");
		check("tophat".equals(Cosmetics.equippedId("hat")), "equipped hat");
		check(Cosmetics.shown(me, "hat") != null && "tophat".equals(Cosmetics.shown(me, "hat").render), "self shows hat");
		check(Cosmetics.shown(other, "hat") == null, "others show nothing until told");

		Cosmetics.setPreview("hat", "crown");
		check("crown".equals(Cosmetics.shown(me, "hat").id), "try-on wins for self");
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

		System.out.println("CosmeticsTest: " + checks + " checks, failures=" + failures);
		System.exit(failures == 0 ? 0 : 1);
	}
}
