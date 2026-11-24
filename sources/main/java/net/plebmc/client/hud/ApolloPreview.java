package net.plebmc.client.hud;

import net.lax1dude.eaglercraft.v1_8.EaglercraftUUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.plebmc.client.apollo.Apollo;

/**
 * "Preview features" in the Apollo debug screen: sends this client the
 * same kinds of messages PlebNite sends during a match (squad, glow,
 * nametag, pings, beams, cooldowns, match timer, vignette, pop-ups), built
 * around where you're standing, so each overlay can be checked in
 * singleplayer. Leaving the world clears it like any server's state.
 */
final class ApolloPreview {

	private ApolloPreview() {
	}

	private static final String T = "type.googleapis.com/lunarclient.apollo.";

	static void run(Minecraft mc) {
		EntityPlayerSP p = mc.thePlayer;
		if (p == null) {
			return;
		}
		int bx = (int) Math.floor(p.posX);
		int by = (int) Math.floor(p.posY);
		int bz = (int) Math.floor(p.posZ);
		String me = uuid(p.getUniqueID());
		String mate1 = "{\"high64\":\"1311768467463790320\",\"low64\":\"2\"}";
		String mate2 = "{\"high64\":\"1311768467463790320\",\"low64\":\"3\"}";
		send("team.v1.UpdateTeamMembersMessage", "\"members\":[" //
				+ member(mate1, "Teammate1", 0xFF55FFFF, p.posX + 12, p.posY, p.posZ - 20) + ","
				+ member(mate2, "Teammate2", 0xFFFFAA00, p.posX - 60, p.posY, p.posZ + 90) + ","
				+ member(me, p.getName(), 0xFF55FF55, p.posX, p.posY, p.posZ) + "]");
		// you glow (press F5 to see it) and wear a squad nametag
		send("glow.v1.OverrideGlowEffectMessage", "\"player_uuid\":" + me + ",\"color\":{\"color\":" + 0xFF55FF55 + "}");
		send("nametag.v1.OverrideNametagMessage", "\"player_uuid\":" + me
				+ ",\"adventure_json_lines\":[\"{\\\"text\\\":\\\"" + p.getName()
				+ "\\\",\\\"color\\\":\\\"green\\\"}\",\"{\\\"text\\\":\\\"\\u2764 20\\\",\\\"color\\\":\\\"red\\\"}\"]");
		// a squad ping and a knocked teammate
		send("waypoint.v1.DisplayWaypointMessage", "\"name\":\"Ping\",\"location\":" + block(bx + 6, by, bz + 10)
				+ ",\"color\":{\"color\":" + 0xFF55FFFF + "}");
		send("waypoint.v1.DisplayWaypointMessage", "\"name\":\"Teammate DOWN\",\"location\":"
				+ block(bx - 14, by, bz + 6) + ",\"color\":{\"color\":" + 0xFFFF5555 + "}");
		send("beam.v1.DisplayBeaconBeamMessage", "\"id\":\"knocked\",\"location\":" + block(bx - 14, by, bz + 6)
				+ ",\"color\":{\"color\":" + 0xFFFF5555 + "}");
		// cooldown icons
		cooldown("Grapple", "15s", "FISHING_ROD");
		cooldown("Quake Axe", "8s", "GOLDEN_AXE");
		cooldown("Phase Bow", "12s", "BOW");
		cooldown("Radar", "20s", "COMPASS");
		// match timer, low-health vignette
		send("stopwatch.v1.StartStopwatchMessage", "");
		send("vignette.v1.DisplayVignetteMessage",
				"\"resource_location\":\"minecraft:textures/misc/vignette.png\",\"opacity\":0.55");
		// pop-ups
		note("&cFIRST BLOOD!", "You drew first blood! +15 coins", "6s");
		note("&6Kill streak: 3", "You're on a 3 kill streak!", "6s");
		note("&e&lMode: Arsenal", "Modifier: Low Gravity", "7s");
		// cosmetics: what the PlebMc shop plugin would send
		net.plebmc.client.cosmetics.Cosmetics.handle("{\"type\":\"state\",\"coins\":3200,"
				+ "\"owned\":[\"tophat\",\"crown\",\"flame\"],\"equipped\":{\"hat\":\"crown\",\"trail\":\"flame\"},"
				+ "\"dailyResetIn\":18720,\"weeklyResetIn\":297000,\"loginStreak\":4,"
				+ "\"questCategories\":[{\"id\":\"general\",\"name\":\"General\",\"icon\":\"general\",\"color\":\"#3FD8E6\"},"
				+ "{\"id\":\"plebnite\",\"name\":\"PlebNite\",\"icon\":\"plebnite\",\"color\":\"#FF9A3C\"}],\"quests\":["
				+ "{\"id\":\"login\",\"category\":\"general\",\"cycle\":\"demo\",\"streak\":4,\"streakLength\":7,\"name\":\"Daily login\",\"description\":\"Day 4 of your streak\",\"period\":\"login\",\"progress\":1,\"goal\":1,\"reward\":125,\"done\":true},"
				+ "{\"id\":\"general_play_30\",\"category\":\"general\",\"cycle\":\"demo\",\"name\":\"Hang out for 30 minutes\",\"description\":\"Be online anywhere on PlebMc\",\"period\":\"daily\",\"progress\":18,\"goal\":30,\"reward\":60},"
				+ "{\"id\":\"general_chat_10\",\"category\":\"general\",\"cycle\":\"demo\",\"name\":\"Say hi\",\"description\":\"Send 10 chat messages\",\"period\":\"daily\",\"progress\":10,\"goal\":10,\"reward\":40,\"done\":true},"
				+ "{\"id\":\"general_join_3\",\"category\":\"general\",\"cycle\":\"demo\",\"name\":\"Server hopper\",\"description\":\"Join or switch servers 3 times\",\"period\":\"daily\",\"progress\":1,\"goal\":3,\"reward\":40},"
				+ "{\"id\":\"login_5\",\"category\":\"general\",\"cycle\":\"demo\",\"name\":\"Log in on 5 days\",\"description\":\"Claim your daily login 5 times\",\"period\":\"weekly\",\"progress\":4,\"goal\":5,\"reward\":400},"
				+ "{\"id\":\"general_play_300\",\"category\":\"general\",\"cycle\":\"demo\",\"name\":\"Play for 5 hours\",\"description\":\"Be online anywhere this week\",\"period\":\"weekly\",\"progress\":95,\"goal\":300,\"reward\":500},"
				+ "{\"id\":\"kills_3\",\"category\":\"plebnite\",\"cycle\":\"demo\",\"name\":\"Get 3 kills\",\"description\":\"Eliminate players in matches\",\"period\":\"daily\",\"progress\":2,\"goal\":3,\"reward\":75},"
				+ "{\"id\":\"win_1\",\"category\":\"plebnite\",\"cycle\":\"demo\",\"name\":\"Win a match\",\"description\":\"Be the last one standing\",\"period\":\"daily\",\"progress\":0,\"goal\":1,\"reward\":150},"
				+ "{\"id\":\"play_30\",\"category\":\"plebnite\",\"cycle\":\"demo\",\"name\":\"Play PlebNite for 30 minutes\",\"description\":\"Be on PlebNite\",\"period\":\"daily\",\"progress\":30,\"goal\":30,\"reward\":60,\"done\":true},"
				+ "{\"id\":\"kills_40\",\"category\":\"plebnite\",\"cycle\":\"demo\",\"name\":\"Get 40 kills\",\"description\":\"Eliminate players this week\",\"period\":\"weekly\",\"progress\":17,\"goal\":40,\"reward\":600},"
				+ "{\"id\":\"wins_5\",\"category\":\"plebnite\",\"cycle\":\"demo\",\"name\":\"Win 5 matches\",\"description\":\"Be the last one standing 5 times\",\"period\":\"weekly\",\"progress\":1,\"goal\":5,\"reward\":750}]}");
		// a shop with an HD cloak in it (the client downloads the image like it would from the proxy)
		StringBuilder cat = new StringBuilder("{\"type\":\"state\",\"catalog\":[");
		for (net.plebmc.client.cosmetics.Cosmetics.Item it : net.plebmc.client.cosmetics.Cosmetics.catalog()) {
			cat.append("{\"id\":\"").append(it.id).append("\",\"name\":\"").append(it.name).append("\",\"category\":\"")
					.append(it.category).append("\",\"render\":\"").append(it.render).append("\",\"price\":").append(it.price)
					.append(it.color >= 0 ? ",\"color\":" + it.color : "").append(",\"description\":\"").append(it.description).append("\"},");
		}
		cat.append("{\"id\":\"storm_cloak\",\"name\":\"Storm Cloak\",\"category\":\"cloak\",\"render\":\"cloak\",\"price\":9000,\"texture\":\"")
				.append(PreviewCloak.ID).append("\",\"textureWidth\":512,\"description\":\"The storm, on your back (HD)\"}],")
				.append("\"owned\":[\"tophat\",\"crown\",\"flame\",\"storm_cloak\"],")
				.append("\"equipped\":{\"hat\":\"crown\",\"trail\":\"flame\",\"cloak\":\"storm_cloak\"}}");
		net.plebmc.client.cosmetics.Cosmetics.handle(cat.toString());
		net.plebmc.client.cosmetics.Cosmetics.handle("{\"type\":\"texture\",\"id\":\"" + PreviewCloak.ID
				+ "\",\"part\":0,\"parts\":1,\"data\":\"" + PreviewCloak.PNG_BASE64 + "\"}");
		// the storm: a shrinking world border nearby (client side only)
		try {
			net.minecraft.world.border.WorldBorder wb = mc.theWorld.getWorldBorder();
			wb.setCenter(p.posX + 60, p.posZ - 40);
			wb.setTransition(260, 140, 95_000L);
		} catch (Throwable t) {
			// no world border in this world
		}
		net.plebmc.client.cosmetics.Cosmetics.handle("{\"type\":\"account\",\"enabled\":true,\"linked\":true,\"username\":\"PreviewPleb\","
				+ "\"displayName\":\"PreviewPleb\",\"groups\":[\"Owner\",\"Member\"],\"staff\":true,\"site\":\"https://plebmc.top\"}");
		net.plebmc.client.cosmetics.Cosmetics.handle("{\"type\":\"notify\",\"title\":\"\u00a7aQuest complete!\",\"message\":\"Daily login: +125 PlebMc Coins\"}");
	}

	/** Takes everything the preview put on screen away again. */
	static void clear() {
		net.plebmc.client.cosmetics.Cosmetics.reset();
		try {
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
			if (mc.theWorld != null) {
				mc.theWorld.getWorldBorder().setCenter(0, 0);
				mc.theWorld.getWorldBorder().setTransition(6.0E7D);
			}
		} catch (Throwable t) {
			// nothing to undo
		}
		String[] resets = { "team.v1.ResetTeamMembersMessage", "glow.v1.ResetGlowEffectsMessage",
				"nametag.v1.ResetNametagsMessage", "waypoint.v1.ResetWaypointsMessage",
				"beam.v1.ResetBeaconBeamsMessage", "cooldown.v1.ResetCooldownsMessage",
				"stopwatch.v1.ResetStopwatchMessage", "vignette.v1.ResetVignetteMessage",
				"notification.v1.ResetNotificationsMessage" };
		for (String r : resets) {
			send(r, "");
		}
	}

	private static void cooldown(String name, String dur, String item) {
		send("cooldown.v1.DisplayCooldownMessage", "\"name\":\"" + name + "\",\"duration\":\"" + dur
				+ "\",\"icon\":{\"item_stack\":{\"item_name\":\"" + item + "\"}}");
	}

	private static void note(String title, String desc, String time) {
		send("notification.v1.DisplayNotificationMessage", "\"title_adventure_json_lines\":\"{\\\"text\\\":\\\""
				+ legacyToJson(title) + "\",\"description_adventure_json_lines\":\"{\\\"text\\\":\\\"" + desc
				+ "\\\",\\\"color\\\":\\\"gray\\\"}\",\"display_time\":\"" + time + "\"");
	}

	/** "&cText" to the rest of a JSON component string after {"text":" (escaped for embedding). */
	private static String legacyToJson(String s) {
		String color = "white";
		boolean bold = false;
		while (s.length() >= 2 && s.charAt(0) == '&') {
			char c = s.charAt(1);
			if (c == 'l') {
				bold = true;
			} else if (c == 'c') {
				color = "red";
			} else if (c == '6') {
				color = "gold";
			} else if (c == 'e') {
				color = "yellow";
			}
			s = s.substring(2);
		}
		return s + "\\\",\\\"color\\\":\\\"" + color + "\\\"" + (bold ? ",\\\"bold\\\":true" : "") + "}";
	}

	private static String member(String uuid, String name, int color, double x, double y, double z) {
		return "{\"player_uuid\":" + uuid + ",\"adventure_json_player_name\":\"{\\\"text\\\":\\\"" + name
				+ "\\\"}\",\"marker_color\":{\"color\":" + color + "},\"location\":{\"world\":\"world\",\"x\":" + x
				+ ",\"y\":" + y + ",\"z\":" + z + "}}";
	}

	private static String block(int x, int y, int z) {
		return "{\"world\":\"world\",\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z + "}";
	}

	private static void send(String type, String body) {
		Apollo.preview("{\"@type\":\"" + T + type + "\"" + (body.isEmpty() ? "" : "," + body) + "}");
	}

	static String uuid(EaglercraftUUID u) {
		return "{\"high64\":\"" + unsigned(u.getMostSignificantBits()) + "\",\"low64\":\""
				+ unsigned(u.getLeastSignificantBits()) + "\"}";
	}

	private static String unsigned(long v) {
		if (v >= 0) {
			return String.valueOf(v);
		}
		long q = (v >>> 1) / 5;
		long r = v - q * 10;
		return String.valueOf(q) + r;
	}
}
