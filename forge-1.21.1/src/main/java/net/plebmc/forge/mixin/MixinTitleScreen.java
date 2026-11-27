package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import net.plebmc.forge.TitleHooks;

/** The PlebClient title screen over the vanilla panorama. */
@Mixin(TitleScreen.class)
public abstract class MixinTitleScreen {

	@Shadow
	private SplashRenderer splash;

	@Unique
	private static java.lang.reflect.Field plebmc$splashText;

	@Unique
	private String plebmc$splash() {
		if (this.splash == null) {
			return null;
		}
		try {
			if (plebmc$splashText == null) {
				for (java.lang.reflect.Field f : SplashRenderer.class.getDeclaredFields()) {
					if (f.getType() == String.class) {
						f.setAccessible(true);
						plebmc$splashText = f;
					}
				}
			}
			return plebmc$splashText == null ? null : (String) plebmc$splashText.get(this.splash);
		} catch (Throwable t) {
			return null;
		}
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/TitleScreen;renderPanorama(Lnet/minecraft/client/gui/GuiGraphics;F)V", shift = At.Shift.AFTER), cancellable = true)
	private void plebmc$title(GuiGraphics gg, int mx, int my, float pt, CallbackInfo ci) {
		if (TitleHooks.render((TitleScreen) (Object) this, gg, mx, my, pt, plebmc$splash())) {
			ci.cancel();
		}
	}
}
