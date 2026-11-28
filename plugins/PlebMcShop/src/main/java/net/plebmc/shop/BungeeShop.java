package net.plebmc.shop;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.TabExecutor;
import net.md_5.bungee.event.EventHandler;

/**
 * BungeeCord side of PlebMcShop. Runs on the proxy, so one PlebMc Coins
 * balance, one set of quests and one wardrobe work on every server behind
 * it. Talks to PlebMc clients on {@code plebmc:shop} (those messages are
 * answered here and never reach the backend servers) and hears quest events
 * from the PlebMcBridge plugin on the backends (sub-channel
 * {@code PlebMcBridge} of the standard BungeeCord channel).
 */
public final class BungeeShop extends Plugin implements Listener {

	static final String CHANNEL = "plebmc:shop";
	static final String BRIDGE = "PlebMcBridge";

	private ShopCore core;
	private StaffLock staffLock;
	/** Admin passwords (plugins/PlebioAPI/admin-passwords.yml): a second password, only for the admin menu. */
	private StaffLock adminPasswords;
	/** Players who typed their admin password with /adminlogin this session. */
	private final java.util.Set<UUID> adminUnlocked = ConcurrentHashMap.newKeySet();
	private final BungeeNet bnet = new BungeeNet();
	/** Players who gave their staff password at this login (the only ones who get admin powers). */
	private final java.util.Set<UUID> staffVerified = ConcurrentHashMap.newKeySet();
	/** EaglerXBungee is installed and its auth hooks are on (staff can get the password screen). */
	private boolean eaglerAuth;
	/** PlebMc accounts (the panel on the VPS): who has signed in to the account that owns their name. */
	private SignIn signin;
	private volatile PanelLink panelLink;
	private long syncedVersion = -1;
	private long lastSync;
	private final java.util.concurrent.atomic.AtomicBoolean polling = new java.util.concurrent.atomic.AtomicBoolean();

	/**
	 * The plugin used to be called PlebMcShop. On the first start as PlebioAPI,
	 * move its folder (config, players, cloaks) over so balances and quests carry on.
	 */
	private void migrateOldFolder() {
		File now = getDataFolder();
		File old = new File(now.getParentFile(), "PlebMcShop");
		if (now.exists() || !old.isDirectory()) {
			return;
		}
		if (old.renameTo(now)) {
			getLogger().info("Moved plugins/PlebMcShop to plugins/" + now.getName() + " (config and player data kept)");
		} else {
			getLogger().warning("Could not move plugins/PlebMcShop to plugins/" + now.getName()
					+ ": move it by hand to keep balances and quests");
		}
	}
	/** The server each player is on, as we last saw it (for the switch "from"). */
	private final Map<UUID, String> lastServer = new ConcurrentHashMap<>();
	private final Gson gson = new Gson();

	@Override
	public void onEnable() {
		migrateOldFolder();
		File cfg = new File(getDataFolder(), "config.yml");
		try {
			if (!cfg.exists()) {
				getDataFolder().mkdirs();
				try (InputStream in = getResourceAsStream("config.yml")) {
					Files.copy(in, cfg.toPath());
				}
			} else {
				// add settings this version is new to (never changes or removes yours)
				try (InputStream in = getResourceAsStream("config.yml")) {
					java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
					byte[] buf = new byte[8192];
					int n;
					while ((n = in.read(buf)) > 0) {
						b.write(buf, 0, n);
					}
					ConfigUpdate.update(cfg, b.toByteArray(), getLogger());
				} catch (IOException | RuntimeException e) {
					getLogger().warning("Couldn't check config.yml for new settings: " + e);
				}
			}
			// the sample cloak textures and hat model (never overwrites yours)
			for (String c : new String[] { "cloaks/plebmc.png", "cloaks/storm.png", "cloaks/aurora.png", "cloaks/inferno.png", "cloaks/galaxy.png", "cloaks/rainbow_wave.png", "hats/king_crown.bbmodel" }) {
				File out = new File(getDataFolder(), c);
				if (!out.exists()) {
					out.getParentFile().mkdirs();
					try (InputStream in = getResourceAsStream(c)) {
						if (in != null) {
							Files.copy(in, out.toPath());
						}
					}
				}
			}
			core = new ShopCore(bnet, new Store(new File(getDataFolder(), "players.yml"), getLogger()),
					Cfg.load(cfg), getLogger());
			core.folder(getDataFolder());
			staffLock = new StaffLock(new File(getDataFolder(), "staff-lock.yml"), getLogger());
			adminPasswords = new StaffLock(new File(getDataFolder(), "admin-passwords.yml"), getLogger());
			core.reloader = this::reload;
			signin = new SignIn(bnet, getLogger());
			signin.mayRegister = p -> !usingNLogin() || nLoginAuthenticated(p);
			signin.changed = p -> core.authChanged(p);
			// the admin menu goes by the PlebMc account's role (admin, owner)
			core.panelRole = p -> {
				SignIn.Session s = signin.session(p);
				return s != null && s.signedIn ? s.role : null;
			};
			core.signin = signin;
			configureAccounts();
			startVotifier();
		} catch (IOException e) {
			getLogger().severe("Could not load config.yml: " + e);
			return;
		}
		getProxy().registerChannel(CHANNEL);
		getProxy().getPluginManager().registerListener(this, this);
		getProxy().getPluginManager().registerCommand(this, new AdminCommand());
		getProxy().getPluginManager().registerCommand(this, new AdminLoginCommand());
		getProxy().getPluginManager().registerCommand(this, new VoteCommand());
		if (adminPasswordRequired() && adminPasswords.all().isEmpty()) {
			getLogger().warning("Admin menu is OFF for everyone until you set an admin password in this console:"
					+ " pleb adminpass set <name> <password>  (then in game: /login, then /adminlogin <password>)");
		}
		eaglerAuth = registerEaglerAuth();
		if (usingNLogin()) {
			getLogger().info(findNLogin() ? "nLogin found: shop, account and admin actions need /login first"
					: "nLogin found but not usable: account changes are locked");
			getProxy().getScheduler().schedule(this, this::checkLogins, 2, 2, TimeUnit.SECONDS);
		}
		getProxy().getScheduler().schedule(this, staffLock::cleanup, 1, 1, TimeUnit.MINUTES);
		if (staffLockOn() && !usingNLogin()) {
			int n = staffLock.all().size();
			getLogger().info("Staff lock: " + n + " protected name" + (n == 1 ? "" : "s")
					+ (eaglerAuth ? "" : " (Eaglercraft password screen OFF: protected names can't join until you turn on"
							+ " EaglerXBungee's authservice.yml, see the PlebioAPI README)"));
			if (n == 0) {
				getLogger().warning("Staff lock: no protected names yet. On a cracked server anyone can join as your Owner."
						+ " In this console run: pleb staff set <name> <password>");
			}
		}
		getProxy().getScheduler().schedule(this, signin::tick, 5, 5, TimeUnit.SECONDS);
		getProxy().getScheduler().schedule(this, this::pollPanel, 5, 3, TimeUnit.SECONDS);
		getProxy().getScheduler().schedule(this, this::syncPanel, 10, 20, TimeUnit.SECONDS);
		getProxy().getScheduler().schedule(this, core::minute, 1, 1, TimeUnit.MINUTES);
		getProxy().getScheduler().schedule(this, core::saveIfDirty, 30, 30, TimeUnit.SECONDS);
		getLogger().info(core.catalog.all().size() + " cosmetics, " + core.quests.allQuests().size() + " quests in "
				+ core.quests.categories.keySet());
	}

	@Override
	public void onDisable() {
		if (votifier != null) {
			votifier.stop();
		}
		if (core != null) {
			core.save();
		}
	}

	private void reload() {
		try {
			core.load(Cfg.load(new File(getDataFolder(), "config.yml")));
			configureAccounts();
			startVotifier();
		} catch (IOException e) {
			getLogger().severe("Could not reload config.yml: " + e);
		}
	}

	// ==================== staff lock ====================

	/** nLogin's API (com.nickuc.login.api.nLoginAPI), found by reflection; null when it isn't installed. */
	private Object nlogin;
	private java.lang.reflect.Method nloginIsAuthenticated;
	/** Last login state we told the core about, per player. */
	private final Map<UUID, Boolean> lastAuth = new ConcurrentHashMap<>();

	private boolean findNLogin() {
		if (getProxy().getPluginManager().getPlugin("nLogin") == null) {
			return false;
		}
		try {
			Class<?> api = Class.forName("com.nickuc.login.api.nLoginAPI", true,
					getProxy().getPluginManager().getPlugin("nLogin").getClass().getClassLoader());
			nlogin = api.getMethod("getApi").invoke(null);
			nloginIsAuthenticated = api.getMethod("isAuthenticated", String.class);
			return nlogin != null;
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			getLogger().warning("nLogin is installed but its API couldn't be used (" + e + "): account changes and the admin menu"
					+ " stay locked for everyone until this is fixed");
			nlogin = null;
			return false;
		}
	}

	/**
	 * Proved this name is theirs this session: signed in to its PlebMc account
	 * when accounts are on (doesn't depend on IPs or nLogin sessions), else
	 * nLogin /login.
	 */
	private boolean authenticated(UUID player) {
		if (accountsOn()) {
			return signin.signedIn(player);
		}
		return nLoginAuthenticated(player);
	}

	private boolean accountsOn() {
		return signin != null && signin.enabled();
	}

	/** "Log in first" in the words of whichever login is in charge. */
	private String loginFirst() {
		return accountsOn() ? "Sign in first (PlebClient: Right Shift > Sign in)" : "Log in first (/login <password>)";
	}

	/** Logged in with nLogin this session; true when there's no nLogin; false (locked) if nLogin can't be asked. */
	private boolean nLoginAuthenticated(UUID player) {
		boolean hasNLogin = getProxy().getPluginManager().getPlugin("nLogin") != null;
		if (!hasNLogin) {
			return true;
		}
		if (nlogin == null && !findNLogin()) {
			return false;
		}
		ProxiedPlayer p = getProxy().getPlayer(player);
		if (p == null) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(nloginIsAuthenticated.invoke(nlogin, p.getName()));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** Tells the core when someone logs in with nLogin, so their admin door appears right away. */
	private void checkLogins() {
		for (ProxiedPlayer p : getProxy().getPlayers()) {
			boolean now = authenticated(p.getUniqueId());
			Boolean before = lastAuth.put(p.getUniqueId(), now);
			if (before != null && before != now) {
				core.authChanged(p.getUniqueId());
			}
		}
	}

	private boolean usingNLogin() {
		return getProxy().getPluginManager().getPlugin("nLogin") != null;
	}

	private boolean adminPasswordRequired() {
		return core != null && core.config.bool("admin-menu.require-password", true);
	}

	/** Logged in for this session: nLogin /login, or (without nLogin) the staff lock if the name is protected. */
	private boolean loggedIn(ProxiedPlayer p) {
		if (usingNLogin() || accountsOn()) {
			return authenticated(p.getUniqueId());
		}
		return !staffLockOn() || !staffLock.isProtected(p.getName()) || staffVerified.contains(p.getUniqueId());
	}

	/** /adminlogin <password>: unlocks the admin menu for this session. Handled on the proxy, never reaches a server. */
	// ==================== voting ====================

	private Votifier votifier;
	private String votifierAt = "";

	/** Starts (or restarts after a config change) the Votifier listener for server-list votes. */
	private void startVotifier() {
		Cfg c = core.config;
		boolean on = c.bool("voting.enabled", true) && c.bool("voting.votifier.enabled", true);
		String host = c.str("voting.votifier.host", "0.0.0.0");
		int port = c.integer("voting.votifier.port", 8192);
		String at = on ? host + ":" + port : "";
		if (at.equals(votifierAt)) {
			return;
		}
		if (votifier != null) {
			votifier.stop();
		}
		votifierAt = at;
		if (!on) {
			return;
		}
		try {
			if (votifier == null) {
				votifier = new Votifier(new File(getDataFolder(), "votifier"), getLogger(),
						(user, service) -> getLogger().info(core.vote(user, service)));
				votifier.loadKeys();
			}
			votifier.start(host, port);
			getLogger().info("Votifier listening on port " + port + " for server-list votes (" + core.voteReward + " "
					+ "coins a vote). Run 'pleb votifier' in this console for the key and token to give the server list.");
		} catch (IOException e) {
			votifierAt = "";
			getLogger().warning("Votifier couldn't start on port " + port + ": " + e.getMessage()
					+ ". If NuVotifier is installed, turn voting.votifier off and set NuVotifier to run 'pleb vote {username}'"
					+ " on each vote, or pick another voting.votifier.port.");
		}
	}

	/** /pleb votifier (console): what to paste into a server list's Votifier settings. */
	@SuppressWarnings("deprecation")
	private void votifierCommand(CommandSender sender) {
		if (sender instanceof ProxiedPlayer) {
			sender.sendMessage(TextComponent.fromLegacyText("\u00a7cRun 'pleb votifier' in the proxy console (it shows a secret token)."));
			return;
		}
		if (votifier == null || votifier.port() < 0) {
			sender.sendMessage(TextComponent.fromLegacyText("Votifier is off (voting.votifier in config.yml) or couldn't start, see above."));
			return;
		}
		sender.sendMessage(TextComponent.fromLegacyText("Votifier settings for the server list (eaglercraftservers.com etc):"));
		sender.sendMessage(TextComponent.fromLegacyText("  Host: your proxy's public IP or domain (like play.plebmc.top)   Port: " + votifier.port()));
		sender.sendMessage(TextComponent.fromLegacyText("  Token (NuVotifier v2): " + votifier.token()));
		sender.sendMessage(TextComponent.fromLegacyText("  Public key (Votifier v1): " + votifier.publicKey()));
		sender.sendMessage(TextComponent.fromLegacyText("  Open TCP port " + votifier.port() + " in your firewall. Test it with: pleb vote <player>"));
	}

	/** /vote: the link to vote for the server. */
	private final class VoteCommand extends Command {
		VoteCommand() {
			super("vote", null);
		}

		@Override
		public void execute(CommandSender sender, String[] args) {
			String url = core.voteUrl;
			TextComponent t = new TextComponent("\u00a7b\u00a7lVote for PlebMc \u00a77(+" + core.voteReward + " " + "coins a day): \u00a7f\u00a7n" + url);
			t.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, url));
			sender.sendMessage(t);
		}
	}

	private final class AdminLoginCommand extends Command {
		AdminLoginCommand() {
			super("adminlogin", null, "alogin");
		}

		@Override
		@SuppressWarnings("deprecation")
		public void execute(CommandSender sender, String[] args) {
			if (!(sender instanceof ProxiedPlayer)) {
				sender.sendMessage(TextComponent.fromLegacyText("Players only. Set passwords with: pleb adminpass set <name> <password>"));
				return;
			}
			ProxiedPlayer p = (ProxiedPlayer) sender;
			java.util.function.Consumer<String> reply = line -> p.sendMessage(TextComponent.fromLegacyText(line));
			if (!loggedIn(p)) {
				reply.accept("\u00a7c" + loginFirst() + ".");
				return;
			}
			if (args.length != 1) {
				reply.accept("\u00a7eUsage: /adminlogin <admin password>");
				return;
			}
			String ip = p.getAddress() == null || p.getAddress().getAddress() == null ? null : p.getAddress().getAddress().getHostAddress();
			if (adminPasswords.nameThrottled(p.getName())) {
				p.disconnect(TextComponent.fromLegacyText("\u00a7cToo many wrong admin passwords. Try again in 10 minutes."));
				return;
			}
			if (!adminPasswords.checkPlain(p.getName(), ip, args[0])) {
				getLogger().warning("Wrong /adminlogin for " + p.getName() + " from " + ip);
				reply.accept("\u00a7cWrong admin password.");
				for (ProxiedPlayer o : getProxy().getPlayers()) {
					if (adminUnlocked.contains(o.getUniqueId())) {
						o.sendMessage(TextComponent.fromLegacyText("\u00a7c[Admin] Wrong admin password typed by " + p.getName() + "."));
					}
				}
				return;
			}
			adminUnlocked.add(p.getUniqueId());
			getLogger().info("Admin menu unlocked for " + p.getName() + " from " + ip);
			reply.accept("\u00a7aAdmin menu unlocked for this session.");
			core.authChanged(p.getUniqueId());
		}
	}

	private boolean staffLockOn() {
		return core != null && core.config.bool("staff-lock.enabled", true);
	}

	/** Hooks EaglerXBungee's password screen if it's installed and its auth system is on. */
	private boolean registerEaglerAuth() {
		net.md_5.bungee.api.plugin.Plugin eagler = getProxy().getPluginManager().getPlugin("EaglercraftXBungee");
		if (eagler == null) {
			return false;
		}
		try {
			Class.forName("net.lax1dude.eaglercraft.v1_8.plugin.gateway_bungeecord.api.event.EaglercraftIsAuthRequiredEvent");
			getProxy().getPluginManager().registerListener(this, new EaglerStaffAuth(staffLock, getLogger()));
		} catch (ClassNotFoundException | LinkageError e) {
			getLogger().warning("Staff lock: this EaglerXBungee has no auth events (" + e + "), update it");
			return false;
		}
		try {
			File auth = new File(eagler.getDataFolder(), "authservice.yml");
			Cfg a = Cfg.load(auth);
			boolean on = a.bool("enable_authentication_system", false);
			boolean onboard = a.bool("use_onboard_eaglerx_system", true);
			if (!on || onboard) {
				getLogger().warning("Staff lock: in plugins/EaglercraftXBungee/authservice.yml set enable_authentication_system: true"
						+ " and use_onboard_eaglerx_system: false, then restart, or staff can't log in with a password");
			}
			return on && !onboard;
		} catch (IOException | RuntimeException e) {
			getLogger().warning("Staff lock: could not read EaglerXBungee's authservice.yml: " + e);
			return true;
		}
	}

	private static String address(PendingConnection c) {
		java.net.SocketAddress a = c.getSocketAddress();
		return a instanceof java.net.InetSocketAddress && ((java.net.InetSocketAddress) a).getAddress() != null
				? ((java.net.InetSocketAddress) a).getAddress().getHostAddress()
				: null;
	}

	private static boolean isEagler(PendingConnection c) {
		for (Class<?> k = c.getClass(); k != null; k = k.getSuperclass()) {
			if (k.getName().endsWith(".EaglerInitialHandler")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The last word on protected names: an Eaglercraft player must have just
	 * given the right password; a Java player must be Mojang-verified (online
	 * mode). Anyone else typing a staff name is refused before they reach a
	 * server, so they never get that name's UUID, rank or website link.
	 */
	@EventHandler(priority = net.md_5.bungee.event.EventPriority.LOWEST)
	@SuppressWarnings("deprecation")
	public void onLogin(LoginEvent e) {
		PendingConnection c = e.getConnection();
		String name = c.getName();
		if (!staffLockOn() || usingNLogin() || accountsOn() || e.isCancelled() || !staffLock.isProtected(name)) {
			return;
		}
		StaffLock.Entry entry = staffLock.get(name);
		String why = null;
		if (!entry.hasPassword()) {
			why = "This staff name is locked until the server owner sets its password.";
		} else if (isEagler(c)) {
			if (!staffLock.consumeVerified(name, address(c))) {
				why = eaglerAuth ? "This is a staff name. Join again and enter its password."
						: "Staff password login isn't set up on this server yet.";
			}
		} else if (!c.isOnlineMode()) {
			why = "This is a staff name. Join with PlebClient (Eaglercraft) and enter its password.";
		}
		if (why != null) {
			e.setCancelled(true);
			e.setCancelReason(TextComponent.fromLegacyText("\u00a7c" + why));
			getLogger().warning("Staff lock: refused " + name + " from " + address(c) + ": " + why);
			return;
		}
		staffVerified.add(c.getUniqueId() != null ? c.getUniqueId() : StaffLock.offlineUuid(entry.name));
	}

	// ==================== PlebMc accounts (the panel) ====================

	private void configureAccounts() {
		Cfg c = core.config;
		panelLink = new PanelLink(PanelHttp.REAL, c.str("accounts.url", ""), c.str("accounts.server-key", ""));
		boolean was = signin.enabled();
		signin.configure(c, panelLink);
		if (signin.enabled() && !was) {
			getLogger().info("PlebMc accounts ON (panel " + panelLink.url + "): coins, shop and admin menu need"
					+ " the client's Sign in first");
			for (ProxiedPlayer p : getProxy().getPlayers()) {
				signin.joined(p.getUniqueId(), p.getName());
			}
		}
	}

	@EventHandler
	public void onPostLogin(PostLoginEvent e) {
		signin.joined(e.getPlayer().getUniqueId(), e.getPlayer().getName());
	}

	/** LuckPerms on the proxy, or null (found once, after every plugin enabled). */
	private Object luckPerms;
	private boolean luckPermsTried;

	private LuckPermsBridge luckPerms() {
		if (!luckPermsTried) {
			luckPermsTried = true;
			if (getProxy().getPluginManager().getPlugin("LuckPerms") != null) {
				luckPerms = LuckPermsBridge.find();
			}
			getLogger().info(luckPerms != null ? "LuckPerms found: ranks show on the panel and owners can change them there"
					: "LuckPerms isn't on this proxy: the panel's Ranks page stays empty");
		}
		return (LuckPermsBridge) luckPerms;
	}

	/** Ranks for the panel: every LuckPerms group, and each online player's rank. */
	@SuppressWarnings("unchecked")
	private void addRanks(Map<String, Object> snap) {
		LuckPermsBridge lp = luckPerms();
		if (lp == null) {
			Map<String, Object> r = new java.util.LinkedHashMap<>();
			r.put("ok", false);
			r.put("reason", "LuckPerms isn't installed on the BungeeCord proxy.");
			snap.put("ranks", r);
			return;
		}
		try {
			snap.put("ranks", lp.report());
			for (Map<String, Object> p : (List<Map<String, Object>>) snap.get("players")) {
				String rank = lp.rankOf(UUID.fromString(String.valueOf(p.get("uuid"))));
				if (rank != null) {
					p.put("rank", rank);
				}
			}
		} catch (Throwable t) {
			Map<String, Object> r = new java.util.LinkedHashMap<>();
			r.put("ok", false);
			r.put("reason", "LuckPerms answered with an error: " + t);
			snap.put("ranks", r);
		}
	}

	/** Every 20 s: send the panel everyone's coins and cosmetics (when something changed, or each minute for who's online). */
	private void syncPanel() {
		PanelLink link = panelLink;
		if (!accountsOn() || link == null) {
			return;
		}
		long v = core.store.version();
		long now = System.currentTimeMillis();
		if (v == syncedVersion && now - lastSync < 60_000) {
			return;
		}
		try {
			Map<String, Object> snap = core.snapshot();
			addRanks(snap);
			List<String> need = link.sync(snap);
			syncedVersion = v;
			lastSync = now;
			for (String h : need) {
				byte[] b = core.asset(h);
				if (b != null) {
					try {
						link.asset(h, b);
					} catch (IOException e) {
						getLogger().warning("PlebMc accounts: can't send a shop preview file to the panel (" + e.getMessage() + ")");
						break;
					}
				}
			}
		} catch (IOException e) {
			if (now - lastSync > 5 * 60_000) {
				getLogger().warning("PlebMc accounts: can't send coins to the panel (" + e.getMessage() + ")");
				lastSync = now;
			}
		}
	}

	/** Every 3 s: apply changes admins made on the panel (coins, cosmetics, kicks, sign-outs). */
	private void pollPanel() {
		PanelLink link = panelLink;
		if (!accountsOn() || link == null || !polling.compareAndSet(false, true)) {
			return;
		}
		try {
			List<Map<?, ?>> cmds = link.commands();
			if (cmds.isEmpty()) {
				return;
			}
			List<Map<String, Object>> results = new ArrayList<>();
			for (Map<?, ?> c : cmds) {
				Map<String, Object> r = new java.util.LinkedHashMap<>();
				r.put("id", c.get("id") instanceof Number ? ((Number) c.get("id")).intValue() : -1);
				String out;
				boolean ok;
				try {
					out = applyPanelCommand(c);
					ok = !out.startsWith("!");
					if (!ok) {
						out = out.substring(1);
					}
				} catch (RuntimeException ex) {
					out = "Error: " + ex;
					ok = false;
				}
				r.put("ok", ok);
				r.put("result", out);
				results.add(r);
				getLogger().info("Panel: " + c.get("by") + " " + c.get("action") + " " + c.get("target") + " " + c.get("args") + " -> " + out);
			}
			link.ack(results);
			syncedVersion = -1; // send the new numbers back soon
		} catch (IOException e) {
			// panel down: try again next time
		} finally {
			polling.set(false);
		}
	}

	/** Runs one panel change; returns what happened ("!" first when it failed). */
	@SuppressWarnings("deprecation")
	private String applyPanelCommand(Map<?, ?> c) {
		String target = String.valueOf(c.get("target"));
		String action = String.valueOf(c.get("action"));
		List<String> args = new ArrayList<>();
		if (c.get("args") instanceof List) {
			for (Object o : (List<?>) c.get("args")) {
				args.add(o instanceof Number ? String.valueOf(((Number) o).longValue()) : String.valueOf(o));
			}
		}
		switch (action) {
		case "kick": {
			ProxiedPlayer p = getProxy().getPlayer(target);
			if (p == null) {
				return "!" + target + " isn't online";
			}
			p.disconnect(TextComponent.fromLegacyText("\u00a7c" + (args.isEmpty() ? "Kicked by an admin." : args.get(0))));
			return "kicked";
		}
		case "signout":
			return signin.signOutName(target) ? "signed out in game" : "not online";
		case "rename": {
			if (args.isEmpty() || !args.get(0).matches("[A-Za-z0-9_]{3,16}")) {
				return "!bad name";
			}
			String to = args.get(0);
			ProxiedPlayer on = getProxy().getPlayer(target);
			if (on != null && !on.getName().equals(to)) {
				on.disconnect(TextComponent.fromLegacyText("\u00a7eYour PlebMc name is now \u00a7f" + to + "\u00a7e.\n\u00a77Sign in again with the new name."));
			}
			UUID[] moved = core.rename(target, to);
			if (moved == null) {
				return target + " never joined, nothing to carry over";
			}
			String out = "coins and cosmetics moved to " + to;
			LuckPermsBridge lp = luckPerms();
			if (lp != null) {
				try {
					out += ", " + lp.move(moved[0], moved[1], to);
				} catch (Exception e) {
					out += " (ranks did not move: " + e.getMessage() + ")";
				}
			}
			return out;
		}
		case "coinsall": {
			long n;
			try {
				n = args.isEmpty() ? -1 : Long.parseLong(args.get(0));
			} catch (NumberFormatException e) {
				n = -1;
			}
			// the panel can say who gets this one gift: "client" (PlebMc client only) or "all" (everyone online)
			String who = args.size() > 1 ? String.valueOf(args.get(1)) : "";
			Boolean clientOnly = "all".equals(who) ? Boolean.FALSE : "client".equals(who) ? Boolean.TRUE : null;
			return n <= 0 ? "!bad amount" : "gave " + n + " to " + core.giveOnline(n, clientOnly) + " players online";
		}
		case "rank": {
			LuckPermsBridge lp = luckPerms();
			if (lp == null) {
				return "!LuckPerms isn't on the proxy";
			}
			try {
				return lp.apply(target, args);
			} catch (Exception e) {
				return "!LuckPerms: " + e.getMessage();
			}
		}
		case "buy":
			return args.isEmpty() ? "!no item" : core.webBuy(target, args.get(0));
		case "broadcast":
			return args.isEmpty() ? "!empty message" : "sent to " + core.announce(args.get(0)) + " players";
		case "additem": {
			if (args.size() < 2) {
				return "!bad upload";
			}
			Map<?, ?> item;
			byte[] file;
			try {
				item = gson.fromJson(args.get(0), Map.class);
				file = java.util.Base64.getDecoder().decode(args.get(1));
			} catch (RuntimeException e) {
				return "!bad upload: " + e.getMessage();
			}
			return core.publishItem(item, file);
		}
		default:
			break;
		}
		String[] a = PanelLink.plebshopArgs(target, action, args);
		if (a == null) {
			return "!unknown change " + action;
		}
		StringBuilder out = new StringBuilder();
		boolean[] failed = { false };
		core.command(new ShopCore.Sender() {
			@Override
			public void reply(String line) {
				String plain = line.replaceAll("(?i)\u00a7[0-9a-fk-or]", "");
				if (line.contains("\u00a7c")) {
					failed[0] = true;
				}
				out.append(out.length() == 0 ? "" : " ").append(plain.trim());
			}

			@Override
			public boolean hasPermission(String perm) {
				return true;
			}
		}, "pleb", a, this::reload);
		return (failed[0] ? "!" : "") + out;
	}

	// ==================== events ====================

	@EventHandler
	public void onSwitch(ServerSwitchEvent e) {
		ProxiedPlayer p = e.getPlayer();
		Server s = p.getServer();
		if (s == null) {
			return;
		}
		String now = s.getInfo().getName();
		String from = lastServer.put(p.getUniqueId(), now);
		boolean first = from == null;
		// give the client a moment to register plebmc:shop and say hello before the login popup
		getProxy().getScheduler().schedule(this, () -> {
			if (p.isConnected() && p.getServer() != null && now.equals(p.getServer().getInfo().getName())) {
				core.connected(p.getUniqueId(), from, first);
			}
		}, first ? 3 : 1, TimeUnit.SECONDS);
	}

	/** Chat (not commands) counts for "chat" quests; runs last so muted or cancelled messages don't. */
	@EventHandler(priority = net.md_5.bungee.event.EventPriority.HIGHEST)
	public void onChat(ChatEvent e) {
		if (e.isCancelled() || e.isCommand() || !(e.getSender() instanceof ProxiedPlayer)) {
			return;
		}
		ProxiedPlayer p = (ProxiedPlayer) e.getSender();
		if (p.getServer() != null) {
			core.chat(p.getUniqueId());
		}
	}

	@EventHandler
	public void onQuit(PlayerDisconnectEvent e) {
		String server = lastServer.remove(e.getPlayer().getUniqueId());
		staffVerified.remove(e.getPlayer().getUniqueId());
		adminUnlocked.remove(e.getPlayer().getUniqueId());
		lastAuth.remove(e.getPlayer().getUniqueId());
		signin.left(e.getPlayer().getUniqueId());
		core.quit(e.getPlayer().getUniqueId(), server);
	}

	@EventHandler
	public void onMessage(PluginMessageEvent e) {
		String tag = e.getTag();
		if (CHANNEL.equals(tag)) {
			e.setCancelled(true); // ours: never forward to the backend
			if (e.getSender() instanceof ProxiedPlayer && e.getData().length <= 4096) {
				core.fromClient(((ProxiedPlayer) e.getSender()).getUniqueId(),
						new String(e.getData(), StandardCharsets.UTF_8));
			}
			return;
		}
		if (("BungeeCord".equals(tag) || "bungeecord:main".equals(tag)) && e.getSender() instanceof Server) {
			try {
				DataInputStream in = new DataInputStream(new ByteArrayInputStream(e.getData()));
				if (!BRIDGE.equals(in.readUTF())) {
					return;
				}
				e.setCancelled(true);
				bridge(((Server) e.getSender()).getInfo().getName(), in.readUTF());
			} catch (IOException | RuntimeException ex) {
				// not one of ours
			}
		}
	}

	/** {"player":uuid,"type":"kill","amount":1} or {"player":uuid,"world":"arena1"} from a backend. */
	private void bridge(String server, String json) {
		Map<?, ?> m = gson.fromJson(json, Map.class);
		if (m == null || !(m.get("player") instanceof String)) {
			return;
		}
		UUID id = UUID.fromString((String) m.get("player"));
		if (m.get("world") instanceof String) {
			core.world(id, (String) m.get("world"));
		}
		if (m.get("type") instanceof String) {
			long amount = m.get("amount") instanceof Number ? ((Number) m.get("amount")).longValue() : 1;
			core.progress(id, server, (String) m.get("type"), amount);
		}
	}

	// ==================== proxy access for the core ====================

	private final class BungeeNet implements ShopCore.Net {
		@Override
		public void send(UUID player, String json) {
			ProxiedPlayer p = ProxyServer.getInstance().getPlayer(player);
			if (p != null) {
				p.sendData(CHANNEL, json.getBytes(StandardCharsets.UTF_8));
			}
		}

		@Override
		@SuppressWarnings("deprecation")
		public void chat(UUID player, String message) {
			ProxiedPlayer p = ProxyServer.getInstance().getPlayer(player);
			if (p != null) {
				p.sendMessage(TextComponent.fromLegacyText(message));
			}
		}

		@Override
		public List<UUID> online() {
			List<UUID> out = new ArrayList<>();
			for (ProxiedPlayer p : ProxyServer.getInstance().getPlayers()) {
				out.add(p.getUniqueId());
			}
			return out;
		}

		@Override
		public String server(UUID player) {
			ProxiedPlayer p = ProxyServer.getInstance().getPlayer(player);
			return p == null || p.getServer() == null ? null : p.getServer().getInfo().getName();
		}

		@Override
		public String name(UUID player) {
			ProxiedPlayer p = ProxyServer.getInstance().getPlayer(player);
			return p == null ? null : p.getName();
		}

		@Override
		public boolean hasPermission(UUID player, String permission) {
			ProxiedPlayer p = ProxyServer.getInstance().getPlayer(player);
			return p != null && p.hasPermission(permission);
		}

		@Override
		public boolean staffVerified(UUID player) {
			if (!staffLockOn()) {
				return true;
			}
			// with nLogin, its /login password is the proof; without it, the staff lock password
			boolean loggedIn = usingNLogin() || accountsOn() ? BungeeShop.this.authenticated(player) : staffVerified.contains(player);
			// and on top of that the separate admin password (/adminlogin), which doesn't depend on
			// nLogin's sessions or on players' IPs (Eaglercraft players can all share one IP)
			return loggedIn && (!adminPasswordRequired() || adminUnlocked.contains(player));
		}

		@Override
		public boolean authenticated(UUID player) {
			return BungeeShop.this.authenticated(player);
		}

		@Override
		public void protectStaff(UUID player) {
			ProxiedPlayer p = ProxyServer.getInstance().getPlayer(player);
			if (p != null && staffLockOn() && !usingNLogin() && !accountsOn() && staffLock.protect(p.getName())) {
				getLogger().warning("Staff lock: " + p.getName() + " has staff powers but no password, so the name is now locked."
						+ " Set its password in this console: pleb staff set " + p.getName() + " <password>");
			}
		}

		@Override
		public long now() {
			return System.currentTimeMillis();
		}

		@Override
		public void async(Runnable r) {
			ProxyServer.getInstance().getScheduler().runAsync(BungeeShop.this, r);
		}

		@Override
		public List<String> servers() {
			return new ArrayList<>(ProxyServer.getInstance().getServers().keySet());
		}

		@Override
		public boolean connect(UUID player, String server) {
			ProxiedPlayer p = ProxyServer.getInstance().getPlayer(player);
			net.md_5.bungee.api.config.ServerInfo info = ProxyServer.getInstance().getServerInfo(server);
			if (p == null || info == null) {
				return false;
			}
			p.connect(info);
			return true;
		}

		@Override
		@SuppressWarnings("deprecation")
		public boolean kick(UUID player, String reason) {
			ProxiedPlayer p = ProxyServer.getInstance().getPlayer(player);
			if (p == null) {
				return false;
			}
			p.disconnect(TextComponent.fromLegacyText(reason));
			return true;
		}
	}

	private final class AdminCommand extends Command implements TabExecutor {
		AdminCommand() {
			super("pleb", null, "plebshop", "pmshop");
		}

		@Override
		@SuppressWarnings("deprecation")
		public void execute(CommandSender sender, String[] args) {
			if (args.length > 0 && "adminpass".equalsIgnoreCase(args[0])) {
				adminPassCommand(sender, args);
				return;
			}
			if (args.length > 0 && "staff".equalsIgnoreCase(args[0])) {
				staffCommand(sender, args);
				return;
			}
			if (args.length > 0 && "votifier".equalsIgnoreCase(args[0])) {
				votifierCommand(sender);
				return;
			}
			if (sender instanceof ProxiedPlayer && sender.hasPermission(ShopCore.ADMIN)
					&& !bnet.staffVerified(((ProxiedPlayer) sender).getUniqueId())) {
				// BungeeCord permissions go by name, which a cracked player can just type
				bnet.protectStaff(((ProxiedPlayer) sender).getUniqueId());
				sender.sendMessage(TextComponent.fromLegacyText(usingNLogin() || accountsOn() ? "\u00a7c" + loginFirst() + ", then /adminlogin <admin password>."
						: "\u00a7cYour name has no staff password yet, so /pleb is off for it."
								+ " Set one in the proxy console: pleb staff set " + sender.getName() + " <password>, then rejoin."));
				return;
			}
			core.command(new ShopCore.Sender() {
				@Override
				public void reply(String line) {
					sender.sendMessage(TextComponent.fromLegacyText(line));
				}

				@Override
				public boolean hasPermission(String perm) {
					return sender.hasPermission(perm);
				}
			}, "pleb", args, BungeeShop.this::reload);
		}

		/** /pleb adminpass ...: console only. The second password /adminlogin checks. */
		@SuppressWarnings("deprecation")
		private void adminPassCommand(CommandSender sender, String[] a) {
			java.util.function.Consumer<String> reply = line -> sender.sendMessage(TextComponent.fromLegacyText(line));
			if (sender instanceof ProxiedPlayer) {
				reply.accept("\u00a7cAdmin passwords can only be set from the proxy console.");
				return;
			}
			String sub = a.length < 2 ? "help" : a[1].toLowerCase(java.util.Locale.ROOT);
			ProxiedPlayer on = a.length >= 3 ? getProxy().getPlayer(a[2]) : null;
			if ("set".equals(sub) && a.length == 4) {
				String err = adminPasswords.setPassword(a[2], a[3]);
				reply.accept(err != null ? "\u00a7c" + err
						: "\u00a7a" + a[2] + " can use the admin menu after /login and /adminlogin <password>."
								+ " Use a different password from the /login one.");
			} else if ("remove".equals(sub) && a.length == 3) {
				reply.accept(adminPasswords.remove(a[2]) ? a[2] + " can no longer use the admin menu." : a[2] + " had no admin password.");
			} else if ("list".equals(sub)) {
				List<StaffLock.Entry> all = adminPasswords.all();
				reply.accept("Names with an admin password (" + all.size() + "):");
				for (StaffLock.Entry e : all) {
					reply.accept(" - " + e.name);
				}
				return;
			} else {
				reply.accept("pleb adminpass set <name> <password>  - allow a name to unlock the admin menu (/adminlogin)");
				reply.accept("pleb adminpass remove <name>");
				reply.accept("pleb adminpass list");
				reply.accept("The name also needs a PlebMc account with a role from admin-menu.roles (admin, owner), signed in.");
				return;
			}
			if (on != null) {
				adminUnlocked.remove(on.getUniqueId());
				core.authChanged(on.getUniqueId());
			}
		}

		/** /pleb staff ...: console only, since in-game the name proves nothing. */
		@SuppressWarnings("deprecation")
		private void staffCommand(CommandSender sender, String[] a) {
			java.util.function.Consumer<String> reply = line -> sender.sendMessage(TextComponent.fromLegacyText(line));
			if (sender instanceof ProxiedPlayer) {
				reply.accept("\u00a7cStaff passwords can only be changed from the proxy console.");
				return;
			}
			String sub = a.length < 2 ? "help" : a[1].toLowerCase(java.util.Locale.ROOT);
			switch (sub) {
			case "set":
				if (a.length != 4) {
					reply.accept("Usage: pleb staff set <name> <password>");
					return;
				}
				String err = staffLock.setPassword(a[2], a[3]);
				if (err != null) {
					reply.accept("\u00a7c" + err);
					return;
				}
				reply.accept("\u00a7a" + a[2] + " is protected. Join with PlebClient and type this password at the password screen."
						+ (eaglerAuth ? "" : " \u00a7eThe password screen is OFF: turn on EaglerXBungee's authservice.yml (see the PlebioAPI README) and restart."));
				kickUnverified(a[2]);
				return;
			case "add":
				if (a.length != 3) {
					reply.accept("Usage: pleb staff add <name>  (locks it until you set a password)");
					return;
				}
				reply.accept(staffLock.protect(a[2]) ? a[2] + " is locked. Set its password: pleb staff set " + a[2] + " <password>"
						: a[2] + " was already protected.");
				kickUnverified(a[2]);
				return;
			case "remove":
				if (a.length != 3) {
					reply.accept("Usage: pleb staff remove <name>");
					return;
				}
				reply.accept(staffLock.remove(a[2]) ? a[2] + " is no longer protected: anyone can join with that name again."
						: a[2] + " wasn't protected.");
				return;
			case "list": {
				List<StaffLock.Entry> all = staffLock.all();
				reply.accept("Protected names (" + all.size() + "):" + (eaglerAuth ? "" : " \u00a7e[password screen OFF]"));
				for (StaffLock.Entry e : all) {
					reply.accept(" - " + e.name + (e.hasPassword() ? "" : " \u00a7c(locked: no password set)"));
				}
				return;
			}
			default:
				reply.accept("pleb staff set <name> <password>  - protect a name with a password (or change it)");
				reply.accept("pleb staff add <name>  - lock a name until it has a password");
				reply.accept("pleb staff remove <name>  - stop protecting a name");
				reply.accept("pleb staff list");
			}
		}

		/** Someone on a newly protected name who didn't give its password is kicked. */
		@SuppressWarnings("deprecation")
		private void kickUnverified(String name) {
			ProxiedPlayer p = getProxy().getPlayer(name);
			if (p != null && !staffVerified.contains(p.getUniqueId())) {
				p.disconnect(TextComponent.fromLegacyText("\u00a7cThis name is now a protected staff name. Rejoin and enter its password."));
			}
		}

		@Override
		public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
			return sender.hasPermission(ShopCore.ADMIN) ? core.tabComplete(args) : new ArrayList<String>();
		}
	}
}
