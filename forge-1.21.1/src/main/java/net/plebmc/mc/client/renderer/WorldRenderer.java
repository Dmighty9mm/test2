package net.plebmc.mc.client.renderer;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.GameRenderer;
import net.plebmc.mc.R;
import net.plebmc.mc.client.renderer.vertex.DefaultVertexFormats;

/**
 * 1.8's WorldRenderer (vertex builder). Vertices are kept here and sent on
 * {@link Tessellator#draw()} with the matching 1.21.1 shader, transformed
 * by the current PoseStack.
 */
public final class WorldRenderer {
	private int mode;
	private DefaultVertexFormats.Format format;
	private float[] v = new float[256 * 8];
	private int n;
	private boolean building;
	private double ox, oy, oz;

	// the vertex being built
	private float x, y, z, u, vv, nx, ny, nz;
	private int r = 255, g = 255, b = 255, a = 255;

	public void begin(int glMode, DefaultVertexFormats.Format fmt) {
		mode = glMode;
		format = fmt;
		n = 0;
		building = true;
		r = g = b = a = 255;
	}

	public boolean isBuilding() {
		return building;
	}

	public void setTranslation(double x, double y, double z) {
		ox = x;
		oy = y;
		oz = z;
	}

	public WorldRenderer pos(double px, double py, double pz) {
		x = (float) (px + ox);
		y = (float) (py + oy);
		z = (float) (pz + oz);
		return this;
	}

	public WorldRenderer tex(double pu, double pv) {
		u = (float) pu;
		vv = (float) pv;
		return this;
	}

	public WorldRenderer color(float pr, float pg, float pb, float pa) {
		return color((int) (pr * 255f), (int) (pg * 255f), (int) (pb * 255f), (int) (pa * 255f));
	}

	public WorldRenderer color(int pr, int pg, int pb, int pa) {
		r = clamp(pr);
		g = clamp(pg);
		b = clamp(pb);
		a = clamp(pa);
		return this;
	}

	public WorldRenderer normal(float px, float py, float pz) {
		nx = px;
		ny = py;
		nz = pz;
		return this;
	}

	public WorldRenderer lightmap(int s, int t) {
		return this;
	}

	private static int clamp(int c) {
		return c < 0 ? 0 : c > 255 ? 255 : c;
	}

	public void endVertex() {
		if ((n + 1) * 8 > v.length) {
			v = java.util.Arrays.copyOf(v, v.length * 2);
		}
		int i = n * 8;
		v[i] = x;
		v[i + 1] = y;
		v[i + 2] = z;
		v[i + 3] = u;
		v[i + 4] = vv;
		v[i + 5] = Float.intBitsToFloat((a << 24) | (r << 16) | (g << 8) | b);
		v[i + 6] = nx;
		v[i + 7] = ny;
		n++;
	}

	void finish(boolean textured) {
		building = false;
		if (n == 0 || format == null) {
			return;
		}
		boolean tex = format.tex && textured;
		boolean col = format.color;
		VertexFormat.Mode m;
		switch (mode) {
		case 1:
			m = VertexFormat.Mode.DEBUG_LINES;
			break;
		case 3:
			m = VertexFormat.Mode.DEBUG_LINE_STRIP;
			break;
		case 4:
			m = VertexFormat.Mode.TRIANGLES;
			break;
		case 5:
			m = VertexFormat.Mode.TRIANGLE_STRIP;
			break;
		case 6:
			m = VertexFormat.Mode.TRIANGLE_FAN;
			break;
		default:
			m = VertexFormat.Mode.QUADS;
		}
		R.flush();
		VertexFormat vf;
		if (tex && col) {
			vf = DefaultVertexFormat.POSITION_TEX_COLOR;
			RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		} else if (tex) {
			vf = DefaultVertexFormat.POSITION_TEX;
			RenderSystem.setShader(GameRenderer::getPositionTexShader);
		} else {
			vf = DefaultVertexFormat.POSITION_COLOR;
			RenderSystem.setShader(GameRenderer::getPositionColorShader);
		}
		Matrix4f mat = R.pose().last().pose();
		BufferBuilder bb = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(m, vf);
		for (int k = 0; k < n; k++) {
			int i = k * 8;
			var vc = bb.addVertex(mat, v[i], v[i + 1], v[i + 2]);
			if (tex) {
				vc.setUv(v[i + 3], v[i + 4]);
			}
			if (col || !tex) {
				vc.setColor(col ? Float.floatToRawIntBits(v[i + 5]) : 0xFFFFFFFF);
			}
		}
		MeshData mesh = bb.build();
		n = 0;
		if (mesh != null) {
			BufferUploader.drawWithShader(mesh);
		}
	}
}
