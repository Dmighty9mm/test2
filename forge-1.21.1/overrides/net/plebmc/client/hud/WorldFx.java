package net.plebmc.client.hud;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.plebmc.client.apollo.Apollo;
import net.plebmc.client.apollo.module.BeamModule;
import net.plebmc.client.apollo.module.PlayerColorModule;
import net.plebmc.client.apollo.module.WaypointModule;
import net.plebmc.forge.compat.GlCompat;
import net.plebmc.mc.client.Minecraft;

/**
 * World-space Apollo effects on 1.21.1:
 * <ul>
 * <li><b>Glow</b>: 1.21 has the glowing outline itself; a mixin makes the
 * players the server colored glow, in that color (see {@link #glowColor}).</li>
 * <li><b>Beams</b>: waypoint and beam module columns as see-through sky
 * beams, faint through walls and stronger in the open. Drawn after the
 * world's particles, camera-relative.</li>
 * </ul>
 */
public final class WorldFx {

	private WorldFx() {
	}

	/** Drawing is done by the game's own glowing effect on 1.21.1. */
	static void renderGlow(Minecraft mc, PlayerColorModule glow, float partialTicks) {
	}

	/** The glow color the server gave this player (0 = not glowing). */
	public static int glowColor(java.util.UUID u) {
		return color("glow", u);
	}

	/** The fire color the server gave this player (0 = vanilla fire). */
	public static int fireColor(java.util.UUID u) {
		return color("colored_fire", u);
	}

	private static int color(String module, java.util.UUID u) {
		Object m = Apollo.module(module);
		if (u == null || !(m instanceof PlayerColorModule) || !((PlayerColorModule) m).isEnabled()) {
			return 0;
		}
		return ((PlayerColorModule) m).color(net.plebmc.client.apollo.module.Ids.uuid(u.getMostSignificantBits(), u.getLeastSignificantBits()));
	}

	static void renderBeams(Minecraft mc, float partialTicks) {
		BeamModule bm = Apollo.module(BeamModule.class);
		WaypointModule wm = Apollo.module(WaypointModule.class);
		boolean beams = bm != null && bm.isEnabled() && !bm.all().isEmpty();
		List<WaypointModule.Waypoint> wps = wm != null && wm.isEnabled() ? wm.visible("") : null;
		if (!beams && (wps == null || wps.isEmpty()) || GlCompat.shadersOn()) {
			return;
		}
		net.minecraft.client.Minecraft real = net.minecraft.client.Minecraft.getInstance();
		Vec3 cam = real.gameRenderer.getMainCamera().getPosition();
		double far = Math.max(48.0, real.options.getEffectiveRenderDistance() * 16.0 * 0.85);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(770, 771, 0, 1);
		RenderSystem.depthMask(false);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		try {
			for (int pass = 0; pass < 2; pass++) {
				if (pass == 0) {
					RenderSystem.disableDepthTest();
				} else {
					RenderSystem.enableDepthTest();
				}
				float alpha = pass == 0 ? 0.22f : 0.38f;
				BufferBuilder wr = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
				if (beams) {
					for (BeamModule.Beam b : bm.all()) {
						beam(wr, b.pos.x + 0.5, b.pos.y, b.pos.z + 0.5, b.argb, alpha, cam.x, cam.y, cam.z, far);
					}
				}
				if (wps != null) {
					for (int i = 0; i < wps.size(); i++) {
						WaypointModule.Waypoint wp = wps.get(i);
						beam(wr, wp.pos.x + 0.5, wp.pos.y, wp.pos.z + 0.5, wp.argb, alpha, cam.x, cam.y, cam.z, far);
					}
				}
				MeshData mesh = wr.build();
				if (mesh != null) {
					BufferUploader.drawWithShader(mesh);
				}
			}
		} finally {
			RenderSystem.depthMask(true);
			RenderSystem.enableDepthTest();
			RenderSystem.defaultBlendFunc();
			RenderSystem.disableBlend();
			RenderSystem.enableCull();
		}
	}

	/**
	 * A vertical column from the block to the sky. Beams farther than the
	 * render distance are pulled in along the view ray (and narrowed to
	 * match), so they look the same on screen but are never clipped.
	 */
	private static void beam(BufferBuilder wr, double x, double y, double z, int argb, float alpha, double ex, double ey,
			double ez, double far) {
		double dx = x - ex;
		double dz = z - ez;
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist < 3.0) {
			alpha *= (float) Math.max(0.0, (dist - 0.75) / 2.25);
			if (alpha <= 0.01f) {
				return;
			}
		}
		double k = dist > far ? far / dist : 1.0;
		double half = Math.max(0.15, dist * 0.004) * k;
		double cx = dx * k;
		double cz = dz * k;
		double y0 = (y - ey) * k;
		double y1 = Math.min((320.0 - ey) * k, far);
		if (y1 <= y0) {
			return;
		}
		float r = ((argb >> 16) & 0xFF) / 255f;
		float g = ((argb >> 8) & 0xFF) / 255f;
		float b = (argb & 0xFF) / 255f;
		float x0 = (float) (cx - half), x1 = (float) (cx + half), z0 = (float) (cz - half), z1 = (float) (cz + half);
		float fy0 = (float) y0, fy1 = (float) y1;
		float[][] sides = { { x0, z0, x1, z0 }, { x1, z0, x1, z1 }, { x1, z1, x0, z1 }, { x0, z1, x0, z0 } };
		for (float[] s : sides) {
			wr.addVertex(s[0], fy0, s[1]).setColor(r, g, b, alpha);
			wr.addVertex(s[2], fy0, s[3]).setColor(r, g, b, alpha);
			wr.addVertex(s[2], fy1, s[3]).setColor(r, g, b, alpha * 0.25f);
			wr.addVertex(s[0], fy1, s[1]).setColor(r, g, b, alpha * 0.25f);
		}
	}
}
