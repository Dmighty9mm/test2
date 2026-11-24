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
				+ "\"dailyResetIn\":18720,\"loginStreak\":4,"
				+ "\"questCategories\":[{\"id\":\"lobby\",\"name\":\"PlebMc Lobby\",\"tab\":\"Lobby\"},{\"id\":\"plebnite\",\"name\":\"PlebNite\",\"tab\":\"PlebNite\"}],\"quests\":["
				+ "{\"id\":\"login\",\"name\":\"Daily login\",\"description\":\"Day 4 of your streak\",\"period\":\"login\",\"progress\":1,\"goal\":1,\"reward\":125,\"done\":true},"
				+ "{\"id\":\"lobby_parkour\",\"category\":\"lobby\",\"name\":\"Parkour run\",\"description\":\"Finish the lobby parkour\",\"period\":\"daily\",\"progress\":0,\"goal\":1,\"reward\":100},"
				+ "{\"id\":\"lobby_play_20\",\"category\":\"lobby\",\"name\":\"Hang out 20 minutes\",\"description\":\"Be online in the lobby\",\"period\":\"daily\",\"progress\":12,\"goal\":20,\"reward\":50},"
				+ "{\"id\":\"kills_3\",\"category\":\"plebnite\",\"name\":\"Get 3 kills\",\"description\":\"Eliminate players in matches\",\"period\":\"daily\",\"progress\":2,\"goal\":3,\"reward\":75},"
				+ "{\"id\":\"play_30\",\"category\":\"plebnite\",\"name\":\"Play 30 minutes\",\"description\":\"Play PlebNite\",\"period\":\"daily\",\"progress\":12,\"goal\":30,\"reward\":60},"
				+ "{\"id\":\"wins_5\",\"category\":\"plebnite\",\"name\":\"Win 5 matches\",\"description\":\"Be the last one standing\",\"period\":\"weekly\",\"progress\":1,\"goal\":5,\"reward\":750}]}");
		net.plebmc.client.cosmetics.Cosmetics.handle("{\"type\":\"notify\",\"title\":\"\u00a7aQuest complete!\",\"message\":\"Daily login: +125 PlebMc Coins\"}");
	}

	/** Takes everything the preview put on screen away again. */
	static void clear() {
		net.plebmc.client.cosmetics.Cosmetics.reset();
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
