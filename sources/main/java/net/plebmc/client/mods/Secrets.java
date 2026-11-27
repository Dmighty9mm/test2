package net.plebmc.client.mods;

/**
 * The pure parts of the client's easter eggs (no game classes, so they can be
 * tested): the list of secrets, the Konami code and typed-word matchers, and
 * the holiday calendar. The effects live in hud.EasterEggs.
 */
public final class Secrets {

	public static final String[] IDS = { "konami", "rain", "roll", "dinnerbone", "plebium", "holiday", "fpslarp",
			"hello", "credits", "sleepy", "nightowl", "coinflip", "dizzy", "snow", "buddy", "bighead" };
	public static final String[] HINTS = { "An old cheat code works on the title screen (arrows, then B A)",
			"Type the server's name on the title screen", "Ask the title screen to do a barrel roll",
			"A famous upside-down name, typed on the title screen",
			"Something is hiding in the Video Settings title", "Come back on a holiday",
			"Secret mod: FPS is just a number. Try it in the Mods search.",
			"Say hello to Plebio on the title screen", "Every movie ends with them. Type them on the title screen.",
			"Leave Plebio alone on the title screen for a while", "Play when you should be asleep",
			"Click your coins in the Right Shift menu", "Spin yourself around in the Shop or Locker. A lot.",
			"Secret mod: it's cold outside. Let it... (Mods search)",
			"Secret mod: Plebio wants to be your best friend (Mods search)",
			"Secret mod: some heads are bigger than others (Mods search)" };
	/** Secret mods: the word typed in the Mods search, and the secret it finds. */
	public static final String[][] MOD_WORDS = { { "fpslarp", "fpslarp" }, { "letitsnow", "snow" },
			{ "bestfriend", "buddy" }, { "bighead", "bighead" } };

	/** Up up down down left right left right B A (LWJGL key codes). */
	public static final int[] KONAMI = { 200, 200, 208, 208, 203, 205, 203, 205, 48, 30 };
	/** Words typed on the title screen, and the secret each one finds. */
	public static final String[][] WORDS = { { "pleb", "rain" }, { "barrelroll", "roll" }, { "dinnerbone", "dinnerbone" },
			{ "hello", "hello" }, { "credits", "credits" } };

	private int konamiAt;
	private final StringBuilder typed = new StringBuilder();

	/** Feeds one key; true when it completes the Konami code. */
	public boolean konami(int key) {
		if (key == KONAMI[konamiAt]) {
			konamiAt++;
		} else if (key == KONAMI[0]) {
			konamiAt = konamiAt == 2 ? 2 : 1; // a third "up" still counts as the start
		} else {
			konamiAt = 0;
		}
		if (konamiAt < KONAMI.length) {
			return false;
		}
		konamiAt = 0;
		return true;
	}

	/** Feeds one typed character; returns the secret id of a word it completes, or null. */
	public String letter(char c) {
		if (!Character.isLetter(c)) {
			return null;
		}
		typed.append(Character.toLowerCase(c));
		if (typed.length() > 24) {
			typed.delete(0, typed.length() - 24);
		}
		String s = typed.toString();
		for (String[] w : WORDS) {
			if (s.endsWith(w[0])) {
				typed.setLength(0);
				return w[1];
			}
		}
		return null;
	}

	/** The secret a Mods search finds, or null. */
	public static String modWord(String search) {
		String s = search == null ? "" : search.trim().toLowerCase().replace(" ", "");
		for (String[] w : MOD_WORDS) {
			if (w[0].equals(s)) {
				return w[1];
			}
		}
		return null;
	}

	/** True for the small hours (2 to 4:59 AM). */
	public static boolean nightOwl(int hour) {
		return hour >= 2 && hour < 5;
	}

	public void reset() {
		konamiAt = 0;
		typed.setLength(0);
	}

	/**
	 * The holiday on a date, or null.
	 *
	 * @param month 1..12
	 * @param day   1..31
	 * @param dow   day of the week, 1 = Sunday .. 7 = Saturday
	 * @return {greeting, color 1, color 2}
	 */
	public static Object[] holiday(int month, int day, int dow) {
		if (month == 1 && day == 1) {
			return new Object[] { "Happy New Year!", 0xFFF5D142, 0xFF7AD3FF };
		}
		if (month == 2 && day == 14) {
			return new Object[] { "Happy Valentine's Day!", 0xFFFF6FAE, 0xFFFF3B5C };
		}
		if (month == 4 && day == 1) {
			return new Object[] { "Happy April Fools' Day!", 0xFF4FE39A, 0xFFB085FF };
		}
		if (month == 10 && day == 31) {
			return new Object[] { "Happy Halloween!", 0xFFFF8A1F, 0xFF9B4DFF };
		}
		// the fourth Thursday of November
		if (month == 11 && dow == 5 && day >= 22 && day <= 28) {
			return new Object[] { "Happy Thanksgiving!", 0xFFE0782A, 0xFFB5482A };
		}
		if (month == 12 && day >= 24 && day <= 26) {
			return new Object[] { "Merry Plebmas!", 0xFFE8EEF2, 0xFFFF4A4A };
		}
		if (month == 12 && day == 31) {
			return new Object[] { "Happy New Year's Eve!", 0xFFF5D142, 0xFFE8EEF2 };
		}
		return null;
	}
}
