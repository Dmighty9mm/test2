package net.plebmc.client.itemrender;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.v1_8.EagRuntime;
import net.lax1dude.eaglercraft.v1_8.log4j.LogManager;
import net.lax1dude.eaglercraft.v1_8.log4j.Logger;
import net.lax1dude.eaglercraft.v1_8.minecraft.EaglerFolderResourcePack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.plebmc.client.items.ItemRules;
import net.plebmc.client.items.ModelConverter;
import net.plebmc.client.items.NewItems;
import net.plebmc.client.items.PackFiles;
import net.plebmc.client.items.VanillaNames;
import net.plebmc.client.mods.ClientPrefs;

/**
 * Custom item models on 1.8 (Settings &gt; Behavior &gt; Custom item models).
 * <p>
 * Hooks, one call each:
 * <ul>
 * <li>Minecraft.refreshResources: {@link #onReload} reads the custom item
 * rules of every pack (the server's pack and imported packs), keeps 1.13+
 * packs from replacing vanilla models, and adds PlebClient's own pack.</li>
 * <li>ModelBakery.loadItemModels: {@link #itemModelLocations} so the custom
 * models are baked with the vanilla ones (same atlas, no extra cost to
 * draw).</li>
 * <li>ItemModelMesher.getItemModel: {@link #itemModel}, which every item
 * renderer goes through (first and third person, GUI, dropped items, item
 * frames).</li>
 * <li>LayerArmorBase: {@link #armorTexture} for netherite armor.</li>
 * </ul>
 * Items without NBT on items with no rules return at once; everything else is
 * matched once per item stack and cached.
 */
public final class PlebItems {

	private static final Logger LOG = LogManager.getLogger("PlebClient");

	/** A PlebNite pack shipped with the client (optional), used on play.plebmc.top. */
	public static final String BUNDLED_PACK = "/plebclient/plebnite-pack.zip";
	private static final String BUNDLED_PREFIX = "plebitems";
	public static final String HOME_SERVER = "plebmc.top";

	private static final PlebItemPack PACK = new PlebItemPack();
	private static volatile ItemRules serverRules = ItemRules.EMPTY;
	private static volatile ItemRules bundledRules = ItemRules.EMPTY;
	private static boolean bundledTried;
	private static FolderPackFiles bundledPack;
	private static int generation;

	private static final class Hit {
		Item item;
		int damage;
		NBTTagCompound tag;
		int gen;
		boolean onHome;
		IBakedModel model;
		String newItem;
	}

	private static final Map<ItemStack, Hit> cache = new IdentityHashMap<>();
	private static final int CACHE_MAX = 2048;
	private static final Map<Item, List<String>> basesOf = new IdentityHashMap<>();

	private PlebItems() {
	}

	// ==================== loading ====================

	/** Minecraft.refreshResources, before the reload: returns the pack list to load. */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static List onReload(List packs) {
		try {
			List out = new ArrayList(packs.size() + 1);
			List<PackFiles> sources = new ArrayList<>();
			for (Object o : packs) {
				if (o instanceof EaglerFolderResourcePack) {
					FolderPackFiles f = new FolderPackFiles((EaglerFolderResourcePack) o);
					sources.add(f);
					out.add(f.packFormat() >= 4 ? new ModernPackFilter((EaglerFolderResourcePack) o) : o);
				} else {
					out.add(o);
				}
			}
			long t0 = System.currentTimeMillis();
			serverRules = ItemRules.build(sources);
			if (!bundledTried) {
				bundledTried = true;
				bundledPack = importBundled();
			}
			if (bundledPack != null) {
				List<PackFiles> b = new ArrayList<>();
				b.add(bundledPack);
				bundledRules = ItemRules.build(b);
			}
			log(serverRules, "packs");
			log(bundledRules, "bundled PlebNite pack");
			LOG.info("custom item rules ready in " + (System.currentTimeMillis() - t0) + " ms");
			out.add(PACK);
			clearCache();
			return out;
		} catch (Throwable t) {
			LOG.warn("custom item models disabled for this reload: " + t);
			serverRules = ItemRules.EMPTY;
			bundledRules = ItemRules.EMPTY;
			List out = new ArrayList(packs);
			out.add(PACK);
			return out;
		}
	}

	private static void log(ItemRules r, String what) {
		if (!r.isEmpty()) {
			LOG.info("custom items from " + what + ": " + r.ruleCount() + " items, " + r.models().size() + " models");
		}
		int n = 0;
		for (String w : r.warnings()) {
			if (n++ < 40) {
				LOG.warn("custom item model skipped: " + w);
			}
		}
	}

	/** Unpacks the bundled PlebNite pack into browser storage once (per version of it). */
	private static FolderPackFiles importBundled() {
		try {
			byte[] zip = EagRuntime.getResourceBytes(BUNDLED_PACK);
			if (zip == null || zip.length == 0 || !EaglerFolderResourcePack.isSupported()) {
				return null;
			}
			java.util.zip.CRC32 crc = new java.util.zip.CRC32();
			crc.update(zip, 0, zip.length);
			String name = "plebnite-" + Long.toHexString(crc.getValue());
			for (EaglerFolderResourcePack p : EaglerFolderResourcePack.getFolderResourcePacks(BUNDLED_PREFIX)) {
				if (p.plebFolder().equals(name)) {
					return new FolderPackFiles(p);
				}
			}
			for (EaglerFolderResourcePack p : EaglerFolderResourcePack.getFolderResourcePacks(BUNDLED_PREFIX)) {
				EaglerFolderResourcePack.deleteResourcePack(p); // an older bundled pack
			}
			EaglerFolderResourcePack p = EaglerFolderResourcePack.importResourcePack(name, BUNDLED_PREFIX, zip);
			return p == null ? null : new FolderPackFiles(p);
		} catch (Throwable t) {
			LOG.warn("bundled PlebNite pack failed to load: " + t);
			return null;
		}
	}

	static ModelConverter.Result findModel(String id) {
		ModelConverter.Result r = serverRules.model(id);
		return r != null ? r : bundledRules.model(id);
	}

	static byte[] findSprite(String spritePath, String suffix) {
		byte[] b = serverRules.spriteFile(spritePath, suffix);
		return b != null ? b : bundledRules.spriteFile(spritePath, suffix);
	}

	/** ModelBakery.loadItemModels: extra item models to load and bake (name to model location). */
	public static Map<String, ResourceLocation> itemModelLocations() {
		Map<String, ResourceLocation> out = new LinkedHashMap<>();
		try {
			for (String id : NewItems.ids()) {
				add(out, NewItems.modelId(id));
			}
			for (ModelConverter.Result r : serverRules.models()) {
				add(out, r.id);
			}
			for (ModelConverter.Result r : bundledRules.models()) {
				add(out, r.id);
			}
		} catch (Throwable t) {
			LOG.warn("custom item models not added: " + t);
		}
		return out;
	}

	private static void add(Map<String, ResourceLocation> out, String id) {
		out.put(ModelConverter.DOMAIN + ":" + id, new ResourceLocation(ModelConverter.DOMAIN, id));
	}

	private static void clearCache() {
		cache.clear();
		generation++;
	}

	// ==================== drawing ====================

	static boolean enabled() {
		return ClientPrefs.get().customItemModels;
	}

	static boolean onHomeServer() {
		try {
			ServerData sd = Minecraft.getMinecraft().getCurrentServerData();
			return sd != null && sd.serverIP != null && sd.serverIP.toLowerCase().contains(HOME_SERVER);
		} catch (Throwable t) {
			return false;
		}
	}

	/** ItemModelMesher.getItemModel: the custom model for this stack, or null for the normal one. */
	public static IBakedModel itemModel(ItemStack stack, ModelManager models) {
		if (stack == null || models == null || !enabled()) {
			return null;
		}
		try {
			Hit h = hit(stack, models);
			return h == null ? null : h.model;
		} catch (Throwable t) {
			return null;
		}
	}

	/** LayerArmorBase: netherite armor (Via turns it into diamond armor) gets its own texture. */
	public static ResourceLocation armorTexture(ItemStack stack, boolean legs, ResourceLocation normal) {
		if (stack == null || stack.getTagCompound() == null || !enabled()) {
			return normal;
		}
		try {
			Hit h = hit(stack, Minecraft.getMinecraft().getRenderItem().getItemModelMesher().getModelManager());
			if (h != null && h.newItem != null && h.newItem.startsWith("netherite_")
					&& NewItems.armorSlot(h.newItem) != null) {
				return new ResourceLocation(ModelConverter.DOMAIN,
						"textures/new/netherite_layer_" + (legs ? 2 : 1) + ".png");
			}
		} catch (Throwable t) {
			// normal texture
		}
		return normal;
	}

	/**
	 * LayerCustomHead: 1.8 only draws blocks and skulls worn on the head, so a
	 * hat from a pack (a QualityArmory crown on quartz) would be invisible.
	 * True when the helmet-slot item has a custom 3D model.
	 */
	public static boolean customHat(ItemStack stack) {
		if (stack == null || stack.getTagCompound() == null || !enabled()) {
			return false;
		}
		try {
			Hit h = hit(stack, Minecraft.getMinecraft().getRenderItem().getItemModelMesher().getModelManager());
			return h != null && h.model != null && h.newItem == null && h.model.isGui3d();
		} catch (Throwable t) {
			return false;
		}
	}

	private static Hit hit(ItemStack stack, ModelManager models) {
		Item item = stack.getItem();
		if (item == null) {
			return null;
		}
		NBTTagCompound tag = stack.getTagCompound();
		ItemRules srv = serverRules, bun = bundledRules;
		boolean home = !bun.isEmpty() && onHomeServer();
		List<String> bases = bases(item);
		if (tag == null && !hasRules(bases, srv, home ? bun : null)) {
			return null; // a plain vanilla item: the common case, no lookups
		}
		Hit h = cache.get(stack);
		if (h != null && h.item == item && h.tag == tag && h.damage == stack.getItemDamage() && h.gen == generation
				&& h.onHome == home) {
			return h;
		}
		h = new Hit();
		h.item = item;
		h.tag = tag;
		h.damage = stack.getItemDamage();
		h.gen = generation;
		h.onHome = home;
		long cmd = 0;
		boolean hasCmd = false;
		String name = null;
		String original = null;
		boolean unbreakable = false;
		if (tag != null) {
			NBTBase c = findKey(tag, "CustomModelData", 0);
			if (c instanceof NBTBase.NBTPrimitive) {
				cmd = ((NBTBase.NBTPrimitive) c).getLong();
				hasCmd = true;
			}
			if (tag.hasKey("display", 10) && tag.getCompoundTag("display").hasKey("Name", 8)) {
				name = tag.getCompoundTag("display").getString("Name");
			}
			unbreakable = tag.getBoolean("Unbreakable");
			original = viaOriginalId(tag, false, 0);
		}
		List<String> all = bases;
		if (original != null) {
			String path = original.startsWith("minecraft:") ? original.substring(10) : original;
			all = new ArrayList<>(bases.size() + 1);
			all.add(path);
			all.addAll(bases);
		}
		h.newItem = NewItems.identify(name, original);
		ModelConverter.Result r = srv.match(all, cmd, hasCmd, stack.getItemDamage(), item.getMaxDamage(), unbreakable,
				name);
		if (r == null && home) {
			r = bun.match(all, cmd, hasCmd, stack.getItemDamage(), item.getMaxDamage(), unbreakable, name);
		}
		String id = r != null ? r.id : h.newItem != null ? NewItems.modelId(h.newItem) : null;
		if (id != null) {
			IBakedModel m = models.getModel(new ModelResourceLocation(ModelConverter.DOMAIN + ":" + id, "inventory"));
			h.model = m == null || m == models.getMissingModel() ? null : m;
		}
		if (cache.size() >= CACHE_MAX) {
			cache.clear();
		}
		cache.put(stack, h);
		return h;
	}

	private static boolean hasRules(List<String> bases, ItemRules a, ItemRules b) {
		for (String s : bases) {
			if (a.hasRules(s) || (b != null && b.hasRules(s))) {
				return true;
			}
		}
		return false;
	}

	private static List<String> bases(Item item) {
		List<String> b = basesOf.get(item);
		if (b == null) {
			ResourceLocation rl = Item.itemRegistry.getNameForObject(item);
			b = rl == null ? new ArrayList<String>() : VanillaNames.modernNames(rl.getResourcePath());
			basesOf.put(item, b);
		}
		return b;
	}

	/** The first tag called {@code key} at any depth (Via may move it into a backup tag). */
	private static NBTBase findKey(NBTTagCompound c, String key, int depth) {
		if (c.hasKey(key)) {
			return c.getTag(key);
		}
		if (depth >= 6) {
			return null;
		}
		for (String k : c.getKeySet()) {
			NBTBase v = c.getTag(k);
			if (v instanceof NBTTagCompound && !"display".equals(k) && !"BlockEntityTag".equals(k)) {
				NBTBase f = findKey((NBTTagCompound) v, key, depth + 1);
				if (f != null) {
					return f;
				}
			}
		}
		return null;
	}

	/** An original item id Via kept ("minecraft:netherite_sword"), from a tag whose name has "|" or "Via". */
	private static String viaOriginalId(NBTBase n, boolean inVia, int depth) {
		if (depth > 6 || n == null) {
			return null;
		}
		if (n instanceof NBTTagString) {
			String s = ((NBTTagString) n).getString();
			return inVia && s.startsWith("minecraft:") && s.length() < 64 && s.indexOf(' ') < 0 ? s : null;
		}
		if (n instanceof NBTTagCompound) {
			NBTTagCompound c = (NBTTagCompound) n;
			for (String k : c.getKeySet()) {
				if ("display".equals(k) || "BlockEntityTag".equals(k)) {
					continue;
				}
				boolean via = inVia || k.indexOf('|') >= 0 || k.contains("Via");
				String r = viaOriginalId(c.getTag(k), via, depth + 1);
				if (r != null) {
					return r;
				}
			}
		} else if (n instanceof NBTTagList) {
			NBTTagList l = (NBTTagList) n;
			for (int i = 0; i < l.tagCount() && i < 16; i++) {
				String r = viaOriginalId(l.get(i), inVia, depth + 1);
				if (r != null) {
					return r;
				}
			}
		}
		return null;
	}

	/** For the Settings screen: how many custom items are loaded. */
	public static String status() {
		int s = serverRules.models().size(), b = bundledRules.models().size();
		if (s == 0 && b == 0) {
			return "No custom item pack loaded (totems and netherite still work)";
		}
		return (s > 0 ? s + " from the server pack" : "") + (s > 0 && b > 0 ? ", " : "")
				+ (b > 0 ? b + " from the built-in PlebNite pack" : "");
	}
}
