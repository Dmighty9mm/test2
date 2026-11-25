package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldRenderer;
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
 * <ul>
 * <li>An icon sidebar on the left that grows into a labelled panel while
 * the mouse is over it. A single accent indicator glides to the hovered
 * entry, each entry fades in a highlight, nudges and recolors its icon and
 * slides in its label.</li>
 * <li>The PlebClient wordmark in the middle with a slow float, a light
 * sweep and a glow; chips under it with the game version and the PlebMc
 * server's live status (online, players, ping); a server card with a big
 * Play button.</li>
 * <li>A "What's new" column on wide screens, an account chip, and a slim
 * info bar at the bottom (versions, splash text, credits).</li>
 * </ul>
 *
 * Every animation is eased by elapsed time (not per frame), so it runs the
 * same at 30 or 240 FPS. Nothing is drawn with blur or shaders: plain
 * colored quads, the font, and a couple of soft glows.
 */
public final class TitleMenu {

	private TitleMenu() {
	}

	/** Short news for the "What's new" column: title, text. */
	static final String[][] NEWS = {
			{ "New title screen", "Point at the sidebar to open it." },
			{ "Partner cosmetics", "A new Partners tab in the shop." },
			{ "8 new hats", "Shown in 3D in the shop. Trails are gone." } };

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
	private static float playHover, chipHover, creditsHover;

	private static String pending; // action waiting for the fade-out
	private static long pendingAt;
	private static long fadeInAt;

	private static final List<float[]> ripples = new ArrayList<>(); // x, y, start(ms, as float offset)
	private static long rippleBase;

	/** Called from GuiMainMenu.initGui (also on resize and when coming back from another screen). */
	public static void init(GuiScreen s) {
		long now = System.currentTimeMillis();
		if (s != screen) {
			screen = s;
			openedAt = now;
			expand = 0f;
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
		TitleActions.stopStatus();
	}

	// ==================== layout ====================

	private static final int COLLAPSED = 30;
	private static final int ROW = 22;

	private static int expanded(int w) {
		return Math.max(96, Math.min(128, w / 3));
	}

	private static float sideWidth(int w) {
		return COLLAPSED + (expanded(w) - COLLAPSED) * ease(expand);
	}

	/** Y of each sidebar entry (top group under the logo mark, bottom group above the info bar). */
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
		int top = 40;
		int room = h - 18 - top;
		int topCount = split;
		int bottomCount = it.length - split - 1;
		int row = Math.max(16, Math.min(ROW, room / Math.max(1, topCount + bottomCount + 1)));
		for (int i = 0; i < split; i++) {
			y[i] = top + i * row;
		}
		if (split < it.length) {
			y[split] = -1;
		}
		int by = h - 18 - 6 - bottomCount * row;
		for (int i = split + 1; i < it.length; i++) {
			y[i] = Math.max(top + split * row + 6, by + (i - split - 1) * row);
		}
		return y;
	}

	private static int rowHeight(int h) {
		int[] y = rows(h);
		return y.length > 1 && y[1] > y[0] ? y[1] - y[0] : ROW;
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
		TitleActions.pollStatus();
		// text writes depth here: without this the panels drawn over it would have holes
		GlStateManager.disableDepth();

		backdrop(w, h, t, acc);

		// ---- sidebar hover / expand ----
		float sw = sideWidth(w);
		boolean inSide = mx >= 0 && mx < sw + 2 && my >= 0 && my < h - 16 && pending == null;
		if (inSide) {
			leftAt = 0;
		} else if (leftAt == 0) {
			leftAt = now;
		}
		boolean open = inSide || (leftAt != 0 && now - leftAt < 140) || keySel >= 0;
		expand = approach(expand, open ? 1f : 0f, open ? 11f : 9f);
		sw = sideWidth(w);

		// ---- content ----
		float slideIn = ease(intro);
		int content = COLLAPSED;
		boolean news = w - content >= 430 && h >= 200;
		int newsW = news ? Math.min(140, (w - content) / 4) : 0;
		int cx0 = content + 12;
		int cx1 = w - 12 - (news ? newsW + 12 : 0);
		centerpiece(s, cx0, cx1, h, mx, my, t, acc, slideIn);
		if (news) {
			newsColumn(w - 12 - newsW, newsW, h, t, acc, intro, now);
		}
		accountChip(w, mx, my, acc, slideIn);
		infoBar(w, h, mx, my, splash, acc, slideIn);

		// darken the content a little while the sidebar is open, so it reads as a layer
		if (expand > 0.01f) {
			fill(sw, 0, w - sw, h, Ui.alpha(0xFF02050A, Math.round(0x50 * ease(expand))));
		}
		sidebar(w, h, mx, my, t, acc, intro, now);

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
		GlStateManager.color(1f, 1f, 1f, 1f);
		GlStateManager.enableTexture2D();
		GlStateManager.enableDepth();
	}

	/** Darkens and tints the panorama, with two slow glows and a few drifting sparks. */
	private static void backdrop(int w, int h, float t, int acc) {
		// left side darker (the sidebar and text sit there), the rest lets the panorama through
		gradientH(0, 0, w, h, 0xE6050A10, 0x8C050A10);
		PlebTheme.gradient(0, 0, w, h / 3, 0x60040609, 0x00040609);
		PlebTheme.gradient(0, h * 2 / 3, w, h - h * 2 / 3, 0x00040609, 0xD0040609);
		float gx = w * (0.55f + 0.08f * (float) Math.sin(t * 0.11));
		float gy = h * (0.42f + 0.06f * (float) Math.cos(t * 0.09));
		PlebTheme.glow(gx, gy, Math.max(w, h) * 0.42f, Ui.alpha(acc, 0x2E));
		int second = Ui.mix(acc, 0xFF8B5CFF, 0.6f);
		PlebTheme.glow(w * (0.9f + 0.04f * (float) Math.cos(t * 0.07)), h * (0.95f + 0.03f * (float) Math.sin(t * 0.13)),
				Math.max(w, h) * 0.45f, Ui.alpha(second, 0x26));
		// a faint light beam behind the logo, slowly swinging
		float beam = (float) Math.sin(t * 0.25) * 0.08f;
		beam(w * (0.5f + beam), -10, w * 0.12f, h * 0.9f, Ui.alpha(acc, 0x12));
		// sparks: few, slow, twinkling (no particle system to update)
		GlStateManager.disableTexture2D();
		GlStateManager.enableBlend();
		for (int i = 0; i < 28; i++) {
			float rx = hash(i * 3 + 11), ry = hash(i * 7 + 5), sp = 0.008f + 0.02f * hash(i * 11 + 7);
			float x = rx * w + (float) Math.sin(t * 0.4f + i) * 8f;
			float y = ((ry - t * sp) % 1f + 1f) % 1f * (h + 20) - 10;
			float tw = 0.5f + 0.5f * (float) Math.sin(t * (1.2f + hash(i) * 1.6f) + i * 1.3f);
			float size = hash(i * 5 + 3) > 0.78f ? 1.5f : 1f;
			int c = hash(i * 13 + 1) > 0.45f ? acc : 0xFFFFFFFF;
			fill(x, y, size, size, Ui.alpha(c, Math.round(25 + 110 * tw)));
		}
		GlStateManager.enableTexture2D();
	}

	// ---- the middle ----

	private static int playX, playY, playW, playH;
	private static int chipX, chipY, chipW, chipH;

	private static void centerpiece(GuiScreen s, int x0, int x1, int h, int mx, int my, float t, int acc, float in) {
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		int cw = x1 - x0;
		float cx = x0 + cw / 2f;
		String a = "PLEB", b = "CLIENT";
		int tw = font.getStringWidth(a + b);
		float sc = Math.max(2f, Math.min(5f, Math.min(cw * 0.78f / tw, (h - 120) / 14f)));
		sc = Math.round(sc * 2f) / 2f;
		int cardW = Math.min(300, cw - 8);
		int cardH = 52;
		float block = 9 * sc + 10 + 14 + 14 + cardH;
		float top = Math.max(24, (h - 16 - block) / 2f - 4);
		float rise = (1f - in) * 14f;

		// wordmark: float, shadow, sweep, glow
		float bob = animations() ? (float) Math.sin(t * 1.1f) * 1.2f : 0f;
		float lx = cx - tw * sc / 2f;
		float ly = top + rise + bob;
		PlebTheme.glow(cx, ly + 4.5f * sc, tw * sc * 0.55f, Ui.alpha(acc, Math.round(0x30 * in)));
		int aw = Math.round(font.getStringWidth(a) * sc);
		int alphaIn = Math.round(255 * in);
		text(font, a, lx + sc * 0.6f, ly + sc * 0.6f, sc, Ui.alpha(0xFF000000, Math.round(0xA0 * in)));
		text(font, b, lx + aw + sc * 0.6f, ly + sc * 0.6f, sc, Ui.alpha(Ui.mix(acc, 0xFF000000, 0.8f), Math.round(0xE0 * in)));
		text(font, a, lx, ly, sc, Ui.alpha(0xFFFFFFFF, alphaIn));
		text(font, b, lx + aw, ly, sc, Ui.alpha(acc, alphaIn));
		float sweep = (t * 0.3f) % 1.8f;
		if (sweep < 1f && animations()) {
			PlebTheme.glow(lx - 16 + (tw * sc + 32) * sweep, ly + 4.5f * sc, 5f * sc, 0x2CFFFFFF);
		}
		// accent underline that breathes
		float ul = tw * sc * (0.35f + 0.08f * (float) Math.sin(t * 1.4f)) * in;
		float uy = ly + 9 * sc + 3;
		gradientH(cx - ul, uy, ul, 1, Ui.alpha(acc, 0), Ui.alpha(acc, 0xE0));
		gradientH(cx, uy, ul, 1, Ui.alpha(acc, 0xE0), Ui.alpha(acc, 0));

		// chips: version | server status | ping
		float cy = uy + 8;
		String ver = TitleActions.gameVersion().toUpperCase();
		String addr = TitleActions.address().toUpperCase();
		int st = TitleActions.state;
		String status = st == 1 ? (TitleActions.players.isEmpty() ? "ONLINE" : online(TitleActions.players) + " ONLINE")
				: st == 2 ? "OFFLINE" : "CHECKING";
		String ping = st == 1 && TitleActions.ping >= 0 ? TitleActions.ping + " MS" : null;
		int w1 = font.getStringWidth(ver) + 12;
		int w2 = font.getStringWidth(addr) + font.getStringWidth(status) + 30;
		int w3 = ping == null ? 0 : font.getStringWidth(ping) + 20;
		int total = w1 + 4 + w2 + (ping == null ? 0 : 4 + w3);
		float px = cx - total / 2f;
		int ca = Math.round(255 * in);
		chip(px, cy, w1, Ui.alpha(0xFF0E151C, Math.round(0xD0 * in)), Ui.alpha(Ui.BORDER, ca));
		text(font, ver, px + 6, cy + 3, 1f, Ui.alpha(Ui.MUTED, ca));
		px += w1 + 4;
		chipX = Math.round(px);
		chipY = Math.round(cy);
		chipW = w2;
		chipH = 14;
		boolean overChip = mx >= chipX && mx < chipX + chipW && my >= chipY && my < chipY + chipH && pending == null;
		chipHover = approach(chipHover, overChip ? 1f : 0f, 14f);
		int dot = st == 1 ? Ui.GOOD : st == 2 ? Ui.BAD : Ui.GOLD;
		chip(px, cy, w2, Ui.alpha(Ui.mix(0xFF0E151C, 0xFF14212B, chipHover), Math.round(0xD0 * in)),
				Ui.alpha(Ui.mix(Ui.BORDER, acc, chipHover * 0.8f), ca));
		float pulse = 0.5f + 0.5f * (float) Math.sin(t * (st == 0 ? 6f : 2.4f));
		PlebTheme.glow(px + 8, cy + 7, 5f + 2f * pulse, Ui.alpha(dot, Math.round(0x70 * in * pulse)));
		fill(px + 6.5f, cy + 5.5f, 3, 3, Ui.alpha(dot, ca));
		int tx = Math.round(px + 14);
		text(font, addr, tx, cy + 3, 1f, Ui.alpha(Ui.TEXT, ca));
		tx += font.getStringWidth(addr) + 4;
		fill(tx, cy + 4, 1, 6, Ui.alpha(Ui.BORDER, ca));
		text(font, status, tx + 5, cy + 3, 1f, Ui.alpha(dot, ca));
		px += w2 + 4;
		if (ping != null) {
			chip(px, cy, w3, Ui.alpha(0xFF0E151C, Math.round(0xD0 * in)), Ui.alpha(Ui.BORDER, ca));
			int bars = TitleActions.ping < 80 ? 4 : TitleActions.ping < 160 ? 3 : TitleActions.ping < 300 ? 2 : 1;
			for (int i = 0; i < 4; i++) {
				int bh = 2 + i * 2;
				fill(px + 5 + i * 2.5f, cy + 11 - bh, 1.5f, bh, Ui.alpha(i < bars ? Ui.GOOD : Ui.FAINT, ca));
			}
			text(font, ping, px + 17, cy + 3, 1f, Ui.alpha(Ui.MUTED, ca));
		}

		// server card with the Play button
		float ky = cy + 14 + 12 + (1f - in) * 10f;
		float kx = cx - cardW / 2f;
		int ka = Math.round(255 * Math.min(1f, in * 1.3f));
		// shadow + glass
		roundF(kx - 2, ky - 1, cardW + 4, cardH + 4, Ui.alpha(0xFF000000, Math.round(0x40 * in)));
		gradientRoundF(kx, ky, cardW, cardH, Ui.alpha(0xFF131C25, Math.round(0xEE * in)), Ui.alpha(0xFF0A1016, Math.round(0xF2 * in)));
		outlineF(kx, ky, cardW, cardH, Ui.alpha(Ui.mix(Ui.BORDER, acc, 0.25f + 0.35f * playHover), ka));
		gradientH(kx + 1, ky, cardW - 2, 1, Ui.alpha(acc, 0), Ui.alpha(acc, Math.round(0xC0 * in)));
		// monogram tile
		float mxp = kx + 8, myp = ky + 8;
		int ms = cardH - 16;
		gradientRoundF(mxp, myp, ms, ms, Ui.alpha(acc, ka), Ui.alpha(Ui.mix(acc, 0xFF0A2A33, 0.55f), ka));
		float ps = ms >= 30 ? 3f : 2f;
		text(font, "P", mxp + ms / 2f - 2.5f * ps, myp + ms / 2f - 3.5f * ps, ps, Ui.alpha(Ui.ON_ACCENT, ka));
		// name, motd, players
		float tx0 = mxp + ms + 8;
		int textRoom = Math.round(kx + cardW - 8 - 78 - tx0);
		text(font, "PlebMc Network", tx0, ky + 9, 1f, Ui.alpha(0xFFFFFFFF, ka));
		String line = st == 1 ? firstLine(TitleActions.motd) : st == 2 ? "Can't reach the server right now" : "Checking the server...";
		if (line.isEmpty()) {
			line = "Battle royale, minigames and more";
		}
		text(font, Ui.fit(line, textRoom), tx0, ky + 21, 1f, Ui.alpha(st == 2 ? Ui.BAD : Ui.MUTED, ka));
		float fillFrac = playersFraction(TitleActions.players);
		float barW = Math.max(30, textRoom);
		if (st != 2) {
			roundF(tx0, ky + 35, barW, 4, Ui.alpha(0xFF0A0E12, ka));
		} else {
			text(font, "Press Play to try anyway", tx0, ky + 33, 1f, Ui.alpha(Ui.FAINT, ka));
		}
		if (st == 1 && fillFrac > 0f) {
			gradientH(tx0 + 1, ky + 36, (barW - 2) * fillFrac, 2, Ui.alpha(acc, ka), Ui.alpha(Ui.GOOD, ka));
		} else if (st == 0) {
			float k = (t * 0.8f) % 1f;
			gradientH(tx0 + 1 + (barW - 2) * Math.max(0f, k - 0.25f), ky + 36, (barW - 2) * 0.25f, 2, Ui.alpha(acc, 0), Ui.alpha(acc, ka));
		}
		// Play
		playW = 70;
		playH = 22;
		playX = Math.round(kx + cardW - 8 - playW);
		playY = Math.round(ky + (cardH - playH) / 2f);
		boolean overPlay = mx >= playX && mx < playX + playW && my >= playY && my < playY + playH && pending == null;
		playHover = approach(playHover, overPlay ? 1f : 0f, 14f);
		float breathe = animations() ? 0.5f + 0.5f * (float) Math.sin(t * 2.2f) : 0.5f;
		PlebTheme.glow(playX + playW / 2f, playY + playH / 2f, 34 + 10 * playHover,
				Ui.alpha(acc, Math.round((0x24 + 0x20 * breathe + 0x30 * playHover) * in)));
		int top0 = Ui.mix(Ui.mix(acc, 0xFFFFFFFF, 0.12f), Ui.mix(acc, 0xFFFFFFFF, 0.35f), playHover);
		int bot0 = Ui.mix(Ui.mix(acc, 0xFF000000, 0.28f), acc, playHover);
		float lift = -1.0f * playHover;
		gradientRoundF(playX, playY + lift, playW, playH, Ui.alpha(top0, ka), Ui.alpha(bot0, ka));
		fill(playX + 1, playY + lift, playW - 2, 1, Ui.alpha(0xFFFFFFFF, Math.round(0x70 * in)));
		float ix = playX + 10 + 2f * playHover;
		iconF("play", ix, playY + lift + 6.5f, 1f, Ui.alpha(Ui.ON_ACCENT, ka));
		text(font, "PLAY", ix + 14, playY + lift + 7, 1f, Ui.alpha(Ui.ON_ACCENT, ka));
	}

	private static String online(String pop) {
		int i = pop.indexOf('/');
		return i > 0 ? pop.substring(0, i) : pop;
	}

	private static float playersFraction(String pop) {
		try {
			int i = pop.indexOf('/');
			if (i <= 0) {
				return 0f;
			}
			float on = Integer.parseInt(pop.substring(0, i).trim());
			float max = Integer.parseInt(pop.substring(i + 1).trim());
			return max <= 0 ? 0f : Math.max(0.03f, Math.min(1f, on / max));
		} catch (RuntimeException e) {
			return 0f;
		}
	}

	private static String firstLine(String motd) {
		if (motd == null) {
			return "";
		}
		int n = motd.indexOf('\n');
		return (n >= 0 ? motd.substring(0, n) : motd).trim();
	}

	// ---- news ----

	private static void newsColumn(int x, int w, int h, float t, int acc, float intro, long now) {
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		int y = Math.max(34, h / 2 - 70);
		float in0 = ease(Math.max(0f, Math.min(1f, (intro - 0.2f) / 0.8f)));
		int a0 = Math.round(255 * in0);
		text(font, "WHAT'S NEW", x, y, 1f, Ui.alpha(acc, a0));
		fill(x, y + 11, w, 1, Ui.alpha(Ui.BORDER, a0));
		y += 17;
		for (int i = 0; i < NEWS.length; i++) {
			float k = ease(Math.max(0f, Math.min(1f, (intro - 0.3f - i * 0.12f) / 0.6f)));
			if (k <= 0f) {
				continue;
			}
			List<String> lines = font.listFormattedStringToWidth(NEWS[i][1], w - 12);
			if (lines.size() > 3) {
				lines = lines.subList(0, 3);
			}
			int ch = 16 + lines.size() * 10;
			float ox = (1f - k) * 16f;
			int a = Math.round(255 * k);
			roundF(x + ox, y, w, ch, Ui.alpha(0xFF0E151C, Math.round(0xC8 * k)));
			fill(x + ox, y + 2, 2, ch - 4, Ui.alpha(acc, a));
			text(font, NEWS[i][0], x + ox + 7, y + 4, 1f, Ui.alpha(Ui.TEXT, a));
			for (int l = 0; l < lines.size(); l++) {
				text(font, lines.get(l), x + ox + 7, y + 15 + l * 10, 1f, Ui.alpha(Ui.MUTED, a));
			}
			y += ch + 5;
		}
	}

	// ---- account chip ----

	private static int accX, accY, accW, accH;
	private static float accHover;

	private static void accountChip(int w, int mx, int my, int acc, float in) {
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		String name = TitleActions.username();
		if (name == null || name.isEmpty()) {
			return;
		}
		int tw = font.getStringWidth(name);
		accW = tw + 26;
		accH = 16;
		accX = w - 8 - accW;
		accY = 6;
		boolean over = mx >= accX && mx < accX + accW && my >= accY && my < accY + accH && pending == null;
		accHover = approach(accHover, over ? 1f : 0f, 14f);
		int a = Math.round(255 * in);
		roundF(accX, accY, accW, accH, Ui.alpha(Ui.mix(0xFF0E151C, 0xFF16222C, accHover), Math.round(0xD8 * in)));
		outlineF(accX, accY, accW, accH, Ui.alpha(Ui.mix(Ui.BORDER, acc, accHover), a));
		gradientRoundF(accX + 3, accY + 3, 10, 10, Ui.alpha(acc, a), Ui.alpha(Ui.mix(acc, 0xFF000000, 0.4f), a));
		text(font, name.substring(0, 1).toUpperCase(), accX + 5.5f, accY + 4, 1f, Ui.alpha(Ui.ON_ACCENT, a));
		text(font, name, accX + 18, accY + 4, 1f, Ui.alpha(Ui.mix(Ui.TEXT, 0xFFFFFFFF, accHover), a));
	}

	// ---- bottom bar ----

	private static int credX, credW;

	private static void infoBar(int w, int h, int mx, int my, String splash, int acc, float in) {
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		int y = h - 16;
		int a = Math.round(255 * in);
		fill(0, y, w, 16, Ui.alpha(0xFF04070B, Math.round(0xC8 * in)));
		gradientH(0, y, w, 1, Ui.alpha(acc, Math.round(0x60 * in)), Ui.alpha(acc, 0));
		String left = TitleActions.gameVersion() + "  \u00b7  " + TitleActions.platform();
		String cred = TitleActions.hasCredits() ? "Credits" : "";
		credW = cred.isEmpty() ? 0 : font.getStringWidth(cred);
		credX = w - 8 - credW;
		boolean over = credW > 0 && mx >= credX - 3 && mx < credX + credW + 3 && my >= y && my < h && pending == null;
		creditsHover = approach(creditsHover, over ? 1f : 0f, 14f);
		if (credW > 0) {
			text(font, cred, credX, y + 4, 1f, Ui.alpha(Ui.mix(Ui.MUTED, acc, creditsHover), a));
			fill(credX, y + 13, credW * creditsHover, 1, Ui.alpha(acc, a));
		}
		int rightEdge = credX - (credW > 0 ? 12 : 0);
		int lx = COLLAPSED + 8;
		left = Ui.fit(left, Math.max(20, (rightEdge - lx) * 11 / 20));
		int lw = font.getStringWidth(left);
		text(font, left, lx, y + 4, 1f, Ui.alpha(Ui.FAINT, a));
		// splash text: the vanilla line, as a small accent ticker between the two
		int room = rightEdge - (lx + lw + 16);
		if (splash != null && !splash.isEmpty() && room >= 50) {
			String sp = Ui.fit(splash, room);
			int sw = font.getStringWidth(sp);
			float pulse = animations() ? 0.8f + 0.2f * (float) Math.sin(System.currentTimeMillis() / 300.0) : 1f;
			float sx = lx + lw + 16 + (room - sw) / 2f;
			fill(sx - 8, y + 6, 3, 3, Ui.alpha(acc, Math.round(a * pulse)));
			text(font, sp, sx, y + 4, 1f, Ui.alpha(0xFFFFE066, Math.round(a * pulse)));
		}
	}

	// ---- sidebar ----

	private static void sidebar(int w, int h, int mx, int my, float t, int acc, float intro, long now) {
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		float e = ease(expand);
		float sw = sideWidth(w);
		float slide = (1f - ease(Math.min(1f, intro * 1.6f))) * -(COLLAPSED + 6);
		GlStateManager.pushMatrix();
		GlStateManager.translate(slide, 0f, 0f);
		int bottom = h - 16;
		// panel: glassy, with a shadow and an accent edge that brightens as it opens
		if (e > 0.01f) {
			gradientH(sw, 0, 18 * e, bottom, Ui.alpha(0xFF000000, Math.round(0x70 * e)), 0x00000000);
		}
		gradientH(0, 0, sw, bottom, 0xFF070B11, 0xFF0B1118);
		fill(sw - 1, 0, 1, bottom, Ui.alpha(Ui.mix(Ui.BORDER_SOFT, acc, 0.15f + 0.6f * e), 0xFF));
		PlebTheme.gradient(Math.round(sw - 1), 0, 1, bottom / 2, Ui.alpha(acc, Math.round(0x90 * e)), Ui.alpha(acc, 0));

		// logo mark: a small accent tile with P, and the name when open
		float lx = 7, ly = 8;
		float lp = animations() ? 0.5f + 0.5f * (float) Math.sin(t * 1.8f) : 0.5f;
		PlebTheme.glow(lx + 8, ly + 8, 12 + 3 * lp, Ui.alpha(acc, Math.round(0x40 + 0x20 * lp)));
		gradientRoundF(lx, ly, 16, 16, acc, Ui.mix(acc, 0xFF0A2A33, 0.5f));
		text(font, "P", lx + 5f, ly + 4f, 1f, Ui.ON_ACCENT);
		if (e > 0.05f) {
			int la = Math.round(255 * e);
			float ox = (1f - e) * -6f;
			text(font, "PLEB", lx + 22 + ox, ly + 4, 1f, Ui.alpha(0xFFFFFFFF, la));
			text(font, "CLIENT", lx + 22 + ox + font.getStringWidth("PLEB"), ly + 4, 1f, Ui.alpha(acc, la));
		}
		fill(7, 30, sw - 14, 1, Ui.alpha(Ui.BORDER_SOFT, 0xFF));

		// entries
		String[][] it = TitleActions.ITEMS;
		int[] ys = rows(h);
		int rh = rowHeight(h) - 2;
		int hovered = -1;
		if (pending == null && mx >= 0 && mx < sw && my < bottom) {
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
		// the sliding indicator
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
				// the divider between the groups
				int dy = ys[i + 1 < it.length ? i + 1 : i] - 5;
				fill(9, dy, sw - 18, 1, Ui.alpha(Ui.BORDER_SOFT, 0xFF));
				continue;
			}
			hover[i] = approach(hover[i], i == hovered ? 1f : 0f, 13f);
			float hv = ease(hover[i]);
			int y = ys[i];
			boolean primary = "play".equals(it[i][0]);
			// highlight: an accent wash from the left that fades in
			if (hv > 0.01f) {
				roundF(4, y, sw - 8, rh, Ui.alpha(0xFF162330, Math.round(0xC0 * hv)));
				gradientH(4, y, (sw - 8) * (0.45f + 0.55f * hv), rh, Ui.alpha(acc, Math.round(0x40 * hv)), Ui.alpha(acc, 0));
				outlineF(4, y, sw - 8, rh, Ui.alpha(acc, Math.round(0x50 * hv)));
			}
			// icon: nudges right, grows a little and lights up
			float icx = 15 + 2.5f * hv;
			float icy = y + rh / 2f;
			int base = primary ? acc : Ui.MUTED;
			int ic = Ui.mix(base, primary ? Ui.mix(acc, 0xFFFFFFFF, 0.4f) : acc, hv);
			if (primary) {
				float pr = animations() ? 0.5f + 0.5f * (float) Math.sin(t * 2.6f) : 0.5f;
				PlebTheme.glow(icx, icy, 9 + 3 * pr + 3 * hv, Ui.alpha(acc, Math.round(0x34 + 0x20 * pr)));
			}
			GlStateManager.pushMatrix();
			GlStateManager.translate(icx, icy, 0f);
			float s = 1f + 0.12f * hv;
			GlStateManager.scale(s, s, 1f);
			if (hv > 0.01f) {
				iconF(it[i][2], -4.5f + 0.5f, -4.5f + 0.5f, 1f, Ui.alpha(acc, Math.round(0x50 * hv)));
			}
			iconF(it[i][2], -4.5f, -4.5f, 1f, ic);
			GlStateManager.popMatrix();
			// label: slides in with the panel, brightens on hover
			if (e > 0.04f) {
				float lxp = 30 + (1f - e) * -10f + 2f * hv;
				int room = Math.round(sw - lxp - 8);
				if (room > 8) {
					String label = Ui.fit(it[i][1], room);
					int lc = primary ? Ui.mix(acc, 0xFFFFFFFF, 0.3f * hv) : Ui.mix(0xFFB7C4CE, 0xFFFFFFFF, hv);
					text(font, label, lxp, icy - 3.5f, 1f, Ui.alpha(lc, Math.round(255 * e)));
				}
			}
		}
		if (indA > 0.01f && indY >= 0) {
			fill(2, indY + (rh - 6 - indH) / 2f, 2, Math.max(0f, indH), Ui.alpha(acc, Math.round(255 * indA)));
			PlebTheme.glow(3, indY + (rh - 6) / 2f, 7, Ui.alpha(acc, Math.round(0x50 * indA)));
		}
		GlStateManager.popMatrix();
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
			PlebTheme.glow(r[0], r[1], 6 + 34 * e, Ui.alpha(acc, Math.round(0x60 * (1f - k))));
		}
	}

	// ==================== input ====================

	/** A click on the title screen; true when it was handled (vanilla buttons are hidden). */
	public static boolean click(GuiScreen s, int mx, int my, int button) {
		if (button != 0 || pending != null) {
			return true;
		}
		int w = s.width, h = s.height;
		String[][] it = TitleActions.ITEMS;
		float sw = sideWidth(w);
		if (mx < sw && my < h - 16) {
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
		if (mx >= playX && mx < playX + playW && my >= playY && my < playY + playH) {
			start("play", mx, my);
			return true;
		}
		if (mx >= chipX && mx < chipX + chipW && my >= chipY && my < chipY + chipH) {
			start("multi", mx, my);
			return true;
		}
		if (mx >= accX && mx < accX + accW && my >= accY && my < accY + accH) {
			start("profile", mx, my);
			return true;
		}
		if (credW > 0 && mx >= credX - 3 && mx < credX + credW + 3 && my >= h - 16) {
			sound();
			TitleActions.credits();
			return true;
		}
		return true;
	}

	/** Keys: up/down move through the sidebar, Enter opens, Escape closes the selection. */
	public static boolean key(GuiScreen s, char c, int key) {
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
		return false;
	}

	private static void start(String id, int mx, int my) {
		sound();
		if (mx >= 0) {
			if (ripples.isEmpty()) {
				rippleBase = System.currentTimeMillis();
			}
			ripples.add(new float[] { mx, my, System.currentTimeMillis() - rippleBase });
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

	private static float hash(int n) {
		n = (n << 13) ^ n;
		int m = (n * (n * n * 15731 + 789221) + 1376312589) & 0x7FFFFFFF;
		return m / (float) 0x7FFFFFFF;
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

	private static void chip(float x, float y, int w, int fill, int border) {
		roundF(x, y, w, 14, fill);
		outlineF(x, y, w, 14, border);
	}

	/** A 9x9 icon at fractional coordinates. */
	private static void iconF(String name, float x, float y, float px, int color) {
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

	/** A soft vertical light beam (bright at the top, gone at the bottom). */
	private static void beam(float cx, float y, float halfW, float h, int c) {
		int clear = c & 0x00FFFFFF;
		begin();
		vtx(cx, y, c);
		vtx(cx - halfW, y, clear);
		vtx(cx - halfW * 1.8f, y + h, clear);
		vtx(cx, y + h, clear);
		vtx(cx + halfW, y, clear);
		vtx(cx, y, c);
		vtx(cx, y + h, clear);
		vtx(cx + halfW * 1.8f, y + h, clear);
		end();
	}
}
