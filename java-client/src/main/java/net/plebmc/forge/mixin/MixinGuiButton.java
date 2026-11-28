package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.plebmc.client.hud.PlebTheme;

/** Every Minecraft button in the PlebClient look (Settings > Interface > PlebClient menus). */
@Mixin(GuiButton.class)
public abstract class MixinGuiButton extends Gui {

	@Shadow
	protected boolean hovered;

	@Shadow
	protected abstract void mouseDragged(Minecraft mc, int mouseX, int mouseY);

	@Shadow
	protected abstract int getHoverState(boolean mouseOver);

	@Inject(method = "drawButton", at = @At("HEAD"), cancellable = true)
	private void plebmc$drawButton(Minecraft mc, int mouseX, int mouseY, CallbackInfo ci) {
		GuiButton self = (GuiButton) (Object) this;
		if (!self.visible || !PlebTheme.buttons()) {
			return;
		}
		this.hovered = mouseX >= self.xPosition && mouseY >= self.yPosition && mouseX < self.xPosition + self.width
				&& mouseY < self.yPosition + self.height;
		PlebTheme.drawButton(self, mouseX, mouseY, this.hovered, self.enabled && this.getHoverState(true) == 0);
		this.mouseDragged(mc, mouseX, mouseY);
		PlebTheme.drawButtonLabel(self, mc.fontRendererObj, this.hovered);
		ci.cancel();
	}
}
