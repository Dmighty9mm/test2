package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.v1_8.log4j.LogManager;
import net.lax1dude.eaglercraft.v1_8.log4j.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.plebmc.client.items.ItemNbtLog;
import net.plebmc.client.mods.ClientInfo;
import net.plebmc.client.mods.ClientPrefs;

/**
 * "Log item NBT" (Settings &gt; Developer): while on, records the full data of
 * every item that changes in your hand, inventory, armor, the open container
 * (shops, chests) and in the hands and armor of players near you. Checked at
 * most 10 times a second and only while the setting is on, so it costs nothing
 * when off.
 */
public final class ItemNbtWatch {

	private static final Logger LOG = LogManager.getLogger("PlebClient");

	public static final ItemNbtLog LOG_DATA = new ItemNbtLog();

	/** What each watched spot held last time (copies), by spot name. */
	private static final Map<String, ItemStack> last = new HashMap<>();
	private static final int MAX_SPOTS = 2048;
	private static final double PLAYER_RANGE_SQ = 32 * 32;
	private static long lastCheck;

	private ItemNbtWatch() {
	}

	/** From the HUD hook every frame; does nothing while the setting is off. */
	static void tick(Minecraft mc) {
		if (!ClientPrefs.get().itemNbtLog || mc == null || mc.thePlayer == null || mc.theWorld == null) {
			if (!last.isEmpty()) {
				last.clear();
			}
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastCheck < 100) {
			return;
		}
		lastCheck = now;
		try {
			scan(mc, now);
		} catch (Throwable t) {
			LOG.warn("item NBT log failed: " + t);
		}
	}

	/** The world changed (leave or switch server): log everything again on the next scan. */
	static void reset() {
		last.clear();
	}

	private static void scan(Minecraft mc, long now) {
		EntityPlayer me = mc.thePlayer;
		ItemStack[] main = me.inventory.mainInventory;
		int held = me.inventory.currentItem;
		check("held", main[held], now);
		for (int i = 0; i < main.length; i++) {
			if (i != held) {
				check(i < 9 ? "hotbar " + (i + 1) : "inventory " + i, main[i], now);
			}
		}
		String[] armor = { "boots", "leggings", "chestplate", "helmet" };
		for (int i = 0; i < 4; i++) {
			check(armor[i], me.inventory.armorInventory[i], now);
		}
		check("cursor", me.inventory.getItemStack(), now);
		Container open = me.openContainer;
		if (open != null && open != me.inventoryContainer) {
			List<Slot> slots = open.inventorySlots;
			for (int i = 0; i < slots.size(); i++) {
				Slot s = slots.get(i);
				if (s.inventory != me.inventory) {
					check("container slot " + i, s.getStack(), now);
				}
			}
		}
		for (EntityPlayer p : mc.theWorld.playerEntities) {
			if (p == me || p.getDistanceSqToEntity(me) > PLAYER_RANGE_SQ) {
				continue;
			}
			String who = p.getName();
			check(who + " held", p.getHeldItem(), now);
			for (int i = 0; i < 4; i++) {
				check(who + " " + armor[i], p.getCurrentArmor(i), now);
			}
		}
		if (last.size() > MAX_SPOTS) {
			// players come and go; forget the oldest spots rather than grow forever
			Iterator<String> it = last.keySet().iterator();
			while (last.size() > MAX_SPOTS / 2 && it.hasNext()) {
				it.next();
				it.remove();
			}
		}
	}

	private static void check(String where, ItemStack now, long time) {
		ItemStack before = last.get(where);
		if (now == null) {
			if (before != null) {
				last.remove(where);
			}
			return;
		}
		if (before != null && ItemStack.areItemStacksEqual(before, now)) {
			return;
		}
		last.put(where, now.copy());
		// a stack that only changed size is not news (ammo being used, blocks placed)
		if (before != null && before.getItem() == now.getItem() && before.getItemDamage() == now.getItemDamage()
				&& ItemStack.areItemStackTagsEqual(before, now)) {
			return;
		}
		ItemNbtLog.Entry e = describe(where, now, time);
		LOG_DATA.add(e);
		LOG.info("[item nbt] " + ItemNbtLog.strip(e.summary()) + (e.hints.isEmpty() ? "" : " | " + e.hints)
				+ " | " + (e.nbt == null ? "(no nbt)" : e.nbt));
	}

	static ItemNbtLog.Entry describe(String where, ItemStack s, long time) {
		Item item = s.getItem();
		ResourceLocation rl = item == null ? null : Item.itemRegistry.getNameForObject(item);
		String id = rl == null ? "?" : rl.toString();
		NBTTagCompound tag = s.getTagCompound();
		String name = null;
		List<String> lore = new ArrayList<>();
		List<String> hints = new ArrayList<>();
		if (tag != null) {
			if (tag.hasKey("display", 10)) {
				NBTTagCompound d = tag.getCompoundTag("display");
				if (d.hasKey("Name", 8)) {
					name = d.getString("Name");
				}
				if (d.hasKey("Lore", 9)) {
					NBTTagList l = d.getTagList("Lore", 8);
					for (int i = 0; i < l.tagCount() && i < 32; i++) {
						lore.add(l.getStringTagAt(i));
					}
				}
			}
			walk("tag", tag, hints, 0);
		}
		return new ItemNbtLog.Entry(time, where, id, Item.getIdFromItem(item), s.getItemDamage(), s.stackSize, name,
				lore, tag == null ? null : tag.toString(), hints);
	}

	private static void walk(String path, NBTBase nbt, List<String> hints, int depth) {
		if (depth > 12 || hints.size() > 24) {
			return;
		}
		if (nbt instanceof NBTTagCompound) {
			NBTTagCompound c = (NBTTagCompound) nbt;
			for (String k : c.getKeySet()) {
				NBTBase v = c.getTag(k);
				String p = path + "." + k;
				boolean leaf = !(v instanceof NBTTagCompound) && !(v instanceof NBTTagList);
				String h = ItemNbtLog.hintFor(p, k, leaf ? String.valueOf(v) : null);
				if (h != null) {
					hints.add(h);
				}
				walk(p, v, hints, depth + 1);
			}
		} else if (nbt instanceof NBTTagList) {
			NBTTagList l = (NBTTagList) nbt;
			for (int i = 0; i < l.tagCount() && i < 64; i++) {
				walk(path + "[" + i + "]", l.get(i), hints, depth + 1);
			}
		}
	}

	/** The whole log as text, for Copy and Save. */
	static String export(Minecraft mc) {
		ServerData sd = mc == null ? null : mc.getCurrentServerData();
		return LOG_DATA.export(sd == null ? null : sd.serverIP, ClientInfo.NAME);
	}
}
