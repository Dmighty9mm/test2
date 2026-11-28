package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiSlider;
import net.plebmc.client.hud.PlebTheme;

/** Sliders on other screens (world customization): the PlebClient knob. */
@Mixin(GuiSlider.class)
public abstract class MixinGuiSlider {

	@Shadow
	private float sliderPosition;

	@Shadow
	public boolean isMouseDown;

	@Inject(method = "mouseDragged", at = @At("TAIL"))
	private void plebmc$knob(Minecraft mc, int mouseX, int mouseY, CallbackInfo ci) {
		GuiButton self = (GuiButton) (Object) this;
		if (self.visible && PlebTheme.buttons()) {
			PlebTheme.sliderKnob(self, self.xPosition + (int) (this.sliderPosition * (float) (self.width - 8)), this.isMouseDown);
		}
	}
}
