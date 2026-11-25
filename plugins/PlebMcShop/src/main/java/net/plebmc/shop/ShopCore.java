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
	}

	/** Re-reads config.yml (set by the proxy plugin; the admin menu's Reload uses it). */
	Runnable reloader;

	static final String ADMIN = "plebshop.admin";
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
	/** plebmc.top logins: the website account of each online player (looked up on join). */
	private final Website.Http http;
	Website website;
	private final Map<UUID, Website.Account> accounts = new HashMap<>();
	private final java.util.Set<UUID> lookedUp = new java.util.HashSet<>();
	private final Map<UUID, Long> lastLinkTry = new HashMap<>();
	private static final long LINK_COOLDOWN_MS = 4000;

	/** The plugin folder (cloak textures are read from it); null in tests that don't need it. */
	private java.io.File folder;

	/** Sets the plugin folder and re-reads the catalog so cloak textures load. */
	synchronized void folder(java.io.File f) {
		folder = f;
		load(config);
	}

	ShopCore(Net net, Store store, Cfg config, Logger log) {
		this(net, store, config, log, Website.REAL);
	}

	ShopCore(Net net, Store store, Cfg config, Logger log, Website.Http http) {
		this.net = net;
		this.store = store;
		this.log = log;
		this.http = http;
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
		website = new Website(cfg, http);
		currency = cfg.str("currency-name", "PlebMc Coins");
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
		if (firstJoin) {
			long reward = quests.login(d, now);
			if (reward >= 0) {
				store.changed();
				notify(p, msg("login-title", "&6Daily login"), msg("login-message", "Day %streak%: +%coins% %currency%")
						.replace("%streak%", Integer.toString(d.streak)).replace("%coins%", fmt(reward)));
				completed(p, quests.add(d, null, "login", 1, now));
			}
		}
		completed(p, quests.add(d, net.server(p), "join", 1, now));
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
			lookupAccount(p, true);
		}
	}

	synchronized void quit(UUID p, String server) {
		lastAction.remove(p);
		worlds.remove(p);
		clients.remove(p);
		accounts.remove(p);
		lookedUp.remove(p);
		lastLinkTry.remove(p);
		lastChat.remove(p);
		Map<String, Object> gone = new LinkedHashMap<>();
		gone.put(p.toString(), null);
		broadcast(playersMessage(gone, false), server, p);
	}

	/** Every minute: playtime coins, and "play" quests in the categories covering the player's server. */
	synchronized void minute() {
		long now = net.now();
		for (UUID p : net.online()) {
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
		if (server == null || !quests.counts(server)) {
			return; // a server no quest category covers: nothing counts there
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
			if (lookedUp.contains(p) || !website.configured()) {
				sendAccount(p, null, true);
			} else {
				lookupAccount(p, false);
			}
			break;
		case "link":
			link(p, str(m.get("code")));
			break;
		case "logout":
			logout(p);
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

	// ==================== plebmc.top login ====================

	/** Asks the website which account this player linked (off-thread), then tells the client. */
	private void lookupAccount(UUID p, boolean greet) {
		if (!website.configured()) {
			return;
		}
		final Website w = website;
		net.async(() -> {
			Website.Result r = w.lookup(p);
			synchronized (ShopCore.this) {
				if (!net.online().contains(p)) {
					return;
				}
				lookedUp.add(p);
				if (r.account != null) {
					accounts.put(p, r.account);
				} else if (r.error == null) {
					accounts.remove(p);
				}
				if (r.error != null) {
					log.warning("website lookup for " + net.name(p) + ": " + r.error);
				}
				sendAccount(p, r.error, r.error == null);
				if (greet && r.account != null) {
					notify(p, msg("website-login-title", "&bplebmc.top"),
							msg("website-login-message", "Logged in as %account%").replace("%account%", r.account.displayName));
				}
			}
		});
	}

	private void link(UUID p, String code) {
		if (!website.configured()) {
			sendAccount(p, "Website login isn't set up on this server.", false);
			return;
		}
		code = code == null ? "" : code.trim();
		if (code.startsWith("/verify ")) {
			code = code.substring(8).trim(); // pasted the whole command
		}
		if (!code.matches("[A-Za-z0-9]{4,64}")) {
			sendAccount(p, "Paste the code from the website (letters and numbers).", false);
			return;
		}
		long now = System.currentTimeMillis();
		Long last = lastLinkTry.get(p);
		if (last != null && now - last < LINK_COOLDOWN_MS) {
			sendAccount(p, "Wait a few seconds and try again.", false);
			return;
		}
		lastLinkTry.put(p, now);
		final String c = code;
		final String name = net.name(p);
		final Website w = website;
		net.async(() -> {
			Website.Result r = w.link(p, name, c);
			synchronized (ShopCore.this) {
				if (!net.online().contains(p)) {
					return;
				}
				lookedUp.add(p);
				if (r.account != null) {
					accounts.put(p, r.account);
					log.info(name + " logged in as " + r.account.username + " on the website");
					sendAccount(p, "Logged in as " + r.account.displayName + "!", true);
				} else {
					sendAccount(p, r.error, false);
				}
			}
		});
	}

	/** Unlinks the player's Minecraft account on the website (off-thread). */
	private void logout(UUID p) {
		if (!website.configured() || !accounts.containsKey(p)) {
			sendAccount(p, null, true);
			return;
		}
		long now = System.currentTimeMillis();
		Long last = lastLinkTry.get(p);
		if (last != null && now - last < LINK_COOLDOWN_MS) {
			sendAccount(p, "Wait a few seconds and try again.", false);
			return;
		}
		lastLinkTry.put(p, now);
		final Website w = website;
		final String name = net.name(p);
		net.async(() -> {
			String err = w.unlink(p);
			synchronized (ShopCore.this) {
				if (!net.online().contains(p)) {
					return;
				}
				if (err == null) {
					accounts.remove(p);
					log.info(name + " logged out of the website (unlinked)");
					sendAccount(p, "Logged out. Link again any time with a new code.", true);
				} else {
					sendAccount(p, err, false);
				}
			}
		});
	}

	/** The website account of an online player, or null. */
	synchronized Website.Account account(UUID p) {
		return accounts.get(p);
	}

	private void sendAccount(UUID p, String message, boolean ok) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("type", "account");
		m.put("enabled", website.configured());
		Website.Account a = accounts.get(p);
		m.put("linked", a != null);
		if (a != null) {
			m.put("username", a.username);
			m.put("displayName", a.displayName);
			m.put("groups", a.groups);
			m.put("staff", a.staff);
		}
		m.put("admin", isAdmin(p));
		m.put("site", website.url);
		m.put("linkUrl", website.connectionsUrl());
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
	 * True when this player may use the admin menu: logged in on the website
	 * with a group from admin-menu.groups (Owner by default). Checked again on
	 * every admin message, so a client can't just claim it.
	 */
	synchronized boolean isAdmin(UUID p) {
		if (!config.bool("admin-menu.enabled", true)) {
			return false;
		}
		Website.Account a = accounts.get(p);
		if (a == null) {
			return false;
		}
		List<String> allowed = config.list("admin-menu.groups");
		if (allowed.isEmpty() && !config.has("admin-menu.groups")) {
			allowed = Arrays.asList("Owner");
		}
		for (String g : a.groups) {
			for (String ok : allowed) {
				if (g != null && g.trim().equalsIgnoreCase(ok.trim())) {
					return true;
				}
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
			r.put("message", "Only website Owners can use the admin menu.");
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
			text = text.length() > 200 ? text.substring(0, 200) : text;
			String title = color(config.str("admin-menu.broadcast-title", "&b&lAnnouncement"));
			for (UUID o : net.online()) {
				if (clients.contains(o)) {
					notify(o, title, color(text));
				} else {
					net.chat(o, prefix + color(text));
				}
			}
			msg = "Sent to " + net.online().size() + " players.";
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
			Website.Account a = accounts.get(o);
			if (a != null) {
				pm.put("account", a.username);
				pm.put("groups", a.groups);
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
		if (hidden(p)) {
			return null;
		}
		Map<String, Object> s = slots(store.peek(p));
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

	private void refreshAll() {
		for (UUID o : net.online()) {
			if (clients.contains(o)) {
				sendState(o, true);
				sendEveryone(o);
			}
		}
	}

	// ==================== /plebshop ====================

	interface Sender {
		void reply(String line);

		boolean hasPermission(String perm);
	}

	/** Finds a player by name: online first, then players.yml. */
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
		case "quests": {
			long now = net.now();
			s.reply(prefix + "Quests for " + quests.dayKey(now) + " (" + quests.weekKey(now) + "):");
			for (Quests.Quest q : quests.active(now)) {
				s.reply(color("&7- &f" + q.id + " &8(" + q.category + " " + q.period + ", " + q.type + " x" + q.goal
						+ ") &e+" + fmt(q.reward)));
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
				s.reply(prefix + color("&cUsage: /" + label + " equip <player> <hat|cloak> <item|none>"));
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
				s.reply(color("&7Hat: &f" + d.equipped.getOrDefault(Catalog.HAT, "none") + "  &7Cloak: &f"
						+ d.equipped.getOrDefault(Catalog.CLOAK, "none")));
				s.reply(color("&7Quests done: &f" + (d.done.isEmpty() ? "none" : String.join(", ", d.done))));
				Website.Account acc = accounts.get(id);
				s.reply(color("&7Website: &f" + (acc == null ? "not logged in (or offline)" : acc.username + " " + acc.groups)));
			}
			return;
		}
		case "coins": {
			if (a.length < 4) {
				s.reply(prefix + color("&cUsage: /" + label + " coins <give|take|set> <player> <amount>"));
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
			for (String line : Arrays.asList("reload", "quests", "list", "coins <give|take|set> <player> <amount>",
					"balance <player>", "info <player>", "progress <player> <type> [amount]", "give <player> <item>",
					"remove <player> <item>", "equip <player> <hat|cloak> <item|none>")) {
				s.reply(color("&7/" + label + " " + line));
			}
		}
	}

	synchronized List<String> tabComplete(String[] a) {
		List<String> options;
		if (a.length == 1) {
			options = Arrays.asList("reload", "quests", "list", "coins", "balance", "info", "progress", "give", "remove",
					"equip");
		} else if (a.length == 2 && a[0].equalsIgnoreCase("coins")) {
			options = Arrays.asList("give", "take", "set");
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
