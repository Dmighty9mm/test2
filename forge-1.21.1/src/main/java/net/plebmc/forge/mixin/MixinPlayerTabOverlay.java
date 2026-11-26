package net.plebmc.forge.mixin;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.plebmc.client.hud.PlebEntities;
import net.plebmc.client.hud.Ui;
import net.plebmc.mc.R;

/** The Pleb head after the names of players on PlebClient in the tab list. */
@Mixin(PlayerTabOverlay.class)
public abstract class MixinPlayerTabOverlay {

	@Unique
	private final Set<Component> plebmc$plebs = Collections.newSetFromMap(new IdentityHashMap<>());

	/** Room for the head after the name. */
	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;"))
	private Component plebmc$name(PlayerTabOverlay self, PlayerInfo info) {
		Component name = self.getNameForDisplay(info);
		if (PlebEntities.isPleb(info.getProfile().getId())) {
			Component spaced = name.copy().append("   ");
			if (plebmc$plebs.size() > 512) {
				plebmc$plebs.clear();
			}
			plebmc$plebs.add(spaced);
			return spaced;
		}
		return name;
	}

	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"))
	private int plebmc$draw(GuiGraphics gg, Font font, Component text, int x, int y, int color) {
		int r = gg.drawString(font, text, x, y, color);
		if (plebmc$plebs.remove(text)) {
			var prev = R.gg;
			R.gg = gg;
			try {
				gg.flush();
				Ui.mascot(x + font.width(text) - font.width("   ") + 2, y, 8, 1f);
			} catch (Throwable t) {
				// just the name
			} finally {
				R.gg = prev;
			}
		}
		return r;
	}
}
