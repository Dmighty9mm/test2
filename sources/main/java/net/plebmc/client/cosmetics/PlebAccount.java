package net.plebmc.client.cosmetics;

import java.util.HashMap;
import java.util.Map;

import net.plebmc.client.mods.SettingsStore;

/**
 * The player's PlebMc account (the server's panel), as the server reports it.
 * On an offline-mode server anyone can join with any name, so the server only
 * lets you spend coins, buy or equip after you sign in to the account that
 * owns your name. With "Remember me" the server hands back a token, kept in
 * this browser, and the client signs in with it by itself on the next join.
 * The password itself is never stored.
 */
public final class PlebAccount {

	/** The server answered at all. */
	public boolean known;
	/** The server has PlebMc accounts turned on. */
	public boolean enabled;
	/** The server knows whether this name has an account yet. */
	public boolean checked;
	public boolean exists;
	public boolean signedIn;
	public boolean locked;
	public String name = "";
	public String role = "player";
	/** The panel's address (shown and opened from the Sign in screen). */
	public String panel = "";
	/** Seconds until the server kicks a name with an account that hasn't signed in (-1: never). */
	public long kickIn = -1;
	public long kickInAt;
	public String message;
	public boolean messageOk;
	/** Waiting for the server. */
	public boolean pending;
	/** Signing in with a saved token right now. */
	public boolean auto;
	public int version;
	private boolean triedToken;

	/** Where tokens are kept (the browser's storage in the real client; a map in tests). */
	static SettingsStore store = new SettingsStore() {
		private final Map<String, String> m = new HashMap<>();

		@Override
		public String get(String key) {
			return m.get(key);
		}

		@Override
		public void set(String key, String value) {
			if (value == null) {
				m.remove(key);
			} else {
				m.put(key, value);
			}
		}
	};

	public static void storage(SettingsStore s) {
		if (s != null) {
			store = s;
		}
	}

	void clear() {
		known = enabled = checked = exists = signedIn = locked = pending = auto = triedToken = false;
		name = "";
		role = "player";
		panel = "";
		kickIn = -1;
		message = null;
		version++;
	}

	private String tokenKey() {
		return "signin." + panel.toLowerCase() + "|" + name.toLowerCase();
	}

	String savedToken() {
		String t = name.isEmpty() || panel.isEmpty() ? null : store.get(tokenKey());
		// the start screen's sign-in works on the server too
		return t != null ? t : ClientSession.tokenFor(name);
	}

	/** A "signin" message from the server. Returns a token to sign in with, or null. */
	String update(Map<?, ?> m) {
		known = true;
		pending = false;
		enabled = Boolean.TRUE.equals(m.get("enabled"));
		checked = Boolean.TRUE.equals(m.get("known"));
		exists = Boolean.TRUE.equals(m.get("exists"));
		signedIn = Boolean.TRUE.equals(m.get("signedIn"));
		locked = Boolean.TRUE.equals(m.get("locked"));
		name = str(m.get("name"), name);
		role = str(m.get("role"), "player");
		panel = str(m.get("panel"), panel);
		if (m.get("kickIn") instanceof Number && !signedIn) {
			kickIn = ((Number) m.get("kickIn")).longValue();
			kickInAt = System.currentTimeMillis();
		} else {
			kickIn = -1;
		}
		if (m.containsKey("message")) {
			message = str(m.get("message"), null);
			messageOk = !Boolean.FALSE.equals(m.get("ok"));
		}
		if (Boolean.TRUE.equals(m.get("forget")) && !name.isEmpty()) {
			if (store.get(tokenKey()) != null) {
				store.set(tokenKey(), null);
			} else if (auto) {
				ClientSession.tokenRefused(name);
			}
		}
		if (m.get("token") instanceof String && !name.isEmpty()) {
			store.set(tokenKey(), (String) m.get("token"));
		}
		boolean wasAuto = auto;
		auto = false;
		if (wasAuto && !signedIn) {
			message = "Sign in again (your saved sign-in expired).";
			messageOk = false;
		}
		version++;
		if (enabled && exists && !signedIn && !locked && !triedToken) {
			String t = savedToken();
			if (t != null) {
				triedToken = true;
				auto = true;
				pending = true;
				return t;
			}
		}
		return null;
	}

	/** Seconds left before the kick, counting down between server updates (-1: none). */
	public long secondsLeft() {
		if (kickIn < 0) {
			return -1;
		}
		return Math.max(0, kickIn - (System.currentTimeMillis() - kickInAt) / 1000);
	}

	public boolean hasSavedToken() {
		return savedToken() != null;
	}

	private static String str(Object o, String def) {
		return o instanceof String && !((String) o).isEmpty() ? (String) o : def;
	}
}
