package net.plebmc.mc.util;

/** 1.8's FoodStats. */
public class FoodStats {
	private final net.minecraft.world.food.FoodData d;

	public FoodStats(net.minecraft.world.food.FoodData d) {
		this.d = d;
	}

	public int getFoodLevel() {
		return d.getFoodLevel();
	}

	public float getSaturationLevel() {
		return d.getSaturationLevel();
	}

	public boolean needFood() {
		return d.needsFood();
	}
}
