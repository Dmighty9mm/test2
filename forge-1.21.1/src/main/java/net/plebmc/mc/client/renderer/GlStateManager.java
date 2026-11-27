package net.plebmc.mc.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;

import net.plebmc.mc.R;

/** 1.8's GlStateManager over 1.21.1's RenderSystem and the current PoseStack. */
public final class GlStateManager {
	private GlStateManager() {
	}

	private static float cr = 1f, cg = 1f, cb = 1f, ca = 1f;

	public static void pushMatrix() {
		R.pose().pushPose();
	}

	public static void popMatrix() {
		R.pose().popPose();
	}

	public static void translate(float x, float y, float z) {
		R.pose().translate(x, y, z);
	}

	public static void translate(double x, double y, double z) {
		R.pose().translate(x, y, z);
	}

	public static void scale(float x, float y, float z) {
		R.pose().scale(x, y, z);
	}

	public static void scale(double x, double y, double z) {
		R.pose().scale((float) x, (float) y, (float) z);
	}

	public static void rotate(float angle, float x, float y, float z) {
		if (x != 0f || y != 0f || z != 0f) {
			R.pose().mulPose(new org.joml.Quaternionf().rotationAxis((float) Math.toRadians(angle), x, y, z));
		}
	}

	public static void color(float r, float g, float b, float a) {
		cr = r;
		cg = g;
		cb = b;
		ca = a;
		RenderSystem.setShaderColor(r, g, b, a);
	}

	public static void color(float r, float g, float b) {
		color(r, g, b, 1f);
	}

	public static void resetColor() {
		color(1f, 1f, 1f, 1f);
	}

	public static float[] currentColor() {
		return new float[] { cr, cg, cb, ca };
	}

	public static void enableBlend() {
		RenderSystem.enableBlend();
	}

	public static void disableBlend() {
		RenderSystem.disableBlend();
	}

	public static void blendFunc(int s, int d) {
		RenderSystem.blendFunc(s, d);
	}

	public static void tryBlendFuncSeparate(int a, int b, int c, int d) {
		RenderSystem.blendFuncSeparate(a, b, c, d);
	}

	public static void enableDepth() {
		RenderSystem.enableDepthTest();
	}

	public static void disableDepth() {
		RenderSystem.disableDepthTest();
	}

	public static void depthMask(boolean b) {
		RenderSystem.depthMask(b);
	}

	public static void depthFunc(int f) {
		RenderSystem.depthFunc(f);
	}

	public static void enableCull() {
		RenderSystem.enableCull();
	}

	public static void disableCull() {
		RenderSystem.disableCull();
	}

	public static void colorMask(boolean r, boolean g, boolean b, boolean a) {
		RenderSystem.colorMask(r, g, b, a);
	}

	public static void clearColor(float r, float g, float b, float a) {
		RenderSystem.clearColor(r, g, b, a);
	}

	public static void clear(int mask) {
		RenderSystem.clear(mask, net.minecraft.client.Minecraft.ON_OSX);
	}

	public static void bindTexture(int id) {
		RenderSystem.setShaderTexture(0, id);
		Tessellator.textured = true;
	}

	public static int generateTexture() {
		return com.mojang.blaze3d.platform.GlStateManager._genTexture();
	}

	public static void deleteTexture(int id) {
		com.mojang.blaze3d.platform.GlStateManager._deleteTexture(id);
	}

	public static void viewport(int x, int y, int w, int h) {
		RenderSystem.viewport(x, y, w, h);
	}

	// 1.8 fixed-function switches: shaders decide these on 1.21.1
	public static void enableTexture2D() {
		Tessellator.textured = true;
	}

	public static void disableTexture2D() {
		Tessellator.textured = false;
	}

	public static void enableAlpha() {
	}

	public static void disableAlpha() {
	}

	public static void alphaFunc(int f, float ref) {
	}

	public static void shadeModel(int m) {
	}

	public static void enableLighting() {
	}

	public static void disableLighting() {
	}

	public static void enableColorMaterial() {
	}

	public static void disableColorMaterial() {
	}

	public static void enableRescaleNormal() {
	}

	public static void disableRescaleNormal() {
	}

	public static void enableFog() {
	}

	public static void disableFog() {
	}

	public static void matrixMode(int m) {
	}

	public static void loadIdentity() {
	}

	public static void ortho(double l, double r, double b, double t, double n, double f) {
	}

	public static void enablePolygonOffset() {
		RenderSystem.enablePolygonOffset();
	}

	public static void disablePolygonOffset() {
		RenderSystem.disablePolygonOffset();
	}

	public static void doPolygonOffset(float a, float b) {
		RenderSystem.polygonOffset(a, b);
	}

	static Axis unused;

	public static void setActiveTexture(int unit) {
		RenderSystem.activeTexture(unit);
	}
}
