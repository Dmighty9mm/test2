package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionSlider;
import net.plebmc.client.hud.PlebTheme;

/** Options sliders: the PlebClient knob on top of the vanilla one. */
@Mixin(GuiOptionSlider.class)
public abstract class MixinGuiOptionSlider {

	@Shadow
	private float sliderValue;

	@Shadow
	public boolean dragging;

	@Inject(method = "mouseDragged", at = @At("TAIL"))
	private void plebmc$knob(Minecraft mc, int mouseX, int mouseY, CallbackInfo ci) {
		GuiButton self = (GuiButton) (Object) this;
		if (self.visible && PlebTheme.enabled()) {
			PlebTheme.sliderKnob(self, self.xPosition + (int) (this.sliderValue * (float) (self.width - 8)), this.dragging);
		}
	}
}
