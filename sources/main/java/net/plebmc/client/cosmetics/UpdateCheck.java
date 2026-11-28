package net.plebmc.client.cosmetics;

import java.util.Map;

/**
 * Asks the PlebMc website (the panel) at start-up whether a newer PlebClient is
 * out. The owner sets the latest and the oldest allowed version on the website;
 * an older client is told to update, and one below the minimum can't be played.
 * The update comes from the website too: "Update now" downloads this platform's
 * build from its Downloads page (or opens the link the owner set instead).
 */
public final class UpdateCheck {

	/** The version of this build. Raise it for each release and set "Latest version" on the panel to match. */
	public static final String VERSION = "1.0";

	/** The panel answered. */
	public static volatile boolean known;
	/** A newer version is out. */
	public static volatile boolean outdated;
	/** This version is too old to play: updating is required. */
	public static volatile boolean required;
	public static volatile String latest = "";
	/** Where "Update now" goes: this platform's download on the website, or the owner's own link. */
	public static volatile String url = "";
	/** The website's Downloads page (every build, and what's new). */
	public static volatile String site = "";
	/** True when {@link #url} is the file itself (the website's download for this platform). */
	public static volatile boolean direct;
	private static volatile String platform = "unknown";
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
		UpdateCheck.platform = platform == null ? "unknown" : platform;
		Map<String, Object> b = new java.util.LinkedHashMap<>();
		b.put("version", VERSION);
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
		String panel = ClientSession.panel();
		site = panel + "/#downloads";
		String kind = downloadKind(platform);
		if ((u.isEmpty() || u.equals(site)) && kind != null) {
			// no link of the owner's own: this platform's build, straight from the website
			url = panel + "/api/dl/" + kind;
			direct = true;
		} else {
			// only web links open from here
			url = u.startsWith("https://") || u.startsWith("http://") ? u : site;
			direct = false;
		}
		message = text(r.get("message"), 200);
		version++;
	}

	/** The website's download for a platform (its Downloads page has one per build), or null. */
	static String downloadKind(String platform) {
		switch (platform == null ? "" : platform) {
		case "web":
		case "js": // what the browser build reports
			return "offline";
		case "wasm":
			return "wasm";
		case "forge":
			return "forge-1.8.9";
		default:
			return null; // desktop and others: the Downloads page
		}
	}

	/** Should the update screen show now? */
	public static boolean shouldShow() {
		return required || (outdated && !dismissed);
	}

	/** Test hook: forget everything. */
	public static synchronized void reset() {
		known = outdated = required = dismissed = asked = false;
		latest = url = site = message = "";
		direct = false;
		platform = "unknown";
		version++;
	}

	private static String text(Object o, int max) {
		String s = o instanceof String ? (String) o : "";
		return s.length() > max ? s.substring(0, max) : s;
	}
}
