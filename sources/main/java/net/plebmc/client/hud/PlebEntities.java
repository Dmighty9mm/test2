package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.List;

import net.lax1dude.eaglercraft.v1_8.EaglercraftUUID;
import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.player.EntityPlayer;
import net.plebmc.client.apollo.Apollo;
import net.plebmc.client.cosmetics.Cosmetics;
import net.plebmc.client.apollo.ApolloText;
import net.plebmc.client.apollo.module.EntityModule;
import net.plebmc.client.apollo.module.Ids;
import net.plebmc.client.apollo.module.LimbModule;
import net.plebmc.client.apollo.module.NametagModule;
import net.plebmc.client.apollo.module.PlayerColorModule;
import net.plebmc.client.apollo.module.ServerRuleModule;
import net.plebmc.client.apollo.module.TntCountdownModule;

/**
 * Entity-render hooks for the Apollo modules that change how entities
 * look: nametags, glow, colored fire, limbs, TNT countdown and the entity
 * module (flipped mobs, rainbow sheep). Each vanilla renderer calls one
 * method here; every method is a no-op when its module is disabled or has
 * no state, so vanilla rendering is unchanged on non-Apollo servers.
 */
public final class PlebEntities {

	private PlebEntities() {
	}

	static String uuid(Entity e) {
		EaglercraftUUID u = e.getUniqueID();
		if (u == null) {
			return "";
		}
		String s = uuidCache.get(u);
		if (s == null) {
			s = Ids.uuid(u.getMostSignificantBits(), u.getLeastSignificantBits());
			if (uuidCache.size() > 2048) {
				uuidCache.clear();
			}
			uuidCache.put(u, s);
		}
		return s;
	}

	// ==================== nametags ====================

	/**
	 * RendererLivingEntity.renderName / RenderPlayer: Apollo's nametag
	 * override for this entity as legacy-formatted lines (top first), or null
	 * for the vanilla nametag.
	 */
	public static List<String> nametag(Entity e) {
		if (!(e instanceof EntityPlayer)) {
			return null;
		}
		NametagModule nm = Apollo.module(NametagModule.class);
		if (nm == null || !nm.isEnabled()) {
			return null;
		}
		String id = uuid(e);
		List<List<ApolloText.Run>> lines = nm.lines(id);
		if (lines == null) {
			return null;
		}
		// converted once per server update, not every frame
		Object[] hit = tagCache.get(id);
		if (hit != null && hit[0] == lines) {
			@SuppressWarnings("unchecked")
			List<String> cached = (List<String>) hit[1];
			return cached;
		}
		List<String> out = new ArrayList<>(lines.size());
		for (int i = 0; i < lines.size(); i++) {
			out.add(legacy(lines.get(i)));
		}
		if (tagCache.size() > 512) {
			tagCache.clear();
		}
		tagCache.put(id, new Object[] { lines, out });
		return out;
	}

	private static final java.util.Map<String, Object[]> tagCache = new java.util.HashMap<>();
	private static final java.util.Map<EaglercraftUUID, String> uuidCache = new java.util.HashMap<>();

	/** Server rule: nametags farther than this many blocks are hidden (0 = vanilla 64). */
	/** True when the player plays on PlebClient (the Pleb head goes by their name). */
	public static boolean isPleb(EaglercraftUUID u) {
		return u != null && Cosmetics.isPleb(Ids.uuid(u.getMostSignificantBits(), u.getLeastSignificantBits()));
	}

	/**
	 * Draws the Pleb head left of a player's nametag (in the label's own pixels,
	 * {@code half} is half the text width) when they play on PlebClient.
	 */
	public static void nametagBadge(Entity e, String label, int half, int y) {
		if (!(e instanceof net.minecraft.entity.player.EntityPlayer) || label == null
				|| !label.contains(e.getName()) || !Cosmetics.isPleb(uuid(e))) {
			return;
		}
		try {
			Ui.mascot(-half - 11, y - 1, 9, 1f);
		} catch (Throwable t) {
			// the texture isn't ready: just the name
		}
	}

	public static double nametagRange(double vanilla) {
		ServerRuleModule rules = Apollo.module(ServerRuleModule.class);
		if (rules == null || !rules.isEnabled()) {
			return vanilla;
		}
		int d = rules.nametagRenderDistance();
		return d > 0 ? d : vanilla;
	}

	private static final int[] CHAT_RGB = { 0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00,
			0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF };

	/** Colored runs as a 1.8 legacy string (each run gets the nearest of the 16 chat colors). */
	static String legacy(List<ApolloText.Run> runs) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < runs.size(); i++) {
			ApolloText.Run r = runs.get(i);
			sb.append('§').append("0123456789abcdef".charAt(nearestChatColor(r.argb)));
			if (r.bold) {
				sb.append("§l");
			}
			sb.append(r.text);
		}
		return sb.toString();
	}

	static int nearestChatColor(int argb) {
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		int best = 15;
		int bestD = Integer.MAX_VALUE;
		for (int i = 0; i < CHAT_RGB.length; i++) {
			int dr = r - ((CHAT_RGB[i] >> 16) & 0xFF);
			int dg = g - ((CHAT_RGB[i] >> 8) & 0xFF);
			int db = b - (CHAT_RGB[i] & 0xFF);
			int d = dr * dr + dg * dg + db * db;
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		return best;
	}

	// ==================== glow ====================

	private static PlayerColorModule colorModule(String id) {
		Object m = Apollo.module(id);
		return m instanceof PlayerColorModule && ((PlayerColorModule) m).isEnabled() ? (PlayerColorModule) m : null;
	}

	/**
	 * EntityRenderer.renderWorldPass, after entities: Apollo sky beams
	 * (waypoints, beam module) and the glow outline around players the
	 * server made glow, visible through walls like the glowing effect of
	 * later versions.
	 */
	public static void renderGlow(float partialTicks) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null) {
			return;
		}
		try {
			CosmeticTrails.update(mc);
			WorldFx.renderBeams(mc, partialTicks);
		} catch (Throwable t) {
			// drawing must never break the frame
		}
		WorldFx.renderGlow(mc, colorModule("glow"), partialTicks);
	}

	// ==================== colored fire ====================

	/** Render.renderEntityOnFire: tints the fire for this entity (white = vanilla). */
	public static void applyFireColor(Entity e) {
		PlayerColorModule fire = colorModule("colored_fire");
		int c = fire == null || !(e instanceof EntityPlayer) ? 0 : fire.color(uuid(e));
		if (c == 0) {
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		} else {
			GlStateManager.color(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, 1.0F);
		}
	}

	// ==================== limbs ====================

	/** RenderPlayer.setModelVisibilities: hides the body parts the server hid. */
	public static void applyLimbs(AbstractClientPlayer p, ModelBiped m) {
		LimbModule lm = Apollo.module(LimbModule.class);
		if (lm == null || !lm.isEnabled()) {
			return;
		}
		String id = uuid(p);
		ModelPlayer mp = m instanceof ModelPlayer ? (ModelPlayer) m : null;
		if (lm.isBodyPartHidden(id, 1)) {
			m.bipedHead.showModel = false;
			m.bipedHeadwear.showModel = false;
		}
		if (lm.isBodyPartHidden(id, 2)) {
			m.bipedBody.showModel = false;
			if (mp != null) {
				mp.bipedBodyWear.showModel = false;
			}
		}
		if (lm.isBodyPartHidden(id, 3)) {
			m.bipedLeftArm.showModel = false;
			if (mp != null) {
				mp.bipedLeftArmwear.showModel = false;
			}
		}
		if (lm.isBodyPartHidden(id, 4)) {
			m.bipedRightArm.showModel = false;
			if (mp != null) {
				mp.bipedRightArmwear.showModel = false;
			}
		}
		if (lm.isBodyPartHidden(id, 5)) {
			m.bipedLeftLeg.showModel = false;
			if (mp != null) {
				mp.bipedLeftLegwear.showModel = false;
			}
		}
		if (lm.isBodyPartHidden(id, 6)) {
			m.bipedRightLeg.showModel = false;
			if (mp != null) {
				mp.bipedRightLegwear.showModel = false;
			}
		}
	}

	/** LayerArmorBase.renderLayer: 1.8 armor slot 4 helmet .. 1 boots. */
	public static boolean armorHidden(Entity e, int armorSlot) {
		if (!(e instanceof EntityPlayer)) {
			return false;
		}
		LimbModule lm = Apollo.module(LimbModule.class);
		return lm != null && lm.isEnabled() && lm.isArmorHidden(uuid(e), 5 - armorSlot);
	}

	// ==================== TNT countdown ====================

	/** Client time each primed TNT was first drawn, by entity id. */
	private static final java.util.Map<Integer, Long> tntSeen = new java.util.HashMap<>();
	private static long tntSweep;

	/**
	 * RenderTNTPrimed.doRender: the countdown label, or null when the module
	 * is off. The fuse is the one the server set for this TNT (thrown TNT on
	 * PlebNite is 40 ticks), else the module default. The entity's own fuse is
	 * synced to it so the white flashing and swelling match the real explosion.
	 */
	public static String tntLabel(EntityTNTPrimed tnt) {
		TntCountdownModule tm = Apollo.module(TntCountdownModule.class);
		if (tm == null || !tm.isEnabled()) {
			return null;
		}
		long now = Apollo.now();
		Integer id = tnt.getEntityId();
		Long seen = tntSeen.get(id);
		if (seen == null) {
			// the client counts its fuse down from 80; that dates the spawn
			seen = now - Math.max(0, 80 - tnt.fuse) * 50L;
			if (tntSeen.size() < 4096) {
				tntSeen.put(id, seen);
			}
		}
		if (now - tntSweep > 10000L) {
			tntSweep = now;
			for (java.util.Iterator<Long> it = tntSeen.values().iterator(); it.hasNext();) {
				if (now - it.next() > 60000L) {
					it.remove();
				}
			}
		}
		int ticks = tm.remainingTicks(tnt.getEntityId(), seen, now);
		if (tm.hasFuse(tnt.getEntityId()) || tm.defaultFuseTicks() != 80) {
			tnt.fuse = Math.max(1, ticks);
		}
		float secs = ticks / 20f;
		String color = secs > 2f ? "\u00a7a" : secs > 1f ? "\u00a76" : "\u00a7c";
		int tenths = Math.round(secs * 10f);
		return color + (tenths / 10) + "." + (tenths % 10) + "s";
	}

	// ==================== entity module ====================

	/** RendererLivingEntity.rotateCorpse: upside down like "Dinnerbone". */
	public static boolean flipped(Entity e) {
		EntityModule em = Apollo.module(EntityModule.class);
		return em != null && em.isEnabled() && em.isFlipped(e.getEntityId());
	}

	/** LayerSheepWool: rainbow wool like "jeb_". */
	public static boolean rainbowSheep(Entity e) {
		EntityModule em = Apollo.module(EntityModule.class);
		return em != null && em.isEnabled() && em.isRainbowSheep(e.getEntityId());
	}

	// ==================== holograms ====================

	/**
	 * ArmorStandRenderer.canRenderName: newer servers' holograms (text
	 * display entities, 1.19.4+) reach 1.8 as name-visible armor stands, one
	 * per line. A blank line arrives with an empty name, which vanilla shows
	 * as "Armor Stand". Hide the label when the name has no visible text.
	 */
	public static boolean showArmorStandName(net.minecraft.entity.item.EntityArmorStand e) {
		if (!net.plebmc.client.mods.ClientPrefs.get().cleanHolograms) {
			return true;
		}
		if (!e.hasCustomName()) {
			return false;
		}
		String s = net.plebmc.client.text.RgbText.strip(e.getCustomNameTag());
		return s != null && !s.trim().isEmpty();
	}

	/**
	 * ArmorStandRenderer.renderModel: true when this armor stand is a
	 * hologram line whose invisible flag got lost on the way to 1.8, so only
	 * its text should draw. A hologram line always shows its name and holds
	 * nothing, and it is a marker or floats (no gravity). Decorative stands
	 * with armor or items, or without a visible name, still draw.
	 */
	public static boolean isHologramStand(net.minecraft.entity.item.EntityArmorStand e) {
		if (!net.plebmc.client.mods.ClientPrefs.get().cleanHolograms || !e.getAlwaysRenderNameTag()) {
			return false;
		}
		for (int i = 0; i < 5; ++i) {
			if (e.getEquipmentInSlot(i) != null) {
				return false;
			}
		}
		// func_181026_s = isMarker (1.8.8)
		return e.hasNoGravity() || e.func_181026_s();
	}
}
