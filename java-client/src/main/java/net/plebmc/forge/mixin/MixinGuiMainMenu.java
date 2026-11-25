package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.plebmc.client.hud.PlebTheme;
import net.plebmc.client.hud.TitleMenu;

/**
 * The title screen: the PlebClient title screen (sidebar, wordmark, server
 * card) over the vanilla panorama when the PlebClient menus are on; the
 * redirects below are the older wordmark-only look, kept as a fallback.
 */
@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu extends GuiScreen {

	@Shadow
	private String splashText;

	@Shadow
	private void renderSkybox(int mouseX, int mouseY, float partialTicks) {
	}

	@Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true)
	private void plebmc$title(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
		if (TitleMenu.active()) {
			net.minecraft.client.renderer.GlStateManager.disableAlpha();
			this.renderSkybox(mouseX, mouseY, partialTicks);
			net.minecraft.client.renderer.GlStateManager.enableAlpha();
			TitleMenu.draw(this, mouseX, mouseY, partialTicks, this.splashText);
			ci.cancel();
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void plebmc$click(int mouseX, int mouseY, int button, CallbackInfo ci) {
		if (TitleMenu.active()) {
			TitleMenu.click(this, mouseX, mouseY, button);
			ci.cancel();
		}
	}

	@Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
	private void plebmc$key(char c, int key, CallbackInfo ci) {
		if (TitleMenu.active() && TitleMenu.key(this, c, key)) {
			ci.cancel();
		}
	}

	@Inject(method = "onGuiClosed", at = @At("HEAD"))
	private void plebmc$closed(CallbackInfo ci) {
		TitleMenu.closed();
	}

	@Redirect(method = "drawScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiMainMenu;drawGradientRect(IIIIII)V", ordinal = 0), require = 0)
	private void plebmc$firstTint(GuiMainMenu self, int l, int t, int r, int b, int c0, int c1) {
		if (PlebTheme.enabled()) {
			PlebTheme.mainMenuTitle(self, 30);
		} else {
			this.drawGradientRect(l, t, r, b, c0, c1);
		}
	}

	@Redirect(method = "drawScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiMainMenu;drawGradientRect(IIIIII)V", ordinal = 1), require = 0)
	private void plebmc$secondTint(GuiMainMenu self, int l, int t, int r, int b, int c0, int c1) {
		if (!PlebTheme.enabled()) {
			this.drawGradientRect(l, t, r, b, c0, c1);
		}
	}

	@Redirect(method = "drawScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V", ordinal = 0), require = 0)
	private void plebmc$splash(float x, float y, float z) {
		if (PlebTheme.enabled()) {
			PlebTheme.splashTranslate((GuiMainMenu) (Object) this);
		} else {
			net.minecraft.client.renderer.GlStateManager.translate(x, y, z);
		}
	}

	@Redirect(method = "drawScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiMainMenu;drawTexturedModalRect(IIIIII)V"), require = 0)
	private void plebmc$logo(GuiMainMenu self, int x, int y, int u, int v, int w, int h) {
		if (!PlebTheme.enabled()) {
			this.drawTexturedModalRect(x, y, u, v, w, h);
		}
	}
}
