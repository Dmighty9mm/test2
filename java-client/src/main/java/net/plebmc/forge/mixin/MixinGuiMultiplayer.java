package net.plebmc.forge.mixin;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ServerSelectionList;
import net.plebmc.client.hud.FeaturedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PlebMc's own starred card between the title and the server list. */
@Mixin(GuiMultiplayer.class)
public abstract class MixinGuiMultiplayer extends GuiScreen {

	private static final int PLEBMC_TOP = 32;

	@Shadow
	private ServerSelectionList serverListSelector;

	@Inject(method = "initGui", at = @At("TAIL"))
	private void plebmc$init(CallbackInfo ci) {
		if (this.serverListSelector != null) {
			this.serverListSelector.setDimensions(this.width, this.height, PLEBMC_TOP + FeaturedServer.HEIGHT, this.height - 64);
		}
		FeaturedServer.refresh();
	}

	@Inject(method = "updateScreen", at = @At("TAIL"))
	private void plebmc$update(CallbackInfo ci) {
		FeaturedServer.update();
	}

	@Inject(method = "drawScreen", at = @At("TAIL"))
	private void plebmc$draw(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
		FeaturedServer.draw(this, PLEBMC_TOP, mouseX, mouseY);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void plebmc$click(int mouseX, int mouseY, int button, CallbackInfo ci) {
		if (FeaturedServer.click(this, PLEBMC_TOP, mouseX, mouseY, button)) {
			ci.cancel();
		}
	}

	@Inject(method = "actionPerformed", at = @At("HEAD"))
	private void plebmc$refresh(GuiButton button, CallbackInfo ci) {
		if (button != null && button.id == 8) {
			FeaturedServer.refresh();
		}
	}
}
