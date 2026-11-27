package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.List;

import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.lax1dude.eaglercraft.v1_8.opengl.ImageData;
import net.lax1dude.eaglercraft.v1_8.opengl.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.plebmc.client.mods.ClientPrefs;

/**
 * The PlebClient title screen, drawn over the vanilla panorama when the
 * PlebClient menus are on (GuiMainMenu hands drawing, clicks and keys to
 * this class; the vanilla buttons stay in the list but are hidden).
 *
 * <p>Deliberately quiet: the PlebMc wallpaper (or the panorama) fills the
 * whole screen, including behind the sidebar. On top of it there is only
 * <ul>
 * <li>a transparent sidebar on the left (the mascot, icons, and labels that
 * slide in while the mouse is over it), separated by a thin accent line,
 * with a translucent hover wash and a small accent indicator;</li>
 * <li>the player's own skin, with the hat and cloak they last wore on
 * PlebMc, standing in the middle (click it for the web store), the Pleb
 * Coins balance in the top right and the mini mascot in the corner;</li>
 * <li>a faint version line at the bottom (plus an optional clock and splash
 * text).</li>
 * </ul>
 * The sidebar can also stay open or stay icons only (Settings, Title
 * screen). Every animation is eased by elapsed time, so it runs the same at
 * 30 or 240 FPS.
 */
public final class TitleMenu {

	private TitleMenu() {
	}

	public static boolean active() {
		Minecraft mc = Minecraft.getMinecraft();
		return PlebTheme.enabled() && (mc == null || !mc.isDemo());
	}

	// ==================== state ====================

	private static GuiScreen screen;
	private static long openedAt;
	private static long lastFrame;
	private static float dt;

	private static float expand; // 0 collapsed .. 1 open
	private static long leftAt; // when the mouse left the sidebar
	private static float[] hover = new float[0];
	private static float indY = -1, indH, indA; // the sliding indicator
	private static int keySel = -1; // keyboard selection
	private static float creditsHover;
	private static int tipItem = -1;
	private static float tipA;

	private static String pending; // action waiting for the fade-out
	private static long pendingAt;
	private static long fadeInAt;

	private static final List<float[]> ripples = new ArrayList<>(); // x, y, start(ms, as float offset)
	private static long rippleBase;

	/** Called from GuiMainMenu.initGui (also on resize and when coming back from another screen). */
	public static void init(GuiScreen s) {
		long now = System.currentTimeMillis();
		PlebClient.saveLookIfChanged();
		if (s != screen) {
			screen = s;
			openedAt = now;
			expand = mode() == 1 ? 1f : 0f;
			indY = -1;
			fadeInAt = 0;
		} else if (pending != null || pendingAt != 0) {
			// back from a screen we opened: fade in instead of popping
			fadeInAt = now;
		}
		pending = null;
		pendingAt = 0;
		keySel = -1;
		hover = new float[TitleActions.ITEMS.length];
		ripples.clear();
	}

	/** The title screen went away (another screen or the game). */
	public static void closed() {
		PlebClient.saveLookIfChanged();
	}

	// ==================== layout ====================

	private static final int COLLAPSED = 30;

	/** 0 opens on hover, 1 always open, 2 icons only. */
	private static int mode() {
		try {
			return ClientPrefs.get().titleSidebar;
		} catch (Throwable t) {
			return 0;
		}
	}

	private static int expanded(int w) {
		return Math.max(96, Math.min(124, w / 3));
	}

	private static float sideWidth(int w) {
		return COLLAPSED + (expanded(w) - COLLAPSED) * ease(expand);
	}

	/** Where the content starts: right of the sidebar as it sits when nobody points at it. */
	private static int contentLeft(int w) {
		return mode() == 1 ? expanded(w) : COLLAPSED;
	}

	private static final int TOP = 34;

	/** Y of each sidebar entry (top group under the mascot, bottom group at the bottom). */
	private static int[] rows(int h) {
		String[][] it = TitleActions.ITEMS;
		int[] y = new int[it.length];
		int split = it.length;
		for (int i = 0; i < it.length; i++) {
			if ("-".equals(it[i][0])) {
				split = i;
				break;
			}
		}
		int bottomCount = it.length - split - (split < it.length ? 1 : 0);
		int row = rowHeight(h);
		for (int i = 0; i < split; i++) {
			y[i] = TOP + i * row;
		}
		if (split < it.length) {
			y[split] = -1;
		}
		int by = h - 8 - bottomCount * row;
		for (int i = split + 1; i < it.length; i++) {
			y[i] = Math.max(TOP + split * row + 4, by + (i - split - 1) * row);
		}
		return y;
	}

	private static int rowHeight(int h) {
		int n = 0;
		for (String[] e : TitleActions.ITEMS) {
			if (!"-".equals(e[0])) {
				n++;
			}
		}
		return Math.max(14, Math.min(22, (h - 8 - TOP) / (n + 1)));
	}

	// ==================== drawing ====================

	/** Draws everything over the panorama. */
	public static void draw(GuiScreen s, int mx, int my, float pt, String splash) {
		if (s != screen) {
			init(s);
		}
		long now = System.currentTimeMillis();
		dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		boolean anim = animations();
		float intro = anim ? Math.min(1f, (now - openedAt) / 700f) : 1f;
		float t = (now % 3_600_000L) / 1000f;
		int w = s.width, h = s.height;
		int acc = Ui.accent();
		ClientPrefs pr = ClientPrefs.get();
		// text writes depth here: without this the quads drawn over it would have holes
		GlStateManager.disableDepth();
		EasterEggs.beginTitle(w, h);

		if (pr.titleBackground == 0) {
			wallpaper(w, h, mx, my);
		}
		backdrop(w, h, pr.titlePanorama);

		// ---- sidebar hover / expand ----
		int mode = mode();
		float sw = sideWidth(w);
		boolean inSide = mx >= 0 && mx < sw + 2 && my >= 0 && my < h && pending == null;
		if (inSide) {
			leftAt = 0;
		} else if (leftAt == 0) {
			leftAt = now;
		}
		boolean open = mode == 1 || (mode == 0 && (inSide || (leftAt != 0 && now - leftAt < 140) || keySel >= 0));
		expand = approach(expand, open ? 1f : 0f, open ? 11f : 9f);
		sw = sideWidth(w);
		float slideIn = ease(intro);

		// ---- content ----
		int x0 = contentLeft(w);
		if (pr.titleParticles > 0 && animations()) {
			particles(w, h, now, pr.titleParticles, slideIn);
		}
		EasterEggs.titleExtras(w, h, now);
		showcase(x0, w, h, mx, my, t, acc, slideIn);
		coinsChip(w, mx, my, acc, slideIn);
		footer(x0, w, h, mx, my, splash, acc, slideIn, pr);
		if (pr.titleBuddy) {
			buddy(w, h, mx, my, now, slideIn);
		}

		// a light veil over the content while the sidebar floats open over it
		if (mode == 0 && expand > 0.01f) {
			fill(sw, 0, w - sw, h, Ui.alpha(0xFF02050A, Math.round(0x30 * ease(expand))));
		}
		sidebar(w, h, mx, my, t, acc, intro, pr.titleMascot);

		// ripples and the fade used between sections
		ripples(now, acc);
		float fade = 0f;
		if (pending != null) {
			fade = Math.min(1f, (now - pendingAt) / 160f);
		} else if (fadeInAt != 0) {
			fade = 1f - Math.min(1f, (now - fadeInAt) / 220f);
			if (fade <= 0f) {
				fadeInAt = 0;
			}
		}
		if (intro < 1f) {
			fade = Math.max(fade, 1f - ease(Math.min(1f, intro * 2.2f)));
		}
		if (fade > 0f) {
			fill(0, 0, w, h, Ui.alpha(0xFF03060A, Math.round(255 * fade)));
		}
		if (pending != null && now - pendingAt >= 160) {
			String id = pending;
			pending = null;
			// keep pendingAt so init() knows to fade back in
			TitleActions.run(s, id);
		}
		EasterEggs.endTitle();
		EasterEggs.overlay(w, h);
		GlStateManager.color(1f, 1f, 1f, 1f);
		GlStateManager.enableTexture2D();
		GlStateManager.enableDepth();
	}

	/** Only shading on the panorama: a soft darkening on the left and at the bottom, plus the chosen dim. */
	private static void backdrop(int w, int h, int dim) {
		int all = dim == 0 ? 0 : dim == 1 ? 0x30 : 0x80;
		if (all > 0) {
			fill(0, 0, w, h, Ui.alpha(0xFF03060A, all));
		}
		gradientH(0, 0, Math.min(w, 170), h, Ui.alpha(0xFF03060A, dim == 0 ? 0x50 : 0x68), 0x0003060A);
		PlebTheme.gradient(0, h - h / 4, w, h / 4, 0x0003060A, Ui.alpha(0xFF03060A, dim == 0 ? 0x60 : 0x80));
	}

	// ---- the wallpaper ----

	private static final ResourceLocation WALLPAPER = new ResourceLocation("plebitems", "textures/gui/wallpaper.jpg");
	private static float parX, parY;

	/** The PlebMc wallpaper over the whole screen (cover), drifting a little with the mouse. */
	private static void wallpaper(int w, int h, int mx, int my) {
		Minecraft mc = Minecraft.getMinecraft();
		try {
			mc.getTextureManager().bindTexture(WALLPAPER);
			net.minecraft.client.renderer.texture.ITextureObject tex = mc.getTextureManager().getTexture(WALLPAPER);
			if (tex != null) {
				tex.setBlurMipmap(true, false);
			}
		} catch (Throwable t) {
			return;
		}
		parX = approach(parX, w > 0 ? (mx / (float) w - 0.5f) : 0f, 3f);
		parY = approach(parY, h > 0 ? (my / (float) h - 0.5f) : 0f, 3f);
		// cover the screen at 16:9 with a 4% margin for the drift
		float iw = w * 1.04f, ih = iw * 9f / 16f;
		if (ih < h * 1.04f) {
			ih = h * 1.04f;
			iw = ih * 16f / 9f;
		}
		float x = (w - iw) / 2f - parX * (iw - w) * 0.9f;
		float y = (h - ih) / 2f - parY * (ih - h) * 0.9f;
		GlStateManager.enableTexture2D();
		GlStateManager.disableBlend();
		GlStateManager.color(1f, 1f, 1f, 1f);
		WorldRenderer r = Tessellator.getInstance().getWorldRenderer();
		r.begin(7, DefaultVertexFormats.POSITION_TEX);
		r.pos(x, y + ih, 0).tex(0, 1).endVertex();
		r.pos(x + iw, y + ih, 0).tex(1, 1).endVertex();
		r.pos(x + iw, y, 0).tex(1, 0).endVertex();
		r.pos(x, y, 0).tex(0, 0).endVertex();
		Tessellator.getInstance().draw();
		GlStateManager.enableBlend();
	}

	/**
	 * The wallpaper behind menus: centered and still, softened by drawing it a
	 * few times slightly offset (a cheap blur), then darkened so text reads.
	 */
	static boolean menuWallpaper(int w, int h, boolean blur, int dim) {
		Minecraft mc = Minecraft.getMinecraft();
		ResourceLocation img = blur ? blurredWallpaper() : WALLPAPER;
		if (img == null) {
			img = WALLPAPER;
		}
		try {
			mc.getTextureManager().bindTexture(img);
			net.minecraft.client.renderer.texture.ITextureObject tex = mc.getTextureManager().getTexture(img);
			if (tex != null) {
				tex.setBlurMipmap(true, false);
			}
		} catch (Throwable t) {
			return false;
		}
		float iw = w * 1.06f, ih = iw * 9f / 16f;
		if (ih < h * 1.06f) {
			ih = h * 1.06f;
			iw = ih * 16f / 9f;
		}
		float x = (w - iw) / 2f, y = (h - ih) / 2f;
		GlStateManager.enableTexture2D();
		GlStateManager.color(1f, 1f, 1f, 1f);
		GlStateManager.disableBlend();
		quad(x, y, iw, ih);
		GlStateManager.enableBlend();
		GlStateManager.disableTexture2D();
		Ui.rect(0, 0, w, h, Ui.alpha(0xFF05080D, dim));
		GlStateManager.enableTexture2D();
		return true;
	}

	private static ResourceLocation blurred;
	private static boolean blurFailed;

	/**
	 * A small, blurred copy of the wallpaper, made once: drawn stretched with
	 * smooth filtering it looks like a strong blur and costs one quad.
	 */
	private static ResourceLocation blurredWallpaper() {
		if (blurred != null || blurFailed) {
			return blurred;
		}
		blurFailed = true;
		try {
			java.io.InputStream in = Minecraft.getMinecraft().getResourceManager().getResource(WALLPAPER).getInputStream();
			java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
			byte[] buf = new byte[16384];
			int n;
			while ((n = in.read(buf)) > 0) {
				bo.write(buf, 0, n);
			}
			in.close();
			ImageData src = ImageData.loadImageFile(bo.toByteArray());
			if (src == null || src.width < 16 || src.height < 9) {
				return null;
			}
			int bw = 256, bh = 144;
			int[] px = new int[bw * bh];
			// area average down to 256x144
			for (int y = 0; y < bh; y++) {
				int sy0 = y * src.height / bh, sy1 = Math.max(sy0 + 1, (y + 1) * src.height / bh);
				for (int x = 0; x < bw; x++) {
					int sx0 = x * src.width / bw, sx1 = Math.max(sx0 + 1, (x + 1) * src.width / bw);
					long c0 = 0, c1 = 0, c2 = 0, c3 = 0, cnt = 0;
					for (int yy = sy0; yy < sy1; yy += 2) {
						for (int xx = sx0; xx < sx1; xx += 2) {
							int p = src.pixels[yy * src.width + xx];
							c0 += p >>> 24;
							c1 += p >> 16 & 0xFF;
							c2 += p >> 8 & 0xFF;
							c3 += p & 0xFF;
							cnt++;
						}
					}
					px[y * bw + x] = (int) (c0 / cnt) << 24 | (int) (c1 / cnt) << 16 | (int) (c2 / cnt) << 8 | (int) (c3 / cnt);
				}
			}
			for (int pass = 0; pass < 2; pass++) {
				px = boxBlur(px, bw, bh, 2);
			}
			for (int i = 0; i < px.length; i++) {
				px[i] |= 0xFF000000;
			}
			blurred = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("pleb_wallpaper_blur",
					new net.minecraft.client.renderer.texture.DynamicTexture(new ImageData(bw, bh, px, true)));
			blurFailed = false;
		} catch (Throwable t) {
			blurred = null;
		}
		return blurred;
	}

	/** One horizontal and one vertical box blur pass, per 8-bit channel. */
	private static int[] boxBlur(int[] p, int w, int h, int r) {
		int[] tmp = new int[p.length], out = new int[p.length];
		for (int pass = 0; pass < 2; pass++) {
			int[] in = pass == 0 ? p : tmp, dst = pass == 0 ? tmp : out;
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int s0 = 0, s1 = 0, s2 = 0, s3 = 0, c = 0;
					for (int k = -r; k <= r; k++) {
						int xx = pass == 0 ? Math.max(0, Math.min(w - 1, x + k)) : x;
						int yy = pass == 0 ? y : Math.max(0, Math.min(h - 1, y + k));
						int v = in[yy * w + xx];
						s0 += v >>> 24;
						s1 += v >> 16 & 0xFF;
						s2 += v >> 8 & 0xFF;
						s3 += v & 0xFF;
						c++;
					}
					dst[y * w + x] = (s0 / c) << 24 | (s1 / c) << 16 | (s2 / c) << 8 | (s3 / c);
				}
			}
		}
		return out;
	}

	private static void quad(float x, float y, float iw, float ih) {
		WorldRenderer r = Tessellator.getInstance().getWorldRenderer();
		r.begin(7, DefaultVertexFormats.POSITION_TEX);
		r.pos(x, y + ih, 0).tex(0, 1).endVertex();
		r.pos(x + iw, y + ih, 0).tex(1, 1).endVertex();
		r.pos(x + iw, y, 0).tex(1, 0).endVertex();
		r.pos(x, y, 0).tex(0, 0).endVertex();
		Tessellator.getInstance().draw();
	}

	// ---- the player showcase (opens the web store) ----

	private static final String STORE_URL = "https://store.plebmc.top";
	private static net.minecraft.client.model.ModelPlayer steve, alex;
	private static int showX, showY, showW, showH;
	private static float showHover;
	private static float storeX, storeY, storeW, storeH;

	private static void showcase(int x0, int w, int h, int mx, int my, float t, int acc, float in) {
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		// right of center, so the wallpaper's own mascot keeps the left side
		float cx = x0 + (w - x0) * (ClientPrefs.get().titleBackground == 0 ? 0.66f : 0.5f);
		float scale = Math.max(34f, Math.min(64f, h * 0.2f));
		float tall = 2f * scale; // the model is 32 pixels = 2 units
		float foot = h / 2f + tall / 2f + 2 + (1f - in) * 10f;
		showW = Math.round(scale * 1.6f);
		showH = Math.round(tall + 8);
		showX = Math.round(cx - showW / 2f);
		showY = Math.round(foot - tall - 6);
		// the pill under the player is part of the button too
		int pillW = font.getStringWidth("Store") + 26, pillH = 16;
		float pillX = cx - pillW / 2f, pillY = foot + 20;
		// only the Store button is a button; the player just watches the mouse
		storeX = pillX;
		storeY = pillY;
		storeW = pillW;
		storeH = pillH;
		boolean over = pending == null && mx >= sideWidth(w) && mx >= pillX && mx < pillX + pillW && my >= pillY
				&& my < pillY + pillH;
		showHover = approach(showHover, over ? 1f : 0f, 10f);
		float hv = ease(showHover);
		int a = Math.round(255 * in);

		// a soft spot of light to stand in, brighter on hover
		PlebTheme.glow(cx, foot - 1, scale * (0.9f + 0.15f * hv), Ui.alpha(acc, Math.round((0x30 + 0x30 * hv) * in)));
		gradientH(cx - scale * 0.55f, foot, scale * 0.55f, 1, Ui.alpha(acc, 0), Ui.alpha(acc, Math.round(0xA0 * in)));
		gradientH(cx, foot, scale * 0.55f, 1, Ui.alpha(acc, Math.round(0xA0 * in)), Ui.alpha(acc, 0));

		// the player: your skin and what you last wore, turning gently and looking at the mouse
		float headY = foot - tall * 0.85f;
		float look = Math.max(-1f, Math.min(1f, (cx - mx) / Math.max(1f, w * 0.5f)));
		float lookY = Math.max(-1f, Math.min(1f, (headY - my) / Math.max(1f, h * 0.5f)));
		float sway = animations() ? (float) Math.sin(t * 0.6f) * 14f : 0f;
		float bodyYaw = sway + look * 25f + hv * 8f * (float) Math.sin(t * 2f);
		drawPlayer(cx, foot - (1f + hv) * 1.5f, scale, bodyYaw, look * 35f, -lookY * 20f, t, in,
				net.plebmc.client.cosmetics.Cosmetics.lastWorn(net.plebmc.client.cosmetics.Cosmetics.CLOAK),
				net.plebmc.client.cosmetics.Cosmetics.lastWorn(net.plebmc.client.cosmetics.Cosmetics.HAT), -8f);

		// name, then the store pill
		String name = TitleActions.username();
		if (name != null && !name.isEmpty()) {
			int nw = font.getStringWidth(name);
			textShadow(font, name, cx - nw / 2f, foot + 6, Ui.alpha(0xFFFFFFFF, a));
		}
		roundF(pillX, pillY, pillW, pillH, Ui.alpha(Ui.mix(0xFF05090E, acc, hv * 0.85f), Math.round((0x9C + 0x50 * hv) * in)));
		outlineF(pillX, pillY, pillW, pillH, Ui.alpha(acc, Math.round((0x90 + 0x6F * hv) * in)));
		int fg = Ui.mix(0xFFFFFFFF, Ui.ON_ACCENT, hv);
		iconF("shop", pillX + 6, pillY + 3.5f, 1f, Ui.alpha(hv > 0.5f ? fg : acc, a));
		text(font, "Store", pillX + 19, pillY + 4, 1f, Ui.alpha(fg, a));
		if (hv > 0.02f) {
			String tip = "Open the store";
			int tw = font.getStringWidth(tip);
			textShadow(font, tip, cx - tw / 2f, pillY + pillH + 5 + (1f - hv) * -3f, Ui.alpha(0xFFDCE4EA, Math.round(0xC0 * hv * in)));
		}
	}

	/** Draws the player model in the GUI: feet at (x, foot), {@code scale} GUI pixels per block. */
	/**
	 * The player's own skin wearing the given cloak and hat, for previews that work
	 * without a world (the shop on the main menu).
	 */
	public static void drawSkinModel(float x, float foot, float scale, float yaw, net.plebmc.client.cosmetics.Cosmetics.Item cloak,
			net.plebmc.client.cosmetics.Cosmetics.Item hat) {
		drawSkinModel(x, foot, scale, yaw, cloak, hat, null);
	}

	public static void drawSkinModel(float x, float foot, float scale, float yaw, net.plebmc.client.cosmetics.Cosmetics.Item cloak,
			net.plebmc.client.cosmetics.Cosmetics.Item hat, net.plebmc.client.cosmetics.Cosmetics.Item wings) {
		drawWings = wings;
		try {
			// seconds within the hour: the whole clock in a float is too coarse to move anything
			drawPlayer(x, foot, scale, yaw, 0f, 0f, (System.currentTimeMillis() % 3_600_000L) / 1000f, 1f, cloak, hat, 0f);
		} finally {
			drawWings = null;
		}
	}

	/** Wings for the next drawPlayer (the title screen shows the last worn ones). */
	private static net.plebmc.client.cosmetics.Cosmetics.Item drawWings;

	private static void drawPlayer(float x, float foot, float scale, float bodyYaw, float headYaw, float headPitch, float t,
			float in, net.plebmc.client.cosmetics.Cosmetics.Item cloak, net.plebmc.client.cosmetics.Cosmetics.Item hat, float tilt) {
		Minecraft mc = Minecraft.getMinecraft();
		boolean slim = TitleActions.slim();
		if (steve == null) {
			steve = new net.minecraft.client.model.ModelPlayer(0f, false);
			alex = new net.minecraft.client.model.ModelPlayer(0f, true);
		}
		net.minecraft.client.model.ModelPlayer m = slim ? alex : steve;
		m.isChild = false;
		m.isSneak = false;
		m.swingProgress = 0f;
		ResourceLocation skin = TitleActions.skin();
		GlStateManager.pushMatrix();
		GlStateManager.clear(256);
		GlStateManager.enableDepth();
		GlStateManager.depthMask(true);
		GlStateManager.enableTexture2D();
		GlStateManager.enableAlpha();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GlStateManager.color(1f, 1f, 1f, in);
		GlStateManager.disableCull();
		try {
			GlStateManager.translate(x, foot - 1.5f * scale, 120f);
			GlStateManager.scale(scale, scale, scale);
			GlStateManager.rotate(180f, 1f, 0f, 0f);
			GlStateManager.scale(1f, -1f, 1f);
			net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
			GlStateManager.translate(0f, 1f, 0f);
			GlStateManager.rotate(tilt, 1f, 0f, 0f);
			GlStateManager.rotate(bodyYaw, 0f, 1f, 0f);
			GlStateManager.translate(0f, -1f, 0f);
			float age = t * 20f;
			m.setRotationAngles(0f, 0f, age, headYaw, headPitch, 0.0625f, null);
			if (skin != null) {
				mc.getTextureManager().bindTexture(skin);
			}
			float sc = 0.0625f;
			m.bipedHead.render(sc);
			m.bipedBody.render(sc);
			m.bipedRightArm.render(sc);
			m.bipedLeftArm.render(sc);
			m.bipedRightLeg.render(sc);
			m.bipedLeftLeg.render(sc);
			m.bipedHeadwear.render(sc);
			m.bipedBodyWear.render(sc);
			m.bipedRightArmwear.render(sc);
			m.bipedLeftArmwear.render(sc);
			m.bipedRightLegwear.render(sc);
			m.bipedLeftLegwear.render(sc);
			if (cloak != null) {
				mc.getTextureManager().bindTexture(CloakLayer.texture(cloak));
				GlStateManager.pushMatrix();
				GlStateManager.translate(0f, 0f, 0.125f);
				GlStateManager.rotate(8f + (float) Math.sin(t * 1.3f) * 3f, 1f, 0f, 0f);
				GlStateManager.rotate(180f, 0f, 1f, 0f);
				m.renderCape(sc);
				GlStateManager.popMatrix();
			}
			net.plebmc.client.cosmetics.Cosmetics.Item wings = drawWings != null ? drawWings
					: net.plebmc.client.cosmetics.Cosmetics.lastWorn(net.plebmc.client.cosmetics.Cosmetics.WINGS);
			if (wings != null) {
				GlStateManager.pushMatrix();
				m.bipedBody.postRender(sc);
				GlStateManager.scale(sc, sc, sc);
				CosmeticLayer.wings(wings, age, 1f, 1f);
				GlStateManager.popMatrix();
			}
			if (hat != null) {
				GlStateManager.pushMatrix();
				m.bipedHead.postRender(sc);
				GlStateManager.scale(sc, sc, sc);
				if (net.plebmc.client.cosmetics.Cosmetics.MODEL.equals(hat.render)) {
					CosmeticLayer.model(hat);
				} else {
					CosmeticLayer.draw(hat.render, hat.color, age);
				}
				GlStateManager.popMatrix();
			}
		} catch (Throwable e) {
			// a model or texture that isn't ready: skip this frame
		} finally {
			GlStateManager.popMatrix();
			net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
			GlStateManager.disableLighting();
			GlStateManager.enableCull();
			GlStateManager.disableDepth();
			GlStateManager.enableTexture2D();
			GlStateManager.color(1f, 1f, 1f, 1f);
		}
	}

	// ---- coins (top right) ----

	private static int coinX, coinY, coinW, coinH;
	private static float coinHover;

	private static void coinsChip(int w, int mx, int my, int acc, float in) {
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		long c = net.plebmc.client.cosmetics.Cosmetics.lastCoins();
		String amount = c < 0 ? "--" : String.format("%,d", c);
		String cur = shortCurrency(net.plebmc.client.cosmetics.Cosmetics.lastCurrency());
		int aw = font.getStringWidth(amount), cw = font.getStringWidth(cur);
		coinW = 8 + 9 + 5 + aw + 5 + cw + 8;
		coinH = 18;
		coinX = w - 8 - coinW;
		coinY = 8;
		boolean over = pending == null && mx >= coinX && mx < coinX + coinW && my >= coinY && my < coinY + coinH;
		coinHover = approach(coinHover, over ? 1f : 0f, 14f);
		float hv = ease(coinHover);
		int a = Math.round(255 * in);
		roundF(coinX, coinY, coinW, coinH, Ui.alpha(0xFF05090E, Math.round((0x9C + 0x30 * hv) * in)));
		outlineF(coinX, coinY, coinW, coinH, Ui.alpha(Ui.mix(0xFF2A3440, Ui.GOLD, 0.35f + 0.65f * hv), a));
		float spin = animations() ? (float) Math.abs(Math.cos(System.currentTimeMillis() / 900.0)) : 1f;
		GlStateManager.pushMatrix();
		GlStateManager.translate(coinX + 8 + 4.5f, coinY + 9f, 0f);
		GlStateManager.scale(0.35f + 0.65f * spin, 1f, 1f);
		iconF("coin", -4.5f, -4.5f, 1f, Ui.alpha(Ui.GOLD, a));
		GlStateManager.popMatrix();
		textShadow(font, amount, coinX + 22, coinY + 5, Ui.alpha(0xFFFFE07A, a));
		textShadow(font, cur, coinX + 22 + aw + 5, coinY + 5, Ui.alpha(0xFFB9C4CD, a));
		if (hv > 0.02f) {
			String tip = c < 0 ? "Join PlebMc to load your coins" : "Open the shop";
			int tw = font.getStringWidth(tip);
			textShadow(font, tip, coinX + coinW - tw, coinY + coinH + 4, Ui.alpha(0xFFDCE4EA, Math.round(0xC0 * hv * in)));
		}
	}

	/** "PlebMc Coins" reads as "Pleb Coins" in the chip. */
	private static String shortCurrency(String cur) {
		if (cur == null || cur.isEmpty()) {
			return "Pleb Coins";
		}
		return cur.replace("PlebMc ", "Pleb ");
	}

	// ---- the mini mascot (bottom right) ----

	private static final ResourceLocation BUDDY = new ResourceLocation("plebitems", "textures/gui/buddy.png");
	/** Milliseconds per frame of the mini mascot's animation (frame 0 is him standing there). */
	private static final int[] BUDDY_MS = { 520, 210, 200, 180, 160, 140, 120, 70, 70, 120, 140, 150, 140, 120, 60, 70, 70, 80,
			90, 240 };
	private static long buddyStart; // 0 = standing; else when he was clicked
	private static long buddyBack; // when he pops back in
	private static float buddyHover;
	private static int budX, budY, budS;

	private static long helloAt;
	private static int lastMx = -1, lastMy = -1;

	private static void buddy(int w, int h, int mx, int my, long now, float in) {
		if (mx != lastMx || my != lastMy) {
			lastMx = mx;
			lastMy = my;
			EasterEggs.activity();
		}
		budS = Math.max(28, Math.min(40, h / 7));
		budX = w - 8 - budS;
		budY = h - 16 - budS;
		boolean over = pending == null && mx >= budX && mx < budX + budS && my >= budY && my < budY + budS;
		buddyHover = approach(buddyHover, over && buddyStart == 0 ? 1f : 0f, 12f);
		int frame = 0;
		float pop = 1f;
		if (buddyStart != 0) {
			long e = now - buddyStart;
			int acc = 0;
			frame = -1;
			for (int i = 1; i < BUDDY_MS.length; i++) {
				acc += BUDDY_MS[i];
				if (e < acc) {
					frame = i;
					break;
				}
			}
			if (frame < 0) {
				// blown up: gone for a moment, then back
				if (buddyBack == 0) {
					buddyBack = now + 900;
				}
				if (now < buddyBack) {
					return;
				}
				float k = Math.min(1f, (now - buddyBack) / 260f);
				pop = ease(k);
				frame = 0;
				if (k >= 1f) {
					buddyStart = 0;
					buddyBack = 0;
				}
			}
		}
		float bob = buddyStart == 0 && animations() ? (float) Math.abs(Math.sin(now / 380.0)) * -1.5f : 0f;
		float hop = -3f * ease(buddyHover);
		if (EasterEggs.takeHello()) {
			helloAt = now;
		}
		if (helloAt != 0) {
			float k = (now - helloAt) / 700f;
			if (k >= 1f) {
				helloAt = 0;
			} else {
				hop -= 14f * (float) Math.sin(Math.PI * k);
			}
		}
		float s = budS * pop;
		float x = budX + (budS - s) / 2f, y = budY + (budS - s) + bob + hop;
		try {
			Minecraft.getMinecraft().getTextureManager().bindTexture(BUDDY);
		} catch (Throwable t) {
			return;
		}
		float u0 = (frame % 5) / 5f, v0 = (frame / 5) / 4f, u1 = u0 + 0.2f, v1 = v0 + 0.25f;
		GlStateManager.enableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GlStateManager.color(1f, 1f, 1f, in);
		WorldRenderer r = Tessellator.getInstance().getWorldRenderer();
		r.begin(7, DefaultVertexFormats.POSITION_TEX);
		r.pos(x, y + s, 0).tex(u0, v1).endVertex();
		r.pos(x + s, y + s, 0).tex(u1, v1).endVertex();
		r.pos(x + s, y, 0).tex(u1, v0).endVertex();
		r.pos(x, y, 0).tex(u0, v0).endVertex();
		Tessellator.getInstance().draw();
		GlStateManager.color(1f, 1f, 1f, 1f);
		bubble(now, in);
	}

	/**
	 * Snow, embers or fireflies over the background. Each particle is a pure
	 * function of its index and the time, so there is nothing to keep or update.
	 */
	private static void particles(int w, int h, long now, int kind, float in) {
		int count = kind == 3 ? 26 : 60;
		double sec = now / 1000.0;
		for (int i = 0; i < count; i++) {
			double r1 = hash(i * 3 + 1), r2 = hash(i * 7 + 2), r3 = hash(i * 13 + 5);
			float size, x, y;
			int color;
			float alpha;
			if (kind == 1) { // snow: falls, sways
				double speed = 10 + r2 * 18, life = (h + 20) / speed;
				double k = ((sec + r3 * life) % life) / life;
				y = (float) (-10 + k * (h + 20));
				x = (float) (r1 * w + Math.sin(sec * (0.6 + r2) + i) * 8);
				size = r2 > 0.7 ? 2f : 1f;
				color = 0xFFFFFF;
				alpha = 0.45f + 0.4f * (float) r3;
			} else if (kind == 2) { // embers: rise and fade
				double speed = 14 + r2 * 22, life = (h * 0.8) / speed;
				double k = ((sec + r3 * life) % life) / life;
				y = (float) (h + 4 - k * h * 0.8);
				x = (float) (r1 * w + Math.sin(sec * 1.7 + i * 1.3) * 5);
				size = r2 > 0.6 ? 2f : 1f;
				color = k < 0.5 ? 0xFFD27A : 0xFF6A2A;
				alpha = (float) (1 - k) * 0.9f;
			} else { // fireflies: wander and blink
				x = (float) ((r1 + Math.sin(sec * 0.13 + i) * 0.06) * w);
				y = (float) ((0.35 + r2 * 0.6 + Math.cos(sec * 0.17 + i * 2) * 0.05) * h);
				size = 2f;
				color = 0xD8FF7A;
				alpha = (float) Math.max(0, Math.sin(sec * (0.8 + r3) + i * 5)) * 0.9f;
			}
			int a = (int) (alpha * in * 255);
			if (a > 4) {
				Ui.rect((int) x, (int) y, (int) size, (int) size, a << 24 | color);
				if (kind != 1 && size > 1) { // a soft glow around embers and fireflies
					Ui.rect((int) x - 1, (int) y - 1, (int) size + 2, (int) size + 2, (a / 5) << 24 | color);
				}
			}
		}
	}

	private static double hash(int n) {
		n = (n << 13) ^ n;
		return ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / (double) 0x7fffffff;
	}

	/** What Plebio (the mini mascot) says when you point at him (a new line each time). */
	private static final String[] BUDDY_LINES = {
			// tips
			"Hi, I'm Plebio!", "Click me. I dare you.", "Pleb Coins come from quests!", "Daily login = free coins",
			"New cloaks in the shop!", "Right Shift opens the mods", "I like TNT. A lot.",
			// Mighty
			"Mighty coded me at 3 AM", "Mighty owes me 5 Pleb Coins", "Mighty's aim? Never met it",
			"Don't tell Mighty I'm alive", "Mighty calls spam-clicking PvP", "Mighty pressed F5 to win",
			"Mighty fell in the void again", "Mighty's mic is always on",
			// Lucky
			"Lucky isn't even that lucky", "Lucky rage quit. Again.", "Lucky's luck ran out in 2019",
			"Lucky said \"one more game\"", "Lucky lost to a chicken", "Lucky blamed lag. It wasn't lag.",
			"Lucky still owes Mighty coins", "Lucky thinks I'm cute. Correct.", "Lucky's CPS: 3 on a good day",
			"Lucky tried to bridge. RIP.", "Lucky asked me for PvP tips",
			// more Mighty
			"Mighty fixes one bug, adds three", "Mighty's code runs on vibes", "Mighty AFK'd in a lava pool",
			"Mighty named me Plebio. No regrets.",
			// ArchMc
			"ArchMc? Never heard of it.", "ArchMc called. Nobody answered.", "ArchMc's TPS is a rumor",
			"ArchMc players come here to cry", "ArchMc is just PlebMc at home", "Even ArchMc's lobby is lagging",
			// Zentic, better known as Zenshit
			"Zentic? You mean Zenshit?", "Zenshit has 3 players. 2 are bots.", "Zenshit's anticheat is a sign",
			"Zenshit: zen for zero players", "I'd rather blow up than play Zenshit", "Zenshit pays me to stay quiet",
			// random
			"Hold still, I'm about to explode", "I am 90% TNT, 10% vibes", "Have you touched grass today?",
			"Your skin looks clean. Suspicious.", "Bedrock players can't see me", "Left click me. Go on." };
	private static int buddyLine = -1;
	private static long buddyLineAt;
	private static float buddyBubble;

	private static void bubble(long now, float in) {
		boolean sleepy = EasterEggs.plebioAsleep();
		boolean show = (buddyHover > 0.5f || sleepy) && buddyStart == 0;
		if (show && buddyLine < 0) {
			int next = (int) ((now / 7) % BUDDY_LINES.length);
			buddyLine = next == lastLine ? (next + 1) % BUDDY_LINES.length : next;
			lastLine = buddyLine;
			buddyLineAt = now;
		}
		if (!show && buddyHover < 0.05f) {
			buddyLine = -1;
		}
		buddyBubble = approach(buddyBubble, show ? 1f : 0f, 10f);
		if (buddyBubble < 0.02f || buddyLine < 0) {
			return;
		}
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		String line = sleepy ? "zZz... zZz..." : BUDDY_LINES[buddyLine];
		// type the line out letter by letter
		int n = animations() ? (int) Math.min(line.length(), (now - buddyLineAt) / 28) : line.length();
		float sc = 0.75f;
		int tw = (int) Math.ceil(font.getStringWidth(line) * sc);
		int bw = tw + 12, bh = 14;
		// pops up from the tail
		float pop = 0.6f + 0.4f * ease(buddyBubble);
		int bx = budX - bw + 10, by = budY - bh - 6;
		int a = Math.round(0xFF * buddyBubble * in);
		int edge = a << 24 | 0x15181C, fill = a << 24 | 0xFFFFFF, shade = (a * 0x30 / 0xFF) << 24;
		GlStateManager.pushMatrix();
		GlStateManager.translate(bx + bw - 8, by + bh + 4, 0);
		GlStateManager.scale(pop, pop, 1f);
		GlStateManager.translate(-(bx + bw - 8), -(by + bh + 4), 0);
		// soft drop shadow
		puffy(bx + 1, by + 2, bw, bh, shade);
		// outline, then the white body, both rounded (two-pixel corners)
		puffy(bx - 1, by - 1, bw + 2, bh + 2, edge);
		puffy(bx, by, bw, bh, fill);
		// the tail, pointing down-right at the mascot: a stepped triangle with an outline
		int tx = bx + bw - 16;
		for (int i = 0; i < 6; i++) {
			int w = 8 - i; // narrows and leans right as it goes down
			Ui.rect(tx + i, by + bh + i - 1, w + 2, 1, edge);
			if (w > 1) {
				Ui.rect(tx + i + 1, by + bh + i - 1, w - 1, 1, fill);
			}
		}
		Ui.rect(tx + 6, by + bh + 5, 3, 1, edge);
		text(font, line.substring(0, Math.max(0, n)), bx + 6, by + 4f, sc, a << 24 | 0x1C1F24);
		GlStateManager.popMatrix();
	}

	private static int lastLine = -1;

	/** A rounded rectangle with two-pixel corners, which reads as a soft cartoon bubble. */
	private static void puffy(int x, int y, int w, int h, int c) {
		Ui.rect(x + 2, y, w - 4, h, c);
		Ui.rect(x + 1, y + 1, 1, h - 2, c);
		Ui.rect(x + w - 2, y + 1, 1, h - 2, c);
		Ui.rect(x, y + 2, 1, h - 4, c);
		Ui.rect(x + w - 1, y + 2, 1, h - 4, c);
	}

	// ---- bottom line ----

	private static int credX, credW;

	private static void footer(int x0, int w, int h, int mx, int my, String splash, int acc, float in, ClientPrefs pr) {
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		int y = h - 12;
		int a = Math.round(0xB0 * in);
		// fades under the sidebar while it floats open
		float under = mode() == 0 ? 1f - 0.85f * ease(expand) : 1f;
		String left = "PlebClient  \u00b7  " + TitleActions.gameVersion();
		textShadow(font, left, x0 + 8, y, Ui.alpha(0xFF9AA8B4, Math.round(a * under)));

		String cred = TitleActions.hasCredits() ? "Credits" : "";
		credW = cred.isEmpty() ? 0 : font.getStringWidth(cred);
		credX = w - 8 - credW;
		boolean over = credW > 0 && mx >= credX - 3 && mx < credX + credW + 3 && my >= y - 3 && my < h && pending == null;
		creditsHover = approach(creditsHover, over ? 1f : 0f, 14f);
		int right = w - 8;
		if (credW > 0) {
			textShadow(font, cred, credX, y, Ui.alpha(Ui.mix(0xFF9AA8B4, acc, creditsHover), a));
			fill(credX, y + 9, credW * creditsHover, 1, Ui.alpha(acc, a));
			right = credX - 10;
		}
		if (pr.titleClock) {
			java.util.Calendar c = java.util.Calendar.getInstance();
			int hr = c.get(java.util.Calendar.HOUR);
			String clock = (hr == 0 ? 12 : hr) + ":" + (c.get(java.util.Calendar.MINUTE) < 10 ? "0" : "")
					+ c.get(java.util.Calendar.MINUTE) + (c.get(java.util.Calendar.AM_PM) == 0 ? " AM" : " PM");
			int cw = font.getStringWidth(clock);
			textShadow(font, clock, right - cw, y, Ui.alpha(0xFFE8EEF2, a));
			right -= cw + 10;
		}
		if (pr.titleSplash && splash != null && !splash.isEmpty()) {
			int lw = font.getStringWidth(left);
			int room = right - (x0 + 8 + lw + 16);
			if (room >= 50) {
				String sp = Ui.fit(splash, room);
				int spw = font.getStringWidth(sp);
				textShadow(font, sp, x0 + 8 + lw + 16 + (room - spw) / 2f, y, Ui.alpha(0xFFFFE066, a));
			}
		}
	}

	// ---- sidebar ----

	private static void sidebar(int w, int h, int mx, int my, float t, int acc, float intro, boolean mascot) {
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		float e = ease(expand);
		float sw = sideWidth(w);
		float slide = (1f - ease(Math.min(1f, intro * 1.6f))) * -(COLLAPSED + 6);
		GlStateManager.pushMatrix();
		GlStateManager.translate(slide, 0f, 0f);
		// no panel: a translucent shade for the labels, and the thin accent divider
		gradientH(0, 0, sw, h, Ui.alpha(0xFF02050A, Math.round(0x40 + 0x50 * e)), Ui.alpha(0xFF02050A, Math.round(0x10 + 0x30 * e)));
		int lineA = Math.round(0x70 + 0x60 * e);
		PlebTheme.gradient(Math.round(sw - 1), 0, 1, h / 2, Ui.alpha(acc, Math.round(lineA * 0.15f)), Ui.alpha(acc, lineA));
		PlebTheme.gradient(Math.round(sw - 1), h / 2, 1, h - h / 2, Ui.alpha(acc, lineA), Ui.alpha(acc, Math.round(lineA * 0.15f)));

		// the mascot (or a small P tile) and the name when open
		float lx = 6, ly = 7;
		float bob = animations() ? (float) Math.sin(t * 1.6f) * 0.6f : 0f;
		if (mascot) {
			Ui.mascot(lx, ly - 1 + bob, 18, 1f);
		} else {
			gradientRoundF(lx + 1, ly, 16, 16, acc, Ui.mix(acc, 0xFF0A2A33, 0.5f));
			text(font, "P", lx + 6f, ly + 4f, 1f, Ui.ON_ACCENT);
		}
		if (e > 0.05f) {
			int la = Math.round(255 * e);
			float ox = (1f - e) * -6f;
			textShadow(font, "Pleb", lx + 24 + ox, ly + 5, Ui.alpha(0xFFFFFFFF, la));
			textShadow(font, "Client", lx + 24 + ox + font.getStringWidth("Pleb"), ly + 5, Ui.alpha(acc, la));
		}

		// entries
		String[][] it = TitleActions.ITEMS;
		int[] ys = rows(h);
		int rh = rowHeight(h) - 2;
		int hovered = -1;
		if (pending == null && mx >= 0 && mx < sw) {
			for (int i = 0; i < it.length; i++) {
				if (ys[i] >= 0 && my >= ys[i] && my < ys[i] + rh) {
					hovered = i;
				}
			}
		}
		if (hovered < 0 && keySel >= 0) {
			hovered = keySel;
		}
		if (hover.length != it.length) {
			hover = new float[it.length];
		}
		// the indicator: a short accent bar that glides between entries
		if (hovered >= 0) {
			float ty = ys[hovered] + 3, th = rh - 6;
			if (indY < 0) {
				indY = ty;
				indH = 0;
			}
			indY = approach(indY, ty, 18f);
			indH = approach(indH, th, 16f);
			indA = approach(indA, 1f, 12f);
		} else {
			indA = approach(indA, 0f, 8f);
			indH = approach(indH, 0f, 8f);
		}
		for (int i = 0; i < it.length; i++) {
			if (ys[i] < 0) {
				// a short faint gap marker between the groups
				int dy = ys[i + 1 < it.length ? i + 1 : i] - 3;
				fill(10, dy, 10 + (sw - 30) * e, 1, Ui.alpha(0xFFFFFFFF, 0x18));
				continue;
			}
			hover[i] = approach(hover[i], i == hovered ? 1f : 0f, 13f);
			float hv = ease(hover[i]);
			int y = ys[i];
			boolean primary = "play".equals(it[i][0]);
			// hover: a translucent accent wash that fades out to the right, no box
			if (hv > 0.01f) {
				gradientH(0, y, sw - 1, rh, Ui.alpha(acc, Math.round(0x2C * hv)), Ui.alpha(acc, 0));
			}
			// icon: nudges right, grows a little and takes the accent
			float icx = 15 + 2f * hv;
			float icy = y + rh / 2f;
			int base = primary ? acc : 0xFFDCE4EA;
			int ic = Ui.mix(base, primary ? Ui.mix(acc, 0xFFFFFFFF, 0.45f) : acc, hv);
			GlStateManager.pushMatrix();
			GlStateManager.translate(icx, icy, 0f);
			float sc = (rh < 14 ? 0.82f : 1f) * (1f + 0.08f * hv);
			GlStateManager.scale(sc, sc, 1f);
			iconF(it[i][2], -5.5f + 0.6f, -5.5f + 0.6f, 1f, 0x90000000);
			iconF(it[i][2], -5.5f, -5.5f, 1f, ic);
			GlStateManager.popMatrix();
			// label: slides in with the sidebar, brightens on hover
			if (e > 0.04f) {
				float lxp = 31 + (1f - e) * -10f + 2f * hv;
				int room = Math.round(sw - lxp - 6);
				if (room > 8) {
					String label = Ui.fit(it[i][1], room);
					int lc = primary ? Ui.mix(acc, 0xFFFFFFFF, 0.3f * hv) : Ui.mix(0xFFD0D9E0, 0xFFFFFFFF, hv);
					textShadow(font, label, lxp, icy - 3.5f, Ui.alpha(lc, Math.round(255 * e)));
				}
			}
		}
		if (indA > 0.01f && indY >= 0) {
			fill(0, indY + (rh - 6 - indH) / 2f, 2, Math.max(0f, indH), Ui.alpha(acc, Math.round(255 * indA)));
		}
		GlStateManager.popMatrix();

		// icons only: the label as a small tag next to the icon
		if (mode() == 2) {
			if (hovered >= 0) {
				tipItem = hovered;
			}
			tipA = approach(tipA, hovered >= 0 ? 1f : 0f, 14f);
			if (tipA > 0.02f && tipItem >= 0 && tipItem < it.length && ys[tipItem] >= 0) {
				String label = it[tipItem][1];
				int tw = font.getStringWidth(label) + 10;
				float tx = sw + 5 + (1f - tipA) * -4f, ty = ys[tipItem] + (rh - 14) / 2f;
				roundF(tx, ty, tw, 14, Ui.alpha(0xFF05090E, Math.round(0xC8 * tipA)));
				fill(tx, ty + 3, 1, 8, Ui.alpha(acc, Math.round(255 * tipA)));
				text(font, label, tx + 5, ty + 3, 1f, Ui.alpha(0xFFFFFFFF, Math.round(255 * tipA)));
			}
		}
	}

	private static void ripples(long now, int acc) {
		for (int i = ripples.size() - 1; i >= 0; i--) {
			float[] r = ripples.get(i);
			float k = (now - rippleBase - r[2]) / 380f;
			if (k >= 1f) {
				ripples.remove(i);
				continue;
			}
			float e = ease(k);
			PlebTheme.glow(r[0], r[1], 6 + 28 * e, Ui.alpha(acc, Math.round(0x50 * (1f - k))));
		}
	}

	// ==================== input ====================

	/** A click on the title screen; true when it was handled (vanilla buttons are hidden). */
	public static boolean click(GuiScreen s, int mx, int my, int button) {
		EasterEggs.activity();
		if (button != 0 || pending != null) {
			return true;
		}
		int w = s.width, h = s.height;
		String[][] it = TitleActions.ITEMS;
		float sw = sideWidth(w);
		if (mx < sw) {
			int[] ys = rows(h);
			int rh = rowHeight(h) - 2;
			for (int i = 0; i < it.length; i++) {
				if (ys[i] >= 0 && my >= ys[i] && my < ys[i] + rh) {
					start(it[i][0], mx, my);
					return true;
				}
			}
			return true;
		}
		if (budS > 0 && ClientPrefs.get().titleBuddy && mx >= budX && mx < budX + budS && my >= budY && my < budY + budS) {
			if (buddyStart == 0) {
				buddyStart = System.currentTimeMillis();
				buddyBack = 0;
			}
			EasterEggs.plebioClicked();
			return true;
		}
		if (mx >= coinX && mx < coinX + coinW && my >= coinY && my < coinY + coinH) {
			start("shop", mx, my);
			return true;
		}
		if (storeW > 0 && mx >= storeX && mx < storeX + storeW && my >= storeY && my < storeY + storeH) {
			sound();
			ripple(mx, my);
			TitleActions.openLink(STORE_URL);
			return true;
		}
		if (credW > 0 && mx >= credX - 3 && mx < credX + credW + 3 && my >= h - 15) {
			sound();
			TitleActions.credits();
			return true;
		}
		return true;
	}

	/** Keys: up/down move through the sidebar, Enter opens, Escape clears, 1-9 open an entry. */
	public static boolean key(GuiScreen s, char c, int key) {
		EasterEggs.activity();
		EasterEggs.titleKey(c, key);
		String[][] it = TitleActions.ITEMS;
		if (key == 200 || key == 208) { // up / down
			int dir = key == 200 ? -1 : 1;
			int i = keySel < 0 ? (dir > 0 ? -1 : it.length) : keySel;
			do {
				i = (i + dir + it.length) % it.length;
			} while ("-".equals(it[i][0]));
			keySel = i;
			return true;
		}
		if ((key == 28 || key == 156) && keySel >= 0) {
			start(it[keySel][0], -1, -1);
			return true;
		}
		if (key == 1 && keySel >= 0) {
			keySel = -1;
			return true;
		}
		if (key >= 2 && key <= 10 && pending == null) { // 1..9
			int n = key - 2;
			for (int i = 0; i < it.length; i++) {
				if ("-".equals(it[i][0])) {
					continue;
				}
				if (n-- == 0) {
					keySel = i;
					start(it[i][0], -1, -1);
					return true;
				}
			}
		}
		return false;
	}

	private static void ripple(int mx, int my) {
		if (ripples.isEmpty()) {
			rippleBase = System.currentTimeMillis();
		}
		ripples.add(new float[] { mx, my, System.currentTimeMillis() - rippleBase });
	}

	private static void start(String id, int mx, int my) {
		sound();
		if (mx >= 0) {
			ripple(mx, my);
		}
		if ("discord".equals(id)) {
			// opens a browser tab: no fade
			TitleActions.run(screen, id);
			return;
		}
		pending = id;
		pendingAt = System.currentTimeMillis();
		if (!animations()) {
			pendingAt -= 1000; // run on the next frame
		}
	}

	private static void sound() {
		try {
			Minecraft.getMinecraft().getSoundHandler()
					.playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
		} catch (Throwable t) {
			// no sound system
		}
	}

	// ==================== helpers ====================

	private static boolean animations() {
		try {
			return ClientPrefs.get().animations;
		} catch (Throwable t) {
			return true;
		}
	}

	/** Moves v toward target, frame-rate independent ({@code speed} ~ 1/seconds). */
	private static float approach(float v, float target, float speed) {
		if (!animations()) {
			return target;
		}
		float k = 1f - (float) Math.exp(-speed * dt);
		float n = v + (target - v) * k;
		return Math.abs(target - n) < 0.002f ? target : n;
	}

	/** Smoothstep-like ease in/out. */
	private static float ease(float x) {
		x = Math.max(0f, Math.min(1f, x));
		return x * x * (3f - 2f * x);
	}

	private static void text(FontRenderer f, String s, float x, float y, float sc, int c) {
		if (s == null || s.isEmpty() || (c >>> 24) < 5) {
			return;
		}
		GlStateManager.enableBlend();
		if (sc == 1f) {
			f.drawString(s, x, y, c, false);
			return;
		}
		GlStateManager.pushMatrix();
		GlStateManager.translate(x, y, 0f);
		GlStateManager.scale(sc, sc, 1f);
		f.drawString(s, 0, 0, c, false);
		GlStateManager.popMatrix();
	}

	private static void textShadow(FontRenderer f, String s, float x, float y, int c) {
		if (s == null || s.isEmpty() || (c >>> 24) < 5) {
			return;
		}
		GlStateManager.enableBlend();
		f.drawString(s, x, y, c, true);
	}

	/** An icon at fractional coordinates. */
	private static void iconF(String name, float x, float y, float px, int color) {
		if (Ui.texturedIcon(name, x, y, px, color)) {
			return;
		}
		int[][] runs = Ui.runs(name);
		if (runs == null) {
			return;
		}
		begin();
		for (int[] r : runs) {
			quad(x + r[0] * px, y + r[1] * px, r[2] * px, px, color, color);
		}
		end();
	}

	private static WorldRenderer wr;

	private static void begin() {
		GlStateManager.disableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.disableAlpha();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GlStateManager.shadeModel(7425);
		GlStateManager.color(1f, 1f, 1f, 1f); // vertex colors only (Eaglercraft multiplies by the current color)
		wr = Tessellator.getInstance().getWorldRenderer();
		wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
	}

	private static void end() {
		Tessellator.getInstance().draw();
		GlStateManager.shadeModel(7424);
		GlStateManager.enableAlpha();
		GlStateManager.enableTexture2D();
	}

	/** One quad with a left and right color (same winding as Gui.drawRect). */
	private static void quad(float x, float y, float w, float h, int left, int right) {
		vtx(x + w, y, right);
		vtx(x, y, left);
		vtx(x, y + h, left);
		vtx(x + w, y + h, right);
	}

	private static void vtx(float x, float y, int c) {
		wr.pos(x, y, 0).color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF).endVertex();
	}

	private static void fill(float x, float y, float w, float h, int c) {
		if (w <= 0 || h <= 0 || (c >>> 24) == 0) {
			return;
		}
		begin();
		quad(x, y, w, h, c, c);
		end();
	}

	private static void gradientH(float x, float y, float w, float h, int left, int right) {
		if (w <= 0 || h <= 0) {
			return;
		}
		begin();
		quad(x, y, w, h, left, right);
		end();
	}

	/** A filled rectangle with its corner pixels cut. */
	private static void roundF(float x, float y, float w, float h, int c) {
		if (w < 3 || h < 3) {
			fill(x, y, w, h, c);
			return;
		}
		begin();
		quad(x + 1, y, w - 2, h, c, c);
		quad(x, y + 1, 1, h - 2, c, c);
		quad(x + w - 1, y + 1, 1, h - 2, c, c);
		end();
	}

	private static void outlineF(float x, float y, float w, float h, int c) {
		if ((c >>> 24) == 0) {
			return;
		}
		begin();
		quad(x + 1, y, w - 2, 1, c, c);
		quad(x + 1, y + h - 1, w - 2, 1, c, c);
		quad(x, y + 1, 1, h - 2, c, c);
		quad(x + w - 1, y + 1, 1, h - 2, c, c);
		end();
	}

	private static void gradientRoundF(float x, float y, float w, float h, int top, int bottom) {
		begin();
		vtx(x + w - 1, y, top);
		vtx(x + 1, y, top);
		vtx(x + 1, y + h, bottom);
		vtx(x + w - 1, y + h, bottom);
		int t1 = Ui.mix(top, bottom, 1f / h), b1 = Ui.mix(top, bottom, (h - 1f) / h);
		vtx(x + 1, y + 1, t1);
		vtx(x, y + 1, t1);
		vtx(x, y + h - 1, b1);
		vtx(x + 1, y + h - 1, b1);
		vtx(x + w, y + 1, t1);
		vtx(x + w - 1, y + 1, t1);
		vtx(x + w - 1, y + h - 1, b1);
		vtx(x + w, y + h - 1, b1);
		end();
	}
}
