package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.plebmc.client.hud.PlebPerf;

/** Block entity draw distance from the Video Settings screen. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class MixinBlockEntityRenderDispatcher {

	@Shadow
	public Camera camera;

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private <E extends BlockEntity> void plebmc$distance(E be, float pt, PoseStack pose, MultiBufferSource buf, CallbackInfo ci) {
		if (this.camera != null) {
			var c = this.camera.getPosition();
			if (PlebPerf.cullBlockEntity(be, c.x, c.y, c.z)) {
				ci.cancel();
			}
		}
	}
}
