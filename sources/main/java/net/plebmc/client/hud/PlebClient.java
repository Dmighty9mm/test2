package net.plebmc.client.hud;

import java.nio.charset.StandardCharsets;

import net.lax1dude.eaglercraft.v1_8.EagRuntime;
import net.lax1dude.eaglercraft.v1_8.internal.KeyboardConstants;
import net.lax1dude.eaglercraft.v1_8.log4j.LogManager;
import net.lax1dude.eaglercraft.v1_8.log4j.Logger;
import net.lax1dude.eaglercraft.v1_8.netty.ByteBuf;
import net.lax1dude.eaglercraft.v1_8.netty.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.plebmc.client.apollo.Apollo;
import net.plebmc.client.apollo.module.BorderModule;
import net.plebmc.client.apollo.module.NickHiderModule;
import net.plebmc.client.apollo.module.OptionsOnlyModule;
import net.plebmc.client.mods.ClickCounter;
import net.plebmc.client.mods.HudBox;
import net.plebmc.client.mods.Mod;
import net.plebmc.client.mods.ModRegistry;
import net.plebmc.client.mods.ClientPrefs;
import net.plebmc.client.mods.SettingsStore;
import net.plebmc.client.network.PluginChannels;

/**
 * Glue between the vanilla 1.8 classes and PlebMc's own features (mods,
 * HUD, Apollo). Every vanilla hook is a single call into this class or
 * {@link PlebEntities}:
 * <ul>
 * <li>Minecraft.startGame: {@link #init}</li>
 * <li>Minecraft.runTick key events: {@link #onKeyDown}</li>
 * <li>Minecraft.clickMouse / rightClickMouse: {@link #onWorldClick}</li>
 * <li>Minecraft.loadWorld(null): {@link #onLeave}</li>
 * <li>S01PacketJoinGame.processPacket: {@link #onJoin}</li>
 * <li>S3FPacketCustomPayload.processPacket: {@link #onPayload}</li>
 * <li>GuiIngame.renderGameOverlay: {@link #renderHud}</li>
 * <li>EntityRenderer: {@link #zoomAllowed}, {@link #gamma}</li>
 * <li>EntityPlayerSP: {@link #sprintHeld}, {@link #borderBlocks}</li>
 * <li>FontRenderer: {@link #filterText} (nick hider)</li>
 * </ul>
 */
public final class PlebClient {

	public static final int KEY_RSHIFT = KeyboardConstants.KEY_RSHIFT;

	private static final Logger LOG = LogManager.getLogger("PlebMc");

	private static Minecraft mc;
	private static final ModRegistry mods = new ModRegistry();
	private static final ClickCounter clicks = new ClickCounter();
	private static GameView view;

	static final SettingsStore STORE = new SettingsStore() {
		@Override
		public String get(String key) {
			byte[] b = EagRuntime.getStorage("plebmc." + key);
			return b == null ? null : new String(b, StandardCharsets.UTF_8);
		}

		@Override
		public void set(String key, String value) {
			EagRuntime.setStorage("plebmc." + key, value == null ? null : value.getBytes(StandardCharsets.UTF_8));
		}
	};

	static final HudBox.Measure MEASURE = new HudBox.Measure() {
		@Override
		public int width(String s) {
			return Draw.width(s);
		}
	};

	private PlebClient() {
	}

	public static void init(Minecraft minecraft) {
		if (mc != null) {
			return;
		}
		mc = minecraft;
		view = new GameView(minecraft, clicks);
		PluginChannels.Log log = new PluginChannels.Log() {
			@Override
			public void info(String msg) {
				LOG.info(msg);
			}

			@Override
			public void warn(String msg) {
				LOG.warn(msg);
			}
		};
		PluginChannels.setLog(log);
		Apollo.setLog(log);
		try {
			Apollo.init();
			// tells the PlebColors server plugin this client draws RGB text codes
			PluginChannels.register("plebmc:rgb", new PluginChannels.Handler() {
				@Override
				public void onMessage(String channel, byte[] data) {
				}
			});
			net.plebmc.client.cosmetics.Cosmetics.init(new PluginChannels.Log() {
				@Override
				public void info(String msg) {
					LOG.info(msg);
				}

				@Override
				public void warn(String msg) {
					LOG.warn(msg);
				}
			});
			net.plebmc.client.cosmetics.Cosmetics.setToasts(new net.plebmc.client.cosmetics.Cosmetics.Toasts() {
				@Override
				public void show(String title, String message) {
					net.plebmc.client.apollo.module.NotificationModule nm = Apollo
							.module(net.plebmc.client.apollo.module.NotificationModule.class);
					if (nm != null && ClientPrefs.get().toasts) {
						nm.showLocal(title, message, ClientPrefs.get().toastMs());
					}
				}
			});
			mods.load(STORE);
			net.plebmc.client.mods.FpsLarp.INSTANCE.load(STORE);
			ClientPrefs.get().load(STORE);
			net.plebmc.client.cosmetics.Cosmetics.loadSeen(STORE.get(QUESTS_SEEN_KEY));
			net.plebmc.client.cosmetics.Cosmetics.loadLook(STORE.get(LOOK_KEY));
			savedLook = net.plebmc.client.cosmetics.Cosmetics.lookVersion();
			// the shop's catalog and textures, saved in the browser's file system (IndexedDB)
			net.plebmc.client.cosmetics.Cosmetics.setCache(new net.plebmc.client.cosmetics.Cosmetics.Cache() {
				@Override
				public byte[] get(String key) {
					net.lax1dude.eaglercraft.v1_8.internal.vfs2.VFile2 f = new net.lax1dude.eaglercraft.v1_8.internal.vfs2.VFile2(
							"plebmc_shop", key.replace('/', '_'));
					return f.exists() ? f.getAllBytes() : null;
				}

				@Override
				public void put(String key, byte[] value) {
					new net.lax1dude.eaglercraft.v1_8.internal.vfs2.VFile2("plebmc_shop", key.replace('/', '_'))
							.setAllBytes(value);
				}
			});
		} catch (Throwable t) {
			LOG.warn("mod/Apollo init failed: " + t);
		}
		PluginChannels.addSessionListener(new PluginChannels.SessionListener() {
			@Override
			public void onJoin() {
			}

			@Override
			public void onReset() {
				mods.toggleSprint.reset();
				clicks.clear();
			}
		});
	}

	/** What Right Shift opens (Settings > Behavior). */
	static net.minecraft.client.gui.GuiScreen shiftScreen() {
		switch (ClientPrefs.get().shiftOpens) {
		case 1:
			return new ModsScreen(null);
		case 2:
			return new QuestsScreen(null);
		case 3:
			return new ShopScreen(null);
		default:
			return new LauncherScreen(null);
		}
	}

	public static ModRegistry mods() {
		return mods;
	}

	/** The frame count the debug lines show (Fps Larp can change it; the game never runs differently). */
	public static String larpDebug(String line) {
		try {
			return net.plebmc.client.mods.FpsLarp.INSTANCE.debugLine(line, Minecraft.getDebugFPS(),
					System.currentTimeMillis());
		} catch (Throwable t) {
			return line;
		}
	}

	static GameView view() {
		return view;
	}

	static final String QUESTS_SEEN_KEY = "questsSeen";
	static final String LOOK_KEY = "lastLook";
	private static int savedLook;

	/** Saves the last balance and outfit (for the title screen) when they changed. */
	static void saveLookIfChanged() {
		try {
			int v = net.plebmc.client.cosmetics.Cosmetics.lookVersion();
			if (v != savedLook) {
				savedLook = v;
				STORE.set(LOOK_KEY, net.plebmc.client.cosmetics.Cosmetics.lookJson());
			}
		} catch (Throwable t) {
			LOG.warn("could not save the last look: " + t);
		}
	}

	/** Remembers which quests the player has seen (for the NEW tags). */
	static void saveQuestsSeen() {
		try {
			STORE.set(QUESTS_SEEN_KEY, net.plebmc.client.cosmetics.Cosmetics.seenJson());
		} catch (Throwable t) {
			LOG.warn("could not save seen quests: " + t);
		}
	}

	/** Copies every setting (not the login) to the clipboard; false if that failed. */
	public static boolean exportSettings() {
		try {
			saveSettings();
			net.minecraft.client.gui.GuiScreen.setClipboardString(net.plebmc.client.mods.SettingsTransfer.export(STORE));
			return true;
		} catch (Throwable t) {
			LOG.warn("could not export settings: " + t);
			return false;
		}
	}

	/** Loads an export from the clipboard: the number of parts loaded, or -1 if it isn't one. */
	public static int importSettings() {
		try {
			int n = net.plebmc.client.mods.SettingsTransfer.importInto(STORE,
					net.minecraft.client.gui.GuiScreen.getClipboardString());
			if (n > 0) {
				mods.load(STORE);
				net.plebmc.client.mods.FpsLarp.INSTANCE.load(STORE);
				ClientPrefs.get().load(STORE);
			}
			return n;
		} catch (Throwable t) {
			LOG.warn("could not import settings: " + t);
			return -1;
		}
	}

	public static void saveSettings() {
		try {
			mods.save(STORE);
			net.plebmc.client.mods.FpsLarp.INSTANCE.save(STORE);
			ClientPrefs.get().save(STORE);
		} catch (Throwable t) {
			LOG.warn("could not save mod settings: " + t);
		}
	}

	// ==================== connection ====================

	/** S01PacketJoinGame handled: the connection is in play, announce channels. */
	public static void onJoin() {
		if (mc == null) {
			return;
		}
		PluginChannels.reset();
		PluginChannels.attach(new PluginChannels.Sender() {
			@Override
			public boolean send(String channel, byte[] data) {
				NetHandlerPlayClient nh = mc.getNetHandler();
				if (nh == null) {
					return false;
				}
				ByteBuf buf = Unpooled.buffer(data.length, data.length);
				buf.writeBytes(data);
				nh.addToSendQueue(new C17PacketCustomPayload(channel, new PacketBuffer(buf)));
				return true;
			}
		});
		PluginChannels.onJoin();
	}

	/** Any S3FPacketCustomPayload (runs before vanilla handles the channel). */
	public static void onPayload(String channel, PacketBuffer data) {
		if (mc == null || channel == null || data == null) {
			return;
		}
		try {
			int start = data.readerIndex();
			byte[] bytes = new byte[data.readableBytes()];
			data.getBytes(start, bytes);
			PluginChannels.dispatch(channel, bytes);
		} catch (Throwable t) {
			LOG.warn("plugin message on " + channel + " failed: " + t);
		}
	}

	/** Disconnect or world unload: drop all per-connection state. */
	public static void onLeave() {
		ItemNbtWatch.reset();
		if (mc != null && PluginChannels.isJoined()) {
			PluginChannels.reset();
		}
	}

	// ==================== input ====================

	/** A key press in game with no screen open; true = consumed here. */
	public static boolean onKeyDown(int key) {
		if (mc == null || mc.currentScreen != null || mc.theWorld == null) {
			return false;
		}
		if (key == KEY_RSHIFT) {
			mc.displayGuiScreen(shiftScreen());
			return true;
		}
		if (key == mc.gameSettings.keyBindSprint.getKeyCode()) {
			mods.toggleSprint.onSprintKey();
		}
		if (key == KeyboardConstants.KEY_F && mc.gameSettings.keyBindSneak.isKeyDown()) {
			squadPing();
		}
		return false;
	}

	private static long lastPing;

	/**
	 * Sneak + F: PlebNite (and other modern servers) mark the block you look
	 * at for your squad when you press the swap-hands key while sneaking.
	 * 1.8 has no off hand, so this sends that action itself: the player
	 * digging packet with action 6 (SWAP_ITEM_WITH_OFFHAND in 1.9+), which the
	 * server's version translator passes through. Only sent to servers that
	 * speak Apollo (never to a plain 1.8 server, which doesn't know action 6).
	 */
	static boolean squadPing() {
		if (!mods.squadPing.isActive() || mc.isSingleplayer() || !Apollo.isServerApollo()) {
			return false;
		}
		long now = System.currentTimeMillis();
		if (now - lastPing < 300L) {
			return false;
		}
		lastPing = now;
		NetHandlerPlayClient nh = mc.getNetHandler();
		if (nh == null || nh.getNetworkManager() == null) {
			return false;
		}
		// action 6, block position 0/0/0 (packed long), face 0 (down): what a 1.9+ client sends
		byte[] body = { 6, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
		try {
			return nh.getNetworkManager().sendRawPacket(0x07, body);
		} catch (Throwable t) {
			LOG.warn("squad ping failed: " + t);
			return false;
		}
	}

	/** In-world attack (0) or use (1) click. */
	public static void onWorldClick(int button) {
		clicks.click(button, System.currentTimeMillis());
	}

	/** EntityRenderer: may the built-in zoom key (C) zoom right now? */
	public static boolean zoomAllowed(boolean keyDown) {
		return keyDown && mods.zoom.isActive();
	}

	/** Zoom key pressed: the FOV to start at (the Zoom level setting). */
	public static float zoomStart(float eaglerDefault) {
		try {
			return mods.zoom.startFov();
		} catch (Throwable t) {
			return eaglerDefault;
		}
	}

	/** Mouse wheel while zooming. */
	public static float zoomScroll(float current, int wheel) {
		return mods.zoom.scrolled(current, wheel);
	}

	private static boolean smoothByZoom;
	private static boolean smoothBefore;

	/** Smooth camera while zoomed (and puts the player's own setting back after). */
	private static void syncZoomCamera() {
		boolean want = mc.isZoomKey && mods.zoom.isActive() && mods.zoom.smoothCamera() && mc.currentScreen == null;
		if (want != smoothByZoom) {
			if (want) {
				smoothBefore = mc.gameSettings.smoothCamera;
				mc.gameSettings.smoothCamera = true;
			} else {
				mc.gameSettings.smoothCamera = smoothBefore;
			}
			smoothByZoom = want;
		}
	}

	/** EntityRenderer.updateLightmap: the gamma to use. */
	public static float gamma(float base) {
		float min = mods.fullbright.minLight();
		return min > 0f ? Math.max(base, min * 16f) : base;
	}

	/**
	 * Minecraft.clickMouse on a miss: apply the vanilla 10-tick miss penalty
	 * unless the server's Apollo combat module turned it off.
	 */
	public static boolean missPenalty() {
		Object m = Apollo.module("combat");
		return !(m instanceof OptionsOnlyModule && ((OptionsOnlyModule) m).option("disable-miss-penalty", false));
	}

	public static boolean sprintHeld() {
		return mods.toggleSprint.sprintHeld();
	}

	/** EntityPlayerSP.moveEntity: would this horizontal move cross an Apollo border that forbids it? */
	public static boolean borderBlocks(double fromX, double fromZ, double toX, double toZ) {
		BorderModule b = Apollo.module(BorderModule.class);
		return b != null && b.isEnabled() && b.blocksMove("", fromX, fromZ, toX, toZ);
	}

	/** FontRenderer: Apollo nick hider swaps the player's own name in drawn text. */
	public static String filterText(String text) {
		if (text == null || mc == null || mc.thePlayer == null) {
			return text;
		}
		NickHiderModule nh = Apollo.module(NickHiderModule.class);
		if (nh == null || !nh.isEnabled() || nh.nick().isEmpty()) {
			return text;
		}
		return nh.apply(text, mc.thePlayer.getName());
	}

	// ==================== HUD ====================

	/** GuiIngame.renderGameOverlay end: Apollo overlays then HUD mods. */
	/** The partial tick of the frame being drawn (HUD mods read the camera with it). */
	static float framePartialTicks = 1f;

	public static void renderHud(ScaledResolution res, float partialTicks) {
		if (mc == null) {
			return;
		}
		framePartialTicks = partialTicks;
		int guiW = res.getScaledWidth();
		int guiH = res.getScaledHeight();
		try {
			FpsBoost.sync(mc, mods.fpsBoost.isActive());
			syncZoomCamera();
		} catch (Throwable t) {
			LOG.warn("FPS boost failed: " + t);
		}
		ItemNbtWatch.tick(mc);
		try {
			Apollo.tick();
			long now = Apollo.now();
			Entity cam = mc.getRenderViewEntity();
			boolean have = cam != null && mc.gameSettings.thirdPersonView == 0;
			double ex = 0, ey = 0, ez = 0;
			float yaw = 0, pitch = 0;
			if (cam != null) {
				ex = cam.prevPosX + (cam.posX - cam.prevPosX) * partialTicks;
				ey = cam.prevPosY + (cam.posY - cam.prevPosY) * partialTicks + cam.getEyeHeight();
				ez = cam.prevPosZ + (cam.posZ - cam.prevPosZ) * partialTicks;
				yaw = cam.prevRotationYaw + (cam.rotationYaw - cam.prevRotationYaw) * partialTicks;
				pitch = cam.prevRotationPitch + (cam.rotationPitch - cam.prevRotationPitch) * partialTicks;
			}
			float fov = mc.entityRenderer.currentProjMatrixFOV > 1f ? mc.entityRenderer.currentProjMatrixFOV
					: mc.gameSettings.fovSetting;
			ApolloOverlay.INSTANCE.render(guiW, guiH, now, have, ex, ey, ez, yaw, pitch, fov, mc.displayWidth,
					mc.displayHeight, partialTicks);
			if (!(mc.currentScreen instanceof HudEditorScreen) && !mc.gameSettings.showDebugInfo) {
				lowHealth(guiW, guiH);
				for (Mod m : mods.all()) {
					if (m.hasHud() && m.isActive()) {
						drawMod(m, m.layout(view, MEASURE), guiW, guiH, false);
					}
				}
			}
		} catch (Throwable t) {
			LOG.warn("HUD render failed: " + t);
		}
	}

	/** Draws one HUD box at its saved position; returns {x, y, w, h}. */
	static int[] drawMod(Mod m, HudBox box, int guiW, int guiH, boolean outline) {
		return drawMod(m, box, guiW, guiH, outline ? 0xFFFFFF55 : 0);
	}

	/**
	 * Draws one HUD box at its saved position with the mod's size, text
	 * color and background; returns its on-screen {x, y, w, h}.
	 */
	static int[] drawMod(Mod m, HudBox box, int guiW, int guiH, int outline) {
		if (box == null || box.width <= 0) {
			return null;
		}
		float sc = m.hudScale();
		int w = Math.max(1, Math.round(box.width * sc));
		int h = Math.max(1, Math.round(box.height * sc));
		int[] p = ModRegistry.place(m, w, h, guiW, guiH);
		int textColor = m.hudTextColor();
		int bg = m.hudBackground();
		boolean shadow = ClientPrefs.get().hudShadow;
		net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager.pushMatrix();
		net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager.translate(p[0], p[1], 0f);
		net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager.scale(sc, sc, 1f);
		for (HudBox.Rect r : box.rects) {
			int c = r.argb;
			if (bg >= 0 && (c == HudBox.BG || c == HudBox.BG_LIGHT)) {
				c = bg;
			}
			Draw.fill(r.x, r.y, r.w, r.h, c);
		}
		if (!box.items.isEmpty()) {
			drawItems(box.items);
			// text over the icons (item models draw in front of z 0)
			net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager.translate(0f, 0f, 200f);
		}
		for (HudBox.Text t : box.texts) {
			int c = textColor != 0 && t.argb == HudBox.TEXT ? textColor : t.argb;
			float tx = t.centered ? t.x - Draw.width(t.text) / 2f : t.x;
			Draw.text(t.text, tx, t.y, c, shadow);
		}
		net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager.popMatrix();
		if (outline != 0) {
			Draw.fill(p[0] - 1, p[1] - 1, w + 2, 1, outline);
			Draw.fill(p[0] - 1, p[1] + h, w + 2, 1, outline);
			Draw.fill(p[0] - 1, p[1], 1, h, outline);
			Draw.fill(p[0] + w, p[1], 1, h, outline);
		}
		return new int[] { p[0], p[1], w, h };
	}

	/** Real item icons (the stack when there is one, else the named item). */
	static void drawItems(java.util.List<HudBox.Item> items) {
		net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager.enableRescaleNormal();
		net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
		try {
			for (HudBox.Item it : items) {
				net.minecraft.item.ItemStack st = it.stack instanceof net.minecraft.item.ItemStack
						? (net.minecraft.item.ItemStack) it.stack
						: ItemIcons.forName(it.id);
				if (st != null) {
					mc.getRenderItem().renderItemAndEffectIntoGUI(st, it.x, it.y);
				}
			}
		} catch (Throwable t) {
			LOG.warn("item icon failed: " + t);
		}
		net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
		net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager.disableRescaleNormal();
		net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager.disableLighting();
		net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager.enableBlend();
		net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager.enableAlpha();
	}

	/** Red screen edges while health is low (Low Health Warning). */
	private static void lowHealth(int w, int h) {
		float k = mods.lowHealth.intensity(view.health(), System.currentTimeMillis());
		if (k <= 0f) {
			return;
		}
		int band = Math.max(12, Math.min(w, h) / 6);
		for (int i = 0; i < band; i++) {
			float f = 1f - i / (float) band;
			int a = Math.round(170 * k * f * f);
			if (a <= 0) {
				continue;
			}
			int c = (a << 24) | 0xC01010;
			Draw.fill(i, i, w - i * 2, 1, c);
			Draw.fill(i, h - 1 - i, w - i * 2, 1, c);
			Draw.fill(i, i + 1, 1, h - i * 2 - 2, c);
			Draw.fill(w - 1 - i, i + 1, 1, h - i * 2 - 2, c);
		}
	}

	static EntityPlayerSP player() {
		return mc == null ? null : mc.thePlayer;
	}
}
