package net.plebmc.mc.potion;

/** 1.8's PotionEffect over a 1.21.1 MobEffectInstance (ids follow 1.8's numbering). */
public class PotionEffect {
	public final net.minecraft.world.effect.MobEffectInstance real;

	public PotionEffect(net.minecraft.world.effect.MobEffectInstance m) {
		real = m;
	}

	public int getPotionID() {
		return net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getId(real.getEffect().value()) + 1;
	}

	public int getAmplifier() {
		return real.getAmplifier();
	}

	public int getDuration() {
		return real.getDuration();
	}

	public boolean getIsPotionDurationMax() {
		return real.isInfiniteDuration();
	}

	public String getEffectName() {
		return real.getDescriptionId();
	}

	public boolean getIsAmbient() {
		return real.isAmbient();
	}
}
