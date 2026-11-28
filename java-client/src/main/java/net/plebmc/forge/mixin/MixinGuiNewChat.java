package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiNewChat;
import net.plebmc.client.hud.PlebChat;

/** Password Hider: a login command with a password in it stays out of the up-arrow history. */
@Mixin(GuiNewChat.class)
public abstract class MixinGuiNewChat {

	@Inject(method = "addToSentMessages", at = @At("HEAD"), cancellable = true)
	private void plebmc$history(String message, CallbackInfo ci) {
		if (!PlebChat.keepInHistory(message)) {
			ci.cancel();
		}
	}
}
