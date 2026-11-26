package net.plebmc.client.cosmetics;

import java.util.LinkedHashMap;
import java.util.Map;

import net.plebmc.client.apollo.proto.Json;
import net.plebmc.client.mods.SettingsStore;

/**
 * The PlebMc account this client is signed in to. The client can't be used
 * until you sign in or create an account (the start screen), and you always
 * play under your account's name. The panel hands back a sign-in token that is
 * kept in this browser (never the password); the server checks the same token
 * when you join, so nobody can play or spend coins as you.
 */
public final class ClientSession {

	/** Where the account panel lives. A server can't change it. */
	public static final String DEFAULT_PANEL = "https://client.plebmc.top";

	public enum State {
		/** Not signed in: the start screen shows. */
		SIGNED_OUT,
		/** Signed in (a saved sign-in is being re-checked in the background, or was). */
		SIGNED_IN
	}

	/** Sends a POST and calls back with (status, body); status 0 = couldn't connect. */
	public interface Http {
		void post(String url, String json, Done done);
	}

	public interface Done {
		void done(int status, String body);
	}

	/** Set by the client at start-up (the browser's XHR or the desktop's HttpURLConnection). */
	static Http http;
	static SettingsStore store = PlebAccount.store;
	/** Sets the account name as the player's username (Eaglercraft profile). */
	static java.util.function.Consumer<String> profileName = n -> {
	};

	private static State state = State.SIGNED_OUT;
	private static String name = "";
	private static String token = "";
	private static String role = "player";
	private static String panel = DEFAULT_PANEL;
	private static boolean loaded;
	/** Waiting for the panel. */
	public static volatile boolean pending;
	public static volatile String message;
	public static volatile boolean messageOk;
	public static volatile int version;

	private ClientSession() {
	}

	public static void setup(Http h, SettingsStore s, java.util.function.Consumer<String> setProfileName) {
		http = h;
		if (s != null) {
			store = s;
		}
		if (setProfileName != null) {
			profileName = setProfileName;
		}
		loaded = false;
		load();
	}

	/** Reads the saved sign-in and re-checks it with the panel in the background. */
	static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		String p = store.get("account.panel");
		panel = p == null || p.trim().isEmpty() ? DEFAULT_PANEL : p.trim();
		String n = store.get("account.name");
		String t = store.get("account.token");
		if (n == null || t == null || n.isEmpty() || t.isEmpty()) {
			state = State.SIGNED_OUT;
			return;
		}
		name = n;
		token = t;
		role = orElse(store.get("account.role"), "player");
		state = State.SIGNED_IN;
		profileName.accept(name);
		final String checkName = n, checkToken = t;
		post("check", body("name", n, "token", t), (status, r) -> {
			synchronized (ClientSession.class) {
				if (!checkToken.equals(token)) {
					return; // signed in again meanwhile
				}
				if (r != null && Boolean.FALSE.equals(r.get("ok"))) {
					// the panel says no (password changed, signed out everywhere, locked, deleted)
					clear();
					message = orElse(str(r.get("error")), "Sign in again.");
					messageOk = false;
				} else if (r != null) {
					role = orElse(str(r.get("role")), role);
					store.set("account.role", role);
				}
				// no answer at all (panel down): keep the saved sign-in; the server checks it again
				version++;
			}
		});
	}

	/** May the client be used? */
	public static synchronized boolean signedIn() {
		load();
		return state == State.SIGNED_IN;
	}

	/** Puts the account's name back as the username (in case it was changed in the profile screen). */
	public static synchronized void ensureName() {
		if (signedIn() && !name.isEmpty()) {
			profileName.accept(name);
		}
	}

	public static synchronized String name() {
		return name;
	}

	public static synchronized String role() {
		return role;
	}

	public static synchronized String panel() {
		load();
		return panel;
	}

	/** The saved token, if it belongs to this name (sent to the server on join). */
	public static synchronized String tokenFor(String playerName) {
		load();
		return state == State.SIGNED_IN && playerName != null && playerName.equalsIgnoreCase(name) ? token : null;
	}

	public static void signIn(String n, String password) {
		submit("login", n, password);
	}

	public static void register(String n, String password) {
		submit("register", n, password);
	}

	private static void submit(String route, String n, String password) {
		n = n == null ? "" : n.trim();
		if (!n.matches("[A-Za-z0-9_]{3,16}")) {
			fail("Names are 3 to 16 letters, digits or _.");
			return;
		}
		if (password == null || password.length() < 8) {
			fail("Passwords are at least 8 characters.");
			return;
		}
		pending = true;
		message = null;
		version++;
		post(route, body("name", n, "password", password), (status, r) -> {
			synchronized (ClientSession.class) {
				pending = false;
				if (r == null) {
					message = status == 0 ? "Can't reach the account server. Check your internet and try again."
							: "The account server had a problem (" + status + "). Try again.";
					messageOk = false;
				} else if (!Boolean.TRUE.equals(r.get("ok")) || !(r.get("token") instanceof String)) {
					message = orElse(str(r.get("error")), "That didn't work. Try again.");
					messageOk = false;
				} else {
					name = orElse(str(r.get("name")), name);
					token = (String) r.get("token");
					role = orElse(str(r.get("role")), "player");
					store.set("account.name", name);
					store.set("account.token", token);
					store.set("account.role", role);
					state = State.SIGNED_IN;
					message = "register".equals(route) ? "Account made. Welcome, " + name + "!" : "Signed in as " + name + ".";
					messageOk = true;
					profileName.accept(name);
				}
				version++;
			}
		});
	}

	/** Signs out of this client (and tells the panel to forget the token). */
	public static void signOut() {
		String t;
		synchronized (ClientSession.class) {
			t = token;
			clear();
			message = "Signed out.";
			messageOk = true;
			version++;
		}
		if (t != null && !t.isEmpty()) {
			post("logout", body("token", t), (s, r) -> {
			});
		}
	}

	/** The server refused this client's token: sign in again. */
	public static synchronized void tokenRefused(String forName) {
		if (state == State.SIGNED_IN && forName != null && forName.equalsIgnoreCase(name)) {
			clear();
			message = "Your sign-in expired. Sign in again.";
			messageOk = false;
			version++;
		}
	}

	/** Changes the account server address (advanced: for servers that host their own panel). */
	public static synchronized void setPanel(String url) {
		String u = url == null ? "" : url.trim().replaceAll("/+$", "");
		if (!u.isEmpty() && !u.matches("https?://.+")) {
			u = "https://" + u;
		}
		panel = u.isEmpty() ? DEFAULT_PANEL : u;
		store.set("account.panel", u.isEmpty() ? null : u);
	}

	private static void clear() {
		state = State.SIGNED_OUT;
		token = "";
		store.set("account.token", null);
	}

	private static void fail(String why) {
		message = why;
		messageOk = false;
		version++;
	}

	private interface Answer {
		void answer(int status, Map<?, ?> json);
	}

	private static void post(String route, Map<String, Object> b, Answer a) {
		Http h = http;
		if (h == null) {
			a.answer(0, null);
			return;
		}
		String url = panel() + "/api/client/" + route;
		try {
			h.post(url, Json.write(b), (status, text) -> {
				Map<?, ?> m = null;
				if (text != null && status > 0 && status < 500) {
					try {
						Object o = Json.parse(text);
						m = o instanceof Map ? (Map<?, ?>) o : null;
					} catch (RuntimeException e) {
						m = null;
					}
				}
				a.answer(status, m);
			});
		} catch (RuntimeException e) {
			a.answer(0, null);
		}
	}

	private static Map<String, Object> body(String... kv) {
		Map<String, Object> m = new LinkedHashMap<>();
		for (int i = 0; i + 1 < kv.length; i += 2) {
			m.put(kv[i], kv[i + 1]);
		}
		return m;
	}

	private static String str(Object o) {
		return o instanceof String ? (String) o : null;
	}

	private static String orElse(String s, String def) {
		return s == null || s.isEmpty() ? def : s;
	}

	/** Tests: forget everything. */
	static synchronized void resetForTests() {
		state = State.SIGNED_OUT;
		name = token = "";
		role = "player";
		panel = DEFAULT_PANEL;
		loaded = false;
		pending = false;
		message = null;
	}
}
