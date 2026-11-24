package net.plebmc.client.cosmetics;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.plebmc.client.apollo.proto.Json;
import net.plebmc.client.network.PluginChannels;

/**
 * PlebMc cosmetics bought with the server's coins.
 *
 * <p>
 * The server owns everything that matters: the coin balance, what the player
 * owns and what they have equipped. The client only shows it, sends buy and
 * equip requests, and draws the equipped cosmetics. Messages are UTF-8 JSON
 * objects on the plugin channel {@value #CHANNEL}; the full protocol is in
 * docs/PLEBMC.md ("Cosmetics shop protocol").
 *
 * <p>
 * Server to client: {@code state} (balance, owned, equipped, optional
 * catalog), {@code players} (what other players have equipped) and
 * {@code result} (the answer to a buy or equip). Client to server:
 * {@code open}, {@code buy} and {@code equip}.
 */
public final class Cosmetics {

	public static final String CHANNEL = "plebmc:shop";

	public static final String HAT = "hat";
	public static final String TRAIL = "trail";
	public static final String[] CATEGORIES = { HAT, TRAIL };

	/** One shop item. {@link #render} is the look the client draws (see {@link #RENDERS}). */
	public static final class Item {
		public final String id;
		public final String name;
		public final String category;
		public final String render;
		public final String description;
		public final int price;
		/** 0xRRGGBB tint, or -1 for the look's own colors. */
		public final int color;

		public Item(String id, String name, String category, String render, String description, int price,
				int color) {
			this.id = id;
			this.name = name;
			this.category = category;
			this.render = render;
			this.description = description;
			this.price = price;
			this.color = color;
		}
	}

	/** One quest from the server: do something {@link #goal} times, get {@link #reward} coins. */
	public static final class Quest {
		public final String id;
		public final String name;
		public final String description;
		/** "daily", "weekly" or "login". */
		public final String period;
		public final long progress;
		public final long goal;
		public final long reward;
		public final boolean done;
		/** The category (tab) id, or null for quests that show in every tab (the daily login). */
		public final String category;

		public Quest(String id, String name, String description, String period, long progress, long goal, long reward,
				boolean done) {
			this(id, name, description, period, progress, goal, reward, done, null);
		}

		public Quest(String id, String name, String description, String period, long progress, long goal, long reward,
				boolean done, String category) {
			this.category = category;
			this.id = id;
			this.name = name;
			this.description = description;
			this.period = period;
			this.progress = progress;
			this.goal = goal;
			this.reward = reward;
			this.done = done;
		}
	}

	/** A quest tab: e.g. "lobby" shown as "Lobby", titled "PlebMc Lobby". */
	public static final class QuestCategory {
		public final String id;
		public final String name;
		public final String tab;

		public QuestCategory(String id, String name, String tab) {
			this.id = id;
			this.name = name;
			this.tab = tab;
		}
	}

	/** Receives coin/quest popups (set by the HUD; null in tests). */
	public interface Toasts {
		void show(String title, String message);
	}

	/** Every look this client knows how to draw. A catalog item with another render id is hidden. */
	public static final String[] RENDERS = { "tophat", "crown", "halo", "partyhat", "beanie", "horns", "flame",
			"hearts", "notes", "rainbow", "cloud", "portal", "emerald", "snow" };

	/** Shown until the server sends its own catalog (prices are the server's to set). */
	static final List<Item> DEFAULT_CATALOG = Collections.unmodifiableList(java.util.Arrays.asList(
			new Item("tophat", "Top Hat", HAT, "tophat", "A classy black top hat", 500, -1),
			new Item("crown", "Crown", HAT, "crown", "Gold crown for champions", 2500, -1),
			new Item("halo", "Halo", HAT, "halo", "A glowing ring over your head", 1500, -1),
			new Item("partyhat", "Party Hat", HAT, "partyhat", "Striped cone hat", 300, -1),
			new Item("beanie", "Beanie", HAT, "beanie", "Warm knitted beanie", 250, 0x3A7BD5),
			new Item("horns", "Devil Horns", HAT, "horns", "Two red horns", 1000, -1),
			new Item("flame", "Flame Trail", TRAIL, "flame", "Fire at your feet", 750, -1),
			new Item("hearts", "Heart Trail", TRAIL, "hearts", "Hearts follow you", 600, -1),
			new Item("notes", "Music Trail", TRAIL, "notes", "Music notes as you walk", 600, -1),
			new Item("rainbow", "Rainbow Trail", TRAIL, "rainbow", "A rainbow dust trail", 2000, -1),
			new Item("cloud", "Cloud Trail", TRAIL, "cloud", "Puffs of cloud", 400, -1),
			new Item("portal", "Ender Trail", TRAIL, "portal", "Ender portal sparkles", 900, -1),
			new Item("emerald", "Emerald Trail", TRAIL, "emerald", "Green sparkles", 800, -1),
			new Item("snow", "Snow Trail", TRAIL, "snow", "Snowflakes", 500, -1)));

	private static List<Item> catalog = DEFAULT_CATALOG;
	private static boolean serverCatalog;
	private static long coins = -1;
	private static String currency = "PlebMc Coins";
	private static List<Quest> quests = Collections.emptyList();
	private static long dailyResetAt;
	private static long weeklyResetAt;
	private static int loginStreak;
	private static Toasts toasts;
	private static List<QuestCategory> questCategories = Collections.emptyList();
	private static final Account account = new Account();
	private static boolean serverSupport;
	private static final Set<String> owned = new HashSet<>();
	private static final Map<String, String> equipped = new HashMap<>();
	private static final Map<String, Map<String, String>> others = new HashMap<>();
	/** Try-on in the shop: local only, never sent, cleared when the shop closes. */
	private static final Map<String, String> preview = new HashMap<>();
	private static String message;
	private static boolean messageOk;
	private static long messageAt;
	private static String selfUuid = "";
	private static int version;

	private static PluginChannels.Log log;

	private Cosmetics() {
	}

	/** Registers the channel. Safe to call more than once. */
	public static void init(PluginChannels.Log logger) {
		log = logger;
		PluginChannels.register(CHANNEL, new PluginChannels.Handler() {
			@Override
			public void onMessage(String channel, byte[] data) {
				handle(new String(data, StandardCharsets.UTF_8));
			}
		});
		PluginChannels.addSessionListener(new PluginChannels.SessionListener() {
			@Override
			public void onJoin() {
				send(obj("type", "open"));
			}

			@Override
			public void onReset() {
				reset();
			}
		});
	}

	/** Forget everything from the last server (disconnect or server switch). */
	public static void reset() {
		catalog = DEFAULT_CATALOG;
		serverCatalog = false;
		coins = -1;
		currency = "PlebMc Coins";
		quests = Collections.emptyList();
		questCategories = Collections.emptyList();
		account.clear();
		dailyResetAt = 0;
		weeklyResetAt = 0;
		loginStreak = 0;
		serverSupport = false;
		owned.clear();
		equipped.clear();
		others.clear();
		message = null;
		version++;
	}

	public static void setSelf(String uuid) {
		selfUuid = uuid == null ? "" : uuid;
	}

	// ==================== incoming ====================

	/** One message from the server. Bad input is logged and ignored. */
	public static void handle(String json) {
		Object o;
		try {
			o = Json.parse(json);
		} catch (RuntimeException e) {
			warn("bad shop message: " + e.getMessage());
			return;
		}
		if (!(o instanceof Map)) {
			return;
		}
		Map<?, ?> m = (Map<?, ?>) o;
		String type = str(m.get("type"), "");
		serverSupport = true;
		if ("state".equals(type)) {
			if (m.containsKey("coins")) {
				coins = Math.max(0, num(m.get("coins"), 0));
			}
			if (m.get("owned") instanceof List) {
				owned.clear();
				for (Object id : (List<?>) m.get("owned")) {
					if (id instanceof String) {
						owned.add((String) id);
					}
				}
			}
			if (m.get("equipped") instanceof Map) {
				equipped.clear();
				readSlots((Map<?, ?>) m.get("equipped"), equipped);
			}
			if (m.get("currency") instanceof String) {
				currency = (String) m.get("currency");
			}
			if (m.get("questCategories") instanceof List) {
				List<QuestCategory> cs = new ArrayList<>();
				for (Object e : (List<?>) m.get("questCategories")) {
					if (e instanceof Map && ((Map<?, ?>) e).get("id") instanceof String) {
						Map<?, ?> c = (Map<?, ?>) e;
						String cid = (String) c.get("id");
						String cname = str(c.get("name"), cid);
						cs.add(new QuestCategory(cid, cname, str(c.get("tab"), cname)));
					}
				}
				questCategories = Collections.unmodifiableList(cs);
			}
			if (m.get("quests") instanceof List) {
				List<Quest> qs = new ArrayList<>();
				for (Object e : (List<?>) m.get("quests")) {
					Quest q = readQuest(e);
					if (q != null) {
						qs.add(q);
					}
				}
				quests = Collections.unmodifiableList(qs);
			}
			long now = System.currentTimeMillis();
			if (m.containsKey("dailyResetIn")) {
				dailyResetAt = now + 1000L * Math.max(0, num(m.get("dailyResetIn"), 0));
			}
			if (m.containsKey("weeklyResetIn")) {
				weeklyResetAt = now + 1000L * Math.max(0, num(m.get("weeklyResetIn"), 0));
			}
			if (m.containsKey("loginStreak")) {
				loginStreak = (int) Math.max(0, num(m.get("loginStreak"), 0));
			}
			if (m.get("catalog") instanceof List) {
				List<Item> items = new ArrayList<>();
				for (Object e : (List<?>) m.get("catalog")) {
					Item it = readItem(e);
					if (it != null) {
						items.add(it);
					}
				}
				catalog = Collections.unmodifiableList(items);
				serverCatalog = true;
			}
		} else if ("players".equals(type)) {
			if (Boolean.TRUE.equals(m.get("clear"))) {
				others.clear();
			}
			Object p = m.get("players");
			if (p instanceof Map) {
				for (Map.Entry<?, ?> e : ((Map<?, ?>) p).entrySet()) {
					String uuid = String.valueOf(e.getKey()).toLowerCase();
					if (e.getValue() instanceof Map) {
						Map<String, String> slots = new HashMap<>();
						readSlots((Map<?, ?>) e.getValue(), slots);
						if (slots.isEmpty()) {
							others.remove(uuid);
						} else {
							others.put(uuid, slots);
						}
					} else {
						others.remove(uuid);
					}
				}
			}
		} else if ("account".equals(type)) {
			account.update(m);
		} else if ("notify".equals(type)) {
			if (m.containsKey("coins")) {
				coins = Math.max(0, num(m.get("coins"), 0));
			}
			String title = str(m.get("title"), "");
			String text = str(m.get("message"), "");
			if (toasts != null && !(title.isEmpty() && text.isEmpty())) {
				toasts.show(title, text);
			}
		} else if ("result".equals(type)) {
			message = str(m.get("message"), null);
			messageOk = !Boolean.FALSE.equals(m.get("ok"));
			messageAt = System.currentTimeMillis();
			if (m.containsKey("coins")) {
				coins = Math.max(0, num(m.get("coins"), 0));
			}
		}
		version++;
	}

	private static void readSlots(Map<?, ?> in, Map<String, String> out) {
		for (Map.Entry<?, ?> e : in.entrySet()) {
			if (e.getValue() instanceof String && !((String) e.getValue()).isEmpty()) {
				out.put(String.valueOf(e.getKey()), (String) e.getValue());
			}
		}
	}

	private static Item readItem(Object o) {
		if (!(o instanceof Map)) {
			return null;
		}
		Map<?, ?> m = (Map<?, ?>) o;
		String id = str(m.get("id"), null);
		String cat = str(m.get("category"), null);
		if (id == null || cat == null) {
			return null;
		}
		String render = str(m.get("render"), id);
		if (!knownRender(render)) {
			return null; // a look this client version can't draw
		}
		int color = -1;
		Object c = m.get("color");
		if (c instanceof String) {
			String s = ((String) c).trim();
			if (s.startsWith("#")) {
				s = s.substring(1);
			}
			try {
				color = Integer.parseInt(s, 16) & 0xFFFFFF;
			} catch (NumberFormatException e) {
				color = -1;
			}
		} else if (c instanceof Number) {
			color = ((Number) c).intValue() & 0xFFFFFF;
		}
		return new Item(id, str(m.get("name"), id), cat, render, str(m.get("description"), ""),
				(int) Math.max(0, num(m.get("price"), 0)), color);
	}

	private static Quest readQuest(Object o) {
		if (!(o instanceof Map)) {
			return null;
		}
		Map<?, ?> m = (Map<?, ?>) o;
		String id = str(m.get("id"), null);
		if (id == null) {
			return null;
		}
		long goal = Math.max(1, num(m.get("goal"), 1));
		long progress = Math.max(0, Math.min(goal, num(m.get("progress"), 0)));
		boolean done = Boolean.TRUE.equals(m.get("done")) || progress >= goal;
		return new Quest(id, str(m.get("name"), id), str(m.get("description"), ""), str(m.get("period"), "daily"),
				done ? goal : progress, goal, Math.max(0, num(m.get("reward"), 0)), done, str(m.get("category"), null));
	}

	public static boolean knownRender(String r) {
		for (String k : RENDERS) {
			if (k.equals(r)) {
				return true;
			}
		}
		return false;
	}

	// ==================== outgoing ====================

	/** Ask the server for a fresh balance/owned/equipped state. */
	public static boolean requestState() {
		return send(obj("type", "open"));
	}

	public static boolean buy(String id) {
		return send(obj("type", "buy", "id", id));
	}

	/** Equip an owned item, or clear the slot with a null id. */
	public static boolean equip(String category, String id) {
		Map<String, Object> m = obj("type", "equip", "category", category);
		m.put("id", id == null ? Json.NULL : id);
		return send(m);
	}

	/** Asks the server for this player's plebmc.top login. */
	public static boolean requestAccount() {
		return send(obj("type", "account"));
	}

	/** Logs in with the code from plebmc.top (Connections > Minecraft > Link). */
	public static boolean link(String code) {
		account.pending = true;
		account.version++;
		return send(obj("type", "link", "code", code));
	}

	/** The plebmc.top login, as the server last reported it. */
	public static Account account() {
		return account;
	}

	private static boolean send(Map<String, Object> m) {
		return PluginChannels.send(CHANNEL, Json.write(m).getBytes(StandardCharsets.UTF_8));
	}

	private static Map<String, Object> obj(String... kv) {
		Map<String, Object> m = new LinkedHashMap<>();
		for (int i = 0; i + 1 < kv.length; i += 2) {
			m.put(kv[i], kv[i + 1]);
		}
		return m;
	}

	// ==================== queries ====================

	/** True once this server has sent anything on the shop channel. */
	public static boolean serverSupported() {
		return serverSupport;
	}

	/** The coin balance, or -1 before the server sent one. */
	public static long coins() {
		return coins;
	}

	/** What the server calls its coins ("PlebMc Coins" by default). */
	public static String currency() {
		return currency;
	}

	public static List<Quest> quests() {
		return quests;
	}

	/** Quests for one tab: that category's plus the ones in every tab. */
	public static List<Quest> quests(String category) {
		List<Quest> out = new ArrayList<>();
		for (Quest q : quests) {
			if (category == null || q.category == null || q.category.equals(category)) {
				out.add(q);
			}
		}
		return out;
	}

	public static List<QuestCategory> questCategories() {
		return questCategories;
	}

	/** Seconds until the daily quests change, or -1 if unknown. */
	public static long dailyResetSeconds() {
		return dailyResetAt == 0 ? -1 : Math.max(0, (dailyResetAt - System.currentTimeMillis()) / 1000);
	}

	/** Seconds until the weekly quests change, or -1 if unknown. */
	public static long weeklyResetSeconds() {
		return weeklyResetAt == 0 ? -1 : Math.max(0, (weeklyResetAt - System.currentTimeMillis()) / 1000);
	}

	public static int loginStreak() {
		return loginStreak;
	}

	public static void setToasts(Toasts t) {
		toasts = t;
	}

	public static List<Item> catalog() {
		return catalog;
	}

	public static boolean hasServerCatalog() {
		return serverCatalog;
	}

	public static List<Item> catalog(String category) {
		List<Item> out = new ArrayList<>();
		for (Item it : catalog) {
			if (it.category.equals(category)) {
				out.add(it);
			}
		}
		return out;
	}

	public static Item item(String id) {
		if (id == null) {
			return null;
		}
		for (Item it : catalog) {
			if (it.id.equals(id)) {
				return it;
			}
		}
		for (Item it : DEFAULT_CATALOG) {
			if (it.id.equals(id)) {
				return it;
			}
		}
		return null;
	}

	public static boolean owns(String id) {
		return owned.contains(id);
	}

	public static String equippedId(String category) {
		return equipped.get(category);
	}

	/** Changes whenever anything above changes (screens use it to refresh). */
	public static int version() {
		return version;
	}

	public static String message() {
		return message != null && System.currentTimeMillis() - messageAt < 6000 ? message : null;
	}

	public static boolean messageOk() {
		return messageOk;
	}

	public static void setPreview(String category, String id) {
		if (id == null) {
			preview.remove(category);
		} else {
			preview.put(category, id);
		}
		version++;
	}

	public static String previewId(String category) {
		return preview.get(category);
	}

	public static void clearPreview() {
		if (!preview.isEmpty()) {
			preview.clear();
			version++;
		}
	}

	/**
	 * What a player shows in a slot: for this player, the shop try-on first,
	 * then their equipped item; for others, what the server said they wear.
	 */
	public static Item shown(String uuid, String category) {
		String id;
		if (uuid != null && uuid.equals(selfUuid)) {
			id = preview.containsKey(category) ? preview.get(category) : equipped.get(category);
		} else {
			Map<String, String> slots = uuid == null ? null : others.get(uuid);
			id = slots == null ? null : slots.get(category);
		}
		return item(id);
	}

	/** True when nobody needs cosmetics drawn (skips all per-player work). */
	public static boolean nothingToDraw() {
		return equipped.isEmpty() && preview.isEmpty() && others.isEmpty();
	}

	// ==================== helpers ====================

	private static String str(Object o, String def) {
		return o instanceof String ? (String) o : def;
	}

	private static long num(Object o, long def) {
		return o instanceof Number ? ((Number) o).longValue() : def;
	}

	private static void warn(String s) {
		if (log != null) {
			log.warn("[Cosmetics] " + s);
		}
	}
}
