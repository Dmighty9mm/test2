package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.plebmc.client.hud.PlebChat;

/** Password Hider and Password Manager: stars in the chat box, Tab fills in the saved password. */
@Mixin(GuiChat.class)
public abstract class MixinGuiChat {

	@Shadow
	protected GuiTextField inputField;

	@Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
	private void plebmc$tab(char typedChar, int keyCode, CallbackInfo ci) {
		if (keyCode == 15 && PlebChat.tab(this.inputField)) {
			ci.cancel();
		}
	}

	@Redirect(method = "drawScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiTextField;drawTextBox()V"))
	private void plebmc$drawInput(GuiTextField field) {
		PlebChat.drawInput(field);
	}
}
