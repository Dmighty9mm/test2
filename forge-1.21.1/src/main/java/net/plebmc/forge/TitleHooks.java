package net.plebmc.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.plebmc.client.hud.PlebMenu;
import net.plebmc.client.hud.TitleMenu;
import net.plebmc.mc.R;
import net.plebmc.mc.client.gui.GuiButton;
import net.plebmc.mc.client.gui.GuiScreen;
import net.plebmc.mc.input.Keyboard;

/**
 * The title screen: the PlebClient title screen (sidebar, wordmark, server
 * card) over the vanilla panorama when the PlebClient menus are on, else
 * the vanilla one with a "Join PlebMc" row added. The drawing is a mixin
 * on TitleScreen.render; input and setup are Forge screen events.
 */
public final class TitleHooks {

	private static boolean logged;

	private static final Map<Screen, GuiScreen> WRAPS = new WeakHashMap<>();

	/** The 1.8-style stand-in for this title screen (one per screen, sized to it). */
	public static GuiScreen wrap(Screen s) {
		GuiScreen g = WRAPS.computeIfAbsent(s, GuiScreen.Vanilla::new);
		g.width = s.width;
		g.height = s.height;
		g.mc = net.plebmc.mc.client.Minecraft.getMinecraft();
		return g;
	}

	/** Called by the TitleScreen mixin; true = drawn here, skip the vanilla screen. */
	public static boolean render(TitleScreen s, GuiGraphics gg, int mx, int my, float pt, String splash) {
		if (!TitleMenu.active()) {
			return false;
		}
		GuiGraphics prev = R.gg;
		R.gg = gg;
		try {
			TitleMenu.draw(wrap(s), mx, my, pt, splash);
			gg.flush();
		} catch (Throwable t) {
			if (!logged) {
				logged = true;
				org.apache.logging.log4j.LogManager.getLogger("PlebMc").error("PlebClient title screen failed", t);
			}
			return false;
		} finally {
			R.gg = prev;
		}
		return true;
	}

	@SubscribeEvent
	public void onInit(ScreenEvent.Init.Post e) {
		if (!(e.getScreen() instanceof TitleScreen ts)) {
			return;
		}
		GuiScreen g = wrap(ts);
		List<GuiButton> extra = new ArrayList<>();
		PlebMenu.initMainMenu(g, extra, ts.height / 4 + 48);
		if (Minecraft.getInstance().screen != ts) {
			return; // the start or update screen took over
		}
		if (TitleMenu.active()) {
			// the PlebClient title screen draws its own sidebar; the buttons stay for other mods but are hidden
			for (var l : e.getListenersList()) {
				if (l instanceof AbstractWidget w) {
					w.visible = false;
					w.active = false;
				}
			}
			TitleMenu.init(g);
			return;
		}
		for (GuiButton b : extra) {
			e.addListener(Button.builder(Component.literal(b.displayString), btn -> PlebMenu.mainMenuAction(g, b))
					.bounds(b.xPosition, b.yPosition, b.width, b.height).build());
		}
	}

	@SubscribeEvent
	public void onClick(ScreenEvent.MouseButtonPressed.Pre e) {
		if (e.getScreen() instanceof TitleScreen ts && TitleMenu.active()) {
			TitleMenu.click(wrap(ts), (int) e.getMouseX(), (int) e.getMouseY(), e.getButton());
			e.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void onKey(ScreenEvent.KeyPressed.Pre e) {
		if (e.getScreen() instanceof TitleScreen ts && TitleMenu.active()) {
			int k = e.getKeyCode();
			char c = k >= 65 && k <= 90 ? (char) (k + 32) : k >= 48 && k <= 57 || k == 32 ? (char) k : 0;
			if (TitleMenu.key(wrap(ts), c, Keyboard.fromGlfw(k))) {
				e.setCanceled(true);
			}
		}
	}

	@SubscribeEvent
	public void onClose(ScreenEvent.Closing e) {
		if (e.getScreen() instanceof TitleScreen) {
			TitleMenu.closed();
		}
	}
}
