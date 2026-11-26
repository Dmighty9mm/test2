package net.plebmc.mc.client;

import java.io.File;

import net.plebmc.mc.client.audio.SoundHandler;
import net.plebmc.mc.client.entity.EntityPlayerSP;
import net.plebmc.mc.client.gui.FontRenderer;
import net.plebmc.mc.client.gui.GuiIngame;
import net.plebmc.mc.client.gui.GuiScreen;
import net.plebmc.mc.client.gui.ScreenAdapter;
import net.plebmc.mc.client.renderer.texture.TextureManager;
import net.plebmc.mc.client.settings.GameSettings;
import net.plebmc.mc.entity.Entity;
import net.plebmc.mc.world.World;

/**
 * 1.8's Minecraft over 1.21.1's. The public fields are refreshed from the
 * real game whenever {@link #getMinecraft()} is called.
 */
public class Minecraft {
	private static final Minecraft INSTANCE = new Minecraft();

	public final net.minecraft.client.Minecraft real = net.minecraft.client.Minecraft.getInstance();
	public EntityPlayerSP thePlayer;
	public World theWorld;
	public GuiScreen currentScreen;
	public final FontRenderer fontRendererObj = new FontRenderer();
	public final GameSettings gameSettings = new GameSettings();
	public final GuiIngame ingameGUI = new GuiIngame();
	public final PlayerController playerController = new PlayerController();
	public final RenderGlobal renderGlobal = new RenderGlobal();
	public int displayWidth, displayHeight;
	public File mcDataDir;
	public String debug = "";
	private final TextureManager textures = new TextureManager();
	private final SoundHandler sounds = new SoundHandler();

	public static Minecraft getMinecraft() {
		INSTANCE.refresh();
		return INSTANCE;
	}

	private void refresh() {
		thePlayer = real.player == null ? null : Entity.wrap(real.player);
		theWorld = World.current();
		currentScreen = screenOf(real.screen);
		displayWidth = real.getWindow().getWidth();
		displayHeight = real.getWindow().getHeight();
		mcDataDir = real.gameDirectory;
		debug = real.fpsString;
		gameSettings.refresh(real.options);
	}

	private static final java.util.Map<net.minecraft.client.gui.screens.Screen, GuiScreen> VANILLA = new java.util.WeakHashMap<>();

	/** What 1.8 code sees for a 1.21.1 screen. */
	public static GuiScreen screenOf(net.minecraft.client.gui.screens.Screen s) {
		if (s == null) {
			return null;
		}
		if (s instanceof ScreenAdapter) {
			return ((ScreenAdapter) s).gui;
		}
		return VANILLA.computeIfAbsent(s, net.plebmc.mc.client.gui.VanillaScreens::wrap);
	}

	public void displayGuiScreen(GuiScreen g) {
		net.minecraft.client.gui.screens.Screen s = g == null ? null : g.toScreen();
		if (s == null && real.level == null) {
			s = new net.minecraft.client.gui.screens.TitleScreen();
		}
		real.setScreen(s);
		currentScreen = screenOf(real.screen);
	}

	public net.plebmc.mc.client.renderer.entity.RenderItem getRenderItem() {
		return net.plebmc.mc.client.renderer.entity.RenderItem.INSTANCE;
	}

	/** 1.8's resource manager: just enough to read a resource's bytes. */
	public Resources getResourceManager() {
		return Resources.INSTANCE;
	}

	public static final class Resources {
		static final Resources INSTANCE = new Resources();

		public Resource getResource(net.plebmc.mc.util.ResourceLocation rl) throws java.io.IOException {
			return new Resource(rl);
		}
	}

	public static final class Resource {
		private final net.plebmc.mc.util.ResourceLocation rl;

		Resource(net.plebmc.mc.util.ResourceLocation rl) {
			this.rl = rl;
		}

		public java.io.InputStream getInputStream() throws java.io.IOException {
			return net.minecraft.client.Minecraft.getInstance().getResourceManager().open(rl.rl);
		}
	}

	public net.plebmc.mc.client.renderer.entity.RenderManager getRenderManager() {
		return net.plebmc.mc.client.renderer.entity.RenderManager.INSTANCE;
	}

	public void setIngameFocus() {
		if (real.screen == null) {
			real.mouseHandler.grabMouse();
		}
	}

	public static int getDebugFPS() {
		return net.minecraft.client.Minecraft.getInstance().getFps();
	}

	public static long getSystemTime() {
		return System.currentTimeMillis();
	}

	public boolean isSingleplayer() {
		return real.hasSingleplayerServer();
	}

	public boolean isIntegratedServerRunning() {
		return real.hasSingleplayerServer();
	}

	public boolean isDemo() {
		return real.isDemo();
	}

	public void shutdown() {
		real.stop();
	}

	public Entity getRenderViewEntity() {
		return Entity.wrap(real.getCameraEntity());
	}

	public TextureManager getTextureManager() {
		return textures;
	}

	public SoundHandler getSoundHandler() {
		return sounds;
	}

	public net.plebmc.mc.client.network.NetHandlerPlayClient getNetHandler() {
		return real.getConnection() == null ? null : new net.plebmc.mc.client.network.NetHandlerPlayClient(real.getConnection());
	}

	public net.plebmc.mc.client.multiplayer.ServerData getCurrentServerData() {
		var d = real.getCurrentServer();
		return d == null ? null : new net.plebmc.mc.client.multiplayer.ServerData(d);
	}

	public Session getSession() {
		return new Session();
	}

	public com.mojang.authlib.minecraft.MinecraftSessionService getSessionService() {
		return real.getMinecraftSessionService();
	}

	public LanguageManager getLanguageManager() {
		return new LanguageManager();
	}

	public void addScheduledTask(Runnable r) {
		real.execute(r);
	}

	public boolean isGamePaused() {
		return real.isPaused();
	}

	/** 1.8's Session: name and profile of the signed-in account. */
	public final class Session {
		public String getUsername() {
			return real.getUser().getName();
		}

		public String getPlayerID() {
			return real.getUser().getProfileId().toString().replace("-", "");
		}

		public com.mojang.authlib.GameProfile getProfile() {
			return real.getGameProfile();
		}
	}

	public final class LanguageManager {
		public Language getCurrentLanguage() {
			return new Language(real.getLanguageManager().getSelected());
		}
	}

	public static final class Language {
		private final String code;

		Language(String code) {
			this.code = code;
		}

		public String getLanguageCode() {
			return code;
		}

		@Override
		public String toString() {
			return code;
		}
	}

	public final class PlayerController {
		public boolean shouldDrawHUD() {
			return real.gameMode != null && real.gameMode.canHurtPlayer();
		}

		public boolean gameIsSurvivalOrAdventure() {
			return real.gameMode != null && real.gameMode.getPlayerMode().isSurvival();
		}

		public boolean isSpectator() {
			return real.gameMode != null && real.gameMode.getPlayerMode() == net.minecraft.world.level.GameType.SPECTATOR;
		}
	}

	public final class RenderGlobal {
		public void loadRenderers() {
			real.levelRenderer.allChanged();
		}
	}
}
