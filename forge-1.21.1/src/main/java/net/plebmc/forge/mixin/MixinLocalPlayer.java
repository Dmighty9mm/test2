package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.plebmc.client.hud.PlebClient;

/** Apollo border: you can't walk through a border the server cancels. */
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {

	@ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
	private Vec3 plebmc$border(Vec3 v, MoverType type) {
		LocalPlayer p = (LocalPlayer) (Object) this;
		if (PlebClient.borderBlocks(p.getX(), p.getZ(), p.getX() + v.x, p.getZ() + v.z)) {
			return new Vec3(0.0, v.y, 0.0);
		}
		return v;
	}
}
