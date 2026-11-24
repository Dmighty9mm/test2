package net.plebmc.shop;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Where the coins come from. Three modes (config.yml "coins.mode"):
 * <ul>
 * <li>{@code command}: the balance is read from a PlaceholderAPI placeholder and
 * coins are taken with a console command. Default, set up for Battle Royale X
 * ({@code %battleroyalex_coins%} and {@code brx coins remove}).</li>
 * <li>{@code vault}: a Vault economy.</li>
 * <li>{@code internal}: this plugin keeps its own balances (players.yml), given
 * with {@code /plebshop coins give}.</li>
 * </ul>
 * Everything runs on the main thread.
 */
final class Coins {

	interface Internal {
		long coins(UUID id);

		void setCoins(UUID id, long coins);
	}

	private final Logger log;
	private final String mode;
	private final String placeholder;
	private final String takeCommand;
	private final Internal internal;

	private Method papiSet; // PlaceholderAPI.setPlaceholders(OfflinePlayer, String)
	private Object vaultEconomy;

	Coins(ConfigurationSection sec, Internal internal, Logger log) {
		this.log = log;
		this.internal = internal;
		String m = sec == null ? "command" : sec.getString("mode", "command").toLowerCase();
		this.placeholder = sec == null ? "%battleroyalex_coins%"
				: sec.getString("balance-placeholder", "%battleroyalex_coins%");
		this.takeCommand = sec == null ? "brx coins remove %player% %amount%"
				: sec.getString("take-command", "brx coins remove %player% %amount%");
		if ("vault".equals(m) && !hookVault()) {
			log.warning("coins.mode is vault but no Vault economy was found. Buying is turned off.");
		}
		if ("command".equals(m) && !hookPapi()) {
			log.warning("coins.mode is command but PlaceholderAPI is missing. Buying is turned off.");
		}
		if (!"vault".equals(m) && !"internal".equals(m) && !"command".equals(m)) {
			log.warning("Unknown coins.mode '" + m + "', using command");
			m = "command";
			hookPapi();
		}
		this.mode = m;
	}

	String mode() {
		return mode;
	}

	private boolean hookPapi() {
		try {
			Class<?> c = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
			papiSet = c.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
			return true;
		} catch (ReflectiveOperationException | LinkageError e) {
			return false;
		}
	}

	private boolean hookVault() {
		try {
			Class<?> econ = Class.forName("net.milkbowl.vault.economy.Economy");
			Object sm = Bukkit.class.getMethod("getServicesManager").invoke(null);
			Object reg = sm.getClass().getMethod("getRegistration", Class.class).invoke(sm, econ);
			if (reg == null) {
				return false;
			}
			vaultEconomy = reg.getClass().getMethod("getProvider").invoke(reg);
			return vaultEconomy != null;
		} catch (ReflectiveOperationException | LinkageError e) {
			return false;
		}
	}

	/** The player's balance, or -1 when it can't be read. */
	long balance(Player p) {
		switch (mode) {
		case "internal":
			return internal.coins(p.getUniqueId());
		case "vault":
			if (vaultEconomy == null) {
				return -1;
			}
			try {
				Method m = vaultEconomy.getClass().getMethod("getBalance", OfflinePlayer.class);
				return (long) Math.floor(((Number) m.invoke(vaultEconomy, p)).doubleValue());
			} catch (ReflectiveOperationException | RuntimeException e) {
				log.warning("Vault getBalance failed: " + e);
				return -1;
			}
		default:
			if (papiSet == null) {
				return -1;
			}
			try {
				String s = (String) papiSet.invoke(null, p, placeholder);
				return parse(s);
			} catch (ReflectiveOperationException | RuntimeException e) {
				log.warning("PlaceholderAPI failed: " + e);
				return -1;
			}
		}
	}

	/** Reads a number like "1,250", "1250.0" or "§61250" (-1 if there is none). */
	static long parse(String s) {
		if (s == null) {
			return -1;
		}
		StringBuilder b = new StringBuilder();
		boolean seenDigit = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '§' || c == '&') {
				i++; // skip a color code
				continue;
			}
			if (c >= '0' && c <= '9') {
				b.append(c);
				seenDigit = true;
			} else if (c == ',' || c == ' ' || c == '_') {
				continue;
			} else if (seenDigit) {
				break; // "1250.0" -> 1250, "1250 coins" -> 1250
			}
		}
		if (!seenDigit || b.length() > 18) {
			return -1;
		}
		return Long.parseLong(b.toString());
	}

	/**
	 * Takes {@code amount} coins. Checks the balance first and, in command mode,
	 * that it actually went down. Returns the new balance, or -1 if nothing was
	 * taken.
	 */
	long take(Player p, long amount) {
		long before = balance(p);
		if (before < 0 || before < amount) {
			return -1;
		}
		if (amount == 0) {
			return before;
		}
		switch (mode) {
		case "internal":
			internal.setCoins(p.getUniqueId(), before - amount);
			return before - amount;
		case "vault":
			try {
				Method m = vaultEconomy.getClass().getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
				Object r = m.invoke(vaultEconomy, p, (double) amount);
				boolean ok = (Boolean) r.getClass().getMethod("transactionSuccess").invoke(r);
				return ok ? Math.max(0, balance(p)) : -1;
			} catch (ReflectiveOperationException | RuntimeException e) {
				log.warning("Vault withdraw failed: " + e);
				return -1;
			}
		default:
			String cmd = takeCommand.replace("%player%", p.getName()).replace("%uuid%", p.getUniqueId().toString())
					.replace("%amount%", Long.toString(amount));
			if (cmd.startsWith("/")) {
				cmd = cmd.substring(1);
			}
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
			long after = balance(p);
			if (after < 0 || after > before - amount) {
				log.severe("'" + cmd + "' did not take the coins from " + p.getName() + " (balance " + before + " -> "
						+ after + "). Check coins.take-command and coins.balance-placeholder in config.yml.");
				return -1;
			}
			return after;
		}
	}
}
