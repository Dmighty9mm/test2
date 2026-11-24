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
import net.md_5.bungee.api.event.ChatEvent;
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
	/** The server each player is on, as we last saw it (for the switch "from"). */
	private final Map<UUID, String> lastServer = new ConcurrentHashMap<>();
	private final Gson gson = new Gson();

	@Override
	public void onEnable() {
		File cfg = new File(getDataFolder(), "config.yml");
		try {
			if (!cfg.exists()) {
				getDataFolder().mkdirs();
				try (InputStream in = getResourceAsStream("config.yml")) {
					Files.copy(in, cfg.toPath());
				}
			}
			core = new ShopCore(new BungeeNet(), new Store(new File(getDataFolder(), "players.yml"), getLogger()),
					Cfg.load(cfg), getLogger());
		} catch (IOException e) {
			getLogger().severe("Could not load config.yml: " + e);
			return;
		}
		getProxy().registerChannel(CHANNEL);
		getProxy().getPluginManager().registerListener(this, this);
		getProxy().getPluginManager().registerCommand(this, new AdminCommand());
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
		public long now() {
			return System.currentTimeMillis();
		}

		@Override
		public void async(Runnable r) {
			ProxyServer.getInstance().getScheduler().runAsync(BungeeShop.this, r);
		}
	}

	private final class AdminCommand extends Command implements TabExecutor {
		AdminCommand() {
			super("plebshop", null, "pmshop");
		}

		@Override
		@SuppressWarnings("deprecation")
		public void execute(CommandSender sender, String[] args) {
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

		@Override
		public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
			return sender.hasPermission(ShopCore.ADMIN) ? core.tabComplete(args) : new ArrayList<String>();
		}
	}
}
