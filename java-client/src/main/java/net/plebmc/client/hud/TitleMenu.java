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
 * <p>Deliberately quiet: the panorama fills the whole screen, including
 * behind the sidebar. On top of it there is only
 * <ul>
 * <li>a transparent sidebar on the left (the mascot, icons, and labels that
 * slide in while the mouse is over it), separated by a thin accent line,
 * with a translucent hover wash and a small accent indicator;</li>
 * <li>one Play button, with the server's status under it;</li>
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
	private static float playHover;
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
		TitleActions.stopStatus();
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
		if (pr.titleStatus) {
			TitleActions.pollStatus();
		}
		// text writes depth here: without this the quads drawn over it would have holes
		GlStateManager.disableDepth();

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
		playArea(x0, w, h, mx, my, t, acc, slideIn, pr.titleStatus);
		footer(x0, w, h, mx, my, splash, acc, slideIn, pr);

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

	// ---- the Play button ----

	private static int playX, playY, playW, playH;

	private static void playArea(int x0, int w, int h, int mx, int my, float t, int acc, float in, boolean status) {
		FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
		float cx = x0 + (w - x0) / 2f;
		playW = Math.max(110, Math.min(150, (w - x0) / 3));
		playH = 24;
		float rise = (1f - in) * 10f;
		playX = Math.round(cx - playW / 2f);
		playY = Math.round(h / 2f - playH / 2f + 6 + rise);
		boolean over = mx >= playX && mx < playX + playW && my >= playY && my < playY + playH && pending == null
				&& mx >= sideWidth(w);
		playHover = approach(playHover, over ? 1f : 0f, 12f);
		float hv = ease(playHover);
		int a = Math.round(255 * in);
		// glass that fills with the accent on hover
		roundF(playX, playY, playW, playH, Ui.alpha(0xFF05090E, Math.round(0x8C * in * (1f - hv))));
		if (hv > 0.01f) {
			gradientRoundF(playX, playY, playW, playH, Ui.alpha(Ui.mix(acc, 0xFFFFFFFF, 0.15f), Math.round(0xF0 * hv * in)),
					Ui.alpha(Ui.mix(acc, 0xFF000000, 0.15f), Math.round(0xF0 * hv * in)));
		}
		outlineF(playX, playY, playW, playH, Ui.alpha(acc, Math.round((0xB0 + 0x4F * hv) * in)));
		int fg = Ui.mix(0xFFFFFFFF, Ui.ON_ACCENT, hv);
		String label = "PLAY";
		int lw = 11 + 6 + font.getStringWidth(label);
		float lx = cx - lw / 2f + 1.5f * hv;
		iconF("nav_play", lx - 1, playY + (playH - 11) / 2f, 1f, Ui.alpha(fg, a));
		text(font, label, lx + 17, playY + (playH - 8) / 2f, 1f, Ui.alpha(fg, a));

		if (!status) {
			return;
		}
		// one quiet line under it: dot, address, players, ping
		int st = TitleActions.state;
		String line = TitleActions.address();
		if (st == 1) {
			if (!TitleActions.players.isEmpty()) {
				line += "  \u00b7  " + online(TitleActions.players) + " online";
			}
			if (TitleActions.ping >= 0) {
				line += "  \u00b7  " + TitleActions.ping + " ms";
			}
		} else {
			line += "  \u00b7  " + (st == 2 ? "offline" : "checking");
		}
		int dot = st == 1 ? Ui.GOOD : st == 2 ? Ui.BAD : Ui.GOLD;
		int tw = font.getStringWidth(line) + 8;
		float sx = cx - tw / 2f, sy = playY + playH + 9;
		float pulse = animations() ? 0.6f + 0.4f * (float) Math.sin(t * (st == 0 ? 6f : 2.2f)) : 1f;
		fill(sx, sy + 2.5f, 3, 3, Ui.alpha(dot, Math.round(a * pulse)));
		textShadow(font, line, sx + 8, sy, Ui.alpha(0xFFC9D3DB, Math.round(0xD0 * in)));
	}

	private static String online(String pop) {
		int i = pop.indexOf('/');
		return (i > 0 ? pop.substring(0, i) : pop).trim();
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
		if (mx >= playX && mx < playX + playW && my >= playY && my < playY + playH) {
			start("play", mx, my);
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
