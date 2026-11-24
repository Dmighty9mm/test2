package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.plebmc.client.hud.PlebTheme;

/** The title screen: the PlebClient wordmark (with a tint, grid and sparks) instead of the Minecraft logo. */
@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu extends GuiScreen {

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
