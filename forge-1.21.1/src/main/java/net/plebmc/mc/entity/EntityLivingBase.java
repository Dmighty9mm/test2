package net.plebmc.mc.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.plebmc.mc.item.ItemStack;
import net.plebmc.mc.potion.PotionEffect;

/** 1.8's EntityLivingBase over a 1.21.1 LivingEntity. */
public class EntityLivingBase extends Entity {
	public final net.minecraft.world.entity.LivingEntity le;
	public float renderYawOffset, prevRenderYawOffset, rotationYawHead, prevRotationYawHead;
	public int hurtTime, maxHurtTime, deathTime;
	public float attackedAtYaw;
	public float swingProgress;
	public float limbSwing, limbSwingAmount, prevLimbSwingAmount;

	public EntityLivingBase(net.minecraft.world.entity.LivingEntity e) {
		super(e);
		this.le = e;
	}

	@Override
	protected void sync() {
		super.sync();
		renderYawOffset = le.yBodyRot;
		prevRenderYawOffset = le.yBodyRotO;
		rotationYawHead = le.yHeadRot;
		prevRotationYawHead = le.yHeadRotO;
		hurtTime = le.hurtTime;
		maxHurtTime = le.hurtDuration;
		deathTime = le.deathTime;
		attackedAtYaw = 0f;
		swingProgress = le.attackAnim;
		limbSwing = le.walkAnimation.position();
		limbSwingAmount = le.walkAnimation.speed();
		prevLimbSwingAmount = limbSwingAmount;
		isDead = isDead || le.isDeadOrDying();
	}

	public float getHealth() {
		return le.getHealth();
	}

	public float getMaxHealth() {
		return le.getMaxHealth();
	}

	public float getAbsorptionAmount() {
		return le.getAbsorptionAmount();
	}

	public ItemStack getHeldItem() {
		return ItemStack.of(le.getMainHandItem());
	}

	/** 1.8 slots: 0 held, 1 boots, 2 legs, 3 chest, 4 head. */
	public ItemStack getEquipmentInSlot(int slot) {
		if (slot == 0) {
			return getHeldItem();
		}
		return getCurrentArmor(slot - 1);
	}

	/** 0 boots .. 3 helmet. */
	public ItemStack getCurrentArmor(int i) {
		net.minecraft.world.entity.EquipmentSlot s = i == 0 ? net.minecraft.world.entity.EquipmentSlot.FEET
				: i == 1 ? net.minecraft.world.entity.EquipmentSlot.LEGS
						: i == 2 ? net.minecraft.world.entity.EquipmentSlot.CHEST : net.minecraft.world.entity.EquipmentSlot.HEAD;
		return ItemStack.of(le.getItemBySlot(s));
	}

	public Collection<PotionEffect> getActivePotionEffects() {
		List<PotionEffect> out = new ArrayList<>();
		for (net.minecraft.world.effect.MobEffectInstance m : le.getActiveEffects()) {
			out.add(new PotionEffect(m));
		}
		return out;
	}

	public boolean isPotionActive(int id) {
		for (PotionEffect p : getActivePotionEffects()) {
			if (p.getPotionID() == id) {
				return true;
			}
		}
		return false;
	}

	public boolean isChild() {
		return le.isBaby();
	}
}
