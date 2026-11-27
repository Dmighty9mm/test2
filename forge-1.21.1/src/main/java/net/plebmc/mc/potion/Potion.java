package net.plebmc.mc.potion;

/** 1.8's Potion: looked up by 1.8-style id. */
public class Potion {
	public final net.minecraft.world.effect.MobEffect real;
	public static final Potion[] potionTypes = new Potion[64];

	static {
		var reg = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT;
		for (var e : reg) {
			int id = reg.getId(e) + 1;
			if (id > 0 && id < potionTypes.length) {
				potionTypes[id] = new Potion(e);
			}
		}
	}

	Potion(net.minecraft.world.effect.MobEffect e) {
		real = e;
	}

	public String getName() {
		return real.getDescriptionId();
	}

	public boolean isBadEffect() {
		return real.getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL;
	}

	public int getLiquidColor() {
		return real.getColor();
	}

	public static String getDurationString(PotionEffect e) {
		if (e.getIsPotionDurationMax()) {
			return "**:**";
		}
		int s = e.getDuration() / 20;
		return String.format("%d:%02d", s / 60, s % 60);
	}
}
