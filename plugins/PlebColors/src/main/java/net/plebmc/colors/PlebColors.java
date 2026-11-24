package net.plebmc.colors;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.InternalStructure;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;

/**
 * Keeps hex colors and gradients for PlebClient players on 1.8.
 *
 * ViaBackwards rounds 1.16+ hex colors to the 16 old colors before a 1.8
 * player sees them. This plugin runs on the 1.16.5 / 1.21.x server (with
 * ProtocolLib), sees each outgoing packet before Via does, and for players
 * whose client registered {@code plebmc:rgb} (PlebClient) moves hex colors
 * into the text as PlebClient RGB codes (see {@link HexJson}), which Via
 * passes through. Everyone else gets the packets unchanged.
 *
 * Covers chat, system messages, titles, action bar, tab header/footer and
 * player names, scoreboard titles and team prefixes/suffixes, entity and
 * hologram names (FancyHolograms text displays become armor stands for 1.8),
 * and item names and lore. Holograms and tab that were sent before the
 * client said it is PlebClient are sent again once it does.
 */
public final class PlebColors extends JavaPlugin implements Listener {

	public static final String CHANNEL = "plebmc:rgb";
	private static final String[] TYPES = { "CHAT", "SYSTEM_CHAT", "DISGUISED_CHAT", "TITLE", "SET_TITLE_TEXT",
			"SET_SUBTITLE_TEXT", "SET_ACTION_BAR_TEXT", "PLAYER_LIST_HEADER_FOOTER", "PLAYER_INFO", "SCOREBOARD_OBJECTIVE",
			"SCOREBOARD_TEAM", "SCOREBOARD_SCORE", "ENTITY_METADATA", "SET_SLOT", "WINDOW_ITEMS", "OPEN_WINDOW" };
	/** How long after joining packets are kept to resend once the client says it is PlebClient. */
	private static final long CATCH_UP_MS = 60_000L;
	private static final int MAX_KEPT = 1024;

	private final Set<UUID> rgbPlayers = ConcurrentHashMap.newKeySet();
	private final Map<UUID, Long> joinedAt = new ConcurrentHashMap<>();
	private final Map<UUID, Map<String, PacketContainer>> kept = new ConcurrentHashMap<>();
	private boolean newPlayerInfo;
	private Class<?> adventureComponent;
	private Object adventureGson;

	@Override
	public void onEnable() {
		if (!Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
			getLogger().severe("PlebColors needs ProtocolLib. Hex colors stay rounded for 1.8 players until it is installed.");
			return;
		}
		try {
			newPlayerInfo = MinecraftVersion.getCurrentVersion().isAtLeast(new MinecraftVersion("1.19.3"));
		} catch (Throwable t) {
			newPlayerInfo = false;
		}
		try {
			adventureComponent = Class.forName("net.kyori.adventure.text.Component");
			Class<?> ser = Class.forName("net.kyori.adventure.text.serializer.gson.GsonComponentSerializer");
			adventureGson = ser.getMethod("gson").invoke(null);
		} catch (Throwable t) {
			adventureComponent = null; // Spigot: no Adventure fields in packets
		}
		List<PacketType> types = new ArrayList<>();
		for (String name : TYPES) {
			try {
				Field f = PacketType.Play.Server.class.getField(name);
				PacketType t = (PacketType) f.get(null);
				if (t != null && t.isSupported()) {
					types.add(t);
				}
			} catch (Throwable ignored) {
				// not in this ProtocolLib / Minecraft version
			}
		}
		Bukkit.getPluginManager().registerEvents(this, this);
		ProtocolLibrary.getProtocolManager().addPacketListener(new Rewriter(types));
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (p.getListeningPluginChannels().contains(CHANNEL)) {
				rgbPlayers.add(p.getUniqueId());
			}
		}
		getLogger().info("PlebColors: keeping hex colors for PlebClient players (" + types.size() + " packet types)");
	}

	@Override
	public void onDisable() {
		rgbPlayers.clear();
		kept.clear();
	}

	boolean isRgb(Player p) {
		return rgbPlayers.contains(p.getUniqueId());
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		joinedAt.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		UUID id = e.getPlayer().getUniqueId();
		rgbPlayers.remove(id);
		joinedAt.remove(id);
		kept.remove(id);
	}

	@EventHandler
	public void onRegister(PlayerRegisterChannelEvent e) {
		if (!CHANNEL.equals(e.getChannel())) {
			return;
		}
		final Player p = e.getPlayer();
		if (!rgbPlayers.add(p.getUniqueId())) {
			return;
		}
		// one tick later, so the registration is done before we resend
		Bukkit.getScheduler().runTaskLater(this, () -> catchUp(p), 1L);
	}

	/** Sends again what this player got before we knew it was PlebClient. */
	private void catchUp(Player p) {
		if (!p.isOnline()) {
			return;
		}
		Map<String, PacketContainer> m = kept.remove(p.getUniqueId());
		if (m != null) {
			List<PacketContainer> list;
			synchronized (m) {
				list = new ArrayList<>(m.values());
			}
			for (PacketContainer c : list) {
				try {
					ProtocolLibrary.getProtocolManager().sendServerPacket(p, c, true);
				} catch (Throwable t) {
					getLogger().log(Level.FINE, "resend failed", t);
				}
			}
		}
		try {
			p.updateInventory();
		} catch (Throwable ignored) {
		}
	}

	private void keep(Player p, PacketContainer c) {
		Long t = joinedAt.get(p.getUniqueId());
		if (t == null || System.currentTimeMillis() - t > CATCH_UP_MS) {
			return;
		}
		String key;
		PacketType type = c.getType();
		if (type == PacketType.Play.Server.ENTITY_METADATA) {
			key = "e" + c.getIntegers().read(0);
		} else if (type == PacketType.Play.Server.PLAYER_LIST_HEADER_FOOTER) {
			key = "tab";
		} else {
			return;
		}
		Map<String, PacketContainer> m = kept.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashMap<>());
		synchronized (m) {
			if (m.size() < MAX_KEPT || m.containsKey(key)) {
				m.put(key, c);
			}
		}
	}

	private final class Rewriter extends PacketAdapter {
		Rewriter(List<PacketType> types) {
			super(PlebColors.this, ListenerPriority.HIGHEST, types);
		}

		@Override
		public void onPacketSending(PacketEvent ev) {
			if (ev.isPlayerTemporary() || ev.getPlayer() == null) {
				return;
			}
			Player p = ev.getPlayer();
			PacketContainer orig = ev.getPacket();
			PacketType type = orig.getType();
			boolean on = isRgb(p);
			boolean keepable = type == PacketType.Play.Server.ENTITY_METADATA
					|| type == PacketType.Play.Server.PLAYER_LIST_HEADER_FOOTER;
			if (!on && !keepable) {
				return;
			}
			try {
				// a copy: the same packet object may go to other players
				PacketContainer c = orig.deepClone();
				if (!rewrite(c, p)) {
					return;
				}
				if (on) {
					ev.setPacket(c);
				} else {
					keep(p, orig.deepClone());
				}
			} catch (Throwable t) {
				getLogger().log(Level.FINE, "could not rewrite " + type, t);
			}
		}
	}

	// ==================== rewriting ====================

	boolean rewrite(PacketContainer c, Player p) {
		boolean changed = false;
		PacketType type = c.getType();
		changed |= chat(c.getChatComponents());
		changed |= extraFields(c.getModifier(), c);
		try {
			StructureModifier<Optional<InternalStructure>> os = c.getOptionalStructures();
			for (int i = 0; i < os.size(); ++i) {
				Optional<InternalStructure> o = os.read(i);
				if (o != null && o.isPresent()) {
					InternalStructure st = o.get();
					if (chat(st.getChatComponents())) {
						changed = true;
					}
				}
			}
		} catch (Throwable ignored) {
		}
		if (type == PacketType.Play.Server.ENTITY_METADATA) {
			changed |= metadata(c);
		} else if (type == PacketType.Play.Server.PLAYER_INFO) {
			changed |= playerInfo(c);
		} else if (type == PacketType.Play.Server.SET_SLOT || type == PacketType.Play.Server.WINDOW_ITEMS) {
			if (p.getGameMode() != GameMode.CREATIVE) { // creative would save the codes into the item
				changed |= items(c);
			}
		}
		return changed;
	}

	private static WrappedChatComponent convert(WrappedChatComponent w) {
		if (w == null) {
			return null;
		}
		String out = HexJson.convert(w.getJson());
		return out == null ? null : WrappedChatComponent.fromJson(out);
	}

	private static boolean chat(StructureModifier<WrappedChatComponent> mod) {
		boolean changed = false;
		for (int i = 0; i < mod.size(); ++i) {
			try {
				WrappedChatComponent n = convert(mod.read(i));
				if (n != null) {
					mod.write(i, n);
					changed = true;
				}
			} catch (Throwable ignored) {
			}
		}
		return changed;
	}

	/**
	 * Spigot's BaseComponent[] and Paper's Adventure Component fields, which
	 * win over the vanilla component when the packet is written (1.16.5).
	 */
	private boolean extraFields(StructureModifier<Object> all, PacketContainer c) {
		boolean changed = false;
		try {
			StructureModifier<BaseComponent[]> bm = c.getSpecificModifier(BaseComponent[].class);
			for (int i = 0; i < bm.size(); ++i) {
				BaseComponent[] v = bm.read(i);
				if (v == null) {
					continue;
				}
				String out = HexJson.convert(ComponentSerializer.toString(v));
				if (out != null) {
					bm.write(i, ComponentSerializer.parse(out));
					changed = true;
				}
			}
		} catch (Throwable ignored) {
		}
		if (adventureComponent != null) {
			try {
				@SuppressWarnings("unchecked")
				StructureModifier<Object> am = c.getSpecificModifier((Class<Object>) adventureComponent);
				Method ser = adventureGson.getClass().getMethod("serialize", adventureComponent);
				Method de = adventureGson.getClass().getMethod("deserialize", Object.class);
				ser.setAccessible(true);
				de.setAccessible(true);
				for (int i = 0; i < am.size(); ++i) {
					Object v = am.read(i);
					if (v == null) {
						continue;
					}
					String out = HexJson.convert((String) ser.invoke(adventureGson, v));
					if (out != null) {
						am.write(i, de.invoke(adventureGson, out));
						changed = true;
					}
				}
			} catch (Throwable ignored) {
			}
		}
		return changed;
	}

	/** A raw metadata value: Optional<Component> (custom name) or Component (text display text). */
	private static Object convertRaw(Object v) {
		if (v instanceof Optional) {
			Object inner = ((Optional<?>) v).orElse(null);
			Object n = convertRaw(inner);
			return n == null ? null : Optional.of(n);
		}
		if (v instanceof WrappedChatComponent) {
			return convert((WrappedChatComponent) v);
		}
		if (v != null && MinecraftReflection.getIChatBaseComponentClass().isInstance(v)) {
			WrappedChatComponent n = convert(WrappedChatComponent.fromHandle(v));
			return n == null ? null : n.getHandle();
		}
		return null;
	}

	private static boolean metadata(PacketContainer c) {
		boolean changed = false;
		try {
			StructureModifier<List<WrappedDataValue>> dv = c.getDataValueCollectionModifier();
			if (dv.size() > 0) {
				List<WrappedDataValue> list = dv.read(0);
				if (list != null) {
					for (WrappedDataValue v : list) {
						Object n = convertRaw(v.getRawValue());
						if (n != null) {
							v.setRawValue(n);
							changed = true;
						}
					}
					if (changed) {
						dv.write(0, list);
					}
					return changed;
				}
			}
		} catch (Throwable ignored) {
			// before 1.19.3
		}
		try {
			StructureModifier<List<WrappedWatchableObject>> wm = c.getWatchableCollectionModifier();
			if (wm.size() > 0) {
				List<WrappedWatchableObject> list = wm.read(0);
				if (list != null) {
					for (WrappedWatchableObject w : list) {
						Object n = convertRaw(w.getRawValue());
						if (n != null) {
							w.setValue(n);
							changed = true;
						}
					}
					if (changed) {
						wm.write(0, list);
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return changed;
	}

	private boolean playerInfo(PacketContainer c) {
		boolean changed = false;
		StructureModifier<List<PlayerInfoData>> mod = c.getPlayerInfoDataLists();
		for (int i = 0; i < mod.size(); ++i) {
			try {
				List<PlayerInfoData> list = mod.read(i);
				if (list == null) {
					continue;
				}
				List<PlayerInfoData> out = new ArrayList<>(list.size());
				boolean any = false;
				for (PlayerInfoData d : list) {
					WrappedChatComponent n = d == null ? null : convert(d.getDisplayName());
					if (n == null) {
						out.add(d);
						continue;
					}
					any = true;
					if (newPlayerInfo) {
						out.add(new PlayerInfoData(d.getProfileId(), d.getLatency(), d.isListed(), d.getGameMode(),
								d.getProfile(), n, d.isShowHat(), d.getListOrder(), d.getRemoteChatSessionData()));
					} else {
						out.add(new PlayerInfoData(d.getProfile(), d.getLatency(), d.getGameMode(), n));
					}
				}
				if (any) {
					mod.write(i, out);
					changed = true;
				}
			} catch (Throwable ignored) {
			}
		}
		return changed;
	}

	private static boolean items(PacketContainer c) {
		boolean changed = false;
		try {
			StructureModifier<ItemStack> im = c.getItemModifier();
			for (int i = 0; i < im.size(); ++i) {
				ItemStack n = item(im.read(i));
				if (n != null) {
					im.write(i, n);
					changed = true;
				}
			}
		} catch (Throwable ignored) {
		}
		try {
			StructureModifier<List<ItemStack>> lm = c.getItemListModifier();
			for (int i = 0; i < lm.size(); ++i) {
				List<ItemStack> list = lm.read(i);
				if (list == null) {
					continue;
				}
				List<ItemStack> out = new ArrayList<>(list);
				boolean any = false;
				for (int k = 0; k < out.size(); ++k) {
					ItemStack n = item(out.get(k));
					if (n != null) {
						out.set(k, n);
						any = true;
					}
				}
				if (any) {
					lm.write(i, out);
					changed = true;
				}
			}
		} catch (Throwable ignored) {
		}
		return changed;
	}

	/** The item with RGB-coded name and lore, or null when nothing changes. */
	private static ItemStack item(ItemStack in) {
		if (in == null || !in.hasItemMeta()) {
			return null;
		}
		try {
			ItemMeta meta = in.getItemMeta();
			boolean changed = false;
			if (meta.hasDisplayName()) {
				BaseComponent[] name = meta.getDisplayNameComponent();
				String out = name == null ? null : HexJson.convert(ComponentSerializer.toString(name));
				if (out != null) {
					meta.setDisplayNameComponent(ComponentSerializer.parse(out));
					changed = true;
				}
			}
			if (meta.hasLore()) {
				List<BaseComponent[]> lore = meta.getLoreComponents();
				if (lore != null) {
					List<BaseComponent[]> nl = new ArrayList<>(lore.size());
					boolean any = false;
					for (BaseComponent[] line : lore) {
						String out = line == null ? null : HexJson.convert(ComponentSerializer.toString(line));
						if (out != null) {
							nl.add(ComponentSerializer.parse(out));
							any = true;
						} else {
							nl.add(line);
						}
					}
					if (any) {
						meta.setLoreComponents(nl);
						changed = true;
					}
				}
			}
			if (!changed) {
				return null;
			}
			ItemStack copy = in.clone();
			copy.setItemMeta(meta);
			return copy;
		} catch (Throwable t) {
			return null;
		}
	}
}
