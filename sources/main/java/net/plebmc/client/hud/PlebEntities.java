package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.List;

import net.lax1dude.eaglercraft.v1_8.EaglercraftUUID;
import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.plebmc.client.apollo.Apollo;
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
		return u == null ? "" : Ids.uuid(u.getMostSignificantBits(), u.getLeastSignificantBits());
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
		List<List<ApolloText.Run>> lines = nm.lines(uuid(e));
		if (lines == null) {
			return null;
		}
		List<String> out = new ArrayList<>(lines.size());
		for (int i = 0; i < lines.size(); i++) {
			out.add(legacy(lines.get(i)));
		}
		return out;
	}

	/** Server rule: nametags farther than this many blocks are hidden (0 = vanilla 64). */
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
	 * EntityRenderer.renderWorldPass, after entities: outlines players the
	 * server made glow, in their glow color, visible through walls like the
	 * vanilla glowing effect of later versions.
	 */
	public static void renderGlow(float partialTicks) {
		PlayerColorModule glow = colorModule("glow");
		Minecraft mc = Minecraft.getMinecraft();
		if (glow == null || glow.size() == 0 || mc.theWorld == null) {
			return;
		}
		RenderManager rm = mc.getRenderManager();
		GlStateManager.disableTexture2D();
		GlStateManager.disableLighting();
		GlStateManager.disableDepth();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GlStateManager.depthMask(false);
		List<EntityPlayer> players = mc.theWorld.playerEntities;
		for (int i = 0; i < players.size(); i++) {
			EntityPlayer p = players.get(i);
			if (p == mc.getRenderViewEntity() && mc.gameSettings.thirdPersonView == 0) {
				continue;
			}
			int c = glow.color(uuid(p));
			if (c == 0 || p.isInvisible()) {
				continue;
			}
			double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * partialTicks - rm.viewerPosX;
			double y = p.lastTickPosY + (p.posY - p.lastTickPosY) * partialTicks - rm.viewerPosY;
			double z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * partialTicks - rm.viewerPosZ;
			double hw = p.width / 2.0 + 0.05;
			AxisAlignedBB bb = new AxisAlignedBB(x - hw, y - 0.02, z - hw, x + hw, y + p.height + 0.05, z + hw);
			RenderGlobal.func_181563_a(bb, (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, 255);
		}
		GlStateManager.depthMask(true);
		GlStateManager.enableDepth();
		GlStateManager.enableTexture2D();
		GlStateManager.disableBlend();
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

	/** RenderPlayer.setModelVisibilities end: hides the body parts the server hid. */
	public static void applyLimbs(AbstractClientPlayer p, ModelPlayer m) {
		LimbModule lm = Apollo.module(LimbModule.class);
		if (lm == null || !lm.isEnabled()) {
			return;
		}
		String id = uuid(p);
		if (lm.isBodyPartHidden(id, 1)) {
			m.bipedHead.showModel = false;
			m.bipedHeadwear.showModel = false;
		}
		if (lm.isBodyPartHidden(id, 2)) {
			m.bipedBody.showModel = false;
			m.bipedBodyWear.showModel = false;
		}
		if (lm.isBodyPartHidden(id, 3)) {
			m.bipedLeftArm.showModel = false;
			m.bipedLeftArmwear.showModel = false;
		}
		if (lm.isBodyPartHidden(id, 4)) {
			m.bipedRightArm.showModel = false;
			m.bipedRightArmwear.showModel = false;
		}
		if (lm.isBodyPartHidden(id, 5)) {
			m.bipedLeftLeg.showModel = false;
			m.bipedLeftLegwear.showModel = false;
		}
		if (lm.isBodyPartHidden(id, 6)) {
			m.bipedRightLeg.showModel = false;
			m.bipedRightLegwear.showModel = false;
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

	/** RenderTNTPrimed.doRender: the countdown label, or null when the module is off. */
	public static String tntLabel(EntityTNTPrimed tnt) {
		TntCountdownModule tm = Apollo.module(TntCountdownModule.class);
		if (tm == null || !tm.isEnabled()) {
			return null;
		}
		long now = Apollo.now();
		// the client counts its fuse down from 80; that dates the spawn
		long spawnedAt = now - Math.max(0, 80 - tnt.fuse) * 50L;
		int ticks = tm.remainingTicks(tnt.getEntityId(), spawnedAt, now);
		float secs = ticks / 20f;
		String color = secs > 2f ? "§a" : secs > 1f ? "§6" : "§c";
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
}
