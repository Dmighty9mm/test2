package net.plebmc.forge.mixin;

import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.plebmc.client.hud.WorldFx;

/** Apollo colored fire: tints the fire on burning players. */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {

	@Unique
	private static int plebmc$fire = -1;

	@Inject(method = "renderFlame", at = @At("HEAD"))
	private void plebmc$fireStart(PoseStack pose, MultiBufferSource buf, Entity e, Quaternionf q, CallbackInfo ci) {
		int c = e instanceof Player ? WorldFx.fireColor(e.getUUID()) : 0;
		plebmc$fire = c == 0 ? -1 : 0xFF000000 | c;
	}

	@Inject(method = "renderFlame", at = @At("RETURN"))
	private void plebmc$fireEnd(PoseStack pose, MultiBufferSource buf, Entity e, Quaternionf q, CallbackInfo ci) {
		plebmc$fire = -1;
	}

	@ModifyArg(method = "fireVertex", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
	private static int plebmc$fireColor(int color) {
		return plebmc$fire;
	}
}
