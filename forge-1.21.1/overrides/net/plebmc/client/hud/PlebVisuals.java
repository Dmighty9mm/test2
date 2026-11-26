package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.plebmc.client.mods.Mod;
import net.plebmc.client.mods.ModRegistry;
import net.plebmc.client.mods.MoreMods;
import net.plebmc.forge.PlebRenderTypes;

/**
 * The visual mods' game hooks on 1.21.1 (custom crosshair, restyled
 * scoreboard, chat timestamps / stacking, hurt camera, block outline,
 * weather and overlays). Each returns true when it handled the thing, so
 * the vanilla code is skipped. Called from the Forge events and mixins in
 * {@code net.plebmc.forge}.
 */
public final class PlebVisuals {

	private PlebVisuals() {
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

	// ==================== crosshair ====================

	/** Draws the custom crosshair; false = draw the vanilla one. */
	public static boolean crosshair(GuiGraphics gg) {
		ModRegistry r = modsOrNull();
		MoreMods.Crosshair c = r == null ? null : r.crosshair;
		if (!on(c)) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.getDebugOverlay().showDebugScreen()) {
			return false; // the axes crosshair
		}
		if (!mc.options.getCameraType().isFirstPerson()) {
			return true;
		}
		int cx = gg.guiWidth() / 2, cy = gg.guiHeight() / 2;
		int len = c.length(), gap = c.gapPx();
		boolean invert = c.invert.on();
		int col = invert ? 0xFFFFFFFF : c.argb();
		List<int[]> rects = new ArrayList<>();
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
		case 3: { // circle
			int rr = len + gap;
			for (int a = 0; a < 360; a += 15) {
				int px = cx + Math.round((float) Math.cos(Math.toRadians(a)) * rr);
				int py = cy + Math.round((float) Math.sin(Math.toRadians(a)) * rr);
				rects.add(new int[] { px, py, 1, 1 });
			}
			break;
		}
		case 4: { // square outline
			int rr = len / 2 + gap + 1;
			rects.add(new int[] { cx - rr, cy - rr, rr * 2 + 1, 1 });
			rects.add(new int[] { cx - rr, cy + rr, rr * 2 + 1, 1 });
			rects.add(new int[] { cx - rr, cy - rr + 1, 1, rr * 2 - 1 });
			rects.add(new int[] { cx + rr, cy - rr + 1, 1, rr * 2 - 1 });
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
		default: // arrow
			for (int i = 0; i <= len; i++) {
				rects.add(new int[] { cx - i, cy + gap + i, 1, 1 });
				rects.add(new int[] { cx + i, cy + gap + i, 1, 1 });
			}
			break;
		}
		if (c.dot.on() && c.style.get() != 2) {
			rects.add(new int[] { cx, cy, 1, 1 });
		}
		gg.flush();
		RenderSystem.enableBlend();
		if (c.outline.on() && !invert) {
			RenderSystem.defaultBlendFunc();
			List<int[]> outer = new ArrayList<>();
			for (int[] q : rects) {
				outer.add(new int[] { q[0] - 1, q[1] - 1, q[2] + 2, q[3] + 2 });
			}
			quads(gg, outer, 0x90000000);
		}
		if (invert) {
			RenderSystem.blendFuncSeparate(GlConst.ONE_MINUS_DST_COLOR, GlConst.ONE_MINUS_SRC_COLOR, GlConst.ONE, GlConst.ZERO);
		} else {
			RenderSystem.defaultBlendFunc();
		}
		quads(gg, rects, col);
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
		return true;
	}

	private static final class GlConst {
		static final int ZERO = 0, ONE = 1, ONE_MINUS_SRC_COLOR = 769, ONE_MINUS_DST_COLOR = 775;
	}

	private static void quads(GuiGraphics gg, List<int[]> rects, int argb) {
		Matrix4f m = gg.pose().last().pose();
		BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
		for (int[] q : rects) {
			float x0 = q[0], y0 = q[1], x1 = q[0] + q[2], y1 = q[1] + q[3];
			b.addVertex(m, x0, y1, 0).setColor(argb);
			b.addVertex(m, x1, y1, 0).setColor(argb);
			b.addVertex(m, x1, y0, 0).setColor(argb);
			b.addVertex(m, x0, y0, 0).setColor(argb);
		}
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		BufferUploader.drawWithShader(b.buildOrThrow());
	}

	// ==================== scoreboard ====================

	private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparing(PlayerScoreEntry::value).reversed()
			.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

	/** Draws the sidebar with the Scoreboard mod's options; false = vanilla. */
	public static boolean scoreboard(GuiGraphics gg, Objective obj) {
		ModRegistry r = modsOrNull();
		MoreMods.Scoreboard sb = r == null ? null : r.scoreboard;
		if (!on(sb)) {
			return false;
		}
		if (sb.hide.on()) {
			return true;
		}
		Font font = Minecraft.getInstance().font;
		Scoreboard board = obj.getScoreboard();
		NumberFormat fmt = obj.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
		List<PlayerScoreEntry> list = board.listPlayerScores(obj).stream().filter(e -> !e.isHidden()).sorted(ORDER).limit(15L)
				.toList();
		boolean nums = sb.numbers.on();
		boolean shadow = sb.shadow.on();
		int n = list.size();
		Component[] names = new Component[n];
		Component[] scores = new Component[n];
		Component title = obj.getDisplayName();
		int width = font.width(title);
		int sep = font.width(": ");
		for (int i = 0; i < n; i++) {
			PlayerScoreEntry e = list.get(i);
			names[i] = PlayerTeam.formatNameForTeam(board.getPlayersTeam(e.owner()), e.ownerName());
			scores[i] = nums ? e.formatValue(fmt) : Component.empty();
			int sw = font.width(scores[i]);
			width = Math.max(width, font.width(names[i]) + (sw > 0 ? sep + sw : 0));
		}
		float scale = sb.scaleF();
		int sw = Math.round(gg.guiWidth() / scale), sh = Math.round(gg.guiHeight() / scale);
		int fh = 9;
		int bottom = sh / 2 + n * fh / 3;
		boolean left = sb.side.get() == 1;
		int x0 = left ? 3 : sw - width - 3;
		int x1 = x0 + width + 2;
		int bg = sb.backgroundAlpha(false) << 24, bgTitle = sb.backgroundAlpha(true) << 24;
		final int w = width;
		gg.drawManaged(() -> {
			gg.pose().pushPose();
			gg.pose().scale(scale, scale, 1f);
			int top = bottom - n * fh;
			if (bgTitle != 0) {
				gg.fill(x0 - 2, top - fh - 1, x1, top - 1, bgTitle);
			}
			if (bg != 0) {
				gg.fill(x0 - 2, top - 1, x1, bottom, bg);
			}
			gg.drawString(font, title, x0 + w / 2 - font.width(title) / 2, top - fh, -1, shadow);
			for (int i = 0; i < n; i++) {
				int y = bottom - (n - i) * fh;
				gg.drawString(font, names[i], x0, y, -1, shadow);
				if (nums) {
					gg.drawString(font, scores[i], x1 - font.width(scores[i]), y, -1, shadow);
				}
			}
			gg.pose().popPose();
		});
		return true;
	}

	// ==================== hurt camera ====================

	/** Applies the camera tilt from damage, scaled by the Hurt Camera mod; false = vanilla. */
	public static boolean hurtCamera(PoseStack pose, float pt) {
		ModRegistry r = modsOrNull();
		MoreMods.HurtCam hc = r == null ? null : r.hurtCam;
		if (!on(hc)) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.getCameraEntity() instanceof LivingEntity e)) {
			return true;
		}
		float f = e.hurtTime - pt;
		if (e.isDeadOrDying()) {
			float f1 = Math.min(e.deathTime + pt, 20.0F);
			pose.mulPose(Axis.ZP.rotationDegrees(40.0F - 8000.0F / (f1 + 200.0F)));
		}
		float k = hc.factor() * mc.options.damageTiltStrength().get().floatValue();
		if (f < 0.0F || k <= 0f) {
			return true;
		}
		f = f / e.hurtDuration;
		f = Mth.sin(f * f * f * f * (float) Math.PI);
		float yaw = e.getHurtDir();
		pose.mulPose(Axis.YP.rotationDegrees(-yaw));
		pose.mulPose(Axis.ZP.rotationDegrees(-f * 14.0F * k));
		pose.mulPose(Axis.YP.rotationDegrees(yaw));
		return true;
	}

	// ==================== chat ====================

	/**
	 * A chat message on its way into the chat box. Returns true when the Chat
	 * mod printed it itself (with a timestamp, or stacked onto the last one).
	 */
	public static boolean chat(Component msg) {
		if (msg == null) {
			return false;
		}
		ModRegistry r = modsOrNull();
		MoreMods.Chat c = r == null ? null : r.chat;
		if (!on(c)) {
			return false;
		}
		int[] place = c.place(msg.getString());
		MutableComponent out;
		int mode = c.timestamps.get();
		if (mode != 0) {
			java.util.Calendar cal = java.util.Calendar.getInstance();
			String st = MoreMods.Chat.stamp(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), mode);
			out = Component.literal("§8[§7" + st + "§8] §r").append(msg);
		} else {
			out = msg.copy();
		}
		if (place[1] > 1) {
			out.append(Component.literal("§7 (x" + place[1] + ")"));
		}
		net.plebmc.mc.client.gui.GuiNewChat.add(out, place[0]);
		return true;
	}

	// ==================== block outline ====================

	/** Draws the Block Outline mod's box; false = vanilla draws its own. */
	public static boolean blockOutline(Camera cam, HitResult hit, PoseStack pose, MultiBufferSource buffers) {
		ModRegistry r = modsOrNull();
		MoreMods.BlockOutline bo = r == null ? null : r.blockOutline;
		if (!on(bo)) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		Level w = mc.level;
		if (w == null || !(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) {
			return true;
		}
		BlockPos pos = bh.getBlockPos();
		BlockState st = w.getBlockState(pos);
		if (st.isAir() || !w.getWorldBorder().isWithinBounds(pos)) {
			return true;
		}
		VoxelShape shape = st.getShape(w, pos, CollisionContext.of(cam.getEntity()));
		if (shape.isEmpty()) {
			return true;
		}
		var cp = cam.getPosition();
		AABB bb = shape.bounds().move(pos).inflate(0.002D).move(-cp.x, -cp.y, -cp.z);
		long now = System.currentTimeMillis();
		int fill = bo.fillArgb(now);
		if (fill != 0) {
			fillBox(pose, buffers.getBuffer(PlebRenderTypes.FILL), bb, fill);
		}
		int line = bo.lineArgb(now);
		LevelRenderer.renderLineBox(pose, buffers.getBuffer(PlebRenderTypes.lines(bo.lineWidth())), bb, (line >> 16 & 255) / 255f,
				(line >> 8 & 255) / 255f, (line & 255) / 255f, (line >>> 24) / 255f);
		return true;
	}

	private static void fillBox(PoseStack pose, VertexConsumer v, AABB b, int argb) {
		Matrix4f m = pose.last().pose();
		float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
		float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
		float[][] f = {
				{ x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1 }, { x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0 },
				{ x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0 }, { x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1 },
				{ x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0 }, { x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1 } };
		for (float[] q : f) {
			for (int i = 0; i < 12; i += 3) {
				v.addVertex(m, q[i], q[i + 1], q[i + 2]).setColor(argb);
			}
		}
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
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			dead = false;
			return;
		}
		boolean now = mc.player.isDeadOrDying() || mc.player.isRemoved();
		if (now && !dead) {
			ModRegistry r = modsOrNull();
			if (r != null && on(r.deathCoords)) {
				int dim = mc.level.dimension() == Level.NETHER ? -1 : mc.level.dimension() == Level.END ? 1 : 0;
				String m = r.deathCoords.message(mc.player.getX(), mc.player.getY(), mc.player.getZ(), dim);
				mc.gui.getChat().addMessage(Component.literal("§c☠ §7" + m));
			}
		}
		dead = now;
	}
}
