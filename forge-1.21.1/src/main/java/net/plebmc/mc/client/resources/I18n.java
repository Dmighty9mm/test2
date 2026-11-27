package net.plebmc.mc.client.resources;

/** 1.8's I18n: 1.8 keys are looked up as they are, then through a few renames. */
public final class I18n {
	private I18n() {
	}

	public static String format(String key, Object... args) {
		String k = key;
		if (key.startsWith("potion.") && !key.startsWith("effect.")) {
			k = "effect.minecraft." + snake(key.substring(7));
		}
		return net.minecraft.client.resources.language.I18n.get(k, args);
	}

	private static String snake(String s) {
		StringBuilder b = new StringBuilder();
		for (char c : s.toCharArray()) {
			if (Character.isUpperCase(c)) {
				b.append('_').append(Character.toLowerCase(c));
			} else {
				b.append(c);
			}
		}
		String r = b.toString();
		switch (r) {
		case "move_speed":
			return "speed";
		case "move_slowdown":
			return "slowness";
		case "dig_speed":
			return "haste";
		case "dig_slow_down":
			return "mining_fatigue";
		case "damage_boost":
			return "strength";
		case "heal":
			return "instant_health";
		case "harm":
			return "instant_damage";
		case "jump":
			return "jump_boost";
		case "confusion":
			return "nausea";
		case "resistance":
			return "resistance";
		default:
			return r;
		}
	}
}
