package net.plebmc.shop;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Sends LiteBans' bans, mutes, warnings and kicks to the PlebMc Panel, for its
 * admin-only Punishments page. Reads them through LiteBans' own API on this
 * proxy, so it works with whatever database LiteBans uses (H2, MySQL, ...)
 * and needs no password. It only reads. IP addresses are never sent.
 *
 * Every tick the newest rows of each kind go over (when something changed);
 * every {@link #FULL_EVERY} ms everything goes over in pages, which also picks
 * up old punishments that were lifted or deleted.
 */
final class Punishments {

	/** Runs one read-only query; every row is column name (lower case) to value. */
	interface Source {
		List<Map<String, Object>> query(String sql, Object... args) throws Exception;
	}

	/** Where the rows go (PanelLink.punishments). */
	interface Sink {
		void send(Map<String, Object> body) throws Exception;
	}

	/** {the panel's name, LiteBans' table} */
	static final String[][] KINDS = { { "ban", "{bans}" }, { "mute", "{mutes}" }, { "warn", "{warnings}" }, { "kick", "{kicks}" } };
	static final int RECENT = 100;
	static final int PAGE = 400;
	static final int MAX_PAGES = 50;
	static final long FULL_EVERY = 10 * 60_000L;
	static final long HEARTBEAT = 2 * 60_000L;

	private static final Set<String> FLAGS = new HashSet<>(Arrays.asList("active", "silent", "ipban", "ipban_wildcard"));
	private static final Set<String> NUMBERS = new HashSet<>(Arrays.asList("id", "time", "until"));

	private final Logger log;
	private Source source;
	private long lookedAt;
	private long lastFull;
	private long lastSent;
	private long lastWarned;
	private int lastHash;
	private long sweep;

	Punishments(Source source, Logger log) {
		this.source = source;
		this.log = log;
	}

	/** LiteBans on this proxy, or null when it isn't installed (or is too old to have its API). */
	static Source liteBans() {
		try {
			Class<?> c = Class.forName("litebans.api.Database");
			Method get = c.getMethod("get");
			Method prepare = c.getMethod("prepareStatement", String.class);
			return (sql, args) -> {
				Object db = get.invoke(null);
				List<Map<String, Object>> out = new ArrayList<>();
				try (PreparedStatement st = (PreparedStatement) prepare.invoke(db, sql)) {
					for (int i = 0; i < args.length; i++) {
						st.setObject(i + 1, args[i]);
					}
					try (ResultSet rs = st.executeQuery()) {
						ResultSetMetaData md = rs.getMetaData();
						while (rs.next()) {
							Map<String, Object> row = new HashMap<>();
							for (int i = 1; i <= md.getColumnCount(); i++) {
								String col = md.getColumnLabel(i).toLowerCase(Locale.ROOT);
								row.put(col, read(rs, i, col));
							}
							out.add(row);
						}
					}
				}
				return out;
			};
		} catch (Throwable t) {
			return null;
		}
	}

	/** Databases disagree on types (a flag is a BIT, a BOOLEAN or a number), so read by what the column means. */
	private static Object read(ResultSet rs, int i, String col) {
		try {
			if (FLAGS.contains(col)) {
				return rs.getBoolean(i);
			}
			if (NUMBERS.contains(col)) {
				return rs.getLong(i);
			}
			if (col.equals("removed_by_date") || col.equals("date")) {
				Timestamp t = rs.getTimestamp(i);
				return t == null ? 0L : t.getTime();
			}
			if (col.equals("ip")) {
				return null;
			}
			return rs.getString(i);
		} catch (Exception e) {
			return null;
		}
	}

	/** One tick, off the proxy's event threads. Never throws. */
	synchronized void tick(Sink sink, long now) {
		try {
			if (source == null) {
				if (now - lookedAt >= 60_000L || lookedAt == 0) {
					lookedAt = now;
					source = liteBans();
					if (source != null) {
						log.info("LiteBans found: bans, mutes, warnings and kicks show on the panel's Punishments page");
					}
				}
				if (source == null) {
					if (now - lastSent >= HEARTBEAT || lastSent == 0) {
						sink.send(unavailable("LiteBans isn't installed on the BungeeCord proxy."));
						lastSent = now;
					}
					return;
				}
			}
			if (lastFull == 0 || now - lastFull >= FULL_EVERY) {
				full(sink, now);
				lastFull = now;
				lastSent = now;
				return;
			}
			List<Map<String, Object>> rows = new ArrayList<>();
			for (String[] k : KINDS) {
				rows.addAll(rows(k[0], source.query("SELECT * FROM " + k[1] + " ORDER BY id DESC LIMIT " + RECENT)));
			}
			int hash = rows.hashCode();
			if (hash == lastHash && now - lastSent < HEARTBEAT) {
				return;
			}
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("available", true);
			body.put("rows", rows);
			sink.send(body);
			lastHash = hash;
			lastSent = now;
		} catch (Throwable t) {
			if (now - lastWarned >= 10 * 60_000L || lastWarned == 0) {
				lastWarned = now;
				log.warning("Punishments: can't send LiteBans' list to the panel (" + cause(t) + ")");
			}
			if (!(t instanceof java.io.IOException)) {
				// the panel is fine but LiteBans isn't answering: say so on the page
				try {
					sink.send(unavailable("LiteBans answered with an error: " + cause(t)));
				} catch (Throwable ignored) {
					// next time
				}
			}
		}
	}

	/** Everything LiteBans has, in pages. The last page of a kind tells the panel to drop what wasn't sent. */
	private void full(Sink sink, long now) throws Exception {
		long id = Math.max(now / 1000L, sweep + 1);
		sweep = id;
		for (String[] k : KINDS) {
			long after = 0;
			for (int page = 0; page < MAX_PAGES; page++) {
				List<Map<String, Object>> raw = source.query("SELECT * FROM " + k[1] + " WHERE id > ? ORDER BY id ASC LIMIT " + PAGE, after);
				boolean last = raw.size() < PAGE;
				for (Map<String, Object> r : raw) {
					after = Math.max(after, num(r.get("id")));
				}
				Map<String, Object> s = new LinkedHashMap<>();
				s.put("id", id);
				s.put("kinds", Arrays.asList(k[0]));
				s.put("done", last);
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("available", true);
				body.put("rows", rows(k[0], raw));
				body.put("sweep", s);
				sink.send(body);
				if (last) {
					break;
				}
			}
		}
		lastHash = 0;
	}

	/** LiteBans rows as the panel wants them, with each player's newest name. */
	private List<Map<String, Object>> rows(String kind, List<Map<String, Object>> raw) throws Exception {
		Map<String, String> names = names(raw);
		List<Map<String, Object>> out = new ArrayList<>();
		for (Map<String, Object> r : raw) {
			out.add(row(kind, r, names));
		}
		return out;
	}

	/** uuid to the name LiteBans saw last, for the players in these rows. */
	private Map<String, String> names(List<Map<String, Object>> raw) throws Exception {
		List<String> ids = new ArrayList<>();
		for (Map<String, Object> r : raw) {
			Object u = r.get("uuid");
			if (u != null && String.valueOf(u).length() == 36 && !ids.contains(String.valueOf(u))) {
				ids.add(String.valueOf(u));
			}
		}
		Map<String, String> names = new HashMap<>();
		for (int from = 0; from < ids.size(); from += 100) {
			List<String> part = ids.subList(from, Math.min(ids.size(), from + 100));
			StringBuilder q = new StringBuilder();
			for (int i = 0; i < part.size(); i++) {
				q.append(i == 0 ? "?" : ",?");
			}
			List<Map<String, Object>> seen = source.query("SELECT * FROM {history} WHERE uuid IN (" + q + ")", part.toArray());
			// oldest first, so the newest name is the one that stays
			seen.sort((x, y) -> num(x.get("date")) != num(y.get("date")) ? Long.compare(num(x.get("date")), num(y.get("date")))
					: Long.compare(num(x.get("id")), num(y.get("id"))));
			for (Map<String, Object> h : seen) {
				if (h.get("uuid") != null && h.get("name") != null) {
					names.put(String.valueOf(h.get("uuid")), String.valueOf(h.get("name")));
				}
			}
		}
		return names;
	}

	static Map<String, Object> row(String kind, Map<String, Object> r, Map<String, String> names) {
		Map<String, Object> o = new LinkedHashMap<>();
		String uuid = r.get("uuid") == null ? "" : String.valueOf(r.get("uuid"));
		String name = names.get(uuid);
		o.put("kind", kind);
		o.put("id", num(r.get("id")));
		o.put("uuid", uuid.length() == 36 ? uuid : "");
		o.put("name", name == null ? "" : name);
		o.put("reason", str(r.get("reason")));
		o.put("staff", str(r.get("banned_by_name")));
		o.put("time", num(r.get("time")));
		o.put("until", Math.max(0, num(r.get("until"))));
		o.put("active", Boolean.TRUE.equals(r.get("active")));
		o.put("removedBy", str(r.get("removed_by_name")));
		o.put("removedReason", str(r.get("removed_by_reason")));
		o.put("removedAt", str(r.get("removed_by_name")).isEmpty() ? 0 : num(r.get("removed_by_date")));
		o.put("server", str(r.get("server_scope")));
		o.put("ipBan", Boolean.TRUE.equals(r.get("ipban")));
		o.put("silent", Boolean.TRUE.equals(r.get("silent")));
		return o;
	}

	private static Map<String, Object> unavailable(String reason) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("available", false);
		body.put("reason", reason);
		return body;
	}

	private static long num(Object o) {
		return o instanceof Number ? ((Number) o).longValue() : 0;
	}

	private static String str(Object o) {
		return o == null ? "" : String.valueOf(o);
	}

	private static String cause(Throwable t) {
		while (t.getCause() != null && (t instanceof java.lang.reflect.InvocationTargetException || t.getMessage() == null)) {
			t = t.getCause();
		}
		String m = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
		return m.length() > 150 ? m.substring(0, 150) : m;
	}
}
