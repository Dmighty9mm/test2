package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.plebmc.client.cosmetics.Account;
import net.plebmc.client.cosmetics.Cosmetics;
import net.plebmc.client.mods.ClientInfo;
import net.plebmc.client.mods.ClientPrefs;

/**
 * Right Shift: the PlebMc launcher. Not a dashboard: a row of doors to the
 * client's own screens (Mods, Shop, Quests, HUD Editor, Account, Settings),
 * with number keys as shortcuts and a small status strip.
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

	public LauncherScreen(GuiScreen parent) {
		super(parent);
		doors.add(new Door("Mods", "mods", "Turn mods on and off and change their settings", () -> new ModsScreen(this)));
		doors.add(new Door("Shop", "shop", "Hats and cloaks for " + Cosmetics.currency(), () -> new ShopScreen(this)));
		doors.add(new Door("Quests", "quests", "Daily and weekly quests for coins", () -> new QuestsScreen(this)));
		doors.add(new Door("HUD", "hud", "Move, resize and recolor your HUD", () -> new HudEditorScreen(this)));
		doors.add(new Door("Sign in", "lock", "Your PlebMc account: sign in so nobody else can spend your coins",
				() -> new SignInScreen(this)));
		doors.add(new Door("Account", "account", "Your plebmc.top login and profile", () -> new AccountScreen(this)));
		doors.add(new Door("Settings", "settings", "Client preferences", () -> new SettingsScreen(this)));
		if (ClientPrefs.get().developer) {
			doors.add(new Door("Debug", "debug", "Apollo diagnostics (developer)", () -> new ApolloDebugScreen(this)));
		}
		adminShown = Cosmetics.account().admin;
		if (adminShown) {
			doors.add(new Door("Admin", "shield", "Players, coins, cosmetics, servers and announcements (Owners)",
					() -> new AdminScreen(this)));
		}
	}

	/** Whether the Admin door is there (it appears when the server says this player is an Owner). */
	private boolean adminShown;

	@Override
	protected void tick() {
		boolean admin = Cosmetics.account().admin;
		if (admin != adminShown) {
			adminShown = admin;
			doors.removeIf(d -> d.icon.equals("shield"));
			if (admin) {
				doors.add(new Door("Admin", "shield", "Players, coins, cosmetics, servers and announcements (Owners)",
						() -> new AdminScreen(this)));
			}
			rebuild();
		}
	}

	private static final int TILE = 58;
	private static final int GAP = 6;

	@Override
	protected void build() {
		int n = doors.size();
		int tile = Math.min(TILE, (this.width - 20 - (n - 1) * GAP) / n);
		int total = n * tile + (n - 1) * GAP;
		int x0 = (this.width - total) / 2;
		int y = this.height / 2 - tile / 2 + 4;
		for (int i = 0; i < n; i++) {
			final Door d = doors.get(i);
			DoorTile t = new DoorTile(d, i + 1);
			t.at(x0 + i * (tile + GAP), y, tile, tile);
			t.tip = d.tip;
			widgets.add(t);
		}
		Button close = button("Close", this.width / 2 - 40, y + tile + 34, 80, 16, this::back);
		close.style(Style.GHOST).icon("close");
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
			Ui.round(x, ty + 2 + lift, w, h, 0x50000000);
			if (hover > 0.01f) {
				Ui.round(x - 2, ty - 2, w + 4, h + 4, Ui.alpha(acc, Math.round(0x38 * hover)));
			}
			PlebTheme.gradientRound(x, ty, w, h, Ui.mix(0xF0141C26, 0xF61E2A38, hover), Ui.mix(0xF0090D13, 0xF6101823, hover));
			Ui.outline(x, ty, w, h, Ui.mix(Ui.BORDER, acc, hover));
			Ui.rect(x + 1, ty, w - 2, 1, Ui.alpha(acc, Math.round(0x40 + 0x80 * hover)));
			int px = w >= 50 ? 3 : 2;
			int is = 9 * px;
			if (hover > 0.01f) {
				PlebTheme.glow(x + w / 2f, ty + 9 + is / 2f, is * 1.1f, Ui.alpha(acc, Math.round(0x50 * hover)));
			}
			Ui.icon(d.icon, x + (w - is) / 2, ty + 9, px, Ui.mix(Ui.MUTED, acc, 0.35f + 0.65f * hover));
			int bw = Math.round((w - 8) * hover);
			if (bw > 0) {
				Ui.rect(x + w / 2 - bw / 2, ty + h - 2, bw, 1, acc);
			}
			Ui.centered(d.label, x + w / 2, ty + h - 17, Ui.mix(Ui.MUTED, Ui.TEXT, 0.5f + 0.5f * hover));
			Ui.text(Integer.toString(key), x + 4, ty + 3, Ui.FAINT);
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
		int n = doors.size();
		int tile = Math.min(TILE, (this.width - 20 - (n - 1) * GAP) / n);
		int y = this.height / 2 - tile / 2 + 4;
		// wordmark: PLEB in white, the rest in the accent
		String word = ClientInfo.NAME.toUpperCase();
		String a1 = word.startsWith("PLEB") ? "PLEB" : word;
		String a2 = word.startsWith("PLEB") ? word.substring(4) : "";
		float sc = 2.5f;
		int ww = Math.round(Ui.width(word) * sc);
		int wx0 = this.width / 2 - ww / 2;
		PlebTheme.glow(this.width / 2f, y - 34, ww * 0.8f, Ui.alpha(acc, 0x30));
		Ui.big(a1, wx0, y - 46, sc, Ui.TEXT, true);
		if (!a2.isEmpty()) {
			Ui.big(a2, wx0 + Math.round(Ui.width(a1) * sc), y - 46, sc, acc, true);
		}
		for (int i = 0; i < 16; i++) {
			float k = 1f - Math.abs(i - 7.5f) / 8f;
			Ui.rect(wx0 + i * ww / 16, y - 20, ww / 16 + 1, 1, Ui.alpha(acc, Math.round(0xC0 * k)));
		}
		Ui.centered("PLAY.PLEBMC.TOP", this.width / 2, y - 16, Ui.MUTED);
		// status strip
		int sy = y + tile + 12;
		StringBuilder status = new StringBuilder();
		Account a = Cosmetics.account();
		String who = a.linked ? a.displayName + (a.rank().isEmpty() ? "" : " §8·§7 " + a.rank()) : "Not logged in";
		String coins = Cosmetics.coins() >= 0 ? Ui.num(Cosmetics.coins()) + " " + Cosmetics.currency() : null;
		status.append(who);
		int w1 = Ui.width(who) + 14;
		int w2 = coins == null ? 0 : Ui.width(coins) + 24;
		int total = w1 + (coins == null ? 0 : w2 + 6);
		int sx = this.width / 2 - total / 2;
		Ui.card(sx, sy, w1, 16, Ui.BASE, Ui.BORDER);
		Ui.round(sx + 4, sy + 6, 4, 4, a.linked ? Ui.GOOD : Ui.FAINT);
		Ui.text(who, sx + 11, sy + 4, a.linked ? Ui.TEXT : Ui.MUTED);
		tooltipArea(sx, sy, w1, 16, a.linked ? "Logged in with plebmc.top" : "Log in from Account (5)");
		if (coins != null) {
			int cx = sx + w1 + 6;
			Ui.card(cx, sy, w2, 16, Ui.BASE, Ui.alpha(Ui.GOLD, 0x60));
			Ui.icon("coin", cx + 5, sy + 4, 1, Ui.GOLD);
			Ui.text(coins, cx + 17, sy + 4, Ui.GOLD);
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
