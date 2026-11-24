package net.plebmc.shop;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Server side of the PlebMc client's cosmetics shop (hats and trails bought
 * with coins). Speaks the JSON protocol on the {@code plebmc:shop} plugin
 * channel described in docs/PLEBMC.md ("Cosmetics shop protocol"). The
 * server owns the coins, what each player owns and what they wear; the
 * client only shows it and asks.
 */
public final class PlebMcShop extends JavaPlugin implements Listener, PluginMessageListener {

	static final String CHANNEL = "plebmc:shop";
	private static final String ADMIN = "plebshop.admin";
	private static final long ACTION_COOLDOWN_MS = 300;

	private final Gson gson = new GsonBuilder().serializeNulls().create();
	private Store store;
	private Catalog catalog;
	private Coins coins;
	private Set<String> hiddenWorlds = new HashSet<>();
	private String prefix = "";
	private final Map<UUID, Long> lastAction = new HashMap<>();

	@Override
	public void onEnable() {
		saveDefaultConfig();
		store = new Store(new File(getDataFolder(), "players.yml"), getLogger());
		loadSettings();
		Bukkit.getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
		Bukkit.getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
		Bukkit.getPluginManager().registerEvents(this, this);
		Bukkit.getScheduler().runTaskTimer(this, store::saveIfDirty, 20L * 30, 20L * 30);
		getLogger().info(catalog.all().size() + " cosmetics, coins from " + coins.mode());
	}

	@Override
	public void onDisable() {
		if (store != null) {
			store.save();
		}
		Bukkit.getMessenger().unregisterIncomingPluginChannel(this);
		Bukkit.getMessenger().unregisterOutgoingPluginChannel(this);
	}

	private void loadSettings() {
		reloadConfig();
		catalog = new Catalog(getConfig().getConfigurationSection("items"));
		for (String w : catalog.warnings()) {
			getLogger().warning(w);
		}
		coins = new Coins(getConfig().getConfigurationSection("coins"), store, getLogger());
		hiddenWorlds = new HashSet<>();
		for (String w : getConfig().getStringList("hidden-in-worlds")) {
			hiddenWorlds.add(w.toLowerCase(Locale.ROOT));
		}
		prefix = color(getConfig().getString("messages.prefix", "&8[&b&lPlebMc&8] &7"));
	}

	private String msg(String key, String def) {
		return getConfig().getString("messages." + key, def);
	}

	private static String color(String s) {
		return ChatColor.translateAlternateColorCodes('&', s);
	}

	/** Strips color codes so the client shop shows plain text. */
	private static String plain(String s) {
		return s == null ? "" : s.replaceAll("(?i)[&§][0-9a-fk-or]", "");
	}

	// ==================== events ====================

	@EventHandler(priority = EventPriority.MONITOR)
	public void onRegister(PlayerRegisterChannelEvent e) {
		if (CHANNEL.equals(e.getChannel())) {
			Player p = e.getPlayer();
			store.get(p.getUniqueId()).name = p.getName();
			sendState(p, true);
			sendEveryone(p);
			broadcastSlots(p);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent e) {
		Player p = e.getPlayer();
		Store.Data d = store.peek(p.getUniqueId());
		if (d != null && !p.getName().equals(d.name)) {
			d.name = p.getName();
			store.changed();
		}
		// clients that registered before the join finished: catch them up a moment later
		Bukkit.getScheduler().runTaskLater(this, () -> {
			if (p.isOnline() && listens(p)) {
				sendEveryone(p);
				broadcastSlots(p);
			}
		}, 20L);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent e) {
		Player p = e.getPlayer();
		lastAction.remove(p.getUniqueId());
		Map<String, Object> players = new LinkedHashMap<>();
		players.put(p.getUniqueId().toString(), null);
		broadcast(playersMessage(players, false), p);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWorldChange(PlayerChangedWorldEvent e) {
		if (!hiddenWorlds.isEmpty()) {
			broadcastSlots(e.getPlayer());
		}
	}

	// ==================== messages from the client ====================

	@Override
	public void onPluginMessageReceived(String channel, Player p, byte[] data) {
		if (!CHANNEL.equals(channel) || data.length > 4096) {
			return;
		}
		Map<?, ?> m;
		try {
			m = gson.fromJson(new String(data, StandardCharsets.UTF_8), Map.class);
		} catch (RuntimeException ex) {
			return;
		}
		if (m == null) {
			return;
		}
		String type = String.valueOf(m.get("type"));
		switch (type) {
		case "open":
			store.get(p.getUniqueId()).name = p.getName();
			sendState(p, true);
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
		default:
			break;
		}
	}

	private static String str(Object o) {
		return o instanceof String ? (String) o : null;
	}

	private boolean cooldown(Player p) {
		long now = System.currentTimeMillis();
		Long last = lastAction.get(p.getUniqueId());
		if (last != null && now - last < ACTION_COOLDOWN_MS) {
			return false;
		}
		lastAction.put(p.getUniqueId(), now);
		return true;
	}

	private void buy(Player p, String id) {
		Catalog.Item it = catalog.get(id);
		Store.Data d = store.get(p.getUniqueId());
		if (it == null) {
			result(p, false, msg("unknown-item", "That item isn't in the shop."), -1);
			return;
		}
		if (d.owned.contains(it.id)) {
			result(p, true, msg("already-owned", "You already own %item%.").replace("%item%", plain(it.name)), -1);
			sendState(p, false);
			return;
		}
		if (it.notForSale()) {
			result(p, false, msg("not-for-sale", "%item% can't be bought.").replace("%item%", plain(it.name)), -1);
			return;
		}
		if (it.permission != null && !it.permission.isEmpty() && !p.hasPermission(it.permission)) {
			result(p, false, msg("no-permission", "You can't buy %item%.").replace("%item%", plain(it.name)), -1);
			return;
		}
		long balance = coins.balance(p);
		if (balance < 0) {
			result(p, false, msg("coins-unavailable", "The shop can't read your coins right now."), -1);
			return;
		}
		if (balance < it.price) {
			result(p, false, msg("not-enough", "You need %need% more coins.")
					.replace("%need%", fmt(it.price - balance)), balance);
			return;
		}
		long after = coins.take(p, it.price);
		if (after < 0) {
			result(p, false, msg("purchase-failed", "The purchase didn't go through. No coins were taken."),
					coins.balance(p));
			return;
		}
		d.owned.add(it.id);
		d.name = p.getName();
		boolean autoEquip = getConfig().getBoolean("equip-on-buy", true);
		if (autoEquip) {
			d.equipped.put(it.category, it.id);
		}
		store.changed();
		store.save(); // coins are gone: keep the item even if the server crashes
		getLogger().info(p.getName() + " bought " + it.id + " for " + it.price + " coins");
		result(p, true, msg("bought", "Bought %item%!").replace("%item%", plain(it.name)), after);
		sendState(p, false, after);
		if (autoEquip) {
			broadcastSlots(p);
		}
	}

	private void equip(Player p, String category, String id) {
		if (!Catalog.HAT.equals(category) && !Catalog.TRAIL.equals(category)) {
			return;
		}
		Store.Data d = store.get(p.getUniqueId());
		if (id == null) {
			if (d.equipped.remove(category) != null) {
				store.changed();
			}
		} else {
			Catalog.Item it = catalog.get(id);
			if (it == null || !it.category.equals(category)) {
				result(p, false, msg("unknown-item", "That item isn't in the shop."), -1);
				return;
			}
			if (!d.owned.contains(it.id)) {
				result(p, false, msg("not-owned", "You don't own %item% yet.").replace("%item%", plain(it.name)), -1);
				return;
			}
			d.equipped.put(category, it.id);
			store.changed();
		}
		sendState(p, false);
		broadcastSlots(p);
	}

	// ==================== messages to clients ====================

	private boolean listens(Player p) {
		try {
			return p.getListeningPluginChannels().contains(CHANNEL);
		} catch (RuntimeException e) {
			return true;
		}
	}

	private void send(Player p, Map<String, Object> m) {
		if (!listens(p)) {
			return;
		}
		p.sendPluginMessage(this, CHANNEL, gson.toJson(m).getBytes(StandardCharsets.UTF_8));
	}

	private void sendState(Player p, boolean withCatalog) {
		sendState(p, withCatalog, Long.MIN_VALUE);
	}

	private void sendState(Player p, boolean withCatalog, long knownBalance) {
		Store.Data d = store.get(p.getUniqueId());
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("type", "state");
		long bal = knownBalance != Long.MIN_VALUE ? knownBalance : coins.balance(p);
		if (bal >= 0) {
			m.put("coins", bal);
		}
		List<String> owned = new ArrayList<>();
		for (String id : d.owned) {
			if (catalog.get(id) != null) {
				owned.add(id);
			}
		}
		m.put("owned", owned);
		m.put("equipped", slots(d));
		if (withCatalog) {
			m.put("catalog", catalog.toJson());
		}
		send(p, m);
	}

	private void result(Player p, boolean ok, String message, long balance) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("type", "result");
		m.put("ok", ok);
		m.put("message", message);
		if (balance >= 0) {
			m.put("coins", balance);
		}
		send(p, m);
	}

	/** What a player wears, limited to items that still exist. */
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

	/** What others should see on this player (nothing in a hidden world). */
	private Object visibleSlots(Player p) {
		if (!hiddenWorlds.isEmpty() && p.getWorld() != null
				&& hiddenWorlds.contains(p.getWorld().getName().toLowerCase(Locale.ROOT))) {
			return null;
		}
		Map<String, Object> s = slots(store.peek(p.getUniqueId()));
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

	/** Tells everyone (including the player) what this player wears now. */
	private void broadcastSlots(Player p) {
		Map<String, Object> players = new LinkedHashMap<>();
		players.put(p.getUniqueId().toString(), visibleSlots(p));
		broadcast(playersMessage(players, false), null);
	}

	/** Sends one player what everyone online wears. */
	private void sendEveryone(Player to) {
		Map<String, Object> players = new LinkedHashMap<>();
		for (Player o : Bukkit.getOnlinePlayers()) {
			Object s = visibleSlots(o);
			if (s != null) {
				players.put(o.getUniqueId().toString(), s);
			}
		}
		send(to, playersMessage(players, true));
	}

	private void broadcast(Map<String, Object> m, Player except) {
		Collection<? extends Player> online = Bukkit.getOnlinePlayers();
		byte[] data = null;
		for (Player o : online) {
			if (o == except || !listens(o)) {
				continue;
			}
			if (data == null) {
				data = gson.toJson(m).getBytes(StandardCharsets.UTF_8);
			}
			o.sendPluginMessage(this, CHANNEL, data);
		}
	}

	private void refreshAll() {
		for (Player o : Bukkit.getOnlinePlayers()) {
			if (listens(o)) {
				sendState(o, true);
				sendEveryone(o);
			}
		}
	}

	// ==================== /plebshop ====================

	@Override
	public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
		if (!s.hasPermission(ADMIN)) {
			s.sendMessage(prefix + color("&cYou don't have permission."));
			return true;
		}
		String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
		switch (sub) {
		case "reload":
			loadSettings();
			refreshAll();
			s.sendMessage(prefix + "Reloaded: " + catalog.all().size() + " cosmetics, coins from " + coins.mode()
					+ (catalog.warnings().isEmpty() ? "" : color(" &c(" + catalog.warnings().size()
							+ " problems, see console)")));
			return true;
		case "list":
			for (Catalog.Item it : catalog.all()) {
				s.sendMessage(color("&7- &f" + it.id + " &8(" + it.category + ", " + it.render + ") &e"
						+ (it.notForSale() ? "not for sale" : fmt(it.price))));
			}
			return true;
		case "give":
		case "remove":
			if (a.length < 3) {
				return usage(s, label + " " + sub + " <player> <item>");
			}
			return giveOrRemove(s, sub.equals("give"), a[1], a[2]);
		case "equip":
			if (a.length < 4) {
				return usage(s, label + " equip <player> <hat|trail> <item|none>");
			}
			return adminEquip(s, a[1], a[2].toLowerCase(Locale.ROOT), a[3]);
		case "info":
			if (a.length < 2) {
				return usage(s, label + " info <player>");
			}
			return info(s, a[1]);
		case "balance":
			if (a.length < 2) {
				return usage(s, label + " balance <player>");
			}
			Player bp = Bukkit.getPlayerExact(a[1]);
			if (bp == null) {
				s.sendMessage(prefix + color("&cThat player isn't online."));
				return true;
			}
			long b = coins.balance(bp);
			s.sendMessage(prefix + bp.getName() + " has " + (b < 0 ? color("&cunreadable coins (check config)")
					: color("&e" + fmt(b) + " &7coins")) + " (" + coins.mode() + ")");
			return true;
		case "coins":
			return internalCoins(s, label, a);
		default:
			s.sendMessage(prefix + color("&fPlebMc cosmetics shop"));
			for (String line : Arrays.asList("reload", "list", "give <player> <item>", "remove <player> <item>",
					"equip <player> <hat|trail> <item|none>", "info <player>", "balance <player>",
					"coins <give|take|set> <player> <amount> &8(internal mode only)")) {
				s.sendMessage(color("&7/" + label + " " + line));
			}
			return true;
		}
	}

	private boolean usage(CommandSender s, String u) {
		s.sendMessage(prefix + color("&cUsage: /" + u));
		return true;
	}

	@SuppressWarnings("deprecation")
	private UUID resolve(String name) {
		Player p = Bukkit.getPlayerExact(name);
		if (p != null) {
			return p.getUniqueId();
		}
		for (UUID id : store.ids()) {
			Store.Data d = store.peek(id);
			if (d != null && name.equalsIgnoreCase(d.name)) {
				return id;
			}
		}
		OfflinePlayer op = Bukkit.getOfflinePlayer(name);
		return op == null ? null : op.getUniqueId();
	}

	private boolean giveOrRemove(CommandSender s, boolean give, String name, String id) {
		Catalog.Item it = catalog.get(id);
		if (it == null) {
			s.sendMessage(prefix + color("&cNo item '" + id + "'. See /plebshop list"));
			return true;
		}
		UUID uid = resolve(name);
		if (uid == null) {
			s.sendMessage(prefix + color("&cUnknown player."));
			return true;
		}
		Store.Data d = store.get(uid);
		if (d.name.isEmpty()) {
			d.name = name;
		}
		if (give) {
			d.owned.add(it.id);
		} else {
			d.owned.remove(it.id);
			if (it.id.equals(d.equipped.get(it.category))) {
				d.equipped.remove(it.category);
			}
		}
		store.changed();
		store.save();
		Player online = Bukkit.getPlayer(uid);
		if (online != null) {
			sendState(online, false);
			broadcastSlots(online);
		}
		s.sendMessage(prefix + (give ? "Gave " : "Removed ") + it.id + (give ? " to " : " from ") + name);
		return true;
	}

	private boolean adminEquip(CommandSender s, String name, String cat, String id) {
		if (!Catalog.HAT.equals(cat) && !Catalog.TRAIL.equals(cat)) {
			s.sendMessage(prefix + color("&cCategory must be hat or trail."));
			return true;
		}
		UUID uid = resolve(name);
		if (uid == null) {
			s.sendMessage(prefix + color("&cUnknown player."));
			return true;
		}
		Store.Data d = store.get(uid);
		if (id.equalsIgnoreCase("none")) {
			d.equipped.remove(cat);
		} else {
			Catalog.Item it = catalog.get(id);
			if (it == null || !it.category.equals(cat)) {
				s.sendMessage(prefix + color("&cNo " + cat + " called '" + id + "'."));
				return true;
			}
			d.owned.add(it.id);
			d.equipped.put(cat, it.id);
		}
		store.changed();
		Player online = Bukkit.getPlayer(uid);
		if (online != null) {
			sendState(online, false);
			broadcastSlots(online);
		}
		s.sendMessage(prefix + "Done.");
		return true;
	}

	private boolean info(CommandSender s, String name) {
		UUID uid = resolve(name);
		Store.Data d = uid == null ? null : store.peek(uid);
		if (d == null) {
			s.sendMessage(prefix + name + " has no cosmetics.");
			return true;
		}
		s.sendMessage(prefix + name + color(" &8(" + uid + ")"));
		s.sendMessage(color("&7Owned: &f" + (d.owned.isEmpty() ? "nothing" : String.join(", ", d.owned))));
		s.sendMessage(color("&7Hat: &f" + d.equipped.getOrDefault(Catalog.HAT, "none") + "  &7Trail: &f"
				+ d.equipped.getOrDefault(Catalog.TRAIL, "none")));
		if ("internal".equals(coins.mode())) {
			s.sendMessage(color("&7Coins: &e" + fmt(d.coins)));
		}
		return true;
	}

	private boolean internalCoins(CommandSender s, String label, String[] a) {
		if (!"internal".equals(coins.mode())) {
			s.sendMessage(prefix + "Coins come from " + coins.mode()
					+ " mode, so change them there (for Battle Royale X: /brx coins).");
			return true;
		}
		if (a.length < 4) {
			return usage(s, label + " coins <give|take|set> <player> <amount>");
		}
		UUID uid = resolve(a[2]);
		long n;
		try {
			n = Long.parseLong(a[3]);
		} catch (NumberFormatException e) {
			n = -1;
		}
		if (uid == null || n < 0) {
			return usage(s, label + " coins <give|take|set> <player> <amount>");
		}
		long cur = store.coins(uid);
		switch (a[1].toLowerCase(Locale.ROOT)) {
		case "give":
			store.setCoins(uid, cur + n);
			break;
		case "take":
			store.setCoins(uid, cur - n);
			break;
		case "set":
			store.setCoins(uid, n);
			break;
		default:
			return usage(s, label + " coins <give|take|set> <player> <amount>");
		}
		Player online = Bukkit.getPlayer(uid);
		if (online != null) {
			sendState(online, false);
		}
		s.sendMessage(prefix + a[2] + " now has " + fmt(store.coins(uid)) + " coins.");
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
		List<String> out = new ArrayList<>();
		if (!s.hasPermission(ADMIN)) {
			return out;
		}
		List<String> options;
		if (a.length == 1) {
			options = Arrays.asList("reload", "list", "give", "remove", "equip", "info", "balance", "coins");
		} else if (a.length == 2 && a[0].equalsIgnoreCase("coins")) {
			options = Arrays.asList("give", "take", "set");
		} else if (a.length == 2 || (a.length == 3 && a[0].equalsIgnoreCase("coins"))) {
			options = new ArrayList<>();
			for (Player p : Bukkit.getOnlinePlayers()) {
				options.add(p.getName());
			}
		} else if (a.length == 3 && a[0].equalsIgnoreCase("equip")) {
			options = Arrays.asList("hat", "trail");
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
			return out;
		}
		String start = a[a.length - 1].toLowerCase(Locale.ROOT);
		for (String o : options) {
			if (o.toLowerCase(Locale.ROOT).startsWith(start)) {
				out.add(o);
			}
		}
		return out;
	}

	static String fmt(long n) {
		return String.format(Locale.ROOT, "%,d", n);
	}
}
