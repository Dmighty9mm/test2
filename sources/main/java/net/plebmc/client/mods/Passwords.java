package net.plebmc.client.mods;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import net.plebmc.client.apollo.proto.Base64;
import net.plebmc.client.apollo.proto.Json;

/**
 * The login commands of auth plugins (/login, /l, /register, /reg): hiding
 * what is typed after them (Password Hider) and remembering one password per
 * server (Password Manager). Pure logic; the chat screen calls in.
 * <p>
 * Saved passwords stay on this device, under {@link #STORE_KEY}. They are
 * scrambled so they don't sit there as readable text, but that is not
 * encryption: anyone with access to the browser profile or the config folder
 * can get them back. They are never part of a settings export.
 */
public final class Passwords {

	public static final String STORE_KEY = "passwords";
	/** The command the [Save] button in chat runs; handled by the client, never sent. */
	public static final String COMMAND = "/plebpass";
	public static final int MAX_SERVERS = 100;
	private static final long PENDING_MS = 120000L;

	private static final String[] LOGIN = { "login", "l" };
	private static final String[] REGISTER = { "register", "reg" };

	private static final Map<String, String> SAVED = new LinkedHashMap<>();
	private static String pendingServer;
	private static String pendingPassword;
	private static long pendingAt;

	private Passwords() {
	}

	// ==================== reading a command ====================

	/** 1 for /login and /l, 2 for /register and /reg, 0 for anything else. The command may still be alone. */
	public static int kind(String text) {
		String c = command(text);
		if (c == null) {
			return 0;
		}
		for (String s : LOGIN) {
			if (s.equals(c)) {
				return 1;
			}
		}
		for (String s : REGISTER) {
			if (s.equals(c)) {
				return 2;
			}
		}
		return 0;
	}

	/** The command word without its slash, lower case, or null when the text isn't a command. */
	private static String command(String text) {
		if (text == null || text.length() < 2 || text.charAt(0) != '/') {
			return null;
		}
		int sp = text.indexOf(' ');
		return (sp < 0 ? text.substring(1) : text.substring(1, sp)).toLowerCase(Locale.ROOT);
	}

	/** True when the text is a login or register command with something typed after it. */
	public static boolean isSecret(String text) {
		if (kind(text) == 0) {
			return false;
		}
		int sp = text.indexOf(' ');
		return sp >= 0 && !text.substring(sp).trim().isEmpty();
	}

	/**
	 * The text as the chat box shows it: everything typed after a login or
	 * register command becomes stars, one per character, so the length (and
	 * the cursor) stay where they are. Other text comes back unchanged.
	 */
	public static String mask(String text) {
		if (kind(text) == 0) {
			return text;
		}
		int sp = text.indexOf(' ');
		if (sp < 0) {
			return text;
		}
		StringBuilder sb = new StringBuilder(text.length());
		sb.append(text, 0, sp);
		for (int i = sp; i < text.length(); i++) {
			sb.append(text.charAt(i) == ' ' ? ' ' : '*');
		}
		return sb.toString();
	}

	/** The password in a login or register command (its first argument), or null. */
	public static String passwordIn(String text) {
		if (!isSecret(text)) {
			return null;
		}
		String[] parts = text.trim().split(" +");
		if (parts.length < 2) {
			return null;
		}
		if (kind(text) == 2 && parts.length >= 3 && !parts[1].equals(parts[2])) {
			return null; // the two passwords differ: the server will refuse it
		}
		return parts[1];
	}

	// ==================== the saved passwords ====================

	private static String key(String server) {
		return server == null ? null : server.trim().toLowerCase(Locale.ROOT);
	}

	public static String saved(String server) {
		String k = key(server);
		return k == null ? null : SAVED.get(k);
	}

	public static int count() {
		return SAVED.size();
	}

	public static boolean save(String server, String password) {
		String k = key(server);
		if (k == null || k.isEmpty() || password == null || password.isEmpty() || password.length() > 100) {
			return false;
		}
		if (!SAVED.containsKey(k) && SAVED.size() >= MAX_SERVERS) {
			return false;
		}
		SAVED.put(k, password);
		return true;
	}

	public static boolean forget(String server) {
		String k = key(server);
		return k != null && SAVED.remove(k) != null;
	}

	public static void forgetAll() {
		SAVED.clear();
		clearPending();
	}

	/**
	 * What Tab turns the typed text into: the bare command (nothing after it
	 * yet) plus the password saved for this server ("/login" becomes
	 * "/login secret", "/reg" becomes "/reg secret secret"). Null when there is
	 * nothing to fill in.
	 */
	public static String complete(String typed, String server) {
		if (typed == null) {
			return null;
		}
		String t = typed.trim();
		int k = kind(t);
		if (k == 0 || t.indexOf(' ') >= 0) {
			return null;
		}
		String pw = saved(server);
		if (pw == null) {
			return null;
		}
		return k == 1 ? t + " " + pw : t + " " + pw + " " + pw;
	}

	// ==================== offering to save ====================

	/**
	 * A chat message is about to be sent: when it logs in with a password that
	 * isn't the saved one, it is kept for a moment so the player can choose to
	 * save it. True when there is something new to offer.
	 */
	public static boolean sent(String message, String server, long now) {
		String pw = passwordIn(message);
		String k = key(server);
		if (pw == null || k == null || k.isEmpty() || pw.equals(SAVED.get(k))) {
			return false;
		}
		pendingServer = k;
		pendingPassword = pw;
		pendingAt = now;
		return true;
	}

	/** The server the kept password is for, or null when there is none (or it got old). */
	public static String pendingServer(long now) {
		if (pendingPassword == null || now - pendingAt > PENDING_MS || now < pendingAt) {
			clearPending();
			return null;
		}
		return pendingServer;
	}

	/** Saves the kept password; the server it was saved for, or null. */
	public static String savePending(long now) {
		String s = pendingServer(now);
		if (s == null) {
			return null;
		}
		boolean ok = save(s, pendingPassword);
		clearPending();
		return ok ? s : null;
	}

	public static void clearPending() {
		pendingServer = null;
		pendingPassword = null;
		pendingAt = 0;
	}

	// ==================== storage ====================

	private static final byte[] PAD = "PlebClient keeps this on your device".getBytes(StandardCharsets.UTF_8);

	static String scramble(String s) {
		byte[] b = s.getBytes(StandardCharsets.UTF_8);
		for (int i = 0; i < b.length; i++) {
			b[i] ^= PAD[i % PAD.length];
		}
		return Base64.encode(b);
	}

	static String unscramble(String s) {
		byte[] b = Base64.decode(s);
		for (int i = 0; i < b.length; i++) {
			b[i] ^= PAD[i % PAD.length];
		}
		return new String(b, StandardCharsets.UTF_8);
	}

	public static String serialize() {
		Map<String, Object> root = new LinkedHashMap<>();
		for (Map.Entry<String, String> e : SAVED.entrySet()) {
			root.put(e.getKey(), scramble(e.getValue()));
		}
		return Json.write(root);
	}

	public static void save(SettingsStore store) {
		if (store != null) {
			store.set(STORE_KEY, serialize());
		}
	}

	/** Loads the saved passwords; a broken entry is skipped. */
	public static void load(SettingsStore store) {
		SAVED.clear();
		String raw = store == null ? null : store.get(STORE_KEY);
		if (raw == null || raw.isEmpty()) {
			return;
		}
		try {
			Object root = Json.parse(raw);
			if (!(root instanceof Map)) {
				return;
			}
			for (Map.Entry<?, ?> e : ((Map<?, ?>) root).entrySet()) {
				if (e.getValue() instanceof String && SAVED.size() < MAX_SERVERS) {
					try {
						String pw = unscramble((String) e.getValue());
						if (!pw.isEmpty()) {
							SAVED.put(key(String.valueOf(e.getKey())), pw);
						}
					} catch (RuntimeException bad) {
						// skip it
					}
				}
			}
		} catch (RuntimeException e) {
			SAVED.clear();
		}
	}
}
