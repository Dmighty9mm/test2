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
	private final BungeeNet bnet = new BungeeNet();
	/** Players who gave their staff password at this login (the only ones who get admin powers). */
	private final java.util.Set<UUID> staffVerified = ConcurrentHashMap.newKeySet();
	/** EaglerXBungee is installed and its auth hooks are on (staff can get the password screen). */
	private boolean eaglerAuth;

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
			}
			// the sample cloak textures and hat model (never overwrites yours)
			for (String c : new String[] { "cloaks/plebmc.png", "cloaks/storm.png", "hats/king_crown.bbmodel" }) {
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
			core.reloader = this::reload;
		} catch (IOException e) {
			getLogger().severe("Could not load config.yml: " + e);
			return;
		}
		getProxy().registerChannel(CHANNEL);
		getProxy().getPluginManager().registerListener(this, this);
		getProxy().getPluginManager().registerCommand(this, new AdminCommand());
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
						+ " In this console run: plebshop staff set <name> <password>");
			}
		}
		getProxy().getScheduler().schedule(this, core::minute, 1, 1, TimeUnit.MINUTES);
		getProxy().getScheduler().schedule(this, core::saveIfDirty, 30, 30, TimeUnit.SECONDS);
		getLogger().info(core.catalog.all().size() + " cosmetics, " + core.quests.allQuests().size() + " quests in "
				+ core.quests.categories.keySet() + (core.website.configured() ? ", website login via " + core.website.url
						: ", website login off (set website.url and website.api-key)"));
	}

	@Override
	public void onDisable() {
		if (core != null) {
			core.save();
		}
	}

	private void reload() {
		try {
			core.load(Cfg.load(new File(getDataFolder(), "config.yml")));
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

	/** Logged in with nLogin this session; true when there's no nLogin; false (locked) if nLogin can't be asked. */
	private boolean authenticated(UUID player) {
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
		if (!staffLockOn() || usingNLogin() || e.isCancelled() || !staffLock.isProtected(name)) {
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
		lastAuth.remove(e.getPlayer().getUniqueId());
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
			return usingNLogin() ? BungeeShop.this.authenticated(player) : staffVerified.contains(player);
		}

		@Override
		public boolean authenticated(UUID player) {
			return BungeeShop.this.authenticated(player);
		}

		@Override
		public void protectStaff(UUID player) {
			ProxiedPlayer p = ProxyServer.getInstance().getPlayer(player);
			if (p != null && staffLockOn() && !usingNLogin() && staffLock.protect(p.getName())) {
				getLogger().warning("Staff lock: " + p.getName() + " has staff powers but no password, so the name is now locked."
						+ " Set its password in this console: plebshop staff set " + p.getName() + " <password>");
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
			super("plebshop", null, "pmshop");
		}

		@Override
		@SuppressWarnings("deprecation")
		public void execute(CommandSender sender, String[] args) {
			if (args.length > 0 && "staff".equalsIgnoreCase(args[0])) {
				staffCommand(sender, args);
				return;
			}
			if (sender instanceof ProxiedPlayer && sender.hasPermission(ShopCore.ADMIN)
					&& !bnet.staffVerified(((ProxiedPlayer) sender).getUniqueId())) {
				// BungeeCord permissions go by name, which a cracked player can just type
				bnet.protectStaff(((ProxiedPlayer) sender).getUniqueId());
				sender.sendMessage(TextComponent.fromLegacyText(usingNLogin() ? "\u00a7cLog in first (/login <password>)."
						: "\u00a7cYour name has no staff password yet, so /plebshop is off for it."
								+ " Set one in the proxy console: plebshop staff set " + sender.getName() + " <password>, then rejoin."));
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
			}, "plebshop", args, BungeeShop.this::reload);
		}

		/** /plebshop staff ...: console only, since in-game the name proves nothing. */
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
					reply.accept("Usage: plebshop staff set <name> <password>");
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
					reply.accept("Usage: plebshop staff add <name>  (locks it until you set a password)");
					return;
				}
				reply.accept(staffLock.protect(a[2]) ? a[2] + " is locked. Set its password: plebshop staff set " + a[2] + " <password>"
						: a[2] + " was already protected.");
				kickUnverified(a[2]);
				return;
			case "remove":
				if (a.length != 3) {
					reply.accept("Usage: plebshop staff remove <name>");
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
				reply.accept("plebshop staff set <name> <password>  - protect a name with a password (or change it)");
				reply.accept("plebshop staff add <name>  - lock a name until it has a password");
				reply.accept("plebshop staff remove <name>  - stop protecting a name");
				reply.accept("plebshop staff list");
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
