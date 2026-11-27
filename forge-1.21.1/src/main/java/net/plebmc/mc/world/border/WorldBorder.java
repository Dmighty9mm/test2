package net.plebmc.mc.world.border;

/** 1.8's WorldBorder over 1.21.1's. */
public class WorldBorder {
	public final net.minecraft.world.level.border.WorldBorder real;

	public WorldBorder(net.minecraft.world.level.border.WorldBorder b) {
		real = b;
	}

	public double getCenterX() {
		return real.getCenterX();
	}

	public double getCenterZ() {
		return real.getCenterZ();
	}

	public double getDiameter() {
		return real.getSize();
	}

	public double getTargetSize() {
		return real.getLerpTarget();
	}

	public long getTimeUntilTarget() {
		return real.getLerpRemainingTime();
	}

	public EnumBorderStatus getStatus() {
		var s = real.getStatus();
		return s == net.minecraft.world.level.border.BorderStatus.GROWING ? EnumBorderStatus.GROWING
				: s == net.minecraft.world.level.border.BorderStatus.SHRINKING ? EnumBorderStatus.SHRINKING
						: EnumBorderStatus.STATIONARY;
	}

	public void setTransition(double from, double to, long ms) {
		real.lerpSizeBetween(from, to, ms);
	}

	public void setTransition(double size) {
		real.setSize(size);
	}

	public void setCenter(double x, double z) {
		real.setCenter(x, z);
	}
}
