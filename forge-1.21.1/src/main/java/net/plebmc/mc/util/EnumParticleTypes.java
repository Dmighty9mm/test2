package net.plebmc.mc.util;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

/** 1.8's particle names, mapped to 1.21.1 particles. */
public enum EnumParticleTypes {
	FLAME, HEART, NOTE, REDSTONE, CLOUD, PORTAL, VILLAGER_HAPPY, SNOW_SHOVEL, SMOKE_NORMAL, CRIT, CRIT_MAGIC, SPELL_WITCH,
	FIREWORKS_SPARK, WATER_DROP, LAVA, EXPLOSION_NORMAL, ENCHANTMENT_TABLE;

	/** The 1.21 particle; REDSTONE takes its color from the speed values, like 1.8. */
	public ParticleOptions options(double r, double g, double b) {
		switch (this) {
		case FLAME:
			return ParticleTypes.FLAME;
		case HEART:
			return ParticleTypes.HEART;
		case NOTE:
			return ParticleTypes.NOTE;
		case REDSTONE:
			return new net.minecraft.core.particles.DustParticleOptions(
					new org.joml.Vector3f((float) Math.max(0, r), (float) Math.max(0, g), (float) Math.max(0, b)), 1f);
		case CLOUD:
			return ParticleTypes.CLOUD;
		case PORTAL:
			return ParticleTypes.PORTAL;
		case VILLAGER_HAPPY:
			return ParticleTypes.HAPPY_VILLAGER;
		case SNOW_SHOVEL:
			return ParticleTypes.ITEM_SNOWBALL;
		case CRIT:
			return ParticleTypes.CRIT;
		case CRIT_MAGIC:
			return ParticleTypes.ENCHANTED_HIT;
		case SPELL_WITCH:
			return ParticleTypes.WITCH;
		case FIREWORKS_SPARK:
			return ParticleTypes.FIREWORK;
		case WATER_DROP:
			return ParticleTypes.DRIPPING_WATER;
		case LAVA:
			return ParticleTypes.LAVA;
		case EXPLOSION_NORMAL:
			return ParticleTypes.POOF;
		case ENCHANTMENT_TABLE:
			return ParticleTypes.ENCHANT;
		default:
			return ParticleTypes.SMOKE;
		}
	}

	/** Whether the speed values are a color (1.8 colored particles), not a motion. */
	public boolean colored() {
		return this == REDSTONE;
	}
}
