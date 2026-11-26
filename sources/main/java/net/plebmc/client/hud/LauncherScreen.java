package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.plebmc.client.cosmetics.ClientSession;
import net.plebmc.client.cosmetics.Cosmetics;
import net.plebmc.client.mods.ClientInfo;
import net.plebmc.client.mods.ClientPrefs;

/**
 * Right Shift: the PlebClient launcher. A row of doors to the client's own
 * screens with number keys as shortcuts, the wordmark with Plebio above it,
 * and a small status strip under it.
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
		doors.add(new Door("HUD", "hud", "Move, resize and recolor your HUD", () -> new HudEditorScreen(this)));
		doors.add(new Door("Video", "performance", "Render distance, graphics and performance", () -> new VideoOptionsScreen(this)));
		signedInShown = ClientSession.signedIn();
		doors.add(new Door(signedInShown ? "Account" : "Sign in", signedInShown ? "account" : "lock",
				"Your PlebMc account: sign in so nobody else can spend your coins", () -> new SignInScreen(this)));
		doors.add(new Door("Settings", "nav_options", "Client preferences", () -> new SettingsScreen(this)));
		if (ClientPrefs.get().developer) {
			doors.add(new Door("Debug", "debug", "Apollo diagnostics (developer)", () -> new ApolloDebugScreen(this)));
		}
		adminShown = Cosmetics.account().admin;
		if (adminShown) {
			doors.add(new Door("Admin", "shield", "Players, coins, cosmetics, servers and announcements (Owners)",
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

	private static final int TILE = 60;
	private static final int GAP = 6;

	private int tile() {
		int n = doors.size();
		return Math.max(30, Math.min(TILE, (this.width - 24 - (n - 1) * GAP) / n));
	}

	private int rowY() {
		return this.height / 2 - tile() / 2 + 8;
	}

	@Override
	protected void build() {
		int n = doors.size();
		int tile = tile();
		int total = n * tile + (n - 1) * GAP;
		int x0 = (this.width - total) / 2;
		int y = rowY();
		for (int i = 0; i < n; i++) {
			DoorTile t = new DoorTile(doors.get(i), i + 1);
			t.at(x0 + i * (tile + GAP), y, tile, tile);
			t.tip = doors.get(i).tip;
			widgets.add(t);
		}
		Button close = button("Resume", this.width / 2 - 44, y + tile + 36, 88, 18, this::back);
		close.style(Style.GHOST).icon("play");
	}

	/** One big door: icon, name, number key. */
	private final class DoorTile extends W {
		final Door d;
		final int key;

		DoorTile(Door d, int key) {
			this.d = d;
			this.key = key;
		}

		@Override
		void draw(int mx, int my) {
			int acc = Ui.accent();
			int lift = Math.round(hover * 3);
			int ty = y - lift;
			// soft shadow that grows as the tile lifts
			Ui.round(x + 1, y + 3, w - 2, h, Ui.alpha(0xFF000000, 0x40 + Math.round(0x30 * hover)));
			if (hover > 0.01f) {
				Ui.round(x - 2, ty - 2, w + 4, h + 4, Ui.alpha(acc, Math.round(0x30 * hover)));
			}
			PlebTheme.gradientRound(x, ty, w, h, Ui.mix(0xE8151E28, 0xF21D2A37, hover), Ui.mix(0xE80A0E14, 0xF2101922, hover));
			Ui.outline(x, ty, w, h, Ui.mix(Ui.BORDER_SOFT, acc, hover));
			// a thin glassy highlight along the top
			Ui.rect(x + 2, ty + 1, w - 4, 1, Ui.alpha(0xFFFFFFFF, 0x10 + Math.round(0x10 * hover)));
			int px = w >= 52 ? 2 : 1;
			int is = 9 * px;
			int iy = ty + Math.max(6, (h - 12 - is) / 2 - 2);
			if (hover > 0.01f) {
				PlebTheme.glow(x + w / 2f, iy + is / 2f, is * 1.2f, Ui.alpha(acc, Math.round(0x48 * hover)));
			}
			Ui.icon(d.icon, x + (w - is) / 2, iy, px, Ui.mix(0xFFAEB9C2, acc, 0.2f + 0.8f * hover));
			int bw = Math.round((w - 12) * hover);
			if (bw > 0) {
				Ui.rect(x + w / 2 - bw / 2, ty + h - 2, bw, 1, acc);
			}
			String label = Ui.fit(d.label, w - 4);
			Ui.centered(label, x + w / 2, ty + h - 14, Ui.mix(0xFFC9D3DA, Ui.TEXT, hover));
			// the number key, in a little chip
			String k = Integer.toString(key);
			Ui.round(x + 3, ty + 3, Ui.width(k) + 5, 10, Ui.alpha(0xFF000000, 0x50));
			Ui.text(k, x + 6, ty + 4, Ui.mix(Ui.FAINT, acc, hover));
		}

		@Override
		void click(int mx, int my, int button) {
			if (button == 0) {
				mc.displayGuiScreen(d.open.get());
			}
		}
	}

	@Override
	protected void drawContent(int mx, int my, float pt) {
		int acc = Ui.accent();
		int tile = tile();
		int y = rowY();
		// darken the middle of the screen a little so it reads over a bright world
		Ui.gradient(0, 0, this.width, this.height / 2, 0x40000000, 0x80000000);
		Ui.gradient(0, this.height / 2, this.width, this.height - this.height / 2, 0x80000000, 0x40000000);

		// Plebio and the wordmark: PLEB in white, the rest in the accent
		String word = ClientInfo.NAME.toUpperCase();
		String a1 = word.startsWith("PLEB") ? "PLEB" : word;
		String a2 = word.startsWith("PLEB") ? word.substring(4) : "";
		float sc = this.height >= 240 ? 2.5f : 2f;
		int ww = Math.round(Ui.width(word) * sc);
		int head = Math.round(9 * sc + 4);
		int full = head + 6 + ww;
		int x0 = this.width / 2 - full / 2;
		int wy = y - Math.round(12 * sc) - 22;
		PlebTheme.glow(this.width / 2f, wy + 10 * sc / 2, full * 0.7f, Ui.alpha(acc, 0x2C));
		Ui.mascot(x0, wy - 2, head, 1f);
		int wx0 = x0 + head + 6;
		Ui.big(a1, wx0, wy, sc, Ui.TEXT, true);
		if (!a2.isEmpty()) {
			Ui.big(a2, wx0 + Math.round(Ui.width(a1) * sc), wy, sc, acc, true);
		}
		int ly = wy + Math.round(9 * sc) + 3;
		for (int i = 0; i < 16; i++) {
			float k = 1f - Math.abs(i - 7.5f) / 8f;
			Ui.rect(wx0 + i * ww / 16, ly, ww / 16 + 1, 1, Ui.alpha(acc, Math.round(0xC0 * k)));
		}
		Ui.text("PLAY.PLEBMC.TOP", wx0, ly + 4, Ui.MUTED);

		// status strip: who, coins, fps, ping
		int sy = y + tile + 12;
		boolean in = ClientSession.signedIn();
		String role = Cosmetics.account().role;
		String who = in ? ClientSession.name() + (role.isEmpty() || "player".equals(role) ? "" : " §8·§7 " + cap(role)) : "Not signed in";
		String coins = Cosmetics.coins() >= 0 ? Ui.num(Cosmetics.coins()) + " " + Cosmetics.currency() : null;
		int fps = Minecraft.getDebugFPS();
		int ping = ping();
		String perf = fps + " FPS" + (ping >= 0 ? " §8·§r " + ping + " ms" : "");
		int w1 = Ui.width(who) + 16;
		int w2 = coins == null ? 0 : Ui.width(coins) + 24;
		int w3 = Ui.width(perf) + 12;
		int total = w1 + (coins == null ? 0 : w2 + 6) + w3 + 6;
		int sx = this.width / 2 - total / 2;
		Ui.card(sx, sy, w1, 16, 0xD0101820, Ui.BORDER_SOFT);
		Ui.round(sx + 5, sy + 6, 4, 4, in ? Ui.GOOD : Ui.FAINT);
		Ui.text(who, sx + 12, sy + 4, in ? Ui.TEXT : Ui.MUTED);
		tooltipArea(sx, sy, w1, 16, in ? "Signed in to your PlebMc account" : "Sign in from " + signInKey());
		sx += w1 + 6;
		coinW = 0;
		if (coins != null) {
			boolean over = mx >= sx && mx < sx + w2 && my >= sy && my < sy + 16;
			Ui.card(sx, sy, w2, 16, 0xD0101820, Ui.alpha(Ui.GOLD, over ? 0xC0 : 0x70));
			long ft = System.currentTimeMillis() - flipAt;
			if (ft < 700) {
				// the coin flips: squash it sideways and hop it up
				float k = ft / 700f;
				float squash = Math.abs((float) Math.cos(k * Math.PI * 4));
				int hop = Math.round((float) Math.sin(k * Math.PI) * 6);
				int cw = Math.max(1, Math.round(7 * squash));
				Ui.round(sx + 5 + (7 - cw) / 2, sy + 4 - hop, cw, 7, Ui.GOLD);
			} else {
				Ui.icon("coin", sx + 5, sy + 4, 1, Ui.GOLD);
			}
			Ui.text(coins, sx + 17, sy + 4, Ui.GOLD);
			coinX = sx;
			coinY = sy;
			coinW = w2;
			sx += w2 + 6;
		}
		Ui.card(sx, sy, w3, 16, 0xD0101820, Ui.BORDER_SOFT);
		Ui.text(perf, sx + 6, sy + 4, fps >= 60 ? Ui.GOOD : fps >= 30 ? Ui.GOLD : Ui.BAD);

		String hint = "Right Shift to close  §8·§r  1-" + Math.min(9, doors.size()) + " to open";
		Ui.centered(hint, this.width / 2, Math.min(this.height - 12, y + tile + 60), Ui.alpha(Ui.FAINT, 0xB0));
	}

	private int coinX, coinY, coinW;
	private long flipAt;

	@Override
	protected void clicked(int mx, int my, int button) {
		if (button == 0 && coinW > 0 && mx >= coinX && mx < coinX + coinW && my >= coinY && my < coinY + 16
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
			if ("lock".equals(doors.get(i).icon) || "account".equals(doors.get(i).icon)) {
				return "Sign in (" + (i + 1) + ")";
			}
		}
		return "Sign in";
	}

	private int ping() {
		try {
			if (this.mc.isSingleplayer()) {
				return -1;
			}
			NetworkPlayerInfo pi = this.mc.getNetHandler().getPlayerInfo(this.mc.thePlayer.getUniqueID());
			return pi == null ? -1 : pi.getResponseTime();
		} catch (Throwable t) {
			return -1;
		}
	}

	@Override
	protected void key(char c, int key) {
		if (key == PlebClient.KEY_RSHIFT) {
			back();
			return;
		}
		if (c >= '1' && c <= '9') {
			int i = c - '1';
			if (i < doors.size()) {
				click();
				this.mc.displayGuiScreen(doors.get(i).open.get());
			}
		}
	}
}
