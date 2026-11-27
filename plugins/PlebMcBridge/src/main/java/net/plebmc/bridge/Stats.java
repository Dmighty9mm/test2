package net.plebmc.bridge;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Sends this server's game stats (PlebNite wins, PlebRPG levels, ...) to the
 * PlebMc Panel, which shows them per player and as leaderboards. Each stat is
 * read from a PlaceholderAPI placeholder ({@code %battleroyalex_kills%}) or a
 * vanilla statistic ({@code statistic:MOB_KILLS}); add {@code /20} to divide
 * (play time in ticks to seconds). Online players are sent every few minutes
 * and when they leave.
 */
final class Stats {

	private final PlebMcBridge plugin;
	final boolean enabled;
	private final String url;
	private final String key;
	final String game;
	private final int every;
	/** stat id to where its number comes from, in config order */
	final Map<String, String> values = new LinkedHashMap<>();
	private final Map<String, String> labels = new LinkedHashMap<>();
	/** name to stats, filled on the main thread and sent off it */
	private final Map<String, Map<String, Double>> pending = new LinkedHashMap<>();
	private long lastWarn;

	Stats(PlebMcBridge plugin, ConfigurationSection c) {
		this.plugin = plugin;
		String u = c == null ? "" : c.getString("panel-url", "");
		url = u.replaceAll("/+$", "");
		key = c == null ? "" : c.getString("key", "");
		game = c == null ? "" : c.getString("game", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
		every = c == null ? 300 : Math.max(30, c.getInt("every-seconds", 300));
		ConfigurationSection v = c == null ? null : c.getConfigurationSection("values");
		if (v != null) {
			for (String id : v.getKeys(false)) {
				ConfigurationSection one = v.getConfigurationSection(id);
				String sid = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
				if (one != null) { // kills: {value: '%...%', label: Kills}
					values.put(sid, one.getString("value", ""));
					if (one.getString("label") != null) {
						labels.put(sid, one.getString("label"));
					}
				} else {
					values.put(sid, v.getString(id, ""));
				}
			}
		}
		enabled = c != null && c.getBoolean("enabled", false) && !url.isEmpty() && !key.isEmpty() && !game.isEmpty()
				&& !values.isEmpty();
	}

	void start() {
		if (!enabled) {
			return;
		}
		Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			for (Player p : Bukkit.getOnlinePlayers()) {
				collect(p);
			}
			flush();
		}, 20L * 60, 20L * every);
		plugin.getLogger().info("Sending " + game + " stats (" + String.join(", ", values.keySet()) + ") to " + url);
	}

	/** On the main thread: read one player's stats now. */
	void collect(Player p) {
		if (!enabled) {
			return;
		}
		Map<String, Double> got = read(p);
		if (!got.isEmpty()) {
			pending.put(p.getName(), got);
		}
	}

	Map<String, Double> read(Player p) {
		Map<String, Double> got = new LinkedHashMap<>();
		for (Map.Entry<String, String> e : values.entrySet()) {
			Double d = value(p, e.getValue());
			if (d != null) {
				got.put(e.getKey(), d);
			}
		}
		return got;
	}

	private static final Pattern DIVIDE = Pattern.compile("^(.*?)\\s*/\\s*([0-9.]+)\\s*$");

	static Double value(Player p, String src) {
		if (src == null || src.trim().isEmpty()) {
			return null;
		}
		double div = 1;
		Matcher m = DIVIDE.matcher(src);
		if (m.matches() && !m.group(1).isEmpty()) {
			src = m.group(1);
			div = Double.parseDouble(m.group(2));
			if (div == 0) {
				div = 1;
			}
		}
		String text;
		if (src.startsWith("statistic:")) {
			text = statistic(p, src.substring("statistic:".length()).trim());
		} else if (src.contains("%")) {
			text = placeholders(p, src);
		} else {
			text = src;
		}
		Double d = number(text);
		return d == null ? null : d / div;
	}

	private static String statistic(Player p, String name) {
		try {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			Class<? extends Enum> st = (Class<? extends Enum>) Class.forName("org.bukkit.Statistic");
			@SuppressWarnings("unchecked")
			Object e = Enum.valueOf(st, name.toUpperCase(Locale.ROOT));
			Method get = p.getClass().getMethod("getStatistic", st);
			return String.valueOf(get.invoke(p, e));
		} catch (Throwable t) {
			return null;
		}
	}

	private static Method papi;
	private static boolean noPapi;

	private static String placeholders(Player p, String text) {
		if (noPapi) {
			return null;
		}
		try {
			if (papi == null) {
				papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI").getMethod("setPlaceholders",
						Class.forName("org.bukkit.OfflinePlayer"), String.class);
			}
			String out = (String) papi.invoke(null, p, text);
			return out == null || out.equals(text) ? null : out;
		} catch (ClassNotFoundException | NoSuchMethodException e) {
			noPapi = true;
			return null;
		} catch (Throwable t) {
			return null;
		}
	}

	private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*([dhms])", Pattern.CASE_INSENSITIVE);
	private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

	/** The number in a placeholder's text: "1,234", "12.5", "§a7", or a time like "0D-2H-45M-10S" (as seconds). */
	static Double number(String text) {
		if (text == null) {
			return null;
		}
		String t = text.replaceAll("§.", "").replace(",", "").trim();
		if (t.isEmpty()) {
			return null;
		}
		if (t.matches("(?i)^(\\d+\\s*[dhms][\\s:\\-,]*)+$")) {
			long secs = 0;
			Matcher m = DURATION.matcher(t);
			while (m.find()) {
				long n = Long.parseLong(m.group(1));
				switch (Character.toLowerCase(m.group(2).charAt(0))) {
				case 'd': secs += n * 86400; break;
				case 'h': secs += n * 3600; break;
				case 'm': secs += n * 60; break;
				default: secs += n;
				}
			}
			return (double) secs;
		}
		Matcher m = NUMBER.matcher(t);
		return m.find() ? Double.valueOf(m.group()) : null;
	}

	/** On the main thread: send what was collected (off the main thread). */
	void flush() {
		if (!enabled || pending.isEmpty()) {
			return;
		}
		String body = json(game, labels, pending);
		pending.clear();
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> post(body));
	}

	static String json(String game, Map<String, String> labels, Map<String, Map<String, Double>> players) {
		StringBuilder b = new StringBuilder("{\"game\":").append(quote(game)).append(",\"labels\":{");
		boolean first = true;
		for (Map.Entry<String, String> e : labels.entrySet()) {
			b.append(first ? "" : ",").append(quote(e.getKey())).append(':').append(quote(e.getValue()));
			first = false;
		}
		b.append("},\"players\":[");
		first = true;
		for (Map.Entry<String, Map<String, Double>> e : players.entrySet()) {
			b.append(first ? "" : ",").append("{\"name\":").append(quote(e.getKey())).append(",\"stats\":{");
			first = false;
			boolean f2 = true;
			for (Map.Entry<String, Double> s : e.getValue().entrySet()) {
				double v = s.getValue();
				b.append(f2 ? "" : ",").append(quote(s.getKey())).append(':')
						.append(v == Math.rint(v) && Math.abs(v) < 1e15 ? String.valueOf((long) v) : String.valueOf(v));
				f2 = false;
			}
			b.append("}}");
		}
		return b.append("]}").toString();
	}

	private static String quote(String s) {
		StringBuilder b = new StringBuilder("\"");
		for (char c : s.toCharArray()) {
			if (c == '"' || c == '\\') {
				b.append('\\').append(c);
			} else if (c < 0x20) {
				b.append(String.format("\\u%04x", (int) c));
			} else {
				b.append(c);
			}
		}
		return b.append('"').toString();
	}

	private void post(String body) {
		try {
			HttpURLConnection c = (HttpURLConnection) new URL(url + "/api/server/stats").openConnection();
			c.setRequestMethod("POST");
			c.setConnectTimeout(5000);
			c.setReadTimeout(10000);
			c.setDoOutput(true);
			c.setRequestProperty("Content-Type", "application/json");
			c.setRequestProperty("Authorization", "Bearer " + key);
			try (OutputStream out = c.getOutputStream()) {
				out.write(body.getBytes(StandardCharsets.UTF_8));
			}
			int code = c.getResponseCode();
			if (code != 200) {
				warn("The panel said " + code + " to our stats" + (code == 401 ? " (check stats.key)" : ""));
			}
			c.disconnect();
		} catch (Exception e) {
			warn("Could not send stats to the panel: " + e);
		}
	}

	private void warn(String m) {
		long now = System.currentTimeMillis();
		if (now - lastWarn > 600_000L) { // once every 10 minutes at most
			lastWarn = now;
			plugin.getLogger().warning(m);
		}
	}
}
