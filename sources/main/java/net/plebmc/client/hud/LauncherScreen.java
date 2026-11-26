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
import net.plebmc.client.mods.Mod;

/**
 * Right Shift: the PlebClient menu over the game, in the title screen's
 * style. A sidebar on the left (account, every PlebClient screen with its
 * number key, Resume), your player in the middle wearing what you wear, and
 * on the right a few one-click mods and how the game is running.
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
		doors.add(new Door("HUD editor", "hud", "Move, resize and recolor your HUD", () -> new HudEditorScreen(this)));
		doors.add(new Door("Video settings", "performance", "Render distance, graphics and performance", () -> new VideoOptionsScreen(this)));
		doors.add(new Door("Settings", "nav_options", "Client preferences", () -> new SettingsScreen(this)));
		signedInShown = ClientSession.signedIn();
		doors.add(new Door(signedInShown ? "Account" : "Sign in", "account",
				"Your PlebMc account: sign in so nobody else can spend your coins", () -> new SignInScreen(this)));
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

	// ==================== layout ====================

	private int sw; // sidebar width
	private int navY, rowH;
	private int midX, midW; // the player area
	private int rx, rw; // the right column
	private float indY = -1, indA;
	private float yaw = -25f;
	private boolean dragging;
	private int dragX;
	private float dragYaw;

	@Override
	protected void build() {
		sw = Math.max(128, Math.min(170, this.width * 30 / 100));
		rowH = this.height >= 260 ? 20 : 17;
		navY = 78;
		for (int i = 0; i < doors.size(); i++) {
			NavRow r = new NavRow(doors.get(i), i + 1);
			r.at(8, navY + i * rowH, sw - 16, rowH - 2);
			r.tip = doors.get(i).tip;
			widgets.add(r);
		}
		Button resume = button("Resume", 10, this.height - 30, sw - 20, 18, this::back);
		resume.style(Style.PRIMARY).icon("play");

		// right column: one-click mods
		boolean wide = this.width - sw >= 260;
		rw = wide ? Math.max(100, Math.min(150, (this.width - sw) * 36 / 100)) : 0;
		rx = this.width - rw - 10;
		midX = sw;
		midW = (wide ? rx - 10 : this.width) - sw;
		if (wide) {
			Mods m = new Mods();
			int y = 30;
			for (Mod mod : m.list) {
				Toggle t = toggle(mod.name(), null, rx, y, rw, 18, () -> mod.isEnabled() && !mod.isBlockedByServer(), () -> {
					mod.setEnabled(!mod.isEnabled());
					PlebClient.saveSettings();
				});
				t.enabled = !mod.isBlockedByServer();
				t.tip = mod.description();
				y += 20;
			}
			Button all = button("All mods", rx, y + 2, rw, 16, () -> this.mc.displayGuiScreen(new ModsScreen(this)));
			all.style(Style.GHOST).icon("next");
		}
	}

	/** The mods people flip most, for the right column. */
	private static final class Mods {
		final List<Mod> list = new ArrayList<>();

		Mods() {
			net.plebmc.client.mods.ModRegistry r = PlebClient.mods();
			list.add(r.fpsBoost);
			list.add(r.toggleSprint);
			list.add(r.fullbright);
			list.add(r.keystrokes);
			list.add(r.fps);
			list.add(r.coordinates);
		}
	}

	/** One sidebar entry: icon, name, number key. */
	private final class NavRow extends W {
		final Door d;
		final int key;

		NavRow(Door d, int key) {
			this.d = d;
			this.key = key;
		}

		@Override
		void draw(int mx, int my) {
			int acc = Ui.accent();
			if (hover > 0.01f) {
				Ui.round(x, y, w, h, Ui.alpha(0xFFFFFFFF, Math.round(0x14 * hover)));
			}
			int ix = x + 8 + Math.round(2 * hover);
			Ui.icon(d.icon, ix, y + (h - 9) / 2, 1, Ui.mix(Ui.MUTED, acc, 0.25f + 0.75f * hover));
			Ui.text(d.label, ix + 15, y + (h - 8) / 2, Ui.mix(0xFFC9D3DA, Ui.TEXT, hover));
			String k = Integer.toString(key);
			Ui.text(k, x + w - 6 - Ui.width(k), y + (h - 8) / 2, Ui.alpha(Ui.FAINT, 0xA0 + Math.round(0x5F * hover)));
		}

		@Override
		void click(int mx, int my, int button) {
			if (button == 0) {
				mc.displayGuiScreen(d.open.get());
			}
		}
	}

	// ==================== drawing ====================

	@Override
	protected void drawContent(int mx, int my, float pt) {
		int acc = Ui.accent();

		// a little darker behind the player and the quick mods, so they read over a bright world
		Ui.gradient(sw, 0, this.width - sw, this.height, 0x30000000, 0x70000000);

		// ---- the player, in the middle ----
		if (midW >= 90 && this.mc.thePlayer != null) {
			int cx = midX + midW / 2;
			PlebTheme.glow(cx, this.height * 0.55f, Math.min(midW, this.height) * 0.45f, Ui.alpha(acc, 0x26));
			if (!dragging) {
				yaw += ((-25f + (mx - cx) * 0.08f) - yaw) * (ClientPrefs.get().animations ? 0.08f : 1f);
			}
			int scale = Math.max(24, Math.min(70, (this.height - 90) * 10 / 26));
			int foot = this.height / 2 + scale * 32 / 30;
			// the stand under your feet
			Ui.round(cx - scale * 7 / 10, foot - 2, scale * 14 / 10, 4, Ui.alpha(acc, 0x40));
			Ui.round(cx - scale / 2, foot - 1, scale, 2, Ui.alpha(acc, 0x90));
			ShopScreen.drawModel(cx, foot, scale, yaw, this.mc.thePlayer);
			// name and coins under the stand
			String name = ClientSession.signedIn() ? ClientSession.name() : this.mc.thePlayer.getName();
			int ny = foot + 10;
			int nw = Math.round(Ui.width(name) * 1.5f);
			Ui.big(name, cx - nw / 2, ny, 1.5f, Ui.TEXT, true);
			String role = Cosmetics.account().role;
			int py = ny + 16;
			List<String[]> pills = new ArrayList<>();
			if (!role.isEmpty() && !"player".equals(role)) {
				pills.add(new String[] { role.substring(0, 1).toUpperCase() + role.substring(1), "role" });
			}
			if (Cosmetics.coins() >= 0) {
				pills.add(new String[] { Ui.num(Cosmetics.coins()) + " " + Cosmetics.currency(), "coin" });
			}
			if (!ClientSession.signedIn()) {
				pills.add(new String[] { "Not signed in", "out" });
			}
			int total = -6;
			for (String[] p : pills) {
				total += Ui.width(p[0]) + ("coin".equals(p[1]) ? 22 : 12) + 6;
			}
			int px = cx - total / 2;
			for (String[] p : pills) {
				boolean coin = "coin".equals(p[1]);
				int w = Ui.width(p[0]) + (coin ? 22 : 12);
				Ui.card(px, py, w, 14, 0xD0101820, coin ? Ui.alpha(Ui.GOLD, 0x80) : Ui.BORDER);
				if (coin) {
					Ui.icon("coin", px + 5, py + 3, 1, Ui.GOLD);
				}
				Ui.text(p[0], px + (coin ? 17 : 6), py + 3, coin ? Ui.GOLD : "out".equals(p[1]) ? Ui.MUTED : acc);
				px += w + 6;
			}
			Ui.text("Drag to turn", cx - Ui.width("Drag to turn") / 2, this.height - 14, Ui.alpha(Ui.FAINT, 0x90));
		}

		// ---- the right column: quick mods and how the game is running ----
		if (rw > 0) {
			Ui.text("QUICK MODS", rx + 2, 18, Ui.FAINT);
			int sy = 30 + 6 * 20 + 26;
			if (sy + 70 < this.height) {
				Ui.text("RIGHT NOW", rx + 2, sy, Ui.FAINT);
				int cy = sy + 11;
				Ui.card(rx, cy, rw, 58, 0xC00D1217, Ui.BORDER_SOFT);
				stat(rx + 7, cy + 6, "FPS", Integer.toString(Minecraft.getDebugFPS()), fpsColor(Minecraft.getDebugFPS()));
				int ping = ping();
				stat(rx + 7, cy + 19, "Ping", ping < 0 ? "-" : ping + " ms", ping < 0 ? Ui.MUTED : ping < 80 ? Ui.GOOD : ping < 200 ? Ui.GOLD : Ui.BAD);
				stat(rx + 7, cy + 32, "Server", server(), Ui.TEXT);
				int players = this.mc.getNetHandler() == null ? 0 : this.mc.getNetHandler().getPlayerInfoMap().size();
				stat(rx + 7, cy + 45, "Players", Integer.toString(players), Ui.TEXT);
			}
		}

		// ---- the sidebar ----
		PlebTheme.gradient(0, 0, sw, this.height, 0xF00B1118, 0xF0070B10);
		Ui.rect(sw - 1, 0, 1, this.height, Ui.alpha(acc, 0x60));
		Ui.mascot(10, 10, 22, 1f);
		String word = ClientInfo.NAME;
		String a1 = word.startsWith("Pleb") ? "Pleb" : word, a2 = word.startsWith("Pleb") ? word.substring(4) : "";
		Ui.big(a1, 38, 12, 1.5f, Ui.TEXT, true);
		Ui.big(a2, 38 + Math.round(Ui.width(a1) * 1.5f), 12, 1.5f, acc, true);
		Ui.text(SUBTITLE, 38, 26, Ui.FAINT);
		// the account card
		int ay = 42;
		boolean in = ClientSession.signedIn();
		Ui.card(8, ay, sw - 16, 28, 0xC0101820, Ui.BORDER_SOFT);
		Ui.round(15, ay + 7, 5, 5, in ? Ui.GOOD : Ui.FAINT);
		Ui.text(Ui.fit(in ? ClientSession.name() : "Not signed in", sw - 44), 25, ay + 5, in ? Ui.TEXT : Ui.MUTED);
		String line2 = Cosmetics.coins() >= 0 ? Ui.num(Cosmetics.coins()) + " " + Cosmetics.currency() : in ? "Signed in" : "Press " + signInKey() + " to sign in";
		Ui.text(Ui.fit(line2, sw - 32), 15, ay + 16, Cosmetics.coins() >= 0 ? Ui.GOLD : Ui.FAINT);
		// the sliding indicator on the hovered row
		W top = hovered();
		float target = -1;
		for (W w : widgets) {
			if (w == top && w instanceof NavRow) {
				target = w.y;
			}
		}
		if (target >= 0) {
			indY = indY < 0 || !ClientPrefs.get().animations ? target : ease(indY, target, 22f);
		}
		indA = ease(indA, target >= 0 ? 1f : 0f, 14f);
		if (indY >= 0 && indA > 0.02f) {
			Ui.rect(8, Math.round(indY) + 3, 2, rowH - 8, Ui.alpha(acc, Math.round(255 * indA)));
		}
		Ui.rect(14, navY - 5, sw - 28, 1, Ui.BORDER_SOFT);
		if (navY + doors.size() * rowH + 12 < this.height - 34) {
			Ui.text("Right Shift to close", 12, this.height - 42, Ui.alpha(Ui.FAINT, 0xA0));
		}
	}

	/** Shown under the wordmark. */
	private static final String SUBTITLE = "PLAY.PLEBMC.TOP";

	private String signInKey() {
		for (int i = 0; i < doors.size(); i++) {
			if ("account".equals(doors.get(i).icon)) {
				return Integer.toString(i + 1);
			}
		}
		return "";
	}

	private static void stat(int x, int y, String k, String v, int color) {
		Ui.text(k, x, y, Ui.MUTED);
		Ui.text(v, x + 42, y, color);
	}

	private static int fpsColor(int fps) {
		return fps >= 60 ? Ui.GOOD : fps >= 30 ? Ui.GOLD : Ui.BAD;
	}

	private int ping() {
		try {
			NetworkPlayerInfo pi = this.mc.getNetHandler().getPlayerInfo(this.mc.thePlayer.getUniqueID());
			return pi == null ? -1 : pi.getResponseTime();
		} catch (Throwable t) {
			return -1;
		}
	}

	private String server() {
		try {
			if (this.mc.isSingleplayer()) {
				return "Singleplayer";
			}
			net.minecraft.client.multiplayer.ServerData sd = this.mc.getCurrentServerData();
			return sd == null ? "-" : Ui.fit(sd.serverIP, rw - 56);
		} catch (Throwable t) {
			return "-";
		}
	}

	// ==================== input ====================

	@Override
	protected void clicked(int mx, int my, int button) {
		if (button == 0 && mx > sw && (rw == 0 || mx < rx - 4)) {
			dragging = true;
			dragX = mx;
			dragYaw = yaw;
		}
	}

	@Override
	protected void mouseClickMove(int mx, int my, int button, long held) {
		super.mouseClickMove(mx, my, button, held);
		if (dragging) {
			yaw = dragYaw + (mx - dragX) * 1.6f;
		}
	}

	@Override
	protected void mouseReleased(int mx, int my, int state) {
		super.mouseReleased(mx, my, state);
		dragging = false;
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
