package net.plebmc.shop;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;

import com.google.gson.Gson;

/**
 * PlebMc accounts in game. On an offline-mode server anyone can join with any
 * name, so a name proves nothing: a player only counts as signed in (and can
 * spend coins, buy, equip, link or open the admin menu) after giving the
 * password of the panel account that owns their name, either with
 * {@code /account login <password>} (any client) or from the PlebMc client's
 * Sign in screen, which can remember a token so it signs in by itself next
 * time. Nothing here depends on IP addresses: Eaglercraft players often share
 * one.
 * <p>
 * Names with an account must sign in within {@code accounts.sign-in-seconds}
 * or are kicked, so nobody can stand around on someone else's name. Names
 * without an account can still play and earn, but can't spend until they make
 * one ({@code /account register} in game, or the website with a code from
 * {@code /account code}).
 * <p>
 * Free of BungeeCord classes so it can be tested.
 */
final class SignIn {

	/** One online player. */
	static final class Session {
		final UUID id;
		final String name;
		final long joined;
		/** The panel answered whether this name has an account. */
		boolean known;
		boolean exists;
		boolean locked;
		String canonical;
		String role = "player";
		boolean signedIn;
		/** A sign-in request is out. */
		boolean busy;
		long lastTry;
		boolean warned;

		Session(UUID id, String name, long joined) {
			this.id = id;
			this.name = name;
			this.joined = joined;
			this.canonical = name;
		}
	}

	private static final long TRY_COOLDOWN_MS = 1500;
	private static final long LOOKUP_RETRY_MS = 30_000;

	private final Gson gson = new Gson();
	private final ShopCore.Net net;
	private final Logger log;
	private final Map<UUID, Session> sessions = new HashMap<>();
	private PanelLink panel;
	private boolean enabled;
	private long signInMs;
	private String publicUrl = "";
	private String prefix = "";
	/** May this player create an account right now? (with nLogin: only after /login) */
	Predicate<UUID> mayRegister = p -> true;
	/** Called when a player signs in or out (the admin door and shop refresh). */
	Consumer<UUID> changed = p -> {
	};

	SignIn(ShopCore.Net net, Logger log) {
		this.net = net;
		this.log = log;
	}

	synchronized void configure(Cfg c, PanelLink link) {
		panel = link;
		enabled = c.bool("accounts.enabled", false) && link.configured();
		signInMs = Math.max(0, c.num("accounts.sign-in-seconds", 90)) * 1000L;
		publicUrl = c.str("accounts.public-url", "").trim().replaceAll("/+$", "");
		if (publicUrl.isEmpty()) {
			publicUrl = link.url;
		}
		prefix = ShopCore.color(c.str("messages.prefix", "&8[&b&lPlebMc&8] &7"));
		if (c.bool("accounts.enabled", false) && !link.configured()) {
			log.warning("accounts.enabled is on but accounts.url or accounts.server-key (24+ characters) is missing:"
					+ " PlebMc accounts are OFF");
		}
	}

	synchronized boolean enabled() {
		return enabled;
	}

	synchronized boolean signedIn(UUID p) {
		Session s = sessions.get(p);
		return s != null && s.signedIn;
	}

	synchronized Session session(UUID p) {
		return sessions.get(p);
	}

	// ==================== join / leave ====================

	synchronized void joined(UUID p, String name) {
		if (!enabled) {
			return;
		}
		Session s = new Session(p, name, net.now());
		sessions.put(p, s);
		lookup(s);
	}

	synchronized void left(UUID p) {
		sessions.remove(p);
	}

	private void lookup(Session s) {
		final PanelLink link = panel;
		net.async(() -> {
			Map<?, ?> r;
			try {
				r = link.lookup(s.name);
			} catch (IOException e) {
				log.warning("PlebMc accounts: can't reach the panel at " + link.url + " (" + e.getMessage() + ")");
				return;
			}
			synchronized (SignIn.this) {
				if (sessions.get(s.id) != s) {
					return;
				}
				s.known = true;
				s.exists = Boolean.TRUE.equals(r.get("exists"));
				s.locked = Boolean.TRUE.equals(r.get("locked"));
				if (s.exists) {
					s.canonical = str(r.get("name"), s.name);
					s.role = str(r.get("role"), "player");
				}
			}
			if (s.exists && !s.canonical.equals(s.name)) {
				// "owner" is a different UUID from "Owner": don't let a look-alike spelling stand in
				net.kick(s.id, "§cThis name is registered as §f" + s.canonical
						+ "§c. Join with exactly that spelling.");
				return;
			}
			sendStatus(s.id, null, true);
			if (s.exists && !s.signedIn) {
				net.chat(s.id, prefix + ShopCore.color(s.locked ? "&cThis account is locked. Ask an admin."
						: "&eThis name has a PlebMc account. Sign in: &f/account login <password>"
								+ (signInMs > 0 ? " &e(within " + signInMs / 1000 + " seconds)" : "")
								+ " &7or Right Shift > Sign in on the PlebMc client."));
			} else if (!s.exists) {
				net.chat(s.id, prefix + ShopCore.color("&7Make a PlebMc account so nobody else can spend your coins: "
						+ "&f/account register <password> <password>"));
			}
		});
	}

	/** Every few seconds: kick names with an account that didn't sign in in time, retry failed lookups. */
	void tick() {
		java.util.List<Session> kick = new java.util.ArrayList<>();
		java.util.List<Session> retry = new java.util.ArrayList<>();
		synchronized (this) {
			if (!enabled) {
				return;
			}
			long now = net.now();
			for (Session s : sessions.values()) {
				if (!s.known && now - s.joined > LOOKUP_RETRY_MS && now - s.lastTry > LOOKUP_RETRY_MS) {
					s.lastTry = now;
					retry.add(s);
				}
				if (!s.known || !s.exists || s.signedIn || signInMs <= 0) {
					continue;
				}
				long left = signInMs - (now - s.joined);
				if (left <= 0) {
					kick.add(s);
				} else if (left <= signInMs / 2 && !s.warned) {
					s.warned = true;
					net.chat(s.id, prefix + ShopCore.color("&eSign in within " + Math.max(1, left / 1000)
							+ " seconds or you'll be kicked: &f/account login <password>"));
				}
			}
		}
		for (Session s : retry) {
			lookup(s);
		}
		for (Session s : kick) {
			log.info("PlebMc accounts: kicked " + s.name + " (didn't sign in)");
			net.kick(s.id, "§cThis name has a PlebMc account and you didn't sign in.\n§7Use §f/account login <password>"
					+ "§7 right after joining, or the Sign in screen on the PlebMc client.");
		}
	}

	// ==================== signing in ====================

	/** From the PlebMc client: {"type":"signin"} (status), with "password" (+"remember") or "token"; {"type":"signout"}. */
	void fromClient(UUID p, Map<?, ?> m) {
		String type = String.valueOf(m.get("type"));
		if ("signout".equals(type)) {
			String token = m.get("token") instanceof String ? (String) m.get("token") : null;
			signOut(p, token, "Signed out.");
			return;
		}
		if (Boolean.TRUE.equals(m.get("register")) && m.get("password") instanceof String) {
			registerFromClient(p, (String) m.get("password"), Boolean.TRUE.equals(m.get("remember")));
			return;
		}
		if (m.get("token") instanceof String && !((String) m.get("token")).isEmpty()) {
			attempt(p, null, (String) m.get("token"), false, true);
		} else if (m.get("password") instanceof String) {
			attempt(p, (String) m.get("password"), null, Boolean.TRUE.equals(m.get("remember")), true);
		} else {
			sendStatus(p, null, true);
		}
	}

	/** Checks a password or a remembered token with the panel (off-thread). */
	private void attempt(UUID p, String password, String token, boolean remember, boolean fromClient) {
		final Session s;
		final PanelLink link;
		synchronized (this) {
			s = sessions.get(p);
			link = panel;
			if (!enabled || s == null) {
				reply(p, fromClient, false, "PlebMc accounts are off on this server.", null);
				return;
			}
			if (s.signedIn) {
				reply(p, fromClient, true, "You're already signed in as " + s.canonical + ".", null);
				return;
			}
			long now = net.now();
			if (s.busy || now - s.lastTry < TRY_COOLDOWN_MS) {
				reply(p, fromClient, false, "One moment...", null);
				return;
			}
			s.busy = true;
			s.lastTry = now;
		}
		net.async(() -> {
			Map<?, ?> r;
			try {
				r = token != null ? link.token(s.name, token) : link.login(s.name, password, remember);
			} catch (IOException e) {
				log.warning("PlebMc accounts: sign-in for " + s.name + " failed, panel unreachable (" + e.getMessage() + ")");
				r = null;
			}
			boolean ok = r != null && Boolean.TRUE.equals(r.get("ok"));
			synchronized (SignIn.this) {
				s.busy = false;
				if (sessions.get(p) != s) {
					return;
				}
				if (ok) {
					s.known = true;
					s.exists = true;
					s.signedIn = true;
					s.canonical = str(r.get("name"), s.name);
					s.role = str(r.get("role"), "player");
				}
			}
			if (r == null) {
				reply(p, fromClient, false, "Can't reach the account server right now. Try again in a minute.", null);
				return;
			}
			if (!ok) {
				String why = str(r.get("error"), "Wrong password.");
				if (Boolean.FALSE.equals(r.get("exists"))) {
					why = "This name has no PlebMc account yet: /account register <password> <password>";
				}
				Map<String, Object> extra = new LinkedHashMap<>();
				if (token != null) {
					extra.put("forget", true); // the client drops a token that no longer works
				}
				reply(p, fromClient, false, token != null ? "Sign in again (your saved sign-in expired)." : why, extra);
				log.info("PlebMc accounts: " + (token != null ? "saved sign-in refused" : "wrong password") + " for " + s.name);
				return;
			}
			log.info("PlebMc accounts: " + s.name + " signed in" + (token != null ? " (remembered client)" : ""));
			Map<String, Object> extra = new LinkedHashMap<>();
			if (r.get("token") instanceof String) {
				extra.put("token", r.get("token"));
			}
			reply(p, fromClient, true, "Signed in as " + s.canonical + ".", extra);
			changed.accept(p);
		});
	}

	/** Signs a player out (and forgets a client's saved token). */
	void signOut(UUID p, String token, String why) {
		final PanelLink link;
		boolean was;
		synchronized (this) {
			Session s = sessions.get(p);
			link = panel;
			was = s != null && s.signedIn;
			if (s != null) {
				s.signedIn = false;
			}
		}
		if (token != null && link != null) {
			net.async(() -> {
				try {
					link.forget(token);
				} catch (IOException e) {
					// it expires on its own
				}
			});
		}
		Map<String, Object> extra = new LinkedHashMap<>();
		extra.put("forget", true);
		reply(p, true, true, why, extra);
		if (was) {
			changed.accept(p);
		}
	}

	/** The panel signed this name out everywhere (password changed, locked, deleted). Returns whether they were online. */
	boolean signOutName(String name) {
		java.util.List<UUID> hit = new java.util.ArrayList<>();
		synchronized (this) {
			for (Session s : sessions.values()) {
				if (s.name.equalsIgnoreCase(name)) {
					hit.add(s.id);
					s.known = false; // look it up again (it may be locked or gone now)
					s.lastTry = 0;
				}
			}
		}
		for (UUID id : hit) {
			signOut(id, null, "You were signed out from the panel. Sign in again.");
			net.chat(id, prefix + ShopCore.color("&eYou were signed out of your PlebMc account. Sign in again: &f/account login <password>"));
			Session s = session(id);
			if (s != null) {
				lookup(s);
			}
		}
		return !hit.isEmpty();
	}

	// ==================== /account ====================

	/** /account [login|logout|register|code]. Passwords never reach a backend server or the proxy log. */
	void command(UUID p, String[] a, Consumer<String> reply) {
		Session s = session(p);
		if (!enabled() || s == null) {
			reply.accept(prefix + ShopCore.color("&cPlebMc accounts are off on this server."));
			return;
		}
		String sub = a.length == 0 ? "" : a[0].toLowerCase(java.util.Locale.ROOT);
		switch (sub) {
		case "login":
		case "signin":
			if (a.length != 2) {
				reply.accept(prefix + ShopCore.color("&eUsage: /account login <password>"));
				return;
			}
			attempt(p, a[1], null, false, false);
			return;
		case "logout":
		case "signout":
			signOut(p, null, "Signed out.");
			reply.accept(prefix + ShopCore.color("&7Signed out. Your coins are safe until you sign in again."));
			return;
		case "register":
			register(p, s, a, reply);
			return;
		case "code":
			code(p, s, reply);
			return;
		default:
			synchronized (this) {
				if (s.signedIn) {
					reply.accept(prefix + ShopCore.color("&aSigned in as &f" + s.canonical + (s.role.equals("player") ? "" : " &7(" + s.role + ")")
							+ "&a. &7Your account: &f" + publicUrl));
				} else if (s.exists) {
					reply.accept(prefix + ShopCore.color("&eNot signed in. &f/account login <password>"));
				} else {
					reply.accept(prefix + ShopCore.color("&eNo PlebMc account on this name yet."));
					reply.accept(ShopCore.color("&7- &f/account register <password> <password> &7makes one now"));
					reply.accept(ShopCore.color("&7- &f/account code &7gives a code to make it on " + publicUrl + " instead"));
				}
			}
		}
	}

	private void register(UUID p, Session s, String[] a, Consumer<String> reply) {
		if (a.length != 3) {
			reply.accept(prefix + ShopCore.color("&eUsage: /account register <password> <password>"));
			return;
		}
		if (!a[1].equals(a[2])) {
			reply.accept(prefix + ShopCore.color("&cThe two passwords don't match."));
			return;
		}
		if (a[1].length() < 8) {
			reply.accept(prefix + ShopCore.color("&cUse a password of at least 8 characters."));
			return;
		}
		synchronized (this) {
			if (s.exists) {
				reply.accept(prefix + ShopCore.color("&cThis name already has an account. &f/account login <password>"));
				return;
			}
		}
		if (!mayRegister.test(p)) {
			reply.accept(prefix + ShopCore.color("&cLog in first (/login <password>), then make your account."));
			return;
		}
		final PanelLink link = panel;
		net.async(() -> {
			Map<?, ?> r;
			try {
				r = link.register(s.name, a[1]);
			} catch (IOException e) {
				reply.accept(prefix + ShopCore.color("&cCan't reach the account server right now. Try again in a minute."));
				return;
			}
			if (!Boolean.TRUE.equals(r.get("ok"))) {
				reply.accept(prefix + ShopCore.color("&c" + str(r.get("error"), "Couldn't make the account.")));
				return;
			}
			synchronized (SignIn.this) {
				s.known = true;
				s.exists = true;
				s.signedIn = true;
				s.canonical = str(r.get("name"), s.name);
			}
			log.info("PlebMc accounts: " + s.name + " made an account in game");
			reply.accept(prefix + ShopCore.color("&aAccount made and signed in. &7Next time: &f/account login <password>"
					+ "&7. See your coins on &f" + publicUrl));
			sendStatus(p, null, true);
			changed.accept(p);
		});
	}

	/** The Sign in screen's "Create account": makes it, signs in, and (with remember) sends back a token. */
	private void registerFromClient(UUID p, String password, boolean remember) {
		Session s = session(p);
		if (!enabled() || s == null) {
			reply(p, true, false, "PlebMc accounts are off on this server.", null);
			return;
		}
		if (password.length() < 8) {
			reply(p, true, false, "Use a password of at least 8 characters.", null);
			return;
		}
		synchronized (this) {
			if (s.exists) {
				reply(p, true, false, "This name already has an account. Sign in instead.", null);
				return;
			}
			long now = net.now();
			if (s.busy || now - s.lastTry < TRY_COOLDOWN_MS) {
				reply(p, true, false, "One moment...", null);
				return;
			}
			s.lastTry = now;
		}
		if (!mayRegister.test(p)) {
			reply(p, true, false, "Log in first (/login <password>), then make your account.", null);
			return;
		}
		final PanelLink link = panel;
		net.async(() -> {
			Map<?, ?> r;
			Map<?, ?> login = null;
			try {
				r = link.register(s.name, password);
				if (Boolean.TRUE.equals(r.get("ok")) && remember) {
					login = link.login(s.name, password, true);
				}
			} catch (IOException e) {
				reply(p, true, false, "Can't reach the account server right now. Try again in a minute.", null);
				return;
			}
			if (!Boolean.TRUE.equals(r.get("ok"))) {
				reply(p, true, false, str(r.get("error"), "Couldn't make the account."), null);
				return;
			}
			synchronized (SignIn.this) {
				s.known = true;
				s.exists = true;
				s.signedIn = true;
				s.canonical = str(r.get("name"), s.name);
			}
			log.info("PlebMc accounts: " + s.name + " made an account from the client");
			Map<String, Object> extra = new LinkedHashMap<>();
			if (login != null && login.get("token") instanceof String) {
				extra.put("token", login.get("token"));
			}
			reply(p, true, true, "Account made. You're signed in.", extra);
			changed.accept(p);
		});
	}

	private void code(UUID p, Session s, Consumer<String> reply) {
		synchronized (this) {
			if (s.exists) {
				reply.accept(prefix + ShopCore.color("&cThis name already has an account. Sign in on &f" + publicUrl));
				return;
			}
		}
		if (!mayRegister.test(p)) {
			reply.accept(prefix + ShopCore.color("&cLog in first (/login <password>)."));
			return;
		}
		final PanelLink link = panel;
		net.async(() -> {
			try {
				Map<?, ?> r = link.claim(s.name);
				if (!Boolean.TRUE.equals(r.get("ok"))) {
					reply.accept(prefix + ShopCore.color("&c" + str(r.get("error"), "Couldn't make a code.")));
					return;
				}
				reply.accept(prefix + ShopCore.color("&aYour code: &f&l" + r.get("code") + " &7(works for "
						+ ((Number) r.get("minutes")).intValue() + " minutes)"));
				reply.accept(ShopCore.color("&7Make your account at &f" + r.get("url")));
			} catch (IOException | RuntimeException e) {
				reply.accept(prefix + ShopCore.color("&cCan't reach the account server right now. Try again in a minute."));
			}
		});
	}

	// ==================== to the client ====================

	private void reply(UUID p, boolean toClient, boolean ok, String message, Map<String, Object> extra) {
		if (toClient) {
			Map<String, Object> m = status(p);
			m.put("ok", ok);
			m.put("message", message);
			if (extra != null) {
				m.putAll(extra);
			}
			net.send(p, gson.toJson(m));
		} else {
			net.chat(p, prefix + ShopCore.color((ok ? "&a" : "&c") + message));
			sendStatus(p, null, false);
		}
	}

	void sendStatus(UUID p, String message, boolean always) {
		Map<String, Object> m = status(p);
		if (message != null) {
			m.put("message", message);
		}
		net.send(p, gson.toJson(m));
	}

	synchronized Map<String, Object> status(UUID p) {
		Session s = sessions.get(p);
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("type", "signin");
		m.put("enabled", enabled);
		m.put("panel", publicUrl);
		if (s != null) {
			m.put("known", s.known);
			m.put("exists", s.exists);
			m.put("signedIn", s.signedIn);
			m.put("locked", s.locked);
			m.put("name", s.canonical);
			m.put("role", s.role);
			if (s.exists && !s.signedIn && signInMs > 0) {
				m.put("kickIn", Math.max(0, (signInMs - (net.now() - s.joined)) / 1000));
			}
		}
		return m;
	}

	private static String str(Object o, String def) {
		return o instanceof String && !((String) o).isEmpty() ? (String) o : def;
	}
}
