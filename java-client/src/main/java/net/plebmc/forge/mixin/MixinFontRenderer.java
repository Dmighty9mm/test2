package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.gui.FontRenderer;
import net.plebmc.client.hud.PlebClient;

/** Apollo nick hider: your name is replaced everywhere text is drawn. */
@Mixin(FontRenderer.class)
public abstract class MixinFontRenderer {

	@ModifyVariable(method = "renderString", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private String plebmc$nickDraw(String text) {
		return PlebClient.filterText(text);
	}

	@ModifyVariable(method = "getStringWidth", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private String plebmc$nickWidth(String text) {
		return PlebClient.filterText(text);
	}
}
