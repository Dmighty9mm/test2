package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiScreen;
import net.plebmc.client.hud.PlebTheme;

/** Menu backgrounds: the animated PlebClient background instead of dirt, a blue wash in game. */
@Mixin(GuiScreen.class)
public abstract class MixinGuiScreen {

	@Inject(method = "drawWorldBackground", at = @At("HEAD"), cancellable = true)
	private void plebmc$worldBackground(int tint, CallbackInfo ci) {
		if (PlebTheme.drawWorldBackground((GuiScreen) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
	private void plebmc$background(int tint, CallbackInfo ci) {
		if (PlebTheme.enabled()) {
			GuiScreen self = (GuiScreen) (Object) this;
			PlebTheme.background(self.width, self.height, true);
			ci.cancel();
		}
	}

	/** Password Manager: the client's own password commands never go to the server. */
	@Inject(method = "sendChatMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
	private void plebmc$outgoing(String msg, boolean addToChat, CallbackInfo ci) {
		if (net.plebmc.client.hud.PlebChat.outgoing(msg)) {
			ci.cancel();
		}
	}

	/** The pause menu shows the client's name and logo above its buttons. */
	@Inject(method = "drawScreen", at = @At("HEAD"))
	private void plebmc$pauseBrand(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
		Object self = this;
		if (self instanceof net.minecraft.client.gui.GuiIngameMenu) {
			net.minecraft.client.gui.GuiScreen g = (net.minecraft.client.gui.GuiScreen) self;
			net.plebmc.client.hud.PlebMenu.pauseBrand(g.width, g.height);
		}
	}
}
