package net.plebmc.forge;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.plebmc.client.hud.PlebClient;
import net.plebmc.client.hud.PlebEntities;
import net.plebmc.client.hud.PlebVisuals;
import net.plebmc.mc.R;
import net.plebmc.mc.client.gui.GuiScreen;
import net.plebmc.mc.client.gui.ScaledResolution;
import net.plebmc.mc.entity.Entity;
import net.plebmc.mc.input.Keyboard;

/**
 * Forge events standing in for the 1.8 client's vanilla hooks (keys,
 * clicks, HUD, world pass, chat, screens, connection). Hooks Forge has no
 * event for are mixins in {@code net.plebmc.forge.mixin}.
 */
public final class ForgeHooks {

	private final Minecraft mc = Minecraft.getInstance();

	/** The HUD layer (added on top of the vanilla HUD). */
	static void renderHud(GuiGraphics gg, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui) {
			return;
		}
		GuiGraphics prev = R.gg;
		R.gg = gg;
		try {
			net.plebmc.mc.client.Minecraft shim = net.plebmc.mc.client.Minecraft.getMinecraft();
			PlebClient.renderHud(new ScaledResolution(shim), dt.getGameTimeDeltaPartialTick(false));
			gg.flush();
		} catch (Throwable t) {
			// the HUD must never take the frame down
		} finally {
			R.gg = prev;
		}
	}

	@SubscribeEvent
	public void onFrame(TickEvent.RenderTickEvent.Pre e) {
		Entity.nextFrame();
	}

	@SubscribeEvent
	public void onKey(InputEvent.Key e) {
		if (e.getAction() == GLFW.GLFW_PRESS && mc.screen == null) {
			PlebClient.onKeyDown(Keyboard.fromGlfw(e.getKey()));
		}
	}

	@SubscribeEvent
	public void onMouse(InputEvent.MouseButton.Pre e) {
		if (mc.screen != null || mc.level == null) {
			return;
		}
		if (e.getAction() == GLFW.GLFW_PRESS && (e.getButton() == 0 || e.getButton() == 1)) {
			PlebClient.onWorldClick(e.getButton());
		}
	}

	@SubscribeEvent
	public void onScroll(InputEvent.MouseScrollingEvent e) {
		if (mc.screen == null && Zoom.scroll(e.getDeltaY())) {
			e.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent.Pre e) {
		Zoom.tick(mc);
		// Toggle Sprint: hold the sprint key for the player while it's toggled on
		if (mc.player != null && mc.screen == null && PlebClient.sprintHeld()) {
			mc.options.keySprint.setDown(true);
		}
	}

	@SubscribeEvent
	public void onFov(ViewportEvent.ComputeFov e) {
		if (e.usedConfiguredFov()) {
			e.setFOV(Zoom.fov(e.getFOV()));
		}
	}

	@SubscribeEvent
	public void onDebugText(CustomizeGuiOverlayEvent.DebugText e) {
		List<String> left = e.getText();
		String fps = mc.fpsString;
		if (left == null || fps == null) {
			return;
		}
		for (int i = 0; i < left.size(); i++) {
			if (fps.equals(left.get(i))) {
				left.set(i, PlebClient.larpDebug(fps));
			}
		}
	}

	/** Sky beams and cosmetic trails, after the world's particles. */
	@SubscribeEvent
	public void onWorld(RenderLevelStageEvent e) {
		if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
			PlebEntities.renderGlow(e.getPartialTick());
		}
	}

	/** Let It Snow and Best Friend over every menu. */
	@SubscribeEvent
	public void onScreenDrawn(ScreenEvent.Render.Post e) {
		GuiGraphics prev = R.gg;
		R.gg = e.getGuiGraphics();
		try {
			net.plebmc.client.hud.SecretMods.gui(e.getScreen().width, e.getScreen().height,
					e.getScreen() instanceof net.minecraft.client.gui.screens.ChatScreen);
			e.getGuiGraphics().flush();
		} catch (Throwable t) {
			// a secret mod must never take the screen down
		} finally {
			R.gg = prev;
		}
	}

	/** Video Settings opens the PlebClient one. */
	@SubscribeEvent
	public void onOpen(ScreenEvent.Opening e) {
		if (e.getNewScreen() instanceof VideoSettingsScreen v) {
			GuiScreen s = PlebClient.swapScreen(new net.plebmc.mc.client.gui.GuiVideoSettings(v));
			if (s != null && !(s instanceof net.plebmc.mc.client.gui.GuiVideoSettings)) {
				e.setNewScreen(s.toScreen());
			}
		}
	}

	@SubscribeEvent
	public void onLogin(ClientPlayerNetworkEvent.LoggingIn e) {
		PlebClient.onJoin();
	}

	@SubscribeEvent
	public void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
		mc.execute(PlebClient::onLeave);
	}

	/** Block Outline mod: our own color, thickness and fill. */
	@SubscribeEvent
	public void onBlockHighlight(RenderHighlightEvent.Block e) {
		if (PlebVisuals.blockOutline(e.getCamera(), e.getTarget(), e.getPoseStack(), e.getMultiBufferSource())) {
			e.setCanceled(true);
		}
	}

	/** Overlays mod: the on-fire overlay lower on the screen, or gone. */
	@SubscribeEvent
	public void onScreenEffect(RenderBlockScreenEffectEvent e) {
		if (e.getOverlayType() != RenderBlockScreenEffectEvent.OverlayType.FIRE) {
			return;
		}
		float drop = PlebVisuals.fireDrop();
		if (drop < 0f) {
			e.setCanceled(true);
		} else if (drop > 0f) {
			e.getPoseStack().translate(0.0F, -drop, 0.0F);
		}
	}

	/** Chat mod: timestamps and stacked repeats (messages above the hotbar are left alone). */
	@SubscribeEvent
	public void onChat(ClientChatReceivedEvent e) {
		if (e instanceof ClientChatReceivedEvent.System s && s.isOverlay()) {
			return;
		}
		if (e.getMessage() != null && PlebVisuals.chat(e.getMessage())) {
			e.setCanceled(true);
		}
	}
}
