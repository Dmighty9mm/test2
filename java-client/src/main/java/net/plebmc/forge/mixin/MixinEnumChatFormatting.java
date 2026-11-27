package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.util.EnumChatFormatting;
import net.plebmc.client.text.RgbText;

/** Stripping color codes also strips RGB codes. */
@Mixin(EnumChatFormatting.class)
public abstract class MixinEnumChatFormatting {

	@ModifyVariable(method = "getTextWithoutFormattingCodes", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private static String plebmc$stripRgb(String text) {
		return RgbText.strip(text);
	}
}
