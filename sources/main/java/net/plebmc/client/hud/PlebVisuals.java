package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.plebmc.client.mods.Mod;
import net.plebmc.client.mods.ModRegistry;
import net.plebmc.client.mods.MoreMods;

/**
 * The visual mods' game hooks: custom crosshair, restyled scoreboard,
 * chat timestamps / stacking and the hurt camera. Each returns true when it
 * handled the thing, so the vanilla code is skipped.
 */
public final class PlebVisuals {

	private PlebVisuals() {
	}

	// ==================== crosshair ====================

	/** Draws the custom crosshair; false = draw the vanilla one. */
	public static boolean crosshair(int w, int h) {
		MoreMods.Crosshair c;
		try {
			c = PlebClient.mods().crosshair;
		} catch (Throwable t) {
			return false;
		}
		if (c == null || !c.isActive()) {
			return false;
		}
		int cx = w / 2, cy = h / 2;
		int len = c.length(), gap = c.gapPx();
		boolean invert = c.invert.on();
		int col = invert ? 0xFFFFFFFF : c.argb();
		GlStateManager.enableBlend();
		GlStateManager.disableTexture2D();
		if (invert) {
			GlStateManager.tryBlendFuncSeparate(775, 769, 1, 0);
		} else {
			GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		}
		List<int[]> rects = new ArrayList<>();
		int t = c.style.get() == 1 ? 1 : 1; // line thickness (1 gui pixel; 2 looks blurry when scaled)
		switch (c.style.get()) {
		case 0: // cross
		case 1: // thin cross
			int th = c.style.get() == 0 ? 2 : 1;
			int o = th / 2;
			rects.add(new int[] { cx - gap - len, cy - o, len, th });
			rects.add(new int[] { cx + gap + (th == 2 ? 0 : 1), cy - o, len, th });
			rects.add(new int[] { cx - o, cy - gap - len, th, len });
			rects.add(new int[] { cx - o, cy + gap + (th == 2 ? 0 : 1), th, len });
			break;
		case 2: // dot
			int d = Math.max(2, len / 2);
			rects.add(new int[] { cx - d / 2, cy - d / 2, d, d });
			break;
		case 3: // circle (an octagon of short segments)
		{
			int r = len + gap;
			for (int a = 0; a < 360; a += 15) {
				int px = cx + Math.round((float) Math.cos(Math.toRadians(a)) * r);
				int py = cy + Math.round((float) Math.sin(Math.toRadians(a)) * r);
				rects.add(new int[] { px, py, 1, 1 });
			}
			break;
		}
		case 4: // square outline
		{
			int r = len / 2 + gap + 1;
			rects.add(new int[] { cx - r, cy - r, r * 2 + 1, 1 });
			rects.add(new int[] { cx - r, cy + r, r * 2 + 1, 1 });
			rects.add(new int[] { cx - r, cy - r + 1, 1, r * 2 - 1 });
			rects.add(new int[] { cx + r, cy - r + 1, 1, r * 2 - 1 });
			break;
		}
		case 5: // X
			for (int i = gap + 1; i <= gap + len; i++) {
				rects.add(new int[] { cx - i, cy - i, 1, 1 });
				rects.add(new int[] { cx + i, cy - i, 1, 1 });
				rects.add(new int[] { cx - i, cy + i, 1, 1 });
				rects.add(new int[] { cx + i, cy + i, 1, 1 });
			}
			break;
		default: // arrow (a chevron pointing up)
			for (int i = 0; i <= len; i++) {
				rects.add(new int[] { cx - i, cy + gap + i, 1, 1 });
				rects.add(new int[] { cx + i, cy + gap + i, 1, 1 });
			}
			break;
		}
		if (c.dot.on() && c.style.get() != 2) {
			rects.add(new int[] { cx - (t == 1 ? 0 : 1), cy - (t == 1 ? 0 : 1), 1 + (t == 1 ? 0 : 1), 1 + (t == 1 ? 0 : 1) });
		}
		if (c.outline.on() && !invert) {
			for (int[] r : rects) {
				Gui.drawRect(r[0] - 1, r[1] - 1, r[0] + r[2] + 1, r[1] + r[3] + 1, 0x90000000);
			}
		}
		for (int[] r : rects) {
			Gui.drawRect(r[0], r[1], r[0] + r[2], r[1] + r[3], col);
		}
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GlStateManager.enableTexture2D();
		GlStateManager.color(1f, 1f, 1f, 1f);
		return true;
	}

	// ==================== scoreboard ====================

	/** Draws the sidebar with the Scoreboard mod's options; false = vanilla. */
	public static boolean scoreboard(ScoreObjective obj, ScaledResolution res) {
		MoreMods.Scoreboard sb;
		try {
			sb = PlebClient.mods().scoreboard;
		} catch (Throwable t) {
			return false;
		}
		if (sb == null || !sb.isActive()) {
			return false;
		}
		if (sb.hide.on()) {
			return true;
		}
		Minecraft mc = Minecraft.getMinecraft();
		FontRenderer fr = mc.fontRendererObj;
		Scoreboard board = obj.getScoreboard();
		Collection<Score> all = board.getSortedScores(obj);
		List<Score> list = new ArrayList<>();
		for (Score s : all) {
			if (s.getPlayerName() != null && !s.getPlayerName().startsWith("#")) {
				list.add(s);
			}
		}
		if (list.size() > 15) {
			list = new ArrayList<>(list.subList(list.size() - 15, list.size()));
		}
		boolean nums = sb.numbers.on();
		boolean shadow = sb.shadow.on();
		int width = fr.getStringWidth(obj.getDisplayName());
		for (Score s : list) {
			String line = ScorePlayerTeam.formatPlayerName(board.getPlayersTeam(s.getPlayerName()), s.getPlayerName());
			if (nums) {
				line += ": " + EnumChatFormatting.RED + s.getScorePoints();
			}
			width = Math.max(width, fr.getStringWidth(line));
		}
		float scale = sb.scaleF();
		int sw = Math.round(res.getScaledWidth() / scale), sh = Math.round(res.getScaledHeight() / scale);
		int fh = fr.FONT_HEIGHT;
		int total = list.size() * fh;
		int bottom = sh / 2 + total / 3;
		boolean left = sb.side.get() == 1;
		int x0 = left ? 3 : sw - width - 3;
		int x1 = x0 + width + 2;
		int bg = sb.backgroundAlpha(false) << 24, bgTitle = sb.backgroundAlpha(true) << 24;
		GlStateManager.pushMatrix();
		GlStateManager.scale(scale, scale, 1f);
		int j = 0;
		for (Score s : list) {
			j++;
			String name = ScorePlayerTeam.formatPlayerName(board.getPlayersTeam(s.getPlayerName()), s.getPlayerName());
			int y = bottom - j * fh;
			if (bg != 0) {
				Gui.drawRect(x0 - 2, y, x1, y + fh, bg);
			}
			fr.drawString(name, x0, y, 0xFFFFFFFF, shadow);
			if (nums) {
				String n = EnumChatFormatting.RED + "" + s.getScorePoints();
				fr.drawString(n, x1 - fr.getStringWidth(n), y, 0xFFFFFFFF, shadow);
			}
			if (j == list.size()) {
				String title = obj.getDisplayName();
				if (bgTitle != 0) {
					Gui.drawRect(x0 - 2, y - fh - 1, x1, y - 1, bgTitle);
					Gui.drawRect(x0 - 2, y - 1, x1, y, bg);
				}
				fr.drawString(title, x0 + width / 2 - fr.getStringWidth(title) / 2, y - fh, 0xFFFFFFFF, shadow);
			}
		}
		GlStateManager.popMatrix();
		GlStateManager.color(1f, 1f, 1f, 1f);
		return true;
	}

	// ==================== hurt camera ====================

	/** Applies the camera tilt from damage, scaled by the Hurt Camera mod; false = vanilla. */
	public static boolean hurtCamera(float partialTicks) {
		MoreMods.HurtCam hc;
		try {
			hc = PlebClient.mods().hurtCam;
		} catch (Throwable t) {
			return false;
		}
		if (hc == null || !hc.isActive()) {
			return false;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (!(mc.getRenderViewEntity() instanceof EntityLivingBase)) {
			return true;
		}
		EntityLivingBase e = (EntityLivingBase) mc.getRenderViewEntity();
		float f = (float) e.hurtTime - partialTicks;
		if (e.getHealth() <= 0.0F) {
			float f1 = (float) e.deathTime + partialTicks;
			GlStateManager.rotate(40.0F - 8000.0F / (f1 + 200.0F), 0.0F, 0.0F, 1.0F);
		}
		float k = hc.factor();
		if (f < 0.0F || k <= 0f) {
			return true;
		}
		f = f / (float) e.maxHurtTime;
		f = MathHelper.sin(f * f * f * f * 3.1415927F);
		float yaw = e.attackedAtYaw;
		GlStateManager.rotate(-yaw, 0.0F, 1.0F, 0.0F);
		GlStateManager.rotate(-f * 14.0F * k, 0.0F, 0.0F, 1.0F);
		GlStateManager.rotate(yaw, 0.0F, 1.0F, 0.0F);
		return true;
	}

	// ==================== chat ====================

	private static boolean printing;

	/**
	 * A chat message on its way into the chat box. Returns true when the Chat
	 * mod printed it itself (with a timestamp, or stacked onto the last one).
	 */
	public static boolean chat(GuiNewChat gui, IChatComponent msg) {
		if (printing || gui == null || msg == null) {
			return false;
		}
		MoreMods.Chat c;
		try {
			c = PlebClient.mods().chat;
		} catch (Throwable t) {
			return false;
		}
		if (c == null || !c.isActive()) {
			return false;
		}
		int[] place = c.place(msg.getUnformattedText());
		IChatComponent out;
		int mode = c.timestamps.get();
		if (mode != 0) {
			java.util.Calendar cal = java.util.Calendar.getInstance();
			String st = MoreMods.Chat.stamp(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), mode);
			out = new ChatComponentText(EnumChatFormatting.DARK_GRAY + "[" + EnumChatFormatting.GRAY + st + EnumChatFormatting.DARK_GRAY
					+ "] " + EnumChatFormatting.RESET);
			out.appendSibling(msg);
		} else {
			out = msg.createCopy();
		}
		if (place[1] > 1) {
			out.appendSibling(new ChatComponentText(EnumChatFormatting.GRAY + " (x" + place[1] + ")"));
		}
		printing = true;
		try {
			gui.printChatMessageWithOptionalDeletion(out, place[0]);
		} finally {
			printing = false;
		}
		return true;
	}

	private static ModRegistry modsOrNull() {
		try {
			return PlebClient.mods();
		} catch (Throwable t) {
			return null;
		}
	}

	private static boolean on(Mod m) {
		return m != null && m.isActive();
	}

	// ==================== block outline ====================

	/** Draws the Block Outline mod's box; false = vanilla draws its own. */
	public static boolean blockOutline(EntityPlayer player, MovingObjectPosition hit, float pt) {
		ModRegistry r = modsOrNull();
		MoreMods.BlockOutline bo = r == null ? null : r.blockOutline;
		if (!on(bo)) {
			return false;
		}
		World w = Minecraft.getMinecraft().theWorld;
		if (w == null || player == null || hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
			return true;
		}
		BlockPos pos = hit.getBlockPos();
		Block b = w.getBlockState(pos).getBlock();
		if (b.getMaterial() == Material.air || !w.getWorldBorder().contains(pos)) {
			return true;
		}
		b.setBlockBoundsBasedOnState(w, pos);
		double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * pt;
		double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * pt;
		double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * pt;
		AxisAlignedBB bb = b.getSelectedBoundingBox(w, pos).expand(0.002D, 0.002D, 0.002D).offset(-x, -y, -z);
		long now = System.currentTimeMillis();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GlStateManager.disableTexture2D();
		GlStateManager.depthMask(false);
		int fill = bo.fillArgb(now);
		if (fill != 0) {
			color(fill);
			fillBox(bb);
		}
		color(bo.lineArgb(now));
		// browsers draw every GL line one pixel wide whatever width is asked for, so the
		// outline is 12 thin bars, sized by distance to look bo.lineWidth() pixels thick
		double cx = (bb.minX + bb.maxX) / 2, cy = (bb.minY + bb.maxY) / 2 - player.getEyeHeight(), cz = (bb.minZ + bb.maxZ) / 2;
		double dist = Math.max(0.6, Math.sqrt(cx * cx + cy * cy + cz * cz));
		int sh = Math.max(240, Minecraft.getMinecraft().displayHeight);
		double hw = Math.max(0.0015, bo.lineWidth() * 0.5 * dist * 1.4 / sh);
		GlStateManager.disableCull();
		edgeBars(bb, hw);
		GlStateManager.enableCull();
		GlStateManager.depthMask(true);
		GlStateManager.enableTexture2D();
		GlStateManager.disableBlend();
		GlStateManager.color(1f, 1f, 1f, 1f);
		return true;
	}

	private static void color(int argb) {
		GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
	}

	/** The 12 edges of {@code b} as bars {@code hw} thick from their middle line. */
	private static void edgeBars(AxisAlignedBB b, double hw) {
		double[] xs = { b.minX, b.maxX }, ys = { b.minY, b.maxY }, zs = { b.minZ, b.maxZ };
		for (int i = 0; i < 2; i++) {
			for (int j = 0; j < 2; j++) {
				fillBox(new AxisAlignedBB(b.minX - hw, ys[i] - hw, zs[j] - hw, b.maxX + hw, ys[i] + hw, zs[j] + hw));
				fillBox(new AxisAlignedBB(xs[i] - hw, b.minY + hw, zs[j] - hw, xs[i] + hw, b.maxY - hw, zs[j] + hw));
				fillBox(new AxisAlignedBB(xs[i] - hw, ys[j] - hw, b.minZ + hw, xs[i] + hw, ys[j] + hw, b.maxZ - hw));
			}
		}
	}

	private static void fillBox(AxisAlignedBB b) {
		Tessellator t = Tessellator.getInstance();
		net.lax1dude.eaglercraft.v1_8.opengl.WorldRenderer wr = t.getWorldRenderer();
		wr.begin(7, DefaultVertexFormats.POSITION);
		// bottom, top
		wr.pos(b.minX, b.minY, b.minZ).endVertex(); wr.pos(b.maxX, b.minY, b.minZ).endVertex();
		wr.pos(b.maxX, b.minY, b.maxZ).endVertex(); wr.pos(b.minX, b.minY, b.maxZ).endVertex();
		wr.pos(b.minX, b.maxY, b.minZ).endVertex(); wr.pos(b.minX, b.maxY, b.maxZ).endVertex();
		wr.pos(b.maxX, b.maxY, b.maxZ).endVertex(); wr.pos(b.maxX, b.maxY, b.minZ).endVertex();
		// north, south
		wr.pos(b.minX, b.minY, b.minZ).endVertex(); wr.pos(b.minX, b.maxY, b.minZ).endVertex();
		wr.pos(b.maxX, b.maxY, b.minZ).endVertex(); wr.pos(b.maxX, b.minY, b.minZ).endVertex();
		wr.pos(b.minX, b.minY, b.maxZ).endVertex(); wr.pos(b.maxX, b.minY, b.maxZ).endVertex();
		wr.pos(b.maxX, b.maxY, b.maxZ).endVertex(); wr.pos(b.minX, b.maxY, b.maxZ).endVertex();
		// west, east
		wr.pos(b.minX, b.minY, b.minZ).endVertex(); wr.pos(b.minX, b.minY, b.maxZ).endVertex();
		wr.pos(b.minX, b.maxY, b.maxZ).endVertex(); wr.pos(b.minX, b.maxY, b.minZ).endVertex();
		wr.pos(b.maxX, b.minY, b.minZ).endVertex(); wr.pos(b.maxX, b.maxY, b.minZ).endVertex();
		wr.pos(b.maxX, b.maxY, b.maxZ).endVertex(); wr.pos(b.maxX, b.minY, b.maxZ).endVertex();
		t.draw();
	}

	// ==================== weather and overlays ====================

	/** Clear Weather: skip drawing rain and snow. */
	public static boolean hideWeather() {
		ModRegistry r = modsOrNull();
		return r != null && on(r.clearWeather);
	}

	/** Clear Weather: skip rain splash particles and the rain sound. */
	public static boolean hideSplashes() {
		ModRegistry r = modsOrNull();
		return r != null && on(r.clearWeather) && r.clearWeather.splashes.on();
	}

	public static boolean hidePumpkin() {
		ModRegistry r = modsOrNull();
		return r != null && on(r.overlays) && r.overlays.pumpkin.on();
	}

	public static boolean hideVignette() {
		ModRegistry r = modsOrNull();
		return r != null && on(r.overlays) && r.overlays.vignette.on();
	}

	/** How far to push the on-fire overlay down: 0 = normal, -1 = hide it. */
	public static float fireDrop() {
		ModRegistry r = modsOrNull();
		return r != null && on(r.overlays) ? r.overlays.fireDrop() : 0f;
	}

	// ==================== death coordinates ====================

	private static boolean dead;

	/** Called every HUD frame: prints where you died once, when you die. */
	public static void deathCheck() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer == null || mc.theWorld == null) {
			dead = false;
			return;
		}
		boolean now = mc.thePlayer.getHealth() <= 0f || mc.thePlayer.isDead;
		if (now && !dead) {
			ModRegistry r = modsOrNull();
			if (r != null && on(r.deathCoords)) {
				String m = r.deathCoords.message(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, mc.thePlayer.dimension);
				mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(EnumChatFormatting.RED + "\u2620 "
						+ EnumChatFormatting.GRAY + m));
			}
		}
		dead = now;
	}
}
