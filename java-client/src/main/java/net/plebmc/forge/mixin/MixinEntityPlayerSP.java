package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.world.World;
import net.plebmc.client.hud.PlebClient;

/** Apollo border: you can't walk through a border the server cancels. */
@Mixin(EntityPlayerSP.class)
public abstract class MixinEntityPlayerSP extends AbstractClientPlayer {

	public MixinEntityPlayerSP(World world, GameProfile profile) {
		super(world, profile);
	}

	@Override
	public void moveEntity(double x, double y, double z) {
		if (PlebClient.borderBlocks(this.posX, this.posZ, this.posX + x, this.posZ + z)) {
			x = 0.0D;
			z = 0.0D;
		}
		super.moveEntity(x, y, z);
	}
}
