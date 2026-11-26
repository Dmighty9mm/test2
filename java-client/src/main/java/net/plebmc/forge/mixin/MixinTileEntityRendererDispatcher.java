package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import net.plebmc.client.hud.PlebPerf;

/** Block entity distance from the Video Settings screen: far chests, signs and banners aren't drawn. */
@Mixin(TileEntityRendererDispatcher.class)
public abstract class MixinTileEntityRendererDispatcher {

	@Shadow
	public double entityX;
	@Shadow
	public double entityY;
	@Shadow
	public double entityZ;

	@Inject(method = "renderTileEntity", at = @At("HEAD"), cancellable = true)
	private void plebmc$blockEntityDistance(TileEntity te, float partialTicks, int destroyStage, CallbackInfo ci) {
		if (destroyStage < 0 && PlebPerf.cullBlockEntity(te, entityX, entityY, entityZ)) {
			ci.cancel();
		}
	}
}
