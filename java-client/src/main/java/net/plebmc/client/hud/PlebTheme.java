package net.plebmc.client.hud;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.plebmc.client.mods.ClientInfo;
import net.plebmc.client.mods.ClientPrefs;

/**
 * The PlebClient look for Minecraft's own menus (hooked from GuiButton,
 * GuiScreen, GuiSlot, the sliders and GuiMainMenu): glassy dark buttons
 * with an accent underline that grows on hover, an animated background
 * (night gradient, soft accent glows, a moving grid and drifting sparks)
 * instead of dirt, dark header and footer bars on lists, and the PlebClient
 * wordmark on the title screen. Settings > Interface > "PlebClient menus"
 * turns it all off.
 */
public final class PlebTheme {

	private PlebTheme() {
	}

	public static boolean enabled() {
		try {
			return ClientPrefs.get().modernMenus;
		} catch (Throwable t) {
			return false;
		}
	}

	private static final net.minecraft.util.ResourceLocation WIDGETS = new net.minecraft.util.ResourceLocation(
			"textures/gui/widgets.png");
	private static long packChecked;
	private static boolean packButtons;

	/**
	 * Whether buttons and sliders get the Pleb look: the theme is on, and no texture pack
	 * in use brings its own button art (textures/gui/widgets.png), which wins. The title
	 * screen is PlebClient's own design, so its buttons always keep the Pleb look.
	 */
	public static boolean buttons() {
		if (!enabled()) {
			return false;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.currentScreen instanceof net.minecraft.client.gui.GuiMainMenu) {
			return true;
		}
		long now = System.currentTimeMillis();
		if (now - packChecked > 1000 || now < packChecked) {
			packChecked = now;
			packButtons = packHasButtons(mc);
		}
		return !packButtons;
	}

	private static boolean packHasButtons(Minecraft mc) {
		try {
			net.minecraft.client.resources.ResourcePackRepository repo = mc.getResourcePackRepository();
			if (repo == null) {
				return false;
			}
			net.minecraft.client.resources.IResourcePack server = repo.getResourcePackInstance();
			if (server != null && server.resourceExists(WIDGETS)) {
				return true;
			}
			for (Object o : repo.getRepositoryEntries()) {
				net.minecraft.client.resources.IResourcePack pack = ((net.minecraft.client.resources.ResourcePackRepository.Entry) o)
						.getResourcePack();
				if (pack != null && pack.resourceExists(WIDGETS)) {
					return true;
				}
			}
		} catch (Throwable t) {
			// no packs to ask
		}
		return false;
	}

	// ==================== containers (inventory, chests, furnaces...) ====================

	private static final String CONTAINERS = "textures/gui/container/";
	private static final Map<String, ResourceLocation> skins = new HashMap<>();
	private static final Map<String, Boolean> packSkins = new HashMap<>();
	private static long packSkinsChecked;
	private static long skinnedAt;

	/**
	 * TextureManager.bindTexture: a vanilla container background (the inventory, chests,
	 * furnaces, the creative tabs...) is swapped for a copy in the Pleb colors, made from
	 * the vanilla picture. A texture pack that has its own picture for it wins.
	 */
	public static ResourceLocation containerSkin(ResourceLocation r) {
		String p = r.getResourcePath();
		if (p.length() < 24 || p.charAt(13) != 'c' || !p.startsWith(CONTAINERS) || p.endsWith("stats_icons.png")
				|| !"minecraft".equals(r.getResourceDomain()) || !enabled()) {
			return r;
		}
		try {
			long now = System.currentTimeMillis();
			if (now - packSkinsChecked > 1500 || now < packSkinsChecked) {
				packSkinsChecked = now;
				packSkins.clear();
			}
			Boolean pack = packSkins.get(p);
			if (pack == null) {
				pack = packHas(Minecraft.getMinecraft(), r);
				packSkins.put(p, pack);
			}
			if (pack) {
				return r;
			}
			ResourceLocation skin = skins.get(p);
			if (skin == null) {
				skin = makeSkin(r, p);
				skins.put(p, skin);
			}
			if (skin != r) {
				skinnedAt = now;
			}
			return skin;
		} catch (Throwable t) {
			skins.put(p, r);
			return r;
		}
	}

	/** Font renderer: the dark gray of container titles turns light while a Pleb container is drawn. */
	public static int containerText(int color) {
		if (color != 4210752 || skinnedAt == 0) {
			return color;
		}
		if (System.currentTimeMillis() - skinnedAt > 250) {
			return color;
		}
		Minecraft mc = Minecraft.getMinecraft();
		return mc != null && mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainer ? 0xFFC9D4DD : color;
	}

	/** Gray levels of the vanilla container pictures and the Pleb color each one becomes. */
	private static final int[][] RAMP = { { 0x00, 0x05080C }, { 0x37, 0x080C12 }, { 0x55, 0x0B1118 }, { 0x8B, 0x0E151E },
			{ 0xC6, 0x18222E }, { 0xFF, 0x3A4C60 } };

	/** Recolors ARGB pixels in place: the grays become the Pleb blue-grays, colors stay. */
	static void recolor(int[] px) {
		for (int i = 0; i < px.length; i++) {
			int c = px[i];
			int a = c >>> 24, r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
			if (a == 0 || Math.abs(r - g) > 6 || Math.abs(g - b) > 6 || Math.abs(r - b) > 6) {
				continue;
			}
			int l = (r + g + b) / 3;
			int k = 1;
			while (k < RAMP.length - 1 && l > RAMP[k][0]) {
				k++;
			}
			float t = (l - RAMP[k - 1][0]) / (float) (RAMP[k][0] - RAMP[k - 1][0]);
			px[i] = a << 24 | (Ui.mix(0xFF000000 | RAMP[k - 1][1], 0xFF000000 | RAMP[k][1], Math.max(0f, Math.min(1f, t))) & 0xFFFFFF);
		}
	}

	private static boolean packHas(Minecraft mc, ResourceLocation r) {
		try {
			net.minecraft.client.resources.ResourcePackRepository repo = mc.getResourcePackRepository();
			if (repo == null) {
				return false;
			}
			net.minecraft.client.resources.IResourcePack server = repo.getResourcePackInstance();
			if (server != null && server.resourceExists(r)) {
				return true;
			}
			for (Object o : repo.getRepositoryEntries()) {
				net.minecraft.client.resources.IResourcePack pack = ((net.minecraft.client.resources.ResourcePackRepository.Entry) o)
						.getResourcePack();
				if (pack != null && pack.resourceExists(r)) {
					return true;
				}
			}
		} catch (Throwable t) {
			// no packs to ask
		}
		return false;
	}

	private static ResourceLocation makeSkin(ResourceLocation r, String p) throws java.io.IOException {
		Minecraft mc = Minecraft.getMinecraft();
		java.awt.image.BufferedImage src = net.minecraft.client.renderer.texture.TextureUtil
				.readBufferedImage(mc.getResourceManager().getResource(r).getInputStream());
		int w = src.getWidth(), h = src.getHeight();
		int[] px = src.getRGB(0, 0, w, h, null, 0, w);
		recolor(px);
		java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		img.setRGB(0, 0, w, h, px, 0, w);
		ResourceLocation skin = new ResourceLocation("plebskin", p);
		mc.getTextureManager().loadTexture(skin, new net.minecraft.client.renderer.texture.DynamicTexture(img));
		return skin;
	}

	// ==================== colors ====================

	static final int SKY_TOP = 0xFF0C1522;
	static final int SKY_BOTTOM = 0xFF04070B;
	static final int BAR = 0xF0070B10;

	private static float time() {
		return (System.currentTimeMillis() % 3_600_000L) / 1000f;
	}

	// ==================== buttons ====================

	private static final Map<GuiButton, float[]> hover = new HashMap<>();
	private static Object hoverScreen;
	private static long lastFrame;
	private static float frameDt;

	/** Eased 0..1 hover value per button. */
	private static float hoverOf(GuiButton b, boolean on) {
		long now = System.currentTimeMillis();
		if (now != lastFrame) {
			frameDt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
			lastFrame = now;
		}
		Object screen = Minecraft.getMinecraft().currentScreen;
		if (screen != hoverScreen || hover.size() > 256) {
			hover.clear(); // forget the last screen's buttons
			hoverScreen = screen;
		}
		float[] h = hover.get(b);
		if (h == null) {
			h = new float[] { on ? 1f : 0f };
			hover.put(b, h);
		}
		float target = on ? 1f : 0f;
		h[0] = ClientPrefs.get().animations ? h[0] + (target - h[0]) * Math.min(1f, frameDt * 14f) : target;
		return h[0];
	}

	/** Buttons drawn filled with the accent (the title screen's Join button). */
	static boolean primary(GuiButton b) {
		return b.id == PlebMenu.ID_JOIN;
	}

	/**
	 * Draws a button's body (the caller then lets sliders draw their knob and
	 * calls {@link #drawButtonLabel}). {@code slider}: a slider track.
	 */
	public static void drawButton(GuiButton b, int mouseX, int mouseY, boolean hovered, boolean slider) {
		int x = b.xPosition, y = b.yPosition, w = b.width, h = b.height;
		int acc = Ui.accent();
		float hv = hoverOf(b, hovered && b.enabled);
		GlStateManager.disableTexture2D();
		GlStateManager.enableBlend();
		if (primary(b) && b.enabled) {
			int top = Ui.mix(acc, 0xFFFFFFFF, 0.22f + hv * 0.2f);
			int bottom = Ui.mix(acc, 0xFF000000, 0.08f - hv * 0.08f);
			Ui.round(x - 1, y - 1, w + 2, h + 2, Ui.alpha(acc, Math.round(0x30 + 0x50 * hv)));
			gradientRound(x, y, w, h, top, bottom);
			Ui.rect(x + 1, y, w - 2, 1, 0x60FFFFFF);
		} else {
			int fillTop = b.enabled || slider ? Ui.mix(0xD0141C26, 0xE81C2836, hv) : 0xA00C1117;
			int fillBottom = b.enabled || slider ? Ui.mix(0xD00B1118, 0xE8131C27, hv) : 0xA0090D12;
			int border = b.enabled || slider ? Ui.mix(0x80344251, Ui.alpha(acc, 0xF0), hv) : 0x60222A33;
			gradientRound(x, y, w, h, fillTop, fillBottom);
			Ui.outline(x, y, w, h, border);
			Ui.rect(x + 1, y + 1, w - 2, 1, 0x14FFFFFF);
			if (!slider) {
				int uw = Math.round((w - 4) * hv);
				if (uw > 0) {
					Ui.rect(x + w / 2 - uw / 2, y + h - 2, uw, 1, acc);
				}
			}
		}
		GlStateManager.enableTexture2D();
		GlStateManager.color(1f, 1f, 1f, 1f);
	}

	public static void drawButtonLabel(GuiButton b, FontRenderer font, boolean hovered) {
		float hv = hoverOf(b, hovered && b.enabled);
		int color;
		if (!b.enabled) {
			color = 0xFF6A7682;
		} else if (primary(b)) {
			color = 0xFF04131A;
		} else {
			color = Ui.mix(0xFFDCE5EC, 0xFFFFFFFF, hv);
		}
		String s = b.displayString;
		if (primary(b) && b.enabled) {
			s = net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(s);
		}
		int maxW = b.width - 6;
		if (font.getStringWidth(s) > maxW) {
			s = font.trimStringToWidth(s, maxW - font.getStringWidth("...")) + "...";
		}
		int tx = b.xPosition + b.width / 2 - font.getStringWidth(s) / 2;
		int ty = b.yPosition + (b.height - 8) / 2;
		if (primary(b) && b.enabled) {
			font.drawString(s, tx, ty, color);
		} else {
			font.drawStringWithShadow(s, tx, ty, color);
		}
	}

	/** A slider's knob: a small accent pill. {@code kx} is where vanilla would draw it (8 wide). */
	public static void sliderKnob(GuiButton b, int kx, boolean hovered) {
		int acc = Ui.accent();
		int y = b.yPosition, h = b.height;
		GlStateManager.disableTexture2D();
		GlStateManager.enableBlend();
		// the filled part of the track
		if (kx > b.xPosition + 2) {
			Ui.rect(b.xPosition + 2, y + h - 3, kx - b.xPosition - 2, 1, Ui.alpha(acc, 0xC0));
		}
		Ui.round(kx, y + 2, 8, h - 4, hovered ? Ui.mix(acc, 0xFFFFFFFF, 0.3f) : acc);
		Ui.rect(kx + 3, y + h / 2 - 3, 2, 6, 0x50000000);
		GlStateManager.enableTexture2D();
		GlStateManager.color(1f, 1f, 1f, 1f);
	}

	// ==================== backgrounds ====================

	/** Menu backgrounds. Returns true when drawn (the vanilla dirt / grey is skipped). */
	public static boolean drawWorldBackground(GuiScreen s) {
		if (!enabled()) {
			return false;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld != null) {
			// in game: a deep blue wash with a soft accent glow on top, so the world still shows
			gradient(0, 0, s.width, s.height, 0xA0060B12, 0xD8040609);
			glow(s.width / 2f, -s.height * 0.15f, s.width * 0.7f, Ui.alpha(Ui.accent(), 0x30));
			return true;
		}
		background(s.width, s.height, true);
		return true;
	}

	/** The animated menu background (also behind lists and loading screens). */
	public static void background(int w, int h, boolean grid) {
		float t = time();
		int acc0 = Ui.accent();
		switch (ClientPrefs.get().menuBackground) {
		case 0: // the wallpaper, blurred and dimmed
			if (TitleMenu.menuWallpaper(w, h, true, 0x88)) {
				glow(w / 2f, -h * 0.2f, w * 0.6f, Ui.alpha(acc0, 0x22));
				return;
			}
			break;
		case 1: // the wallpaper, sharp and lightly dimmed
			if (TitleMenu.menuWallpaper(w, h, false, 0x8C)) {
				return;
			}
			break;
		case 2: // plain and dark
			gradient(0, 0, w, h, 0xFF0B0F15, 0xFF06080C);
			return;
		case 4: // the old animated grid
			break;
		default: // soft glows, no grid
			grid = false;
			break;
		}
		if (ClientPrefs.get().menuBackground != 4) {
			grid = false;
		}
		gradient(0, 0, w, h, SKY_TOP, SKY_BOTTOM);
		int acc = Ui.accent();
		float gx = w * (0.22f + 0.06f * (float) Math.sin(t * 0.21));
		float gy = h * (0.28f + 0.05f * (float) Math.cos(t * 0.17));
		glow(gx, gy, Math.max(w, h) * 0.55f, Ui.alpha(acc, 0x42));
		int second = Ui.mix(acc, 0xFF8B5CFF, 0.65f);
		glow(w * (0.82f + 0.05f * (float) Math.cos(t * 0.13)), h * (0.78f + 0.04f * (float) Math.sin(t * 0.19)),
				Math.max(w, h) * 0.5f, Ui.alpha(second, 0x34));
		if (grid) {
			grid(w, h, t, acc);
		}
		sparks(w, h, t, acc);
	}

	/** A retro perspective grid on the lower part of the screen, moving toward the viewer. */
	private static void grid(int w, int h, float t, int acc) {
		float horizon = h * 0.62f;
		GlStateManager.disableTexture2D();
		GlStateManager.enableBlend();
		// horizontal lines, closer together toward the horizon
		float phase = (t * 0.35f) % 1f;
		for (int i = 0; i < 14; i++) {
			float z = (i + 1 - phase) / 14f; // 0 = horizon, 1 = bottom
			if (z <= 0) {
				continue;
			}
			float y = horizon + (h - horizon) * z * z;
			int a = Math.round(0x38 * z);
			Ui.rect(0, Math.round(y), w, 1, Ui.alpha(acc, a));
		}
		// vertical lines converging on the middle of the horizon
		Tessellator tes = Tessellator.getInstance();
		WorldRenderer wr = tes.getWorldRenderer();
		GlStateManager.shadeModel(7425);
		GlStateManager.color(1f, 1f, 1f, 1f); // vertex colors only (Eaglercraft multiplies by the current color)
		wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
		float cx = w / 2f;
		int lines = 22;
		float r = (acc >> 16 & 0xFF) / 255f, g = (acc >> 8 & 0xFF) / 255f, b = (acc & 0xFF) / 255f;
		for (int i = -lines / 2; i <= lines / 2; i++) {
			float bx = cx + i * (w / (float) lines) * 2.2f;
			float tx = cx + i * (w / (float) lines) * 0.18f;
			// same winding as Gui.drawRect (the GUI may cull back faces)
			wr.pos(bx, h, 0).color(r, g, b, 0.4f).endVertex();
			wr.pos(bx + 1.5f, h, 0).color(r, g, b, 0.4f).endVertex();
			wr.pos(tx + 0.8f, horizon, 0).color(r, g, b, 0f).endVertex();
			wr.pos(tx, horizon, 0).color(r, g, b, 0f).endVertex();
		}
		tes.draw();
		GlStateManager.shadeModel(7424);
		// fade the horizon line into a glow
		gradient(0, Math.round(horizon) - 18, w, 18, 0x00000000, Ui.alpha(acc, 0x18));
		GlStateManager.enableTexture2D();
	}

	/** Little squares drifting up and twinkling. */
	private static void sparks(int w, int h, float t, int acc) {
		GlStateManager.disableTexture2D();
		GlStateManager.enableBlend();
		for (int i = 0; i < 46; i++) {
			float rx = hash(i * 3 + 1), ry = hash(i * 7 + 2), sp = 0.012f + 0.03f * hash(i * 11 + 3);
			float x = rx * w + (float) Math.sin(t * 0.6f + i) * 6f;
			float y = ((ry - t * sp) % 1f + 1f) % 1f * (h + 20) - 10;
			float tw = 0.5f + 0.5f * (float) Math.sin(t * (1.5f + hash(i) * 2f) + i * 1.7f);
			int size = hash(i * 5 + 4) > 0.8f ? 2 : 1;
			int c = hash(i * 13 + 5) > 0.5f ? acc : 0xFFFFFFFF;
			Ui.rect(Math.round(x), Math.round(y), size, size, Ui.alpha(c, Math.round(30 + 120 * tw)));
		}
		GlStateManager.enableTexture2D();
	}

	private static float hash(int n) {
		n = (n << 13) ^ n;
		int m = (n * (n * n * 15731 + 789221) + 1376312589) & 0x7FFFFFFF;
		return m / (float) 0x7FFFFFFF;
	}

	// ==================== lists (GuiSlot) ====================

	/** Behind a list's rows. Returns true when drawn. */
	public static boolean slotBackground(int left, int top, int right, int bottom) {
		if (!enabled()) {
			return false;
		}
		GlStateManager.disableTexture2D();
		GlStateManager.enableBlend();
		Ui.rect(left, top, right - left, bottom - top, 0x5A000000);
		GlStateManager.enableTexture2D();
		return true;
	}

	/** The bars above and below a list (they hide rows scrolled out of view). Returns true when drawn. */
	public static boolean slotOverlay(int left, int width, int startY, int endY, int screenHeight) {
		if (!enabled()) {
			return false;
		}
		GlStateManager.disableTexture2D();
		GlStateManager.enableBlend();
		boolean topBar = startY <= 0;
		int c0 = topBar ? SKY_TOP : 0xFF070B11;
		int c1 = topBar ? 0xFF0A121C : SKY_BOTTOM;
		gradient(left, startY, width, endY - startY, c0, c1);
		int acc = Ui.accent();
		if (topBar) {
			Ui.rect(left, endY - 1, width, 1, Ui.alpha(acc, 0x70));
		} else {
			Ui.rect(left, startY, width, 1, Ui.alpha(acc, 0x70));
		}
		GlStateManager.enableTexture2D();
		return true;
	}

	// ==================== title screen ====================

	/**
	 * Over the title screen's panorama: a dark tint, the grid and sparks, and
	 * the PlebClient wordmark in place of the Minecraft logo. Returns true
	 * when drawn (the vanilla logo is skipped).
	 */
	/** The wordmark's scale: as big as fits between the top and the title screen's buttons. */
	static float titleScale(int w, int h) {
		float room = h / 4 + 48 - 24 - 6 - 8 - 18; // Join button top, minus margins and the tagline
		return Math.max(2f, Math.min(w < 360 ? 3f : 4f, room / 9f));
	}

	static float titleTop(int h, float sc) {
		float bottom = h / 4 + 48 - 24 - 6;
		return Math.max(6f, Math.min(30f, bottom - (9 * sc + 18)));
	}

	/** Moves the splash text to the end of the wordmark (call instead of vanilla's translate). */
	public static void splashTranslate(GuiScreen s) {
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		float sc = titleScale(s.width, s.height);
		int tw = font.getStringWidth("PLEBCLIENT");
		GlStateManager.translate(s.width / 2f + tw * sc / 2f + 12f, titleTop(s.height, sc) + 2f * sc, 0f);
		GlStateManager.scale(0.85f, 0.85f, 1f);
	}

	public static boolean mainMenuTitle(GuiScreen s, int logoY) {
		if (!enabled()) {
			return false;
		}
		int w = s.width, h = s.height;
		float t = time();
		int acc = Ui.accent();
		GlStateManager.disableTexture2D();
		GlStateManager.enableBlend();
		gradient(0, 0, w, h, 0x9A060B12, 0xE0040609);
		glow(w * 0.5f, titleTop(h, titleScale(w, h)) + 20, w * 0.45f, Ui.alpha(acc, 0x38));
		GlStateManager.enableTexture2D();
		grid(w, h, t, acc);
		sparks(w, h, t, acc);

		// the wordmark: PLEB in white, CLIENT in the accent, sized to fit above the buttons
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		String a = "PLEB", b = ClientInfo.NAME.toUpperCase().startsWith("PLEB") ? ClientInfo.NAME.substring(4).toUpperCase()
				: "CLIENT";
		int tw = font.getStringWidth(a + b);
		float sc = titleScale(w, h);
		float x0 = w / 2f - tw * sc / 2f;
		float y0 = titleTop(h, sc);
		int aw = Math.round(font.getStringWidth(a) * sc);
		int shadow = Ui.mix(acc, 0xFF000000, 0.8f);
		drawScaled(font, a, x0 + sc * 0.75f, y0 + sc * 0.75f, sc, Ui.alpha(0xFF000000, 0xB0));
		drawScaled(font, b, x0 + aw + sc * 0.75f, y0 + sc * 0.75f, sc, Ui.alpha(shadow, 0xE0));
		drawScaled(font, a, x0, y0, sc, 0xFFFFFFFF);
		drawScaled(font, b, x0 + aw, y0, sc, acc);
		// a light sweep across the letters every few seconds
		float sweep = (t * 0.35f) % 1.6f;
		if (sweep < 1f) {
			GlStateManager.disableTexture2D();
			GlStateManager.enableBlend();
			int sx = Math.round(x0 - 20 + (tw * sc + 40) * sweep);
			glow(sx, y0 + 4.5f * sc, 5f * sc, 0x30FFFFFF);
			GlStateManager.enableTexture2D();
		}
		// underline and tagline
		int lineW = Math.round(tw * sc);
		int ly = Math.round(y0 + 8 * sc + 4);
		GlStateManager.disableTexture2D();
		GlStateManager.enableBlend();
		hline(Math.round(x0), ly, lineW, acc);
		GlStateManager.enableTexture2D();
		String tag = "MINECRAFT 1.8.8  ·  PLAY.PLEBMC.TOP";
		font.drawString(tag, w / 2 - font.getStringWidth(tag) / 2, ly + 5, 0xFF9AA8B4);
		return true;
	}

	/** A line that fades out at both ends. */
	private static void hline(int x, int y, int w, int c) {
		int seg = Math.max(1, w / 24);
		for (int i = 0; i < 24; i++) {
			float k = 1f - Math.abs(i - 11.5f) / 12f;
			Ui.rect(x + i * seg, y, seg, 1, Ui.alpha(c, Math.round(0xE0 * k)));
		}
	}

	private static void drawScaled(FontRenderer f, String s, float x, float y, float sc, int c) {
		if ((c >>> 24) < 5) {
			return;
		}
		GlStateManager.pushMatrix();
		GlStateManager.translate(x, y, 0f);
		GlStateManager.scale(sc, sc, 1f);
		f.drawString(s, 0, 0, c);
		GlStateManager.popMatrix();
	}

	// ==================== shapes ====================

	/** A smooth vertical gradient (vertex colors). */
	static void gradient(int x, int y, int w, int h, int top, int bottom) {
		if (w <= 0 || h <= 0) {
			return;
		}
		GlStateManager.disableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.disableAlpha();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GlStateManager.shadeModel(7425);
		GlStateManager.color(1f, 1f, 1f, 1f); // vertex colors only (Eaglercraft multiplies by the current color)
		Tessellator t = Tessellator.getInstance();
		WorldRenderer wr = t.getWorldRenderer();
		wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
		color(wr.pos(x + w, y, 0), top);
		color(wr.pos(x, y, 0), top);
		color(wr.pos(x, y + h, 0), bottom);
		color(wr.pos(x + w, y + h, 0), bottom);
		t.draw();
		GlStateManager.shadeModel(7424);
		GlStateManager.enableAlpha();
		GlStateManager.enableTexture2D();
	}

	/** A gradient with its corner pixels cut (reads as rounded). */
	static void gradientRound(int x, int y, int w, int h, int top, int bottom) {
		if (w < 3 || h < 3) {
			gradient(x, y, w, h, top, bottom);
			return;
		}
		gradient(x + 1, y, w - 2, h, top, bottom);
		gradient(x, y + 1, 1, h - 2, Ui.mix(top, bottom, 1f / h), Ui.mix(top, bottom, (h - 1f) / h));
		gradient(x + w - 1, y + 1, 1, h - 2, Ui.mix(top, bottom, 1f / h), Ui.mix(top, bottom, (h - 1f) / h));
	}

	/** A soft round glow: the color in the middle fading to nothing at the radius. */
	static void glow(float cx, float cy, float radius, int c) {
		GlStateManager.disableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.disableAlpha();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GlStateManager.shadeModel(7425);
		GlStateManager.color(1f, 1f, 1f, 1f); // vertex colors only (Eaglercraft multiplies by the current color)
		Tessellator t = Tessellator.getInstance();
		WorldRenderer wr = t.getWorldRenderer();
		wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
		int seg = 32;
		int edge = c & 0x00FFFFFF;
		for (int i = 0; i < seg; i++) {
			double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
			color(wr.pos(cx, cy, 0), c);
			color(wr.pos(cx + Math.cos(a1) * radius, cy + Math.sin(a1) * radius, 0), edge);
			color(wr.pos(cx + Math.cos(a0) * radius, cy + Math.sin(a0) * radius, 0), edge);
			color(wr.pos(cx, cy, 0), c);
		}
		t.draw();
		GlStateManager.shadeModel(7424);
		GlStateManager.enableAlpha();
		GlStateManager.enableTexture2D();
	}

	private static void color(WorldRenderer wr, int argb) {
		wr.color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
	}
}
