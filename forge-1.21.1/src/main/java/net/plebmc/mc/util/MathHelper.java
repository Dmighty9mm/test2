package net.plebmc.mc.util;

/** 1.8's MathHelper. */
public final class MathHelper {
	private MathHelper() {
	}

	public static float sin(float f) {
		return net.minecraft.util.Mth.sin(f);
	}

	public static float cos(float f) {
		return net.minecraft.util.Mth.cos(f);
	}

	public static float sqrt_float(float f) {
		return (float) Math.sqrt(f);
	}

	public static float sqrt_double(double d) {
		return (float) Math.sqrt(d);
	}

	public static int floor_double(double d) {
		return net.minecraft.util.Mth.floor(d);
	}

	public static int floor_float(float d) {
		return net.minecraft.util.Mth.floor(d);
	}

	public static float clamp_float(float v, float a, float b) {
		return v < a ? a : v > b ? b : v;
	}

	public static int clamp_int(int v, int a, int b) {
		return v < a ? a : v > b ? b : v;
	}

	public static double clamp_double(double v, double a, double b) {
		return v < a ? a : v > b ? b : v;
	}

	public static float wrapAngleTo180_float(float a) {
		return net.minecraft.util.Mth.wrapDegrees(a);
	}

	public static double wrapAngleTo180_double(double a) {
		return net.minecraft.util.Mth.wrapDegrees(a);
	}

	public static int ceiling_double_int(double d) {
		return net.minecraft.util.Mth.ceil(d);
	}

	public static double abs_max(double a, double b) {
		return net.minecraft.util.Mth.absMax(a, b);
	}
}
