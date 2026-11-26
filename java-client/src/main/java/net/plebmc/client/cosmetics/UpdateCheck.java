package net.plebmc.client.cosmetics;

import java.util.Map;

import net.plebmc.client.mods.ClientInfo;

/**
 * Asks the account panel at start-up whether a newer PlebClient is out. The
 * owner sets the latest and the oldest allowed version on the panel; an older
 * client is told to update, and one below the minimum can't be played.
 */
public final class UpdateCheck {

	/** The panel answered. */
	public static volatile boolean known;
	/** A newer version is out. */
	public static volatile boolean outdated;
	/** This version is too old to play: updating is required. */
	public static volatile boolean required;
	public static volatile String latest = "";
	public static volatile String url = "";
	public static volatile String message = "";
	/** "Later" was pressed this session. */
	public static volatile boolean dismissed;
	public static volatile int version;
	private static volatile boolean asked;

	private UpdateCheck() {
	}

	/** Asks once per start. {@code platform}: web, wasm, desktop or forge. */
	public static void check(String platform) {
		if (asked) {
			return;
		}
		asked = true;
		Map<String, Object> b = new java.util.LinkedHashMap<>();
		b.put("version", ClientInfo.VERSION);
		b.put("platform", platform == null ? "unknown" : platform);
		ClientSession.post("version", b, (status, r) -> apply(r));
	}

	/** Reads the panel's answer (no answer: nothing changes, the client works as before). */
	static synchronized void apply(Map<?, ?> r) {
		if (r == null || !Boolean.TRUE.equals(r.get("ok"))) {
			return;
		}
		known = true;
		outdated = Boolean.TRUE.equals(r.get("outdated"));
		required = Boolean.TRUE.equals(r.get("required"));
		latest = text(r.get("latest"), 20);
		String u = text(r.get("url"), 300);
		// only web links open from here
		url = u.startsWith("https://") || u.startsWith("http://") ? u : ClientSession.panel();
		message = text(r.get("message"), 200);
		version++;
	}

	/** Should the update screen show now? */
	public static boolean shouldShow() {
		return required || (outdated && !dismissed);
	}

	/** Test hook: forget everything. */
	public static synchronized void reset() {
		known = outdated = required = dismissed = asked = false;
		latest = url = message = "";
		version++;
	}

	private static String text(Object o, int max) {
		String s = o instanceof String ? (String) o : "";
		return s.length() > max ? s.substring(0, max) : s;
	}
}
