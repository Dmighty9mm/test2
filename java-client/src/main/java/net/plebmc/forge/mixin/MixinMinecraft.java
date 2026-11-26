package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Shadow;
import org.lwjgl.opengl.Display;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.plebmc.client.hud.PlebClient;
import net.plebmc.client.hud.PlebPerf;

/** Apollo combat (miss penalty) and per-connection reset when the world unloads. */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

	@Shadow
	public WorldClient theWorld;
	@Shadow
	public GuiScreen currentScreen;
	@Shadow
	public GameSettings gameSettings;

	/** Menu and background frame caps from the Video Settings screen. */
	@Inject(method = "getLimitFramerate", at = @At("HEAD"), cancellable = true)
	private void plebmc$frameLimit(CallbackInfoReturnable<Integer> cir) {
		boolean active;
		try {
			active = Display.isActive();
		} catch (Throwable t) {
			active = true;
		}
		cir.setReturnValue(PlebPerf.frameLimit(gameSettings.limitFramerate, theWorld == null && currentScreen != null, active));
	}

	@Redirect(method = "clickMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;isNotCreative()Z"))
	private boolean plebmc$missPenalty(PlayerControllerMP pc) {
		return pc.isNotCreative() && PlebClient.missPenalty();
	}

	@Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("HEAD"))
	private void plebmc$leave(WorldClient world, String message, CallbackInfo ci) {
		if (world == null) {
			PlebClient.onLeave();
		}
	}
}
