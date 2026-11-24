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
		doors.add(new Door("Shop", "shop", "Hats and trails for " + Cosmetics.currency(), () -> new ShopScreen(this)));
		doors.add(new Door("Quests", "quests", "Daily and weekly quests for coins", () -> new QuestsScreen(this)));
		doors.add(new Door("HUD", "hud", "Move, resize and recolor your HUD", () -> new HudEditorScreen(this)));
		doors.add(new Door("Account", "account", "Your plebmc.top login and profile", () -> new AccountScreen(this)));
		doors.add(new Door("Settings", "settings", "Client preferences", () -> new SettingsScreen(this)));
		if (ClientPrefs.get().developer) {
			doors.add(new Door("Debug", "debug", "Apollo diagnostics (developer)", () -> new ApolloDebugScreen(this)));
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
			Ui.round(x, ty + 2, w, h, 0x50000000);
			Ui.card(x, ty, w, h, Ui.mix(Ui.BASE, Ui.RAISED_2, hover), Ui.mix(Ui.BORDER, acc, hover));
			Ui.rect(x + 1, ty, w - 2, 1, Ui.alpha(acc, Math.round(0x40 + 0x80 * hover)));
			int px = w >= 50 ? 3 : 2;
			int is = 9 * px;
			Ui.icon(d.icon, x + (w - is) / 2, ty + 9, px, Ui.mix(Ui.MUTED, acc, 0.35f + 0.65f * hover));
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
		// wordmark
		String word = ClientInfo.NAME.toUpperCase();
		float sc = 2.5f;
		int ww = Math.round(Ui.width(word) * sc);
		Ui.big(word, this.width / 2 - ww / 2, y - 46, sc, Ui.TEXT, true);
		Ui.rect(this.width / 2 - ww / 2, y - 20, ww, 1, Ui.alpha(acc, 0x90));
		Ui.centered("CLIENT", this.width / 2, y - 16, acc);
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
