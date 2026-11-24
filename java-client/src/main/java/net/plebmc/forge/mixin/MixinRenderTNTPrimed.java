package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderTNTPrimed;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.plebmc.client.hud.PlebEntities;

/** Apollo TNT countdown: seconds above primed TNT. */
@Mixin(RenderTNTPrimed.class)
public abstract class MixinRenderTNTPrimed extends Render<EntityTNTPrimed> {

	protected MixinRenderTNTPrimed(RenderManager renderManager) {
		super(renderManager);
	}

	@Inject(method = "doRender(Lnet/minecraft/entity/item/EntityTNTPrimed;DDDFF)V", at = @At("HEAD"))
	private void plebmc$tnt(EntityTNTPrimed tnt, double x, double y, double z, float yaw, float partialTicks,
			CallbackInfo ci) {
		String label = PlebEntities.tntLabel(tnt);
		if (label != null) {
			this.renderLivingLabel(tnt, label, x, y + 0.5D, z, 64);
		}
	}
}
