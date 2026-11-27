package net.plebmc.bridge;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Backend half of PlebMcShop: tells the proxy about quest events on this
 * server (kills, deaths, finished matches, and anything run through
 * {@code /plebquest}) and which world each player is in. Messages go to the
 * proxy over the standard BungeeCord channel (sub-channel PlebMcBridge), so
 * nothing needs to be set up between the servers. The proxy decides which
 * quest category this server's events count for.
 */
public final class PlebMcBridge extends JavaPlugin implements Listener {

	static final String SUBCHANNEL = "PlebMcBridge";

	private Stats stats;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		Bukkit.getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
		Bukkit.getPluginManager().registerEvents(this, this);
		stats = new Stats(this, getConfig().getConfigurationSection("stats"));
		stats.start();
	}

	@Override
	public void onDisable() {
		Bukkit.getMessenger().unregisterOutgoingPluginChannel(this);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent e) {
		if (stats != null && stats.enabled) {
			stats.collect(e.getPlayer());
			stats.flush();
		}
	}

	/** /plebstats [player]: what this server sends to the panel for a player, and sends it now. */
	private boolean statsCommand(CommandSender s, String[] a) {
		if (stats == null || stats.values.isEmpty()) {
			s.sendMessage("§cNo stats are set up. See stats: in PlebMcBridge's config.yml.");
			return true;
		}
		Player p = a.length > 0 ? Bukkit.getPlayerExact(a[0]) : (s instanceof Player ? (Player) s : null);
		if (p == null) {
			s.sendMessage("§7Usage: /plebstats <online player>");
			return true;
		}
		java.util.Map<String, Double> got = stats.read(p);
		s.sendMessage("§b" + (stats.game.isEmpty() ? "(no game set)" : stats.game) + " stats for " + p.getName()
				+ (stats.enabled ? "" : " §c(sending is off)"));
		for (String id : stats.values.keySet()) {
			Double v = got.get(id);
			s.sendMessage(" §7" + id + ": " + (v == null ? "§cnothing (check " + stats.values.get(id) + ")" : "§f" + v));
		}
		if (stats.enabled) {
			stats.collect(p);
			stats.flush();
			s.sendMessage("§aSent to the panel.");
		}
		return true;
	}

	/** Sends one event for {@code p} through {@code p}'s own connection. */
	void send(Player p, String type, long amount, String world) {
		StringBuilder json = new StringBuilder("{\"player\":\"").append(p.getUniqueId()).append('"');
		if (type != null) {
			json.append(",\"type\":\"").append(type.replaceAll("[^a-z0-9_\\-]", "")).append("\",\"amount\":")
					.append(amount);
		}
		if (world != null) {
			json.append(",\"world\":\"").append(world.replace("\\", "").replace("\"", "")).append('"');
		}
		json.append('}');
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bytes);
			out.writeUTF(SUBCHANNEL);
			out.writeUTF(json.toString());
			p.sendPluginMessage(this, "BungeeCord", bytes.toByteArray());
		} catch (IOException | RuntimeException e) {
			getLogger().warning("Could not send to the proxy: " + e);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(PlayerDeathEvent e) {
		Player dead = e.getEntity();
		Player killer = dead.getKiller();
		send(dead, "death", 1, null);
		send(dead, "match", 1, null);
		if (killer != null && killer != dead) {
			send(killer, "kill", 1, null);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent e) {
		Player p = e.getPlayer();
		// a moment later: the proxy connection must be fully up to carry plugin messages
		Bukkit.getScheduler().runTaskLater(this, () -> {
			if (p.isOnline() && p.getWorld() != null) {
				send(p, null, 0, p.getWorld().getName());
			}
		}, 20L);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWorld(PlayerChangedWorldEvent e) {
		Player p = e.getPlayer();
		if (p.getWorld() != null) {
			send(p, null, 0, p.getWorld().getName());
		}
	}

	/** /plebquest <player> <type> [amount]: count quest progress (for other plugins' command hooks). */
	@Override
	public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
		if (!s.hasPermission("plebshop.admin")) {
			s.sendMessage("§cYou don't have permission.");
			return true;
		}
		if (c.getName().equalsIgnoreCase("plebstats")) {
			return statsCommand(s, a);
		}
		if (a.length < 2) {
			s.sendMessage("§7Usage: /" + label + " <player> <type> [amount]  (types: win, parkour, kill, match, ...)");
			return true;
		}
		Player p = Bukkit.getPlayerExact(a[0]);
		if (p == null) {
			s.sendMessage("§cThat player isn't online here.");
			return true;
		}
		long amount = 1;
		if (a.length > 2) {
			try {
				amount = Long.parseLong(a[2]);
			} catch (NumberFormatException ex) {
				s.sendMessage("§cBad amount.");
				return true;
			}
		}
		String type = a[1].toLowerCase(Locale.ROOT);
		send(p, type, amount, null);
		if (type.equals("win")) {
			send(p, "match", 1, null);
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
		List<String> out = new ArrayList<>();
		if (!s.hasPermission("plebshop.admin")) {
			return out;
		}
		List<String> options = new ArrayList<>();
		if (a.length == 1) {
			for (Player p : Bukkit.getOnlinePlayers()) {
				options.add(p.getName());
			}
		} else if (a.length == 2) {
			options.addAll(Arrays.asList("win", "parkour", "kill", "match", "death"));
		}
		for (String o : options) {
			if (o.toLowerCase(Locale.ROOT).startsWith(a[a.length - 1].toLowerCase(Locale.ROOT))) {
				out.add(o);
			}
		}
		return out;
	}
}
