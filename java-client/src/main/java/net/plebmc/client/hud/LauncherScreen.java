package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.gui.GuiScreen;
import net.plebmc.client.cosmetics.ClientSession;
import net.plebmc.client.cosmetics.Cosmetics;
import net.plebmc.client.mods.ClientInfo;
import net.plebmc.client.mods.ClientPrefs;

/**
 * Right Shift: the PlebClient launcher. One centered column: a compact
 * wordmark with Plebio, a row of equal navigation cards with number keys,
 * a quiet line of player info, and Resume. Everything shares one spacing
 * scale, one border weight, one icon size and one easing.
 */
public final class LauncherScreen extends PlebScreen {

	private static final class Door {
		final String label;
		final String icon;
		final String tip;
		final java.util.function.Supplier<GuiScreen> open;

		Door(String label, String icon, String tip, java.util.function.Supplier<GuiScreen> open) {
			this.label = label;
			this.icon = icon;
			this.tip = tip;
			this.open = open;
		}
	}

	private final List<Door> doors = new ArrayList<>();
	/** Whether the Admin door is there (it appears when the server says this player is an Owner). */
	private boolean adminShown;
	private boolean signedInShown;

	public LauncherScreen(GuiScreen parent) {
		super(parent);
		fillDoors();
	}

	private void fillDoors() {
		doors.clear();
		doors.add(new Door("Mods", "nav_mods", "Turn mods on and off and change their settings", () -> new ModsScreen(this)));
		doors.add(new Door("Wardrobe", "nav_wardrobe", "Hats, cloaks and wings for " + Cosmetics.currency(), () -> new ShopScreen(this)));
		doors.add(new Door("Quests", "nav_quests", "Daily and weekly quests for coins", () -> new QuestsScreen(this)));
		doors.add(new Door("HUD", "nav_hud", "Move, resize and recolor your HUD", () -> new HudEditorScreen(this)));
		doors.add(new Door("Video", "nav_video", "Render distance, graphics and performance", () -> new VideoOptionsScreen(this)));
		signedInShown = ClientSession.signedIn();
		doors.add(new Door(signedInShown ? "Account" : "Sign in", signedInShown ? "nav_profile" : "nav_lock",
				"Your PlebMc account: sign in so nobody else can spend your coins", () -> new SignInScreen(this)));
		doors.add(new Door("Settings", "nav_options", "Client preferences", () -> new SettingsScreen(this)));
		if (ClientPrefs.get().developer) {
			doors.add(new Door("Debug", "nav_debug", "Apollo diagnostics (developer)", () -> new ApolloDebugScreen(this)));
		}
		adminShown = Cosmetics.account().admin;
		if (adminShown) {
			doors.add(new Door("Admin", "nav_admin", "Players, coins, cosmetics, servers and announcements (Owners)",
					() -> new AdminScreen(this)));
		}
	}

	@Override
	protected void tick() {
		if (Cosmetics.account().admin != adminShown || ClientSession.signedIn() != signedInShown) {
			fillDoors();
			rebuild();
		}
	}

	// ==================== layout ====================

	/** The spacing scale: every gap on this screen is one of these. */
	private static final int S1 = 4, S2 = 8, S3 = 12, S4 = 18;
	private static final int CARD_W = 62;
	private static final int CARD_H = 58;
	private static final int GAP = S2;
	private static final int BRAND_H = 32;
	private static final int INFO_H = 16;
	private static final int RESUME_W = 92, RESUME_H = 20;

	private int cardW() {
		int n = doors.size();
		return Math.max(34, Math.min(CARD_W, (this.width - 2 * S4 - (n - 1) * GAP) / n));
	}

	private int cardH() {
		return Math.round(cardW() * (CARD_H / (float) CARD_W));
	}

	/** The big vertical gap: a little tighter on short screens. */
	private int sv() {
		return this.height < 300 ? S3 : S4;
	}

	private int columnH() {
		return BRAND_H + sv() + cardH() + S3 + INFO_H + sv() + RESUME_H + S3 + 9;
	}

	private int top() {
		return Math.max(S2, (this.height - columnH()) / 2 - S1);
	}

	private int rowY() {
		return top() + BRAND_H + sv();
	}

	private int rowX() {
		int n = doors.size();
		return (this.width - (n * cardW() + (n - 1) * GAP)) / 2;
	}

	@Override
	protected void build() {
		int n = doors.size();
		int cw = cardW(), ch = cardH();
		int x0 = rowX();
		int y = rowY();
		cards.clear();
		for (int i = 0; i < n; i++) {
			DoorTile t = new DoorTile(doors.get(i), i);
			t.at(x0 + i * (cw + GAP), y, cw, ch);
			widgets.add(t);
			cards.add(t);
		}
		int ry = y + ch + S3 + INFO_H + sv();
		ResumeButton r = new ResumeButton();
		r.at(this.width / 2 - RESUME_W / 2, ry, RESUME_W, RESUME_H);
		widgets.add(r);
	}

	// ==================== animation ====================

	private static final int OPEN_MS = 260, CLOSE_MS = 150, STAGGER_MS = 28, PRESS_MS = 140;

	private final long shownAt = System.currentTimeMillis();
	private long closingAt;
	/** A card that was picked and opens when its press animation ends. */
	private DoorTile pressed;
	private long pressedAt;
	/** The card the arrow keys picked, or -1. */
	private int keySel = -1;
	/** Where the selection bar is (in card slots) and how visible it is. */
	private float barSlot = -1f, barShow;
	private final List<DoorTile> cards = new ArrayList<>();

	private static boolean anim() {
		return ClientPrefs.get().animations;
	}

	private static float clamp01(float v) {
		return v < 0f ? 0f : v > 1f ? 1f : v;
	}

	/** The one easing curve used on this screen. */
	private static float out(float t) {
		t = clamp01(t);
		float u = 1f - t;
		return 1f - u * u * u;
	}

	/** 0..1: how far the screen has opened; falls back to 0 while closing. */
	private float shown() {
		if (!anim()) {
			return closingAt == 0 ? 1f : 0f;
		}
		if (closingAt != 0) {
			return 1f - out((System.currentTimeMillis() - closingAt) / (float) CLOSE_MS);
		}
		return out((System.currentTimeMillis() - shownAt) / (float) OPEN_MS);
	}

	/** 0..1 for card {@code i} coming in, a little after the one before it. */
	private float cardIn(int i) {
		if (!anim() || closingAt != 0) {
			return 1f;
		}
		return out((System.currentTimeMillis() - shownAt - 60 - i * STAGGER_MS) / 220f);
	}

	@Override
	protected void back() {
		if (closingAt != 0) {
			return;
		}
		if (!anim()) {
			super.back();
			return;
		}
		closingAt = System.currentTimeMillis();
	}

	private void open(DoorTile t) {
		if (closingAt != 0 || pressed != null) {
			return;
		}
		click();
		if (!anim()) {
			this.mc.displayGuiScreen(t.d.open.get());
			return;
		}
		pressed = t;
		pressedAt = System.currentTimeMillis();
	}

	@Override
	public void drawScreen(int mx, int my, float pt) {
		long now = System.currentTimeMillis();
		if (closingAt != 0 && now - closingAt >= CLOSE_MS) {
			closingAt = Long.MAX_VALUE / 4; // only once
			Draw.fade = 1f;
			super.back();
			return;
		}
		if (pressed != null && now - pressedAt >= PRESS_MS) {
			DoorTile t = pressed;
			pressed = null;
			this.mc.displayGuiScreen(t.d.open.get());
			return;
		}
		float s = shown();
		try {
			Draw.fade = s;
			super.drawScreen(mx, my, pt);
		} finally {
			Draw.fade = 1f;
			GlStateManager.popMatrix();
		}
	}

	/**
	 * One even dark layer over the world (so the HUD and scoreboard sit back),
	 * a touch darker at the edges, faded with the screen. Pushes the matrix
	 * that {@link #drawScreen} pops, so the menu can scale as it opens and closes.
	 */
	@Override
	protected void drawBackdrop() {
		float s = shown();
		float keep = Draw.fade;
		Draw.fade = s;
		if (this.mc != null && this.mc.theWorld != null) {
			Ui.rect(0, 0, this.width, this.height, 0xB4070B10);
		} else {
			Draw.fade = 1f;
			this.drawDefaultBackground();
			Draw.fade = s;
			Ui.rect(0, 0, this.width, this.height, 0x90080B10);
		}
		int band = this.height / 3;
		Ui.gradient(0, 0, this.width, band, 0x60000000, 0x00000000);
		Ui.gradient(0, this.height - band, this.width, band, 0x00000000, 0x90000000);
		Draw.fade = keep;
		GlStateManager.pushMatrix();
		float k = closingAt != 0 ? 0.97f + 0.03f * s : 0.985f + 0.015f * s;
		float cx = this.width / 2f, cy = this.height / 2f;
		GlStateManager.translate(cx, cy + (closingAt != 0 ? (1f - s) * 4f : 0f), 0f);
		GlStateManager.scale(k, k, 1f);
		GlStateManager.translate(-cx, -cy, 0f);
	}

	// ==================== the cards ====================

	/** One navigation card: icon, name, number key. */
	private final class DoorTile extends W {
		final Door d;
		final int index;
		/** 0..1 eased: hovered or picked with the arrow keys. */
		float sel;

		DoorTile(Door d, int index) {
			this.d = d;
			this.index = index;
		}

		@Override
		boolean clickSound() {
			return false; // open() plays it
		}

		@Override
		void draw(int mx, int my) {
			float in = cardIn(index);
			if (in <= 0f) {
				return;
			}
			int acc = Ui.accent();
			sel = ease(sel, hover > 0.5f || keySel == index ? 1f : 0f, 14f);
			float h1 = Math.max(hover, sel);
			float press = 0f;
			if (pressed == this) {
				float t = (System.currentTimeMillis() - pressedAt) / (float) PRESS_MS;
				press = (float) Math.sin(clamp01(t) * Math.PI);
			}
			float scale = (0.92f + 0.08f * in) * (1f + 0.035f * h1 - 0.05f * press);
			float lift = h1 * 2f + (1f - in) * 6f;
			float keep = Draw.fade;
			Draw.fade = keep * in;

			GlStateManager.pushMatrix();
			GlStateManager.translate(x + w / 2f, y + h / 2f - lift, 0f);
			GlStateManager.scale(scale, scale, 1f);
			GlStateManager.translate(-w / 2f, -h / 2f, 0f);

			// shadow, deeper when lifted
			Ui.round(0, 2 + Math.round(h1 * 2), w, h, Ui.alpha(0xFF000000, 0x46 + Math.round(0x28 * h1)));
			// body: a dark glass that brightens a little toward the accent
			int top = Ui.mix(0xD9141B23, 0xE81A2530, h1);
			int bot = Ui.mix(0xD90B1016, 0xE80E161E, h1);
			PlebTheme.gradientRound(0, 0, w, h, top, bot);
			if (h1 > 0.01f) {
				PlebTheme.glow(w / 2f, h * 0.42f, w * 0.62f, Draw.alpha(Ui.alpha(acc, Math.round(0x2A * h1)), Draw.fade));
			}
			// border: hairline at rest; the accent runs out from the middle of the top edge on hover
			Ui.outline(0, 0, w, h, Ui.mix(0x24FFFFFF, Ui.alpha(acc, 0x90), h1));
			Ui.rect(2, 1, w - 4, 1, Ui.alpha(0xFFFFFFFF, 0x0E + Math.round(0x0A * h1)));
			int run = Math.round((w - 2) * h1);
			if (run > 1) {
				Ui.rect(w / 2 - run / 2, 0, run, 1, Ui.mix(acc, 0xFFFFFFFF, 0.25f));
			}
			if (press > 0f) {
				Ui.outline(0, 0, w, h, Ui.alpha(0xFFFFFFFF, Math.round(0x90 * press)));
			}

			// icon: 11x11, the same size on every card (scaled with the card); grows a touch and brightens
			int labelY = h - S3 - 2;
			float icy = (labelY - S1) / 2f + 1f;
			float ik = Math.max(1f, Math.min(2f, w / 34f)) * (1f + 0.08f * h1 + 0.06f * press);
			GlStateManager.pushMatrix();
			GlStateManager.translate(w / 2f, icy - h1, 0f);
			GlStateManager.scale(ik, ik, 1f);
			GlStateManager.translate(-5.5f, -5.5f, 0f);
			Ui.icon(d.icon, 0, 0, 1, Ui.mix(0xFF8E9DA9, acc, h1));
			GlStateManager.popMatrix();

			// label: shrinks a little instead of cutting off on narrow cards
			int lc = Ui.mix(0xFFB9C4CC, Ui.TEXT, h1);
			int lw = Ui.width(d.label);
			if (lw <= w - 4) {
				Ui.centered(d.label, w / 2, labelY, lc);
			} else {
				float ls = (w - 4) / (float) lw;
				GlStateManager.pushMatrix();
				GlStateManager.translate(w / 2f - lw * ls / 2f, labelY + (1f - ls) * 4f, 0f);
				GlStateManager.scale(ls, ls, 1f);
				Ui.text(d.label, 0, 0, lc);
				GlStateManager.popMatrix();
			}
			// number key, quiet, top right
			if (index < 9) {
				String k = Integer.toString(index + 1);
				Ui.text(k, w - Ui.width(k) - S1 + 1, S1 - 1, Ui.mix(Ui.alpha(Ui.FAINT, 0x90), acc, h1));
			}
			GlStateManager.popMatrix();
			Draw.fade = keep;
		}

		@Override
		void click(int mx, int my, int button) {
			if (button == 0) {
				open(this);
			}
		}
	}

	/** Resume: an outlined accent button with a play mark that nudges forward on hover. */
	private final class ResumeButton extends W {
		@Override
		void draw(int mx, int my) {
			int acc = Ui.accent();
			float h1 = hover;
			Ui.round(x, y + 2, w, h, Ui.alpha(0xFF000000, 0x40));
			Ui.round(x, y, w, h, Ui.mix(Ui.alpha(acc, 0x16), Ui.alpha(acc, 0x34), h1));
			Ui.outline(x, y, w, h, Ui.mix(Ui.alpha(acc, 0x60), Ui.alpha(acc, 0xE0), h1));
			int run = Math.round((w - 2) * h1);
			if (run > 1) {
				Ui.rect(x + w / 2 - run / 2, y, run, 1, Ui.mix(acc, 0xFFFFFFFF, 0.3f));
			}
			String s = "Resume";
			int cw = 7 + S1 + Ui.width(s);
			int cx = x + (w - cw) / 2;
			int ty = y + (h - 8) / 2;
			Ui.icon("play", cx + Math.round(h1 * 2f) - 1, ty - 1 + 1, 1, Ui.mix(acc, 0xFFFFFFFF, 0.2f * h1));
			Ui.text(s, cx + 7 + S1 + Math.round(h1), ty, Ui.mix(0xFFDDE7EC, 0xFFFFFFFF, h1));
		}

		@Override
		void click(int mx, int my, int button) {
			if (button == 0) {
				back();
			}
		}
	}

	// ==================== everything else ====================

	@Override
	protected void drawContent(int mx, int my, float pt) {
		int acc = Ui.accent();
		int ch = cardH();
		int y = rowY();
		int t0 = top();

		// --- branding: Plebio + wordmark on one line, the address under it ---
		String word = ClientInfo.NAME.toUpperCase();
		String a1 = word.startsWith("PLEB") ? "PLEB" : word;
		String a2 = word.startsWith("PLEB") ? word.substring(4) : "";
		float sc = this.height >= 260 ? 2f : 1.5f;
		int ww = Math.round(Ui.width(word) * sc);
		int wh = Math.round(7 * sc);
		int head = wh + 4;
		int full = head + S2 + ww;
		int bx = this.width / 2 - full / 2;
		int wy = t0 + 1;
		PlebTheme.glow(this.width / 2f, wy + wh / 2f, full * 0.6f, Draw.alpha(Ui.alpha(acc, 0x1C), Draw.fade));
		Ui.mascot(bx, wy - 2, head, Draw.fade);
		int wx = bx + head + S2;
		Ui.big(a1, wx, wy, sc, Ui.TEXT, true);
		if (!a2.isEmpty()) {
			Ui.big(a2, wx + Math.round(Ui.width(a1) * sc), wy, sc, acc, true);
		}
		// the address, spaced out between two short accent rules
		String addr = spaced("JAVA EDITION");
		int aw = Ui.width(addr);
		int ay = wy + head + S1 + 1;
		Ui.centered(addr, this.width / 2, ay, Ui.alpha(Ui.MUTED, 0xE0));
		int rl = 18;
		Ui.rect(this.width / 2 - aw / 2 - S2 - rl, ay + 3, rl, 1, Ui.alpha(acc, 0x70));
		Ui.rect(this.width / 2 + aw / 2 + S2, ay + 3, rl, 1, Ui.alpha(acc, 0x70));

		// --- the selection bar: one accent line that slides to the card in focus ---
		int focus = -1;
		for (DoorTile t : cards) {
			if (t.hover > 0.5f) {
				focus = t.index;
			}
		}
		if (focus < 0) {
			focus = keySel;
		}
		if (focus >= 0) {
			barSlot = barSlot < 0 || !anim() ? focus : ease(barSlot, focus, 18f);
		}
		barShow = ease(barShow, focus >= 0 ? 1f : 0f, 12f);
		if (barShow > 0.02f && barSlot >= 0 && !cards.isEmpty()) {
			int cw = cardW();
			float bxc = rowX() + barSlot * (cw + GAP) + cw / 2f;
			int bw = Math.round((cw - 2 * S3) * barShow);
			int by = y + ch + S1 + 1;
			Ui.rect(Math.round(bxc - bw / 2f), by, bw, 2, Ui.alpha(acc, Math.round(0xE0 * barShow)));
			Ui.rect(Math.round(bxc - bw / 2f) - 2, by, 2, 2, Ui.alpha(acc, Math.round(0x40 * barShow)));
			Ui.rect(Math.round(bxc + bw / 2f), by, 2, 2, Ui.alpha(acc, Math.round(0x40 * barShow)));
		}

		// --- player info: one quiet line, segments split by hairlines ---
		int iy = y + ch + S3;
		boolean in = ClientSession.signedIn();
		String role = Cosmetics.account().role;
		String name = in ? ClientSession.name() : "Not signed in";
		String rank = in && !role.isEmpty() && !"player".equals(role) ? cap(role) : null;
		long coinsN = Cosmetics.coins();
		String coins = coinsN >= 0 ? Ui.num(coinsN) : null;
		String cur = Cosmetics.currency();

		int segName = 6 + S1 + Ui.width(name) + (rank == null ? 0 : S1 + Ui.width(rank));
		int segCoins = coins == null ? 0 : 7 + S1 + Ui.width(coins) + S1 + Ui.width(cur);
		int div = S3 * 2 + 1;
		int total = segName + (coins == null ? 0 : div + segCoins);
		int pad = S3;
		int ix = this.width / 2 - total / 2;
		Ui.round(ix - pad, iy, total + 2 * pad, INFO_H, 0x8C080C11);
		Ui.outline(ix - pad, iy, total + 2 * pad, INFO_H, 0x14FFFFFF);
		int ty = iy + (INFO_H - 8) / 2;

		int sx = ix;
		tooltipArea(sx, iy, segName, INFO_H, in ? "Signed in to your PlebMc account" : "Sign in from " + signInKey());
		Ui.round(sx, ty + 2, 4, 4, in ? Ui.GOOD : Ui.FAINT);
		if (in) {
			Ui.round(sx - 1, ty + 1, 6, 6, Ui.alpha(Ui.GOOD, 0x30));
		}
		sx += 6 + S1;
		sx += Ui.text(name, sx, ty, in ? Ui.TEXT : Ui.MUTED);
		if (rank != null) {
			sx += S1;
			sx += Ui.text(rank, sx, ty, Ui.mix(acc, Ui.MUTED, 0.35f));
		}

		coinW = 0;
		if (coins != null) {
			sx = divider(sx, iy);
			boolean over = mx >= sx - S2 && mx < sx + segCoins + S2 && my >= iy && my < iy + INFO_H;
			long ft = System.currentTimeMillis() - flipAt;
			if (ft < 700) {
				// the coin flips: squash it sideways and hop it up
				float k = ft / 700f;
				float squash = Math.abs((float) Math.cos(k * Math.PI * 4));
				int hop = Math.round((float) Math.sin(k * Math.PI) * 5);
				int cwd = Math.max(1, Math.round(7 * squash));
				Ui.round(sx + (7 - cwd) / 2, ty - hop, cwd, 7, Ui.GOLD);
			} else {
				Ui.icon("coin", sx, ty, 1, over ? Ui.mix(Ui.GOLD, 0xFFFFFFFF, 0.3f) : Ui.GOLD);
			}
			coinX = sx - S2;
			coinY = iy;
			coinW = segCoins + 2 * S2;
			sx += 7 + S1;
			sx += Ui.text(coins, sx, ty, Ui.GOLD);
			sx += S1;
			sx += Ui.text(cur, sx, ty, Ui.FAINT);
		}

		// --- shortcuts, as small key caps under Resume ---
		int hy = y + ch + S3 + INFO_H + sv() + RESUME_H + S3;
		int n = Math.min(9, doors.size());
		String k1 = "Right Shift", l1 = "close", k2 = "1-" + n, l2 = "open";
		int w1 = keyCap(k1, 0, 0, false) + S1 + Ui.width(l1);
		int w2 = keyCap(k2, 0, 0, false) + S1 + Ui.width(l2);
		int hx = this.width / 2 - (w1 + S4 + w2) / 2;
		hx += keyCap(k1, hx, hy, true) + S1;
		hx += Ui.text(l1, hx, hy, Ui.alpha(Ui.FAINT, 0xE0)) + S4;
		hx += keyCap(k2, hx, hy, true) + S1;
		Ui.text(l2, hx, hy, Ui.alpha(Ui.FAINT, 0xE0));
	}

	/** A hairline between info segments; returns where the next one starts. */
	private int divider(int sx, int iy) {
		Ui.rect(sx + S3, iy + S1, 1, INFO_H - 2 * S1, 0x22FFFFFF);
		return sx + S3 * 2 + 1;
	}

	/** A little key cap; returns its width. Draws only when {@code draw}. */
	private int keyCap(String k, int x, int y, boolean draw) {
		int w = Ui.width(k) + 6;
		if (draw) {
			Ui.round(x, y - 2, w, 12, 0x1CFFFFFF);
			Ui.rect(x + 1, y + 9, w - 2, 1, 0x40000000);
			Ui.text(k, x + 3, y, Ui.alpha(Ui.MUTED, 0xD0));
		}
		return w;
	}

	private static String spaced(String s) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			if (i > 0) {
				b.append(' ');
			}
			b.append(s.charAt(i));
		}
		return b.toString();
	}

	private int coinX, coinY, coinW;
	private long flipAt;

	@Override
	protected void clicked(int mx, int my, int button) {
		if (button == 0 && coinW > 0 && mx >= coinX && mx < coinX + coinW && my >= coinY && my < coinY + INFO_H
				&& System.currentTimeMillis() - flipAt > 700) {
			flipAt = System.currentTimeMillis();
			click();
			EasterEggs.coinFlip();
		}
	}

	private static String cap(String s) {
		return s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1);
	}

	private String signInKey() {
		for (int i = 0; i < doors.size(); i++) {
			if ("nav_lock".equals(doors.get(i).icon) || "nav_profile".equals(doors.get(i).icon)) {
				return "Sign in (" + (i + 1) + ")";
			}
		}
		return "Sign in";
	}

	/** Arrow keys and Tab move the selection; Enter or Space opens it. */
	private static final int KEY_LEFT = 203, KEY_RIGHT = 205, KEY_TAB = 15, KEY_ENTER = 28, KEY_SPACE = 57,
			KEY_NUMPAD_ENTER = 156;

	@Override
	protected void key(char c, int key) {
		if (closingAt != 0) {
			return;
		}
		if (key == PlebClient.KEY_RSHIFT) {
			back();
			return;
		}
		int n = cards.size();
		if (n > 0 && (key == KEY_LEFT || key == KEY_RIGHT || key == KEY_TAB)) {
			int d = key == KEY_LEFT ? -1 : 1;
			keySel = keySel < 0 ? (d > 0 ? 0 : n - 1) : (keySel + d + n) % n;
			return;
		}
		if ((key == KEY_ENTER || key == KEY_NUMPAD_ENTER || key == KEY_SPACE) && keySel >= 0 && keySel < n) {
			open(cards.get(keySel));
			return;
		}
		if (c >= '1' && c <= '9') {
			int i = c - '1';
			if (i < n) {
				keySel = i;
				open(cards.get(i));
			}
		}
	}

	@Override
	protected void mouseClicked(int mx, int my, int button) {
		if (closingAt != 0 || pressed != null) {
			return;
		}
		keySel = -1;
		super.mouseClicked(mx, my, button);
	}
}
