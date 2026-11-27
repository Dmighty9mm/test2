package net.plebmc.mc.client.renderer.entity;

import com.mojang.blaze3d.platform.Lighting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.LivingEntity;
import net.plebmc.mc.R;
import net.plebmc.mc.entity.Entity;
import net.plebmc.mc.entity.EntityLivingBase;

/** 1.8's RenderManager: draws an entity on a screen (the shop's player preview). */
public class RenderManager {
	public static final RenderManager INSTANCE = new RenderManager();
	public double viewerPosX, viewerPosY, viewerPosZ;
	private boolean shadow = true;

	private static EntityRenderDispatcher real() {
		return Minecraft.getInstance().getEntityRenderDispatcher();
	}

	public void setPlayerViewY(float y) {
	}

	public void setRenderShadow(boolean b) {
		shadow = b;
		real().setRenderShadow(b);
	}

	/**
	 * Draws the entity at the current matrix (a GUI preview). The 1.8 fields
	 * the caller set on the wrapper (body, head and look angles) are put on
	 * the real entity for this draw and taken off again after.
	 */
	public boolean renderEntityWithPosYaw(Entity ent, double x, double y, double z, float yaw, float pt) {
		if (R.gg == null || ent == null) {
			return false;
		}
		net.minecraft.world.entity.Entity e = ent.e;
		float yr = e.getYRot(), xr = e.getXRot();
		float yb = 0, ybo = 0, yh = 0, yho = 0;
		LivingEntity le = e instanceof LivingEntity ? (LivingEntity) e : null;
		if (le != null) {
			yb = le.yBodyRot;
			ybo = le.yBodyRotO;
			yh = le.yHeadRot;
			yho = le.yHeadRotO;
		}
		try {
			e.setYRot(ent.rotationYaw);
			e.setXRot(ent.rotationPitch);
			if (le != null && ent instanceof EntityLivingBase l) {
				le.yBodyRot = l.renderYawOffset;
				le.yBodyRotO = l.prevRenderYawOffset;
				le.yHeadRot = l.rotationYawHead;
				le.yHeadRotO = l.prevRotationYawHead;
			}
			var pose = R.gg.pose();
			pose.pushPose();
			// 1.8 flipped x for GUI entities, 1.21 flips z: turn it into the 1.21 transform
			pose.scale(-1f, 1f, -1f);
			Lighting.setupForEntityInInventory();
			EntityRenderDispatcher d = real();
			d.setRenderShadow(false);
			com.mojang.blaze3d.systems.RenderSystem.runAsFancy(
					() -> d.render(e, x, y, z, 0f, 1f, pose, R.gg.bufferSource(), 0xF000F0));
			R.gg.flush();
			d.setRenderShadow(shadow);
			pose.popPose();
			Lighting.setupFor3DItems();
			return true;
		} finally {
			e.setYRot(yr);
			e.setXRot(xr);
			if (le != null) {
				le.yBodyRot = yb;
				le.yBodyRotO = ybo;
				le.yHeadRot = yh;
				le.yHeadRotO = yho;
			}
		}
	}

	public boolean renderEntityStatic(Entity ent, float pt, boolean hide) {
		return false;
	}
}
