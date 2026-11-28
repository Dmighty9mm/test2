package net.plebmc.shop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Everything the shop does, independent of BungeeCord so it can be tested:
 * PlebMc Coins, quests, the cosmetics shop, what everyone wears and admin
 * commands. {@link BungeeShop} feeds it events and
 * delivers its messages. All entry points are synchronized (BungeeCord calls
 * from several threads).
 */
final class ShopCore {

	/** What the core needs from the proxy. */
	interface Net {
		/** A plugin message to the player's client on plebmc:shop. */
		void send(UUID player, String json);

		/** A chat line. */
		void chat(UUID player, String message);

		List<UUID> online();

		/** The backend server the player is on, or null. */
		String server(UUID player);

		String name(UUID player);

		boolean hasPermission(UUID player, String permission);

		long now();

		/** Runs slow work (website calls) off the proxy's event threads. */
		void async(Runnable r);

		/** The backend server names (for the admin menu's "send to server"). */
		default List<String> servers() {
			return new ArrayList<>();
		}

		/** Moves a player to a backend server; false if it doesn't exist. */
		default boolean connect(UUID player, String server) {
			return false;
		}

		/** Disconnects a player with a reason. */
		default boolean kick(UUID player, String reason) {
			return false;
		}

		/**
		 * The player proved they own their name at this login (staff password,
		 * see StaffLock), or the staff lock is off. On a cracked server a name
		 * proves nothing, so admin powers need this too.
		 */
		default boolean staffVerified(UUID player) {
			return true;
		}

		/** A player with staff powers but no password: lock their name until one is set. */
		default void protectStaff(UUID player) {
		}

		/**
		 * The player logged in to their account this session (nLogin /login), or
		 * there's no login plugin. Until then nothing on their account can change:
		 * on a cracked server anyone can join with any name.
		 */
		default boolean authenticated(UUID player) {
			return true;
		}
	}

	/** Re-reads config.yml (set by the proxy plugin; the admin menu's Reload uses it). */
	Runnable reloader;
	/** PlebMc account sign-in (null when the proxy plugin didn't set it up, e.g. in tests). */
	SignIn signin;

	static final String ADMIN = "plebshop.admin";
	/** Client messages that change the account (need a logged-in player). */
	private static final java.util.Set<String> CHANGES = new java.util.HashSet<>(
			Arrays.asList("buy", "equip", "link", "logout", "admin"));
	private static final long ACTION_COOLDOWN_MS = 300;

	private final Gson gson = new GsonBuilder().serializeNulls().create();
	private final Net net;
	private final Logger log;
	final Store store;
	Cfg config;
	Catalog catalog;
	Quests quests;
	private String currency = "PlebMc Coins";
	private String prefix = "";
	/** Hidden worlds, as "world" or "server:world", lower case. */
	private final List<String> hiddenWorlds = new ArrayList<>();
	private final Map<UUID, String> worlds = new HashMap<>();
	/** Players whose client speaks plebmc:shop (sent any shop message). */
	private final java.util.Set<UUID> clients = new java.util.HashSet<>();
	private final Map<UUID, Long> lastAction = new HashMap<>();
	/**
	 * The PlebMc account role (player, admin, owner) of a signed-in player, or
	 * null when they haven't signed in. Set from SignIn by the proxy plugin.
	 */
	java.util.function.Function<UUID, String> panelRole;

	/** The plugin folder (cloak textures are read from it); null in tests that don't need it. */
	private java.io.File folder;

	/** Sets the plugin folder and re-reads the catalog so cloak textures load. */
	synchronized void folder(java.io.File f) {
		folder = f;
		load(config);
	}

	ShopCore(Net net, Store store, Cfg config, Logger log) {
		this.net = net;
		this.store = store;
		this.log = log;
		load(config);
	}

	synchronized void load(Cfg cfg) {
		this.config = cfg;
		catalog = new Catalog(cfg.section("items"), folder, cfg.section("collections"));
		quests = new Quests(cfg);
		for (String w : catalog.warnings()) {
			log.warning(w);
		}
		for (String w : quests.warnings) {
			log.warning(w);
		}
		currency = cfg.str("currency-name", "PlebMc Coins");
		voteReward = cfg.bool("voting.enabled", true) ? Math.max(0, (long) cfg.dbl("voting.reward", 100)) : 0;
		voteUrl = cfg.str("voting.url", "https://eaglercraftservers.com/server/plebmc");
		prefix = color(cfg.str("messages.prefix", "&8[&b&lPlebMc&8] &7"));
		hiddenWorlds.clear();
		for (String w : cfg.list("hidden-in-worlds")) {
			hiddenWorlds.add(w.toLowerCase(Locale.ROOT));
		}
	}

	int problems() {
		return catalog.warnings().size() + quests.warnings.size();
	}

	private String msg(String key, String def) {
		return config.str("messages." + key, def);
	}

	static String color(String s) {
		if (s == null) {
			return "";
		}
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '&' && i + 1 < s.length() && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(s.charAt(i + 1)) >= 0) {
				b.append('§').append(Character.toLowerCase(s.charAt(++i)));
			} else {
				b.append(c);
			}
		}
		return b.toString();
	}

	private static String plain(String s) {
		return s == null ? "" : s.replaceAll("(?i)[&§][0-9a-fk-or]", "");
	}

	static String fmt(long n) {
		return String.format(Locale.ROOT, "%,d", n);
	}

	private Store.Data data(UUID id) {
		Store.Data d = store.get(id);
		String n = net.name(id);
		if (n != null && !n.equals(d.name)) {
			d.name = n;
			store.changed();
		}
		return d;
	}

	// ==================== player lifecycle ====================

	/**
	 * The player is connected to a backend server (first join or a switch).
	 * Gives the daily login reward on the first connect, and tells clients
	 * on the new server what everyone wears.
	 */
	synchronized void connected(UUID p, String from, boolean firstJoin) {
		Store.Data d = data(p);
		long now = net.now();
		worlds.remove(p);
		String nm = net.name(p);
		if (firstJoin && nm != null && pendingVotes.containsKey(nm.toLowerCase(Locale.ROOT))) {
			int n = pendingVotes.remove(nm.toLowerCase(Locale.ROOT));
			for (int i = 0; i < n; i++) {
				payVote(p, "a vote from before you joined");
			}
		}
		if (firstJoin) {
			if (earns(p)) {
				loginReward(p);
			} else {
				// the PlebMc client says hello a moment after joining: pay it then
				loginPending.add(p);
			}
		}
		if (earns(p)) {
			completed(p, quests.add(d, net.server(p), "join", 1, now));
		}
		if (from != null) {
			// players on the old server stop seeing this one
			Map<String, Object> gone = new LinkedHashMap<>();
			gone.put(p.toString(), null);
			broadcast(playersMessage(gone, false), from, p);
		}
		if (clients.contains(p)) {
			sendState(p, false);
			sendEveryone(p);
		}
		broadcastSlots(p);
		if (firstJoin) {
			sendAccount(p, null, true);
		}
	}

	/** The daily login reward and the login quest. */
	private void loginReward(UUID p) {
		Store.Data d = data(p);
		long now = net.now();
		long reward = quests.login(d, now);
		if (reward >= 0) {
			store.changed();
			notify(p, msg("login-title", "&6Daily login"), msg("login-message", "Day %streak%: +%coins% %currency%")
					.replace("%streak%", Integer.toString(d.streak)).replace("%coins%", fmt(reward)));
			completed(p, quests.add(d, null, "login", 1, now));
		}
	}

	/** Players whose daily login waits for their PlebMc client to say hello. */
	private final java.util.Set<UUID> loginPending = new java.util.HashSet<>();

	/**
	 * Does this player earn coins and quest progress? With plebclient-only (the
	 * default) only players on the PlebMc client do: coins, quests and rewards
	 * are PlebClient things.
	 */
	boolean earns(UUID p) {
		return clients.contains(p) || !config.bool("plebclient-only", true);
	}

	synchronized void quit(UUID p, String server) {
		Store.Data left = store.peek(p);
		if (left != null) {
			left.seen = net.now();
		}
		loginPending.remove(p);
		lastAction.remove(p);
		worlds.remove(p);
		clients.remove(p);
		lastChat.remove(p);
		Map<String, Object> gone = new LinkedHashMap<>();
		gone.put(p.toString(), null);
		broadcast(playersMessage(gone, false), server, p);
	}

	/** Every minute: playtime coins, and "play" quests in the categories covering the player's server. */
	synchronized void minute() {
		long now = net.now();
		for (UUID p : net.online()) {
			Store.Data on = store.peek(p);
			if (on != null) {
				on.seen = now;
			}
			if (!earns(p)) {
				continue;
			}
			Store.Data d = data(p);
			long paid = quests.minute(d);
			List<Quests.Quest> done = quests.add(d, net.server(p), "play", 1, now);
			store.changed();
			if (paid > 0 && config.bool("playtime.notify", true)) {
				notify(p, msg("playtime-title", "&bThanks for playing"),
						msg("playtime-message", "+%coins% %currency%").replace("%coins%", fmt(paid)));
			}
			completed(p, done);
			if (clients.contains(p)) {
				sendState(p, false);
			}
		}
	}

	/** Quest progress from a backend server (the bridge plugin) or a command. */
	synchronized void progress(UUID p, String server, String type, long amount) {
		if (server != null && !"play".equals(type) && !"chat".equals(type)) {
			bridgeSeen.put(server.toLowerCase(Locale.ROOT), net.now());
		}
		if (server == null || !quests.counts(server)) {
			if (server != null && uncoveredWarned.add(server.toLowerCase(Locale.ROOT)) && log != null) {
				log.warning("Quest events from server '" + server + "' don't count: no quest category lists it. Add '" + server
						+ "' to quests.categories.<category>.servers in config.yml and run /pleb reload.");
			}
			return; // a server no quest category covers: nothing counts there
		}
		if (!earns(p)) {
			return; // not on the PlebMc client
		}
		Store.Data d = data(p);
		List<Quests.Quest> done = quests.add(d, server, type.toLowerCase(Locale.ROOT), amount, net.now());
		store.changed();
		completed(p, done);
		if (clients.contains(p)) {
			sendState(p, false);
		}
	}

	private final Map<UUID, Long> lastChat = new HashMap<>();
	/** When each server last sent a quest event (kill, match, win...) through PlebMcBridge or /plebquest. */
	final Map<String, Long> bridgeSeen = new java.util.TreeMap<>();
	private final java.util.Set<String> uncoveredWarned = new java.util.HashSet<>();

	/** A chat message (not a command). Counts for "chat" quests at most once every 10 seconds, so spam doesn't. */
	synchronized void chat(UUID p) {
		long now = net.now();
		Long last = lastChat.get(p);
		if (last != null && now - last < CHAT_EVERY_MS) {
			return;
		}
		lastChat.put(p, now);
		progress(p, net.server(p), "chat", 1);
	}

	static final long CHAT_EVERY_MS = 10_000L;

	/** The bridge reports the world a player is in (for hidden-in-worlds). */
	synchronized void world(UUID p, String world) {
		boolean wasHidden = hidden(p);
		worlds.put(p, world == null ? "" : world.toLowerCase(Locale.ROOT));
		if (wasHidden != hidden(p)) {
			broadcastSlots(p);
		}
	}

	synchronized void saveIfDirty() {
		store.saveIfDirty();
	}

	synchronized void save() {
		store.save();
	}

	private void completed(UUID p, List<Quests.Quest> done) {
		for (Quests.Quest q : done) {
			log.info(net.name(p) + " finished quest " + q.id + " (+" + q.reward + ")");
			notify(p, msg("quest-title", "&aQuest complete!"), msg("quest-message", "%quest%: +%coins% %currency%")
					.replace("%quest%", plain(q.name)).replace("%coins%", fmt(q.reward)));
		}
	}

	/** A popup on the PlebMc client, or a chat line for everyone else. */
	private void notify(UUID p, String title, String message) {
		message = message.replace("%currency%", currency);
		if (clients.contains(p)) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("type", "notify");
			m.put("title", color(title));
			m.put("message", color(message));
			m.put("coins", store.get(p).coins);
			send(p, m);
		} else if (config.bool("chat-for-other-clients", true)) {
			net.chat(p, prefix + color(title) + color(" &7") + color(message));
		}
	}

	// ==================== messages from the client ====================

	synchronized void fromClient(UUID p, String json) {
		Map<?, ?> m;
		try {
			m = gson.fromJson(json, Map.class);
		} catch (RuntimeException ex) {
			return;
		}
		if (m == null) {
			return;
		}
		String type = String.valueOf(m.get("type"));
		boolean first = clients.add(p);
		if (first) {
			broadcastSlots(p); // everyone here now sees the Pleb head by this player's name
			Long votes = unseenVotes.remove(p);
			if (votes != null) {
				voteAlert(p, votes); // voted while away: thank them now
			}
		}
		if ("signin".equals(type) || "signout".equals(type)) {
			// PlebMc account sign-in (SignIn); answered off-thread
			if (signin != null) {
				signin.fromClient(p, m);
			} else {
				Map<String, Object> r = new LinkedHashMap<>();
				r.put("type", "signin");
				r.put("enabled", false);
				send(p, r);
			}
			return;
		}
		if (CHANGES.contains(type) && !net.authenticated(p)) {
			// joined with this name but hasn't proved it's theirs yet: could be anyone
			boolean accounts = signin != null && signin.enabled();
			String why = accounts ? msg("sign-in-first-message", "Sign in to your PlebMc account first (Right Shift > Sign in on PlebClient)")
					: msg("login-first-message", "Log in first (/login <password>)");
			notify(p, accounts ? msg("sign-in-first-title", "&cNot signed in") : msg("login-first-title", "&cNot logged in"), why);
			if ("admin".equals(type)) {
				Map<String, Object> r = new LinkedHashMap<>();
				r.put("type", "admin");
				r.put("allowed", false);
				r.put("ok", false);
				r.put("message", why + ".");
				send(p, r);
			}
			return;
		}
		if (first && loginPending.remove(p)) {
			loginReward(p);
			completed(p, quests.add(data(p), net.server(p), "join", 1, net.now()));
		}
		switch (type) {
		case "open":
			data(p);
			sendState(p, true);
			if (first) {
				sendEveryone(p);
			}
			break;
		case "buy":
			if (cooldown(p)) {
				buy(p, str(m.get("id")));
			}
			break;
		case "equip":
			if (cooldown(p)) {
				equip(p, str(m.get("category")), str(m.get("id")));
			}
			break;
		case "account":
			sendAccount(p, null, true);
			break;
		case "texture":
			texture(p, str(m.get("id")));
			break;
		case "admin":
			admin(p, m);
			break;
		default:
			break;
		}
	}

	/** The player just logged in (or out) with the login plugin: resend their account and admin flag. */
	synchronized void authChanged(UUID p) {
		sendAccount(p, null, true);
	}

	// ==================== account flags ====================

	/** The player's PlebMc account role when signed in, or null. */
	private String role(UUID p) {
		return panelRole == null ? null : panelRole.apply(p);
	}

	/** Tells the client whether it may open the admin menu (the plebmc.top website login is gone). */
	private void sendAccount(UUID p, String message, boolean ok) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("type", "account");
		m.put("enabled", false);
		m.put("linked", false);
		String r = role(p);
		if (r != null) {
			m.put("role", r);
		}
		m.put("admin", isAdmin(p));
		if (message != null) {
			m.put("message", message);
			m.put("ok", ok);
		}
		send(p, m);
	}

	private static String str(Object o) {
		return o instanceof String ? (String) o : null;
	}

	private boolean cooldown(UUID p) {
		long now = System.currentTimeMillis();
		Long last = lastAction.get(p);
		if (last != null && now - last < ACTION_COOLDOWN_MS) {
			return false;
		}
		lastAction.put(p, now);
		return true;
	}

	private void buy(UUID p, String id) {
		Catalog.Item it = catalog.get(id);
		Store.Data d = data(p);
		if (it == null) {
			result(p, false, msg("unknown-item", "That item isn't in the shop."));
			return;
		}
		if (d.owned.contains(it.id)) {
			result(p, true, msg("already-owned", "You already own %item%.").replace("%item%", plain(it.name)));
			sendState(p, false);
			return;
		}
		if (it.notForSale()) {
			result(p, false, msg("not-for-sale", "%item% can't be bought.").replace("%item%", plain(it.name)));
			return;
		}
		if (it.permission != null && !it.permission.isEmpty() && !net.hasPermission(p, it.permission)) {
			result(p, false, msg("no-permission", "You can't buy %item%.").replace("%item%", plain(it.name)));
			return;
		}
		if (d.coins < it.price) {
			result(p, false, msg("not-enough", "You need %need% more coins.").replace("%need%", fmt(it.price - d.coins)));
			return;
		}
		d.coins -= it.price;
		d.owned.add(it.id);
		boolean autoEquip = config.bool("equip-on-buy", true);
		if (autoEquip) {
			d.equipped.put(it.category, it.id);
		}
		store.changed();
		store.save();
		log.info(net.name(p) + " bought " + it.id + " for " + it.price + " coins");
		result(p, true, msg("bought", "Bought %item%!").replace("%item%", plain(it.name)));
		sendState(p, false);
		if (autoEquip) {
			broadcastSlots(p);
		}
	}

	/** A cloak texture or hat model by id, for the panel's shop previews. */
	synchronized byte[] asset(String hash) {
		return catalog.download(hash);
	}

	/**
	 * A purchase made on the website (the panel checked it first; this checks
	 * again with the real numbers). Returns what happened, "!" first when it failed.
	 */
	synchronized String webBuy(String name, String id) {
		UUID p = resolve(name);
		Catalog.Item it = catalog.get(id);
		if (p == null) {
			return "!" + name + " hasn't joined yet";
		}
		if (it == null) {
			return "!that item isn't in the shop";
		}
		Store.Data d = store.get(p);
		if (d.owned.contains(it.id)) {
			return "!already owned";
		}
		if (it.notForSale()) {
			return "!not for sale";
		}
		boolean online = net.online().contains(p);
		if (it.permission != null && !it.permission.isEmpty() && !(online && net.hasPermission(p, it.permission))) {
			return "!needs a rank, buy it in game";
		}
		if (d.coins < it.price) {
			return "!needs " + fmt(it.price - d.coins) + " more coins";
		}
		d.coins -= it.price;
		d.owned.add(it.id);
		boolean autoEquip = config.bool("equip-on-buy", true);
		if (autoEquip) {
			d.equipped.put(it.category, it.id);
		}
		store.changed();
		store.save();
		log.info(d.name + " bought " + it.id + " for " + it.price + " coins on the website");
		if (online) {
			if (clients.contains(p)) {
				notify(p, color("&a&lShop"), "Bought " + plain(it.name) + " on the website!");
				sendState(p, false);
			} else {
				net.chat(p, prefix + color("&aBought " + plain(it.name) + " on the website!"));
			}
			if (autoEquip) {
				broadcastSlots(p);
			}
		}
		return "bought " + it.id + " for " + it.price;
	}

	/** Base64 characters per texture message (a plugin message stays far under 32 KB). */
	static final int TEXTURE_CHUNK = 24_000;
	private static final long TEXTURE_RESEND_MS = 15_000;
	private final Map<String, Long> textureSent = new HashMap<>();

	/**
	 * Sends a cloak texture the client asked for, split into parts. Only
	 * textures in the catalog, and each one at most every 15 seconds per
	 * player, so a client can't make the proxy stream images in a loop.
	 */
	private void texture(UUID p, String hash) {
		byte[] bytes = catalog.download(hash);
		if (bytes == null) {
			return;
		}
		long now = net.now();
		String key = p + "/" + hash;
		Long last = textureSent.get(key);
		if (last != null && now - last < TEXTURE_RESEND_MS && now >= last) {
			return;
		}
		if (textureSent.size() > 4096) {
			textureSent.clear();
		}
		textureSent.put(key, now);
		String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
		int parts = (b64.length() + TEXTURE_CHUNK - 1) / TEXTURE_CHUNK;
		for (int i = 0; i < parts; i++) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("type", "texture");
			m.put("id", hash);
			m.put("part", i);
			m.put("parts", parts);
			m.put("data", b64.substring(i * TEXTURE_CHUNK, Math.min(b64.length(), (i + 1) * TEXTURE_CHUNK)));
			send(p, m);
		}
	}

	private void equip(UUID p, String category, String id) {
		if (!Catalog.isCategory(category)) {
			return;
		}
		Store.Data d = data(p);
		if (id == null) {
			if (d.equipped.remove(category) != null) {
				store.changed();
			}
		} else {
			Catalog.Item it = catalog.get(id);
			if (it == null || !it.category.equals(category)) {
				result(p, false, msg("unknown-item", "That item isn't in the shop."));
				return;
			}
			if (!d.owned.contains(it.id)) {
				result(p, false, msg("not-owned", "You don't own %item% yet.").replace("%item%", plain(it.name)));
				return;
			}
			d.equipped.put(category, it.id);
			store.changed();
		}
		sendState(p, false);
		broadcastSlots(p);
	}

	// ==================== admin menu ====================

	/**
	 * True when this player may use the admin menu: signed in to a PlebMc
	 * account whose role is in admin-menu.roles (admin and owner by default).
	 * Checked again on every admin message, so a client can't just claim it.
	 */
	synchronized boolean isAdmin(UUID p) {
		if (!adminGroup(p)) {
			return false;
		}
		if (!net.authenticated(p)) {
			return false;
		}
		if (!net.staffVerified(p)) {
			// the admin password (/adminlogin) wasn't given this session
			net.protectStaff(p);
			return false;
		}
		return true;
	}

	/** Signed in to an account with a role from admin-menu.roles (admin, owner). */
	private boolean adminGroup(UUID p) {
		if (!config.bool("admin-menu.enabled", true)) {
			return false;
		}
		String r = role(p);
		if (r == null) {
			return false;
		}
		List<String> allowed = config.list("admin-menu.roles");
		if (allowed.isEmpty()) {
			allowed = Arrays.asList("admin", "owner");
		}
		for (String ok : allowed) {
			if (r.equalsIgnoreCase(ok.trim())) {
				return true;
			}
		}
		return false;
	}

	private void admin(UUID p, Map<?, ?> m) {
		if (!isAdmin(p)) {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("type", "admin");
			r.put("allowed", false);
			r.put("ok", false);
			r.put("message", adminGroup(p)
					? "Type /adminlogin <admin password> in chat first (set in the proxy console: pleb adminpass set <name> <password>)."
					: "Only PlebMc admins and owners can use the admin menu (sign in to your account first).");
			send(p, r);
			return;
		}
		String action = str(m.get("action"));
		if (action == null || "open".equals(action)) {
			sendAdmin(p, null, true);
			return;
		}
		if (!cooldown(p)) {
			return;
		}
		String who = net.name(p);
		UUID target = null;
		String targetName = null;
		if (m.get("player") instanceof String) {
			try {
				target = UUID.fromString((String) m.get("player"));
			} catch (IllegalArgumentException e) {
				target = resolve((String) m.get("player"));
			}
			if (target != null) {
				Store.Data d = store.peek(target);
				targetName = net.name(target) != null ? net.name(target) : d != null ? d.name : target.toString();
			}
		}
		String msg;
		boolean ok = true;
		switch (action) {
		case "coins": {
			long n = m.get("amount") instanceof Number ? ((Number) m.get("amount")).longValue() : -1;
			String op = String.valueOf(m.get("op"));
			if (target == null || n < 0 || n > 1_000_000_000L) {
				msg = "Pick a player and an amount.";
				ok = false;
				break;
			}
			Store.Data d = store.get(target);
			if ("give".equals(op)) {
				d.coins += n;
			} else if ("take".equals(op)) {
				d.coins = Math.max(0, d.coins - n);
			} else if ("set".equals(op)) {
				d.coins = n;
			} else {
				msg = "Unknown coins action.";
				ok = false;
				break;
			}
			store.changed();
			sendState(target, false);
			msg = targetName + " now has " + fmt(d.coins) + " " + currency + ".";
			break;
		}
		case "item": {
			Catalog.Item it = catalog.get(str(m.get("id")));
			String op = String.valueOf(m.get("op"));
			if (target == null || it == null) {
				msg = "Pick a player and an item.";
				ok = false;
				break;
			}
			Store.Data d = store.get(target);
			if ("give".equals(op)) {
				d.owned.add(it.id);
				msg = "Gave " + plain(it.name) + " to " + targetName + ".";
			} else {
				d.owned.remove(it.id);
				if (it.id.equals(d.equipped.get(it.category))) {
					d.equipped.remove(it.category);
				}
				msg = "Took " + plain(it.name) + " from " + targetName + ".";
			}
			store.changed();
			sendState(target, false);
			broadcastSlots(target);
			break;
		}
		case "send": {
			String server = str(m.get("server"));
			if (target == null || server == null || !net.online().contains(target)) {
				msg = "That player isn't online.";
				ok = false;
			} else if (net.connect(target, server)) {
				msg = "Sending " + targetName + " to " + server + ".";
			} else {
				msg = "There is no server called " + server + ".";
				ok = false;
			}
			break;
		}
		case "kick": {
			String reason = str(m.get("reason"));
			if (reason == null || reason.trim().isEmpty()) {
				reason = "Kicked by an admin";
			}
			reason = reason.length() > 200 ? reason.substring(0, 200) : reason;
			if (target == null || target.equals(p) || !net.online().contains(target)) {
				msg = target != null && target.equals(p) ? "You can't kick yourself." : "That player isn't online.";
				ok = false;
			} else {
				net.kick(target, color(reason));
				msg = "Kicked " + targetName + ".";
			}
			break;
		}
		case "broadcast": {
			String text = str(m.get("message"));
			if (text == null || text.trim().isEmpty()) {
				msg = "Type a message first.";
				ok = false;
				break;
			}
			msg = "Sent to " + announce(text) + " players.";
			break;
		}
		case "reload":
			if (reloader != null) {
				reloader.run();
			}
			refreshAll();
			msg = "Reloaded: " + catalog.all().size() + " cosmetics, " + quests.allQuests().size() + " quests"
					+ (problems() == 0 ? "." : " (" + problems() + " problems, see the proxy console).");
			ok = problems() == 0;
			break;
		default:
			msg = "Unknown admin action.";
			ok = false;
			break;
		}
		if (ok) {
			log.info("[admin] " + who + ": " + action + (targetName == null ? "" : " " + targetName) + " -> " + msg);
		}
		sendAdmin(p, msg, ok);
	}

	/** The admin menu's data: online players, servers, items, and the answer to the last action. */
	private void sendAdmin(UUID p, String message, boolean ok) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("type", "admin");
		r.put("allowed", true);
		r.put("currency", currency);
		List<Object> players = new ArrayList<>();
		for (UUID o : net.online()) {
			Store.Data d = store.peek(o);
			Map<String, Object> pm = new LinkedHashMap<>();
			pm.put("uuid", o.toString());
			pm.put("name", net.name(o));
			pm.put("server", net.server(o));
			pm.put("coins", d == null ? 0 : d.coins);
			pm.put("client", clients.contains(o));
			String pr = role(o);
			if (pr != null) {
				pm.put("account", net.name(o));
				pm.put("groups", Arrays.asList(pr));
			}
			List<String> owned = new ArrayList<>();
			if (d != null) {
				for (String id : d.owned) {
					if (catalog.get(id) != null) {
						owned.add(id);
					}
				}
				pm.put("equipped", slots(d));
			}
			pm.put("owned", owned);
			players.add(pm);
		}
		r.put("players", players);
		r.put("servers", net.servers());
		List<Object> items = new ArrayList<>();
		for (Catalog.Item it : catalog.all()) {
			Map<String, Object> im = new LinkedHashMap<>();
			im.put("id", it.id);
			im.put("name", plain(it.name));
			im.put("category", it.category);
			items.add(im);
		}
		r.put("items", items);
		r.put("registered", store.ids().size());
		if (message != null) {
			r.put("message", message);
			r.put("ok", ok);
		}
		send(p, r);
	}

	// ==================== messages to clients ====================

	private void send(UUID p, Map<String, Object> m) {
		if (clients.contains(p)) {
			net.send(p, gson.toJson(m));
		}
	}

	private void sendState(UUID p, boolean withCatalog) {
		Store.Data d = data(p);
		long now = net.now();
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("type", "state");
		m.put("coins", d.coins);
		m.put("currency", currency);
		m.put("voteUrl", voteUrl);
		m.put("voteReward", voteReward);
		List<String> owned = new ArrayList<>();
		for (String id : d.owned) {
			if (catalog.get(id) != null) {
				owned.add(id);
			}
		}
		m.put("owned", owned);
		m.put("equipped", slots(d));
		m.put("questCategories", quests.categoriesJson());
		m.put("quests", quests.toJson(d, now));
		m.put("dailyResetIn", quests.secondsToDailyReset(now));
		m.put("weeklyResetIn", quests.secondsToWeeklyReset(now));
		m.put("loginStreak", quests.loginClaimed(d, now) ? d.streak : 0);
		if (withCatalog) {
			m.put("collections", catalog.collectionsJson());
			m.put("catalog", catalog.toJson());
		}
		send(p, m);
	}

	private void result(UUID p, boolean ok, String message) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("type", "result");
		m.put("ok", ok);
		m.put("message", message);
		m.put("coins", store.get(p).coins);
		send(p, m);
	}

	private Map<String, Object> slots(Store.Data d) {
		Map<String, Object> s = new LinkedHashMap<>();
		if (d == null) {
			return s;
		}
		for (Map.Entry<String, String> e : d.equipped.entrySet()) {
			Catalog.Item it = catalog.get(e.getValue());
			if (it != null && it.category.equals(e.getKey())) {
				s.put(e.getKey(), it.id);
			}
		}
		return s;
	}

	private boolean hidden(UUID p) {
		String w = worlds.get(p);
		if (w == null || hiddenWorlds.isEmpty()) {
			return false;
		}
		String server = net.server(p);
		return hiddenWorlds.contains(w) || (server != null && hiddenWorlds.contains(server.toLowerCase(Locale.ROOT) + ":" + w));
	}

	private Object visibleSlots(UUID p) {
		Map<String, Object> s = hidden(p) ? new LinkedHashMap<>() : slots(store.peek(p));
		if (clients.contains(p)) {
			s.put("pleb", "1"); // plays on PlebClient: others draw the Pleb head by their name
		}
		return s.isEmpty() ? null : s;
	}

	private static Map<String, Object> playersMessage(Map<String, Object> players, boolean clear) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("type", "players");
		if (clear) {
			m.put("clear", true);
		}
		m.put("players", players);
		return m;
	}

	/** Tells everyone on the player's server (including the player) what they wear now. */
	private void broadcastSlots(UUID p) {
		Map<String, Object> players = new LinkedHashMap<>();
		players.put(p.toString(), visibleSlots(p));
		broadcast(playersMessage(players, false), net.server(p), null);
	}

	/** Sends one player what everyone on their server wears. */
	private void sendEveryone(UUID to) {
		String server = net.server(to);
		Map<String, Object> players = new LinkedHashMap<>();
		for (UUID o : net.online()) {
			if (server != null && server.equals(net.server(o))) {
				Object s = visibleSlots(o);
				if (s != null) {
					players.put(o.toString(), s);
				}
			}
		}
		send(to, playersMessage(players, true));
	}

	private void broadcast(Map<String, Object> m, String server, UUID except) {
		String json = null;
		for (UUID o : net.online()) {
			if (o.equals(except) || !clients.contains(o) || (server != null && !server.equals(net.server(o)))) {
				continue;
			}
			if (json == null) {
				json = gson.toJson(m);
			}
			net.send(o, json);
		}
	}

	/** An announcement to everyone online (a popup on the PlebMc client, chat elsewhere). Returns how many got it. */
	/**
	 * Gives coins to everyone online right now (who can earn: PlebMc client players when
	 * plebclient-only is on). Returns how many got them.
	 */
	synchronized int giveOnline(long n) {
		return giveOnline(n, null);
	}

	/**
	 * Gives coins to everyone online right now. clientOnly picks who for this one gift: true = only
	 * players on the PlebMc client, false = everyone, null = whatever plebclient-only says.
	 */
	synchronized int giveOnline(long n, Boolean clientOnly) {
		int got = 0;
		for (UUID o : net.online()) {
			if (clientOnly == null ? !earns(o) : clientOnly && !clients.contains(o)) {
				continue;
			}
			Store.Data d = store.get(o);
			d.coins += n;
			got++;
			if (clients.contains(o)) {
				notify(o, color(config.str("admin-menu.gift-title", "&6&lGift")), "+" + fmt(n) + " " + currency + " for everyone online!");
				sendState(o, false);
			} else {
				net.chat(o, prefix + color("&e+" + fmt(n) + " " + currency + " for everyone online!"));
			}
		}
		store.changed();
		store.save();
		return got;
	}

	// ==================== voting ====================

	long voteReward = 100;
	String voteUrl = "https://eaglercraftservers.com/server/plebmc";
	/** Votes for names that haven't joined yet (paid when they join). */
	private final Map<String, Integer> pendingVotes = new HashMap<>();
	/** The last vote per name, so a server list sending one vote twice doesn't pay twice. */
	private final Map<String, Long> lastVote = new HashMap<>();

	/**
	 * Someone voted for the server on a server list (Votifier or /pleb vote).
	 * Pays the vote reward; returns what happened.
	 */
	synchronized String vote(String name, String service) {
		if (voteReward <= 0) {
			return "voting rewards are off (voting.enabled / voting.reward)";
		}
		String key = name.toLowerCase(Locale.ROOT);
		long now = net.now();
		Long last = lastVote.get(key);
		if (last != null && now - last < 5 * 60_000L) {
			return name + " already got a vote reward in the last 5 minutes (ignored)";
		}
		lastVote.put(key, now);
		UUID p = resolve(name);
		if (p == null) {
			pendingVotes.merge(key, 1, Integer::sum);
			log.info("Vote from " + service + " for " + name + ": they haven't joined yet, paid when they do");
			return name + " hasn't joined yet: paid when they join";
		}
		payVote(p, service);
		String who = net.name(p) != null ? net.name(p) : store.get(p).name;
		log.info("Vote from " + service + ": +" + fmt(voteReward) + " " + currency + " to " + who);
		if (config.bool("voting.announce", true)) {
			String line = color(config.str("voting.announce-message", "&b%player% &7voted for PlebMc and got &e%amount% %currency%&7! Vote too: &f/vote")
					.replace("%player%", who).replace("%amount%", fmt(voteReward)).replace("%currency%", currency));
			for (UUID o : net.online()) {
				if (!o.equals(p)) {
					net.chat(o, prefix + line);
				}
			}
		}
		return "gave " + who + " " + fmt(voteReward) + " " + currency;
	}

	/** Vote coins a player was paid while not on the PlebMc client (shown when they next are). */
	private final Map<UUID, Long> unseenVotes = new HashMap<>();

	private void payVote(UUID p, String service) {
		Store.Data d = store.get(p);
		d.coins += voteReward;
		store.changed();
		store.save();
		if (net.online().contains(p)) {
			net.chat(p, prefix + color("&a+" + fmt(voteReward) + " " + currency + " for voting. Thanks!"));
		}
		if (clients.contains(p)) {
			voteAlert(p, voteReward);
		} else {
			unseenVotes.merge(p, voteReward, Long::sum);
		}
	}

	/** The PlebMc client's big "thanks for voting" banner (with a sound). */
	private void voteAlert(UUID p, long amount) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("type", "vote");
		m.put("amount", amount);
		m.put("currency", currency);
		m.put("coins", store.get(p).coins);
		send(p, m);
		sendState(p, false);
	}

	synchronized int announce(String text) {
		text = text.length() > 200 ? text.substring(0, 200) : text;
		String title = color(config.str("admin-menu.broadcast-title", "&b&lAnnouncement"));
		for (UUID o : net.online()) {
			if (clients.contains(o)) {
				notify(o, title, color(text));
			} else {
				net.chat(o, prefix + color(text));
			}
		}
		return net.online().size();
	}

	private static final java.util.regex.Pattern ITEM_ID = java.util.regex.Pattern.compile("[a-z0-9_]{2,32}");

	private static String yamlQuote(String v) {
		return "'" + v.replace("'", "''").replace("\r", " ").replace("\n", " ") + "'";
	}

	/**
	 * Adds a hat or cloak made on the panel to the shop: saves the file in the
	 * plugin folder, adds the item to config.yml's items and reloads. Nothing
	 * else in config.yml changes. Returns what happened ("!" first on failure).
	 */
	synchronized String publishItem(Map<?, ?> item, byte[] file) {
		if (folder == null) {
			return "!no plugin folder";
		}
		String id = str(item.get("id"));
		String category = str(item.get("category"));
		String name = str(item.get("name"));
		if (id == null || !ITEM_ID.matcher(id).matches()) {
			return "!bad item id";
		}
		if (!Catalog.isCategory(category)) {
			return "!only hats, cloaks and wings can be published";
		}
		if (catalog.get(id) != null) {
			return "!the shop already has " + id;
		}
		if (name == null || name.trim().isEmpty()) {
			name = id;
		}
		if (file == null || file.length == 0 || file.length > 8 << 20) {
			return "!the file is empty or over 8 MB";
		}
		String rel = "cloak".equals(category) ? "cloaks/" + id + ".png"
				: "wings".equals(category) ? "wings/" + id + ".bbmodel" : "hats/" + id + ".bbmodel";
		java.io.File out = new java.io.File(folder, rel);
		java.io.File cfgFile = new java.io.File(folder, "config.yml");
		if (out.exists()) {
			return "!" + rel + " already exists";
		}
		long price;
		try {
			price = Long.parseLong(String.valueOf(item.get("price") instanceof Number ? ((Number) item.get("price")).longValue() : item.get("price")));
		} catch (NumberFormatException e) {
			return "!bad price";
		}
		StringBuilder line = new StringBuilder("  ").append(id).append(": {name: ").append(yamlQuote(name.trim()))
				.append(", category: ").append(category).append(", ")
				.append("cloak".equals(category) ? "texture: " : "model: ").append(yamlQuote(rel));
		if (item.get("frametime") instanceof Number) {
			line.append(", frametime: ").append(((Number) item.get("frametime")).intValue());
		}
		line.append(", price: ").append(price);
		for (String k : new String[] { "collection", "creator" }) {
			String v = str(item.get(k));
			if (v != null && !v.trim().isEmpty()) {
				line.append(", ").append(k).append(": ").append(yamlQuote(v.trim()));
			}
		}
		String desc = str(item.get("description"));
		line.append(", description: ").append(yamlQuote(desc == null ? "" : desc.trim())).append("}");
		String before;
		try {
			before = new String(java.nio.file.Files.readAllBytes(cfgFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
		} catch (java.io.IOException e) {
			return "!can't read config.yml: " + e.getMessage();
		}
		String after = withItem(before, line.toString());
		try {
			Cfg.load(new java.io.ByteArrayInputStream(after.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
			out.getParentFile().mkdirs();
			java.nio.file.Files.write(out.toPath(), file);
			java.nio.file.Files.write(cfgFile.toPath(), after.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			if (reloader != null) {
				reloader.run();
			} else {
				load(Cfg.load(cfgFile));
			}
		} catch (java.io.IOException | RuntimeException e) {
			undoPublish(out, cfgFile, before);
			return "!couldn't add it: " + e.getMessage();
		}
		if (catalog.get(id) == null) {
			String why = catalog.warnings().isEmpty() ? "it didn't load" : catalog.warnings().get(catalog.warnings().size() - 1);
			undoPublish(out, cfgFile, before);
			return "!the shop refused it: " + why;
		}
		refreshAll();
		log.info("Published " + category + " " + id + " (" + name + ", " + price + ") from the panel");
		return "added " + id + " to the shop";
	}

	private void undoPublish(java.io.File out, java.io.File cfgFile, String before) {
		out.delete();
		try {
			java.nio.file.Files.write(cfgFile.toPath(), before.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			if (reloader != null) {
				reloader.run();
			} else {
				load(Cfg.load(cfgFile));
			}
		} catch (java.io.IOException | RuntimeException e) {
			log.warning("Couldn't put config.yml back: " + e);
		}
	}

	/** config.yml text with one more line at the end of the items section (or a new items section). */
	static String withItem(String text, String line) {
		String nl = text.contains("\r\n") ? "\r\n" : "\n";
		List<String> lines = new ArrayList<>(Arrays.asList(text.replace("\r\n", "\n").split("\n", -1)));
		int start = -1;
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).startsWith("items:")) {
				start = i;
				break;
			}
		}
		if (start < 0) {
			while (!lines.isEmpty() && lines.get(lines.size() - 1).trim().isEmpty()) {
				lines.remove(lines.size() - 1);
			}
			lines.add("");
			lines.add("items:");
			lines.add(line);
			lines.add("");
			return String.join(nl, lines);
		}
		if (lines.get(start).trim().equals("items: {}")) {
			lines.set(start, "items:");
		}
		int last = start;
		for (int i = start + 1; i < lines.size(); i++) {
			String l = lines.get(i);
			if (l.trim().isEmpty() || l.startsWith("#")) {
				continue;
			}
			if (!l.startsWith(" ") && !l.startsWith("\t")) {
				break;
			}
			last = i;
		}
		lines.add(last + 1, line);
		return String.join(nl, lines);
	}

	private void refreshAll() {
		for (UUID o : net.online()) {
			if (clients.contains(o)) {
				sendState(o, true);
				sendEveryone(o);
			}
		}
	}

	// ==================== /pleb ====================

	interface Sender {
		void reply(String line);

		boolean hasPermission(String perm);
	}

	/** Finds a player by name: online first, then players.yml. */
	/**
	 * A PlebMc account got a new name on the website. An offline-mode player is their
	 * name, so the new name is a new player: this carries the coins, cosmetics, streak
	 * and quest progress over to it. Returns the two players (old, new), or null when
	 * the old name never joined.
	 */
	synchronized UUID[] rename(String from, String to) {
		UUID old = resolve(from);
		UUID neu = null;
		for (UUID o : net.online()) {
			if (to.equals(net.name(o))) {
				neu = o;
			}
		}
		if (neu == null) {
			neu = StaffLock.offlineUuid(to);
		}
		if (old == null || old.equals(neu)) {
			return null;
		}
		Store.Data a = store.get(old);
		Store.Data b = store.get(neu);
		b.name = to;
		b.coins += a.coins;
		b.owned.addAll(a.owned);
		for (Map.Entry<String, String> e : a.equipped.entrySet()) {
			b.equipped.putIfAbsent(e.getKey(), e.getValue());
		}
		if (a.lastLogin > b.lastLogin) {
			b.lastLogin = a.lastLogin;
			b.streak = a.streak;
		}
		b.seen = Math.max(a.seen, b.seen);
		b.playMinutes += a.playMinutes;
		if (b.progress.isEmpty() && b.done.isEmpty()) {
			b.dailyKey = a.dailyKey;
			b.weeklyKey = a.weeklyKey;
			b.progress.putAll(a.progress);
			b.done.addAll(a.done);
		}
		store.remove(old);
		store.changed();
		store.save();
		return new UUID[] { old, neu };
	}

	private UUID resolve(String name) {
		for (UUID o : net.online()) {
			if (name.equalsIgnoreCase(net.name(o))) {
				return o;
			}
		}
		for (UUID id : store.ids()) {
			Store.Data d = store.peek(id);
			if (d != null && name.equalsIgnoreCase(d.name)) {
				return id;
			}
		}
		return null;
	}

	/** Reload is done by the caller (it reads the file); this runs every other command. */
	synchronized void command(Sender s, String label, String[] a, Runnable reload) {
		if (!s.hasPermission(ADMIN)) {
			s.reply(prefix + color("&cYou don't have permission."));
			return;
		}
		String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
		switch (sub) {
		case "reload":
			reload.run();
			refreshAll();
			s.reply(prefix + "Reloaded: " + catalog.all().size() + " cosmetics, " + quests.allQuests().size()
					+ " quests in " + quests.categories.size() + " categories"
					+ (problems() == 0 ? "" : color(" &c(" + problems() + " problems, see console)")));
			return;
		case "list":
			for (Catalog.Item it : catalog.all()) {
				s.reply(color("&7- &f" + it.id + " &8(" + it.category + ", " + it.render + ") &e"
						+ (it.notForSale() ? "not for sale" : fmt(it.price))));
			}
			return;
		case "vote": {
			if (a.length != 2) {
				s.reply(prefix + "Usage: /pleb vote <player>  (gives the vote reward, like a vote from a server list)");
				return;
			}
			s.reply(prefix + vote(a[1], "command"));
			return;
		}
		case "quests": {
			long now = net.now();
			s.reply(prefix + "Quests for " + quests.dayKey(now) + " (" + quests.weekKey(now) + "):");
			for (Quests.Quest q : quests.active(now)) {
				s.reply(color("&7- &f" + q.id + " &8(" + q.category + " " + q.period + ", " + q.type + " x" + q.goal
						+ ") &e+" + fmt(q.reward)));
			}
			// where quests count, and whether backends are sending events
			java.util.Set<String> servers = new java.util.TreeSet<>(bridgeSeen.keySet());
			for (UUID o : net.online()) {
				if (net.server(o) != null) {
					servers.add(net.server(o).toLowerCase(Locale.ROOT));
				}
			}
			for (String sv : servers) {
				Long seen = bridgeSeen.get(sv);
				s.reply(color("&7Server &f" + sv + "&7: " + (quests.counts(sv) ? "&acounts for quests" : "&cin no quest category")
						+ "&7, " + (seen == null ? "&eno kill/match/win events yet (is PlebMcBridge on it?)"
								: "last event " + Math.max(0, (now - seen) / 1000) + "s ago")));
			}
			if (config.bool("plebclient-only", true)) {
				s.reply(color("&7Only players on the PlebMc client earn (plebclient-only: true)."));
			}
			return;
		}
		case "give":
		case "remove": {
			if (a.length < 3) {
				s.reply(prefix + color("&cUsage: /" + label + " " + sub + " <player> <item>"));
				return;
			}
			Catalog.Item it = catalog.get(a[2]);
			UUID id = resolve(a[1]);
			if (it == null || id == null) {
				s.reply(prefix + color(it == null ? "&cNo item '" + a[2] + "'." : "&cUnknown player."));
				return;
			}
			Store.Data d = store.get(id);
			if (sub.equals("give")) {
				d.owned.add(it.id);
			} else {
				d.owned.remove(it.id);
				if (it.id.equals(d.equipped.get(it.category))) {
					d.equipped.remove(it.category);
				}
			}
			store.changed();
			store.save();
			sendState(id, false);
			broadcastSlots(id);
			s.reply(prefix + (sub.equals("give") ? "Gave " : "Removed ") + it.id + (sub.equals("give") ? " to " : " from ")
					+ a[1]);
			return;
		}
		case "equip": {
			if (a.length < 4) {
				s.reply(prefix + color("&cUsage: /" + label + " equip <player> <hat|cloak|wings> <item|none>"));
				return;
			}
			UUID id = resolve(a[1]);
			String cat = a[2].toLowerCase(Locale.ROOT);
			if (id == null || !Catalog.isCategory(cat)) {
				s.reply(prefix + color("&cUnknown player or category."));
				return;
			}
			Store.Data d = store.get(id);
			if (a[3].equalsIgnoreCase("none")) {
				d.equipped.remove(cat);
			} else {
				Catalog.Item it = catalog.get(a[3]);
				if (it == null || !it.category.equals(cat)) {
					s.reply(prefix + color("&cNo " + cat + " called '" + a[3] + "'."));
					return;
				}
				d.owned.add(it.id);
				d.equipped.put(cat, it.id);
			}
			store.changed();
			sendState(id, false);
			broadcastSlots(id);
			s.reply(prefix + "Done.");
			return;
		}
		case "info":
		case "balance": {
			if (a.length < 2) {
				s.reply(prefix + color("&cUsage: /" + label + " " + sub + " <player>"));
				return;
			}
			UUID id = resolve(a[1]);
			Store.Data d = id == null ? null : store.peek(id);
			if (d == null) {
				s.reply(prefix + a[1] + " has no " + currency + " yet.");
				return;
			}
			s.reply(prefix + a[1] + color(": &e" + fmt(d.coins) + " &7") + currency + color(", login streak &f" + d.streak));
			if (sub.equals("info")) {
				s.reply(color("&7Owned: &f" + (d.owned.isEmpty() ? "nothing" : String.join(", ", d.owned))));
				s.reply(color("&7Hat: &f" + d.equipped.getOrDefault(Catalog.HAT, "none") + "  &7Wings: &f" + d.equipped.getOrDefault(Catalog.WINGS, "none") + "  &7Cloak: &f"
						+ d.equipped.getOrDefault(Catalog.CLOAK, "none")));
				s.reply(color("&7Quests done: &f" + (d.done.isEmpty() ? "none" : String.join(", ", d.done))));
				String r = role(id);
				s.reply(color("&7PlebMc account: &f" + (r == null ? "not signed in (or offline)" : "signed in, " + r)));
			}
			return;
		}
		case "coins": {
			if (a.length >= 2 && "wipe".equalsIgnoreCase(a[1])) {
				if (a.length < 3 || !"confirm".equalsIgnoreCase(a[2])) {
					s.reply(prefix + color("&eThis sets EVERY player's " + currency
							+ " to 0 (cosmetics stay). A backup of players.yml is saved first. Type: /" + label
							+ " coins wipe confirm"));
					return;
				}
				int n = store.wipeCoins();
				if (n < 0) {
					s.reply(prefix + color("&cStopped: couldn't back up players.yml (see the console). Nothing was changed."));
					return;
				}
				for (UUID id : net.online()) {
					sendState(id, false);
				}
				s.reply(prefix + color("&aWiped " + currency + " for " + n + " players. Everyone is at 0 now."));
				return;
			}
			if (a.length >= 3 && ("online".equalsIgnoreCase(a[1]) || "giveall".equalsIgnoreCase(a[1]))) {
				long n;
				try {
					n = Long.parseLong(a[2]);
				} catch (NumberFormatException e) {
					n = -1;
				}
				Boolean who = a.length < 4 ? null : "all".equalsIgnoreCase(a[3]) ? Boolean.FALSE : "client".equalsIgnoreCase(a[3]) ? Boolean.TRUE : null;
				if (n <= 0 || n > 1_000_000_000L || (a.length >= 4 && who == null)) {
					s.reply(prefix + color("&cUsage: /" + label + " coins online <amount> [all|client]"));
					return;
				}
				s.reply(prefix + color("&aGave " + fmt(n) + " " + currency + " to " + giveOnline(n, who) + " players online."));
				return;
			}
			if (a.length < 4) {
				s.reply(prefix + color("&cUsage: /" + label + " coins <give|take|set> <player> <amount>  or  /" + label
						+ " coins online <amount>"));
				return;
			}
			UUID id = resolve(a[2]);
			long n;
			try {
				n = Long.parseLong(a[3]);
			} catch (NumberFormatException e) {
				n = -1;
			}
			if (id == null || n < 0) {
				s.reply(prefix + color("&cUnknown player or bad amount."));
				return;
			}
			Store.Data d = store.get(id);
			switch (a[1].toLowerCase(Locale.ROOT)) {
			case "give":
				d.coins += n;
				break;
			case "take":
				d.coins = Math.max(0, d.coins - n);
				break;
			case "set":
				d.coins = n;
				break;
			default:
				s.reply(prefix + color("&cUsage: /" + label + " coins <give|take|set> <player> <amount>"));
				return;
			}
			store.changed();
			sendState(id, false);
			s.reply(prefix + a[2] + " now has " + fmt(d.coins) + " " + currency + ".");
			return;
		}
		case "progress": {
			if (a.length < 3) {
				s.reply(prefix + color("&cUsage: /" + label + " progress <player> <type> [amount]"));
				return;
			}
			UUID id = resolve(a[1]);
			if (id == null || !net.online().contains(id)) {
				s.reply(prefix + color("&cThat player isn't online."));
				return;
			}
			long n = 1;
			if (a.length > 3) {
				try {
					n = Long.parseLong(a[3]);
				} catch (NumberFormatException e) {
					n = 0;
				}
			}
			if (!quests.counts(net.server(id))) {
				s.reply(prefix + color("&c" + net.server(id) + " isn't in any quest category (see quests.categories)."));
				return;
			}
			progress(id, net.server(id), a[2], n);
			s.reply(prefix + "Counted " + n + " " + a[2] + " for " + a[1] + " on " + net.server(id) + ".");
			return;
		}
		default:
			s.reply(prefix + color("&fPlebMc Coins, quests and cosmetics"));
			for (String line : Arrays.asList("reload", "quests", "list", "coins <give|take|set> <player> <amount>", "coins online <amount>", "coins wipe confirm",
					"balance <player>", "info <player>", "progress <player> <type> [amount]", "give <player> <item>",
					"remove <player> <item>", "equip <player> <hat|cloak|wings> <item|none>", "vote <player>", "votifier")) {
				s.reply(color("&7/" + label + " " + line));
			}
		}
	}

	/** Everyone's coins and cosmetics, the item list and who's online, for the panel. */
	synchronized Map<String, Object> snapshot() {
		List<Map<String, Object>> players = new ArrayList<>();
		for (UUID id : store.ids()) {
			Store.Data d = store.peek(id);
			if (d == null || d.name == null || d.name.isEmpty()) {
				continue;
			}
			Map<String, Object> p = new LinkedHashMap<>();
			p.put("uuid", id.toString());
			p.put("name", d.name);
			p.put("coins", d.coins);
			p.put("owned", new ArrayList<>(d.owned));
			p.put("equipped", new LinkedHashMap<>(d.equipped));
			p.put("streak", d.streak);
			if (d.seen != 0) {
				p.put("seen", d.seen);
			}
			players.add(p);
		}
		List<Map<String, Object>> items = new ArrayList<>();
		for (Catalog.Item it : catalog.all()) {
			// everything the website needs to draw a preview (textures and models are sent when it asks)
			Map<String, Object> i = it.toJson();
			i.put("name", it.name == null ? it.id : it.name.replaceAll("(?i)[&\u00a7][0-9a-fk-or]", ""));
			i.put("price", it.notForSale() ? null : it.price);
			if (it.permission != null && !it.permission.isEmpty()) {
				i.put("permission", true);
			}
			items.add(i);
		}
		List<String> online = new ArrayList<>();
		for (UUID o : net.online()) {
			String n = net.name(o);
			if (n != null) {
				online.add(n);
			}
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("full", true);
		m.put("players", players);
		m.put("catalog", items);
		m.put("online", online);
		m.put("currency", currency);
		return m;
	}

	synchronized List<String> tabComplete(String[] a) {
		List<String> options;
		if (a.length == 1) {
			options = Arrays.asList("reload", "quests", "list", "coins", "balance", "info", "progress", "give", "remove",
					"equip", "vote", "votifier");
		} else if (a.length == 2 && a[0].equalsIgnoreCase("coins")) {
			options = Arrays.asList("give", "take", "set", "online", "wipe");
		} else if (a.length == 2 || (a.length == 3 && a[0].equalsIgnoreCase("coins"))) {
			options = new ArrayList<>();
			for (UUID o : net.online()) {
				options.add(net.name(o));
			}
		} else if (a.length == 3 && a[0].equalsIgnoreCase("progress")) {
			options = Arrays.asList("kill", "win", "match", "death", "play", "join", "chat");
		} else if (a.length == 3 && a[0].equalsIgnoreCase("equip")) {
			options = Catalog.CATEGORIES;
		} else if ((a.length == 3 && (a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("remove")))
				|| (a.length == 4 && a[0].equalsIgnoreCase("equip"))) {
			options = new ArrayList<>();
			for (Catalog.Item it : catalog.all()) {
				if (a.length == 3 || it.category.equalsIgnoreCase(a[2])) {
					options.add(it.id);
				}
			}
			if (a.length == 4) {
				options.add("none");
			}
		} else {
			return new ArrayList<>();
		}
		String start = a[a.length - 1].toLowerCase(Locale.ROOT);
		List<String> out = new ArrayList<>();
		for (String o : options) {
			if (o != null && o.toLowerCase(Locale.ROOT).startsWith(start)) {
				out.add(o);
			}
		}
		return out;
	}
}
