package net.plebmc.client.cosmetics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The admin menu's data (website Owners only): online players with their
 * coins and cosmetics, the servers and the shop items, as the server last
 * sent them. The server decides who is an admin and checks every action;
 * this only shows what it sends.
 */
public final class Admin {

	/** One online player. */
	public static final class Player {
		public final String uuid;
		public final String name;
		public final String server;
		public final long coins;
		/** On the PlebMc client (gets popups). */
		public final boolean client;
		/** Website account name, or "" when not logged in. */
		public final String account;
		public final List<String> groups;
		public final List<String> owned;
		public final Map<String, String> equipped;

		Player(String uuid, String name, String server, long coins, boolean client, String account, List<String> groups,
				List<String> owned, Map<String, String> equipped) {
			this.uuid = uuid;
			this.name = name;
			this.server = server;
			this.coins = coins;
			this.client = client;
			this.account = account;
			this.groups = groups;
			this.owned = owned;
			this.equipped = equipped;
		}

		public String rank() {
			return groups.isEmpty() ? "" : groups.get(0);
		}
	}

	/** One shop item (id, name, category). */
	public static final class ItemRef {
		public final String id;
		public final String name;
		public final String category;

		ItemRef(String id, String name, String category) {
			this.id = id;
			this.name = name;
			this.category = category;
		}
	}

	/** The server answered at all (false until the first answer). */
	public boolean known;
	/** The server said this player may use the admin menu. */
	public boolean allowed;
	public String currency = "PlebMc Coins";
	public List<Player> players = Collections.emptyList();
	public List<String> servers = Collections.emptyList();
	public List<ItemRef> items = Collections.emptyList();
	public long registered;
	/** The answer to the last action, or null. */
	public String message;
	public boolean messageOk;
	public long messageAt;
	public boolean pending;
	public int version;

	void clear() {
		known = allowed = pending = false;
		players = Collections.emptyList();
		servers = Collections.emptyList();
		items = Collections.emptyList();
		registered = 0;
		message = null;
		version++;
	}

	void update(Map<?, ?> m) {
		known = true;
		pending = false;
		allowed = Boolean.TRUE.equals(m.get("allowed"));
		if (m.get("currency") instanceof String) {
			currency = (String) m.get("currency");
		}
		if (m.get("players") instanceof List) {
			List<Player> ps = new ArrayList<>();
			for (Object o : (List<?>) m.get("players")) {
				if (!(o instanceof Map) || ps.size() >= 500) {
					continue;
				}
				Map<?, ?> p = (Map<?, ?>) o;
				String uuid = str(p.get("uuid"));
				String name = str(p.get("name"));
				if (uuid.isEmpty() || name.isEmpty()) {
					continue;
				}
				Map<String, String> eq = new LinkedHashMap<>();
				if (p.get("equipped") instanceof Map) {
					for (Map.Entry<?, ?> e : ((Map<?, ?>) p.get("equipped")).entrySet()) {
						if (e.getValue() instanceof String) {
							eq.put(String.valueOf(e.getKey()), (String) e.getValue());
						}
					}
				}
				ps.add(new Player(uuid, name, str(p.get("server")),
						p.get("coins") instanceof Number ? ((Number) p.get("coins")).longValue() : 0,
						Boolean.TRUE.equals(p.get("client")), str(p.get("account")), strings(p.get("groups")),
						strings(p.get("owned")), eq));
			}
			Collections.sort(ps, (a, b) -> a.name.compareToIgnoreCase(b.name));
			players = Collections.unmodifiableList(ps);
		}
		if (m.get("servers") instanceof List) {
			List<String> s = strings(m.get("servers"));
			Collections.sort(s);
			servers = s;
		}
		if (m.get("items") instanceof List) {
			List<ItemRef> is = new ArrayList<>();
			for (Object o : (List<?>) m.get("items")) {
				if (o instanceof Map && ((Map<?, ?>) o).get("id") instanceof String) {
					Map<?, ?> i = (Map<?, ?>) o;
					String id = (String) i.get("id");
					is.add(new ItemRef(id, str(i.get("name")).isEmpty() ? id : str(i.get("name")), str(i.get("category"))));
				}
			}
			items = Collections.unmodifiableList(is);
		}
		if (m.get("registered") instanceof Number) {
			registered = ((Number) m.get("registered")).longValue();
		}
		if (m.get("message") instanceof String) {
			message = (String) m.get("message");
			messageOk = !Boolean.FALSE.equals(m.get("ok"));
			messageAt = System.currentTimeMillis();
		}
		version++;
	}

	public Player player(String uuid) {
		for (Player p : players) {
			if (p.uuid.equals(uuid)) {
				return p;
			}
		}
		return null;
	}

	/** The last answer, for a few seconds. */
	public String recentMessage() {
		return message != null && System.currentTimeMillis() - messageAt < 7000 ? message : null;
	}

	private static String str(Object o) {
		return o instanceof String ? (String) o : "";
	}

	private static List<String> strings(Object o) {
		List<String> out = new ArrayList<>();
		if (o instanceof List) {
			for (Object x : (List<?>) o) {
				if (x instanceof String && out.size() < 512) {
					out.add((String) x);
				}
			}
		}
		return out;
	}
}
