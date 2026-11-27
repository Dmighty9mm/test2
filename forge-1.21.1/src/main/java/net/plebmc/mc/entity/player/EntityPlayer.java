package net.plebmc.mc.entity.player;

import net.plebmc.mc.entity.EntityLivingBase;

/** 1.8's EntityPlayer over a 1.21.1 Player. */
public class EntityPlayer extends EntityLivingBase {
	public final net.minecraft.world.entity.player.Player pl;
	public int experienceLevel;
	public float experience;
	public final InventoryPlayer inventory;
	public double chasingPosX, chasingPosY, chasingPosZ, prevChasingPosX, prevChasingPosY, prevChasingPosZ;
	public float cameraYaw, prevCameraYaw;

	public EntityPlayer(net.minecraft.world.entity.player.Player p) {
		super(p);
		this.pl = p;
		this.inventory = new InventoryPlayer(this);
	}

	@Override
	protected void sync() {
		super.sync();
		experienceLevel = pl.experienceLevel;
		experience = pl.experienceProgress;
		chasingPosX = pl.xCloak;
		chasingPosY = pl.yCloak;
		chasingPosZ = pl.zCloak;
		prevChasingPosX = pl.xCloakO;
		prevChasingPosY = pl.yCloakO;
		prevChasingPosZ = pl.zCloakO;
		cameraYaw = pl.bob;
		prevCameraYaw = pl.oBob;
	}

	public boolean isSpectator() {
		return pl.isSpectator();
	}

	public boolean isUsingItem() {
		return pl.isUsingItem();
	}

	public net.plebmc.mc.util.FoodStats getFoodStats() {
		return new net.plebmc.mc.util.FoodStats(pl.getFoodData());
	}

	public com.mojang.authlib.GameProfile getGameProfile() {
		return pl.getGameProfile();
	}

	public boolean isWearing(int part) {
		return true;
	}

	/** 1.8's InventoryPlayer, the parts PlebClient reads. */
	public static final class InventoryPlayer {
		private final EntityPlayer p;
		public int currentItem;

		InventoryPlayer(EntityPlayer p) {
			this.p = p;
		}

		/** 0 boots .. 3 helmet. */
		public net.plebmc.mc.item.ItemStack armorItemInSlot(int i) {
			return p.getCurrentArmor(i);
		}

		public net.plebmc.mc.item.ItemStack getCurrentItem() {
			return p.getHeldItem();
		}

		public net.plebmc.mc.item.ItemStack getStackInSlot(int i) {
			return net.plebmc.mc.item.ItemStack.of(p.pl.getInventory().getItem(i));
		}

		public int getSizeInventory() {
			return p.pl.getInventory().getContainerSize();
		}
	}
}
