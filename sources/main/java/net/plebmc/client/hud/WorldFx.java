package net.plebmc.client.hud;

import static net.lax1dude.eaglercraft.v1_8.internal.PlatformOpenGL._wglBindFramebuffer;
import static net.lax1dude.eaglercraft.v1_8.internal.PlatformOpenGL._wglBindTexture;
import static net.lax1dude.eaglercraft.v1_8.internal.PlatformOpenGL._wglCreateFramebuffer;
import static net.lax1dude.eaglercraft.v1_8.internal.PlatformOpenGL._wglFramebufferTexture2D;
import static net.lax1dude.eaglercraft.v1_8.internal.PlatformOpenGL._wglTexImage2D;
import static net.lax1dude.eaglercraft.v1_8.internal.PlatformOpenGL._wglTexParameteri;

import java.util.ArrayList;
import java.util.List;

import net.lax1dude.eaglercraft.v1_8.internal.IFramebufferGL;
import net.lax1dude.eaglercraft.v1_8.internal.buffer.ByteBuffer;
import net.lax1dude.eaglercraft.v1_8.opengl.EaglercraftGPU;
import net.lax1dude.eaglercraft.v1_8.opengl.EffectPipelineFXAA;
import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.lax1dude.eaglercraft.v1_8.opengl.OpenGlHelper;
import net.lax1dude.eaglercraft.v1_8.opengl.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.plebmc.client.apollo.Apollo;
import net.plebmc.client.apollo.module.BeamModule;
import net.plebmc.client.apollo.module.PlayerColorModule;
import net.plebmc.client.apollo.module.WaypointModule;

/**
 * World-space Apollo effects, drawn from the EntityRenderer.renderWorldPass
 * hook right after entities:
 * <ul>
 * <li><b>Glow</b>: the 1.9+ "glowing" look. Each glowing player's model is
 * drawn flat into an offscreen mask, the mask is grown by a few pixels and
 * its inside cut away, leaving an outline in the glow color that shows
 * through walls. Only runs while someone is glowing.</li>
 * <li><b>Beams</b>: waypoint and beam module columns as real see-through
 * sky beams, faint through walls and stronger in the open.</li>
 * </ul>
 */
final class WorldFx {

	private static final int GL_TEXTURE_2D = 0x0DE1;
	private static final int GL_FRAMEBUFFER = 0x8D40;
	private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
	private static final int GL_RGBA8 = 0x8058;
	private static final int GL_RGBA = 0x1908;
	private static final int GL_UNSIGNED_BYTE = 0x1401;
	private static final int GL_NEAREST = 0x2600;
	private static final int GL_CLAMP_TO_EDGE = 0x812F;
	private static final int GL_TEXTURE_MAG_FILTER = 0x2800;
	private static final int GL_TEXTURE_MIN_FILTER = 0x2801;
	private static final int GL_TEXTURE_WRAP_S = 0x2802;
	private static final int GL_TEXTURE_WRAP_T = 0x2803;
	private static final int GL_COLOR_BUFFER_BIT = 0x4000;
	private static final int GL_PROJECTION = 0x1701;
	private static final int GL_MODELVIEW = 0x1700;

	private WorldFx() {
	}

	/** One color-only offscreen target. */
	private static final class Target {
		IFramebufferGL fb;
		int tex = -1;
		int w = -1;
		int h = -1;

		void bind(int width, int height) {
			if (fb == null) {
				fb = _wglCreateFramebuffer();
				tex = GlStateManager.generateTexture();
				use();
				_wglTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
				_wglTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
				_wglBindFramebuffer(GL_FRAMEBUFFER, fb);
				_wglFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D,
						EaglercraftGPU.getNativeTexture(tex), 0);
			}
			if (w != width || h != height) {
				w = width;
				h = height;
				use();
				_wglTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE,
						(ByteBuffer) null);
			}
			_wglBindFramebuffer(GL_FRAMEBUFFER, fb);
		}

		/**
		 * Binds the texture on the active unit for real (the state cache can be
		 * out of step with GL here, mid world pass) and makes sure it samples
		 * without mipmaps: a texture left on the default mipmap filter is
		 * "incomplete" and reads as solid black, which blacked out the frame.
		 */
		void use() {
			GlStateManager.bindTexture(tex);
			_wglBindTexture(GL_TEXTURE_2D, EaglercraftGPU.getNativeTexture(tex));
			_wglTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			_wglTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		}
	}

	private static final Target mask = new Target();
	private static final Target outline = new Target();
	private static boolean glowFailed;

	// ==================== glow ====================

	static void renderGlow(Minecraft mc, PlayerColorModule glow, float partialTicks) {
		if (glowFailed || glow == null || glow.size() == 0 || mc.theWorld == null || mc.gameSettings.shaders) {
			return;
		}
		// group the glowing players by color: one mask pass per color
		List<EntityPlayer> players = mc.theWorld.playerEntities;
		List<Integer> colors = null;
		List<List<EntityPlayer>> groups = null;
		for (int i = 0; i < players.size(); i++) {
			EntityPlayer p = players.get(i);
			if (p == mc.getRenderViewEntity() && mc.gameSettings.thirdPersonView == 0) {
				continue;
			}
			int c = glow.color(PlebEntities.uuid(p));
			if (c == 0 || p.isInvisible()) {
				continue;
			}
			if (colors == null) {
				colors = new ArrayList<>(4);
				groups = new ArrayList<>(4);
			}
			int k = colors.indexOf(c);
			if (k < 0) {
				colors.add(c);
				groups.add(new ArrayList<EntityPlayer>(4));
				k = colors.size() - 1;
			}
			groups.get(k).add(p);
		}
		if (colors == null) {
			return;
		}
		int w = mc.displayWidth;
		int h = mc.displayHeight;
		if (w <= 0 || h <= 0) {
			return;
		}
		float[] clear = GlStateManager.getClearColor();
		boolean fog = GlStateManager.isFogEnabled();
		RenderManager rm = mc.getRenderManager();
		int radius = Math.max(2, Math.round(h / 360f));
		try {
			GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
			for (int g = 0; g < colors.size(); g++) {
				// 1. flat silhouettes of this group's players into the mask
				mask.bind(w, h);
				GlStateManager.viewport(0, 0, w, h);
				GlStateManager.clearColor(0f, 0f, 0f, 0f);
				GlStateManager.clear(GL_COLOR_BUFFER_BIT);
				GlStateManager.disableFog();
				GlStateManager.disableDepth();
				GlStateManager.disableBlend();
				rm.setRenderOutlines(true);
				try {
					List<EntityPlayer> group = groups.get(g);
					for (int i = 0; i < group.size(); i++) {
						rm.renderEntityStatic(group.get(i), partialTicks, false);
					}
				} finally {
					rm.setRenderOutlines(false);
				}
				GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);

				// 2. grow the mask into the outline target, then cut the inside away
				outline.bind(w, h);
				GlStateManager.clearColor(1f, 1f, 1f, 0f);
				GlStateManager.clear(GL_COLOR_BUFFER_BIT);
				beginScreen(w, h);
				GlStateManager.enableTexture2D();
				mask.use();
				GlStateManager.enableBlend();
				GlStateManager.colorMask(false, false, false, true);
				GlStateManager.blendFunc(1, 1); // ONE, ONE
				GlStateManager.color(1f, 1f, 1f, 1f);
				for (int a = 0; a < 12; a++) {
					double ang = a * Math.PI / 6.0;
					screenQuad(w, h, (float) (Math.cos(ang) * radius), (float) (Math.sin(ang) * radius));
				}
				GlStateManager.blendFunc(0, 771); // ZERO, ONE_MINUS_SRC_ALPHA
				screenQuad(w, h, 0f, 0f);
				GlStateManager.colorMask(true, true, true, true);

				// 3. the outline over the world, in the glow color
				EffectPipelineFXAA.rebindWorldTarget();
				GlStateManager.viewport(0, 0, w, h);
				outline.use();
				// color blend only: the frame's alpha must stay as it is (the canvas shows through where it's 0)
				GlStateManager.tryBlendFuncSeparate(770, 771, 0, 1);
				int c = colors.get(g);
				GlStateManager.color(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, 1f);
				screenQuad(w, h, 0f, 0f);
				endScreen();
			}
		} catch (Throwable t) {
			glowFailed = true; // never take the frame down: fall back to no glow
			net.lax1dude.eaglercraft.v1_8.log4j.LogManager.getLogger("PlebMc").warn("glow outline disabled: " + t);
			if (inScreen) {
				endScreen();
			}
			EffectPipelineFXAA.rebindWorldTarget();
			GlStateManager.colorMask(true, true, true, true);
			rm.setRenderOutlines(false);
		}
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GlStateManager.viewport(0, 0, w, h);
		GlStateManager.clearColor(clear[0], clear[1], clear[2], clear[3]);
		GlStateManager.color(1f, 1f, 1f, 1f);
		GlStateManager.disableBlend();
		GlStateManager.enableDepth();
		GlStateManager.enableAlpha();
		GlStateManager.enableTexture2D();
		if (fog) {
			GlStateManager.enableFog();
		}
	}

	private static boolean inScreen;

	private static void beginScreen(int w, int h) {
		inScreen = true;
		GlStateManager.matrixMode(GL_PROJECTION);
		GlStateManager.pushMatrix();
		GlStateManager.loadIdentity();
		GlStateManager.ortho(0.0D, w, h, 0.0D, -1.0D, 1.0D);
		GlStateManager.matrixMode(GL_MODELVIEW);
		GlStateManager.pushMatrix();
		GlStateManager.loadIdentity();
		GlStateManager.disableAlpha();
		GlStateManager.disableLighting();
		GlStateManager.disableCull();
		GlStateManager.disableDepth();
	}

	private static void endScreen() {
		inScreen = false;
		GlStateManager.matrixMode(GL_PROJECTION);
		GlStateManager.popMatrix();
		GlStateManager.matrixMode(GL_MODELVIEW);
		GlStateManager.popMatrix();
		GlStateManager.enableCull();
	}

	/** The whole target, shifted by (dx, dy) pixels (offscreen textures are stored bottom-up). */
	private static void screenQuad(int w, int h, float dx, float dy) {
		Tessellator t = Tessellator.getInstance();
		WorldRenderer wr = t.getWorldRenderer();
		wr.begin(7, DefaultVertexFormats.POSITION_TEX);
		wr.pos(dx, h + dy, 0).tex(0, 0).endVertex();
		wr.pos(w + dx, h + dy, 0).tex(1, 0).endVertex();
		wr.pos(w + dx, dy, 0).tex(1, 1).endVertex();
		wr.pos(dx, dy, 0).tex(0, 1).endVertex();
		t.draw();
	}

	// ==================== sky beams ====================

	static void renderBeams(Minecraft mc, float partialTicks) {
		BeamModule bm = Apollo.module(BeamModule.class);
		WaypointModule wm = Apollo.module(WaypointModule.class);
		boolean beams = bm != null && bm.isEnabled() && !bm.all().isEmpty();
		List<WaypointModule.Waypoint> wps = PlebWaypoints.beams(wm);
		List<WaypointModule.Waypoint> marks = PlebWaypoints.visible(wm);
		if (!beams && wps.isEmpty() && marks.isEmpty() || mc.gameSettings.shaders) {
			return;
		}
		RenderManager rm = mc.getRenderManager();
		double ex = rm.viewerPosX;
		double ey = rm.viewerPosY;
		double ez = rm.viewerPosZ;
		double far = Math.max(48.0, mc.gameSettings.renderDistanceChunks * 16.0 * 0.85);
		boolean fog = GlStateManager.isFogEnabled();
		GlStateManager.disableFog();
		GlStateManager.disableTexture2D();
		GlStateManager.disableLighting();
		GlStateManager.disableCull();
		GlStateManager.disableAlpha();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(770, 771, 0, 1);
		GlStateManager.depthMask(false);
		try {
			// faint pass through walls, then a stronger pass where the beam is in the open
			for (int pass = 0; pass < 2; pass++) {
				if (pass == 0) {
					GlStateManager.disableDepth();
				} else {
					GlStateManager.enableDepth();
				}
				float alpha = pass == 0 ? 0.22f : 0.38f;
				if (beams) {
					for (BeamModule.Beam b : bm.all()) {
						beam(b.pos.x + 0.5, b.pos.y, b.pos.z + 0.5, b.argb, alpha, ex, ey, ez, far);
					}
				}
				for (int i = 0; i < wps.size(); i++) {
					WaypointModule.Waypoint wp = wps.get(i);
					beam(wp.pos.x + 0.5, wp.pos.y, wp.pos.z + 0.5, wp.argb, alpha, ex, ey, ez, far);
				}
				// the marked block itself lights up (a squad ping shows what was pinged)
				for (int i = 0; i < marks.size(); i++) {
					WaypointModule.Waypoint wp = marks.get(i);
					blockBox(Math.floor(wp.pos.x), Math.floor(wp.pos.y), Math.floor(wp.pos.z), wp.argb,
							pass == 0 ? 0.16f : 0.3f, ex, ey, ez);
				}
			}
		} finally {
			GlStateManager.depthMask(true);
			GlStateManager.enableDepth();
			GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
			GlStateManager.disableBlend();
			GlStateManager.enableAlpha();
			GlStateManager.enableCull();
			GlStateManager.enableTexture2D();
			GlStateManager.color(1f, 1f, 1f, 1f);
			if (fog) {
				GlStateManager.enableFog();
			}
		}
	}

	/** A see-through box around one block: faint faces and brighter edges. */
	private static void blockBox(double bx, double by, double bz, int argb, float alpha, double ex, double ey, double ez) {
		double x0 = bx - ex - 0.004, y0 = by - ey - 0.004, z0 = bz - ez - 0.004;
		double x1 = x0 + 1.008, y1 = y0 + 1.008, z1 = z0 + 1.008;
		double cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, cz = (z0 + z1) / 2;
		double dist = Math.sqrt(cx * cx + cy * cy + cz * cz);
		if (dist > 160.0) {
			return;
		}
		float r = ((argb >> 16) & 0xFF) / 255f;
		float g = ((argb >> 8) & 0xFF) / 255f;
		float b = (argb & 0xFF) / 255f;
		Tessellator t = Tessellator.getInstance();
		WorldRenderer wr = t.getWorldRenderer();
		wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
		box(wr, x0, y0, z0, x1, y1, z1, r, g, b, alpha);
		// edges as thin bars (WebGL has no wide lines), a little thicker far away
		double e = Math.max(0.02, dist * 0.004);
		float ea = Math.min(1f, alpha * 3.2f);
		for (int i = 0; i < 2; i++) {
			for (int j = 0; j < 2; j++) {
				double ya = i == 0 ? y0 : y1 - e, za = j == 0 ? z0 : z1 - e;
				box(wr, x0, ya, za, x1, ya + e, za + e, r, g, b, ea);
				double xa = i == 0 ? x0 : x1 - e;
				box(wr, xa, y0, za, xa + e, y1, za + e, r, g, b, ea);
				box(wr, xa, j == 0 ? y0 : y1 - e, z0, xa + e, (j == 0 ? y0 : y1 - e) + e, z1, r, g, b, ea);
			}
		}
		t.draw();
	}

	private static void box(WorldRenderer wr, double x0, double y0, double z0, double x1, double y1, double z1, float r,
			float g, float b, float a) {
		double[][] q = { { x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0 }, { x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1 },
				{ x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0 }, { x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0 },
				{ x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1 }, { x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1 } };
		for (double[] f : q) {
			for (int i = 0; i < 12; i += 3) {
				wr.pos(f[i], f[i + 1], f[i + 2]).color(r, g, b, a).endVertex();
			}
		}
	}

	/**
	 * A vertical column from the block to the sky. Beams farther than the
	 * render distance are pulled in along the view ray (and narrowed to
	 * match), so they look the same on screen but are never clipped.
	 */
	private static void beam(double x, double y, double z, int argb, float alpha, double ex, double ey, double ez,
			double far) {
		double dx = x - ex;
		double dz = z - ez;
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist < 3.0) {
			// fade out as the camera gets close, so standing in your own beam
			// (or seeing yourself in third person under one) doesn't fill the screen
			alpha *= (float) Math.max(0.0, (dist - 0.75) / 2.25);
			if (alpha <= 0.01f) {
				return;
			}
		}
		double k = dist > far ? far / dist : 1.0;
		double half = Math.max(0.15, dist * 0.004) * k; // a little wider far away so it stays visible
		double cx = dx * k;
		double cz = dz * k;
		double y0 = (y - ey) * k;
		double y1 = Math.min((256.0 - ey) * k, far);
		if (y1 <= y0) {
			return;
		}
		float r = ((argb >> 16) & 0xFF) / 255f;
		float g = ((argb >> 8) & 0xFF) / 255f;
		float b = (argb & 0xFF) / 255f;
		Tessellator t = Tessellator.getInstance();
		WorldRenderer wr = t.getWorldRenderer();
		wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
		double x0 = cx - half;
		double x1 = cx + half;
		double z0 = cz - half;
		double z1 = cz + half;
		double[][] sides = { { x0, z0, x1, z0 }, { x1, z0, x1, z1 }, { x1, z1, x0, z1 }, { x0, z1, x0, z0 } };
		for (double[] s : sides) {
			wr.pos(s[0], y0, s[1]).color(r, g, b, alpha).endVertex();
			wr.pos(s[2], y0, s[3]).color(r, g, b, alpha).endVertex();
			wr.pos(s[2], y1, s[3]).color(r, g, b, alpha * 0.25f).endVertex();
			wr.pos(s[0], y1, s[1]).color(r, g, b, alpha * 0.25f).endVertex();
		}
		t.draw();
	}
}
