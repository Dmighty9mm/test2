package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.Tessellator;
import net.plebmc.client.hud.PlebTheme;

/** Lists (servers, worlds, packs): a dark panel and header/footer bars instead of dirt. */
@Mixin(GuiSlot.class)
public abstract class MixinGuiSlot {

	@Shadow
	public int width;
	@Shadow
	public int height;
	@Shadow
	public int top;
	@Shadow
	public int bottom;
	@Shadow
	public int right;
	@Shadow
	public int left;

	@Inject(method = "drawContainerBackground", at = @At("HEAD"), cancellable = true, remap = false)
	private void plebmc$listBackground(Tessellator tessellator, CallbackInfo ci) {
		if (PlebTheme.slotBackground(this.left, this.top, this.right, this.bottom)) {
			ci.cancel();
		}
	}

	@Inject(method = "overlayBackground", at = @At("HEAD"), cancellable = true)
	private void plebmc$bars(int startY, int endY, int startAlpha, int endAlpha, CallbackInfo ci) {
		if (PlebTheme.slotOverlay(this.left, this.width, startY, endY, this.height)) {
			ci.cancel();
		}
	}
}
