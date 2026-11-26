package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.plebmc.client.hud.PlebEntities;
import net.plebmc.client.hud.Ui;

/** The Pleb head after the names of players on PlebClient in the tab list. */
@Mixin(GuiPlayerTabOverlay.class)
public abstract class MixinGuiPlayerTabOverlay {

	@Unique
	private String plebmc$name;

	/** Room for the head when the column width is measured. */
	@Redirect(method = "renderPlayerlist", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;getPlayerName(Lnet/minecraft/client/network/NetworkPlayerInfo;)Ljava/lang/String;", ordinal = 0))
	private String plebmc$measure(GuiPlayerTabOverlay self, NetworkPlayerInfo info) {
		String name = self.getPlayerName(info);
		return PlebEntities.isPleb(info.getGameProfile().getId()) ? name + "   " : name;
	}

	/** Remembers whose row is being drawn. */
	@Redirect(method = "renderPlayerlist", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiPlayerTabOverlay;getPlayerName(Lnet/minecraft/client/network/NetworkPlayerInfo;)Ljava/lang/String;", ordinal = 1))
	private String plebmc$row(GuiPlayerTabOverlay self, NetworkPlayerInfo info) {
		String name = self.getPlayerName(info);
		plebmc$name = PlebEntities.isPleb(info.getGameProfile().getId()) ? name : null;
		return name;
	}

	@Redirect(method = "renderPlayerlist", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;FFI)I"))
	private int plebmc$draw(FontRenderer font, String text, float x, float y, int color) {
		int r = font.drawStringWithShadow(text, x, y, color);
		String name = plebmc$name;
		if (name != null && text != null && text.endsWith(name)) {
			plebmc$name = null;
			try {
				Ui.mascot(x + font.getStringWidth(text) + 2, y, 8, 1f);
				GlStateManager.enableBlend();
			} catch (Throwable t) {
				// just the name
			}
		}
		return r;
	}
}
