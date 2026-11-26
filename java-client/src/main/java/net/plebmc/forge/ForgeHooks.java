package net.plebmc.forge;

import java.util.List;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.plebmc.client.hud.PlebClient;
import net.plebmc.client.hud.PlebEntities;
import net.plebmc.client.hud.PlebMenu;

/**
 * Forge events standing in for the Eaglercraft client's vanilla hooks
 * (key presses, clicks, HUD, world pass, title screen, disconnect). The
 * hooks Forge has no event for are mixins in {@code net.plebmc.forge.mixin}.
 */
public final class ForgeHooks {

	private final Minecraft mc = Minecraft.getMinecraft();

	@SubscribeEvent
	public void onKey(InputEvent.KeyInputEvent e) {
		if (Keyboard.getEventKeyState() && !Keyboard.isRepeatEvent()) {
			PlebClient.onKeyDown(Keyboard.getEventKey());
		}
	}

	@SubscribeEvent
	public void onMouse(MouseEvent e) {
		if (mc.currentScreen != null || mc.theWorld == null) {
			return;
		}
		if (e.buttonstate && (e.button == 0 || e.button == 1)) {
			PlebClient.onWorldClick(e.button);
		}
		if (e.dwheel != 0 && Zoom.scroll(e.dwheel)) {
			e.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent e) {
		if (e.phase != TickEvent.Phase.START) {
			return;
		}
		Zoom.tick(mc);
		// Toggle Sprint: hold the sprint key for the player while it's toggled on
		if (mc.thePlayer != null && mc.currentScreen == null && PlebClient.sprintHeld()) {
			net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
		}
	}

	@SubscribeEvent
	public void onHud(RenderGameOverlayEvent.Post e) {
		if (e.type != RenderGameOverlayEvent.ElementType.ALL || mc.gameSettings.hideGUI) {
			return;
		}
		PlebClient.renderHud(new ScaledResolution(mc), e.partialTicks);
	}

	@SubscribeEvent
	public void onDebugText(RenderGameOverlayEvent.Text e) {
		List<String> left = e.left;
		if (left == null || mc.debug == null) {
			return;
		}
		for (int i = 0; i < left.size(); i++) {
			if (mc.debug.equals(left.get(i))) {
				left.set(i, PlebClient.larpDebug(mc.debug));
			}
		}
	}

	@SubscribeEvent
	public void onWorldLast(RenderWorldLastEvent e) {
		PlebEntities.renderGlow(e.partialTicks);
	}

	@SubscribeEvent
	public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post e) {
		if (e.gui instanceof GuiMainMenu) {
			PlebMenu.initMainMenu(e.gui, e.buttonList, e.gui.height / 4 + 48);
			if (net.plebmc.client.hud.TitleMenu.active()) {
				// the PlebClient title screen draws its own sidebar; the buttons stay for other mods but are hidden
				for (net.minecraft.client.gui.GuiButton b : e.buttonList) {
					b.visible = false;
				}
				net.plebmc.client.hud.TitleMenu.init(e.gui);
			}
		}
	}

	@SubscribeEvent
	public void onGuiAction(GuiScreenEvent.ActionPerformedEvent.Pre e) {
		if (e.gui instanceof GuiMainMenu && PlebMenu.mainMenuAction(e.gui, e.button)) {
			e.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent e) {
		mc.addScheduledTask(new Runnable() {
			@Override
			public void run() {
				PlebClient.onLeave();
			}
		});
	}


	/** Crosshair mod: draw our own instead of the vanilla one. */
	@SubscribeEvent
	public void onCrosshair(RenderGameOverlayEvent.Pre e) {
		if (e.type == RenderGameOverlayEvent.ElementType.CROSSHAIRS
				&& net.plebmc.client.hud.PlebVisuals.crosshair(e.resolution.getScaledWidth(), e.resolution.getScaledHeight())) {
			e.setCanceled(true);
		}
	}

	/** Chat mod: timestamps and stacked repeats (game-info messages above the hotbar are left alone). */
	@SubscribeEvent
	public void onChat(ClientChatReceivedEvent e) {
		if (e.type != 2 && e.message != null && mc.ingameGUI != null
				&& net.plebmc.client.hud.PlebVisuals.chat(mc.ingameGUI.getChatGUI(), e.message)) {
			e.setCanceled(true);
		}
	}
}
