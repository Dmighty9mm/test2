package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.plebmc.client.hud.WorldFx;

/** Apollo glow: players the server colored glow (through walls), in that color. */
@Mixin(Entity.class)
public abstract class MixinEntity {

	@Inject(method = "isCurrentlyGlowing", at = @At("RETURN"), cancellable = true)
	private void plebmc$glowing(CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (!cir.getReturnValueZ() && self instanceof Player && self.level().isClientSide()
				&& WorldFx.glowColor(self.getUUID()) != 0) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getTeamColor", at = @At("RETURN"), cancellable = true)
	private void plebmc$glowColor(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (self instanceof Player && self.level().isClientSide()) {
			int c = WorldFx.glowColor(self.getUUID());
			if (c != 0) {
				cir.setReturnValue(c & 0xFFFFFF);
			}
		}
	}
}
