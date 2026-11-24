package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.plebmc.client.hud.PlebClient;
import net.plebmc.client.text.RgbText;

/**
 * Apollo nick hider (your name is replaced everywhere text is drawn) and RGB
 * text: hex/RGB color codes ({@link RgbText}) draw in their real color.
 */
@Mixin(FontRenderer.class)
public abstract class MixinFontRenderer {

	@Shadow
	private boolean randomStyle;
	@Shadow
	private boolean boldStyle;
	@Shadow
	private boolean italicStyle;
	@Shadow
	private boolean underlineStyle;
	@Shadow
	private boolean strikethroughStyle;
	@Shadow
	private float alpha;
	@Shadow
	private int textColor;
	@Shadow
	private int[] colorCode;

	@Shadow
	protected abstract void renderStringAtPos(String text, boolean shadow);

	@ModifyVariable(method = "renderString", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private String plebmc$nickDraw(String text) {
		return PlebClient.filterText(text);
	}

	@ModifyVariable(method = "getStringWidth", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private String plebmc$nickWidth(String text) {
		return PlebClient.filterText(text);
	}

	/**
	 * Text with RGB codes is drawn in pieces: vanilla draws each piece between
	 * codes, and each code sets the color (and clears bold etc., like a color
	 * code). Text without RGB codes is untouched.
	 */
	@Inject(method = "renderStringAtPos", at = @At("HEAD"), cancellable = true)
	private void plebmc$rgb(String text, boolean shadow, CallbackInfo ci) {
		if (text == null || !hasRgb(text)) {
			return;
		}
		ci.cancel();
		int start = 0;
		int n = text.length();
		for (int i = 0; i < n - 1; ++i) {
			if (text.charAt(i) != RgbText.SECTION) {
				continue;
			}
			int len = RgbText.codeLength(text, i);
			if (len == 0) {
				++i; // a normal code: skip its letter
				continue;
			}
			if (i > start) {
				this.renderStringAtPos(text.substring(start, i), shadow);
			}
			int rgb = RgbText.displayColor(text, i, this.colorCode);
			if (shadow) {
				rgb = RgbText.shadow(rgb);
			}
			this.randomStyle = false;
			this.boldStyle = false;
			this.italicStyle = false;
			this.underlineStyle = false;
			this.strikethroughStyle = false;
			this.textColor = rgb;
			GlStateManager.color((rgb >> 16 & 255) / 255.0F, (rgb >> 8 & 255) / 255.0F, (rgb & 255) / 255.0F, this.alpha);
			i += len - 1;
			start = i + 1;
		}
		if (start < n) {
			this.renderStringAtPos(text.substring(start), shadow);
		}
	}

	@Inject(method = "getFormatFromString", at = @At("HEAD"), cancellable = true)
	private static void plebmc$format(String text, CallbackInfoReturnable<String> cir) {
		cir.setReturnValue(RgbText.formatAtEnd(text));
	}

	private static boolean hasRgb(String s) {
		for (int i = s.indexOf(RgbText.SECTION); i >= 0 && i < s.length() - 1; i = s.indexOf(RgbText.SECTION, i + 1)) {
			if (RgbText.codeLength(s, i) > 0) {
				return true;
			}
		}
		return false;
	}
}
