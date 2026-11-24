package net.plebmc.client.hud;

import net.plebmc.forge.compat.EagRuntime;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.plebmc.client.cosmetics.Account;
import net.plebmc.client.cosmetics.Cosmetics;

/**
 * Your plebmc.top account. The website can't take passwords from the game,
 * so you link once with a code from the website's Connections page; the
 * server remembers it from then on. Logging out unlinks it on the website.
 */
public final class AccountScreen extends PlebScreen {

	private GuiTextField code;
	private int seen = -1;
	private int ticks;
	private long logoutArmedAt;
	private int wx, wy, ww, wh;

	public AccountScreen(GuiScreen parent) {
		super(parent);
		if (Cosmetics.serverSupported() && !Cosmetics.account().known) {
			Cosmetics.requestAccount();
		}
	}

	private enum State {
		OFFLINE, CHECKING, DISABLED, LINKED, LOGGED_OUT
	}

	private State state() {
		Account a = Cosmetics.account();
		if (!Cosmetics.serverSupported() || (!a.known && ticks > 100)) {
			return State.OFFLINE;
		}
		if (!a.known) {
			return State.CHECKING;
		}
		if (!a.enabled) {
			return State.DISABLED;
		}
		return a.linked ? State.LINKED : State.LOGGED_OUT;
	}

	@Override
	protected void build() {
		Account a = Cosmetics.account();
		seen = a.version;
		ww = Math.min(this.width - 16, 330);
		wh = Math.min(this.height - 16, 230);
		wx = (this.width - ww) / 2;
		wy = (this.height - wh) / 2;
		closeButton(wx + ww - 22, wy + 10);
		int x = wx + 14, w = ww - 28;
		int by = wy + wh - 26;
		State s = state();
		code = null;
		switch (s) {
		case LOGGED_OUT:
			code = field(x + 6, wy + 140, w - 92, 12, 64);
			code.setFocused(true);
			Button login = button(a.pending ? "Checking..." : "Log in", x + w - 74, wy + 136, 74, 18, this::submit);
			login.style(Style.PRIMARY).icon("account").enabled = !a.pending;
			Button site = button("Get my code on plebmc.top", x, by, w, 18, () -> EagRuntime.openLink(a.linkUrl));
			site.icon("link").tip = "Opens " + a.linkUrl;
			break;
		case LINKED:
			Button open = button("Open plebmc.top", x, by, (w - 6) / 2, 18, () -> EagRuntime.openLink(a.site));
			open.icon("link");
			boolean armed = System.currentTimeMillis() - logoutArmedAt < 4000;
			Button out = button(a.pending ? "Logging out..." : armed ? "Click to confirm" : "Log out", x + (w + 6) / 2, by,
					(w - 6) / 2, 18, this::logout);
			out.style(Style.DANGER).icon("logout").enabled = !a.pending;
			out.tip = "Unlinks this Minecraft account from " + host(a.site) + ". You can link again any time.";
			break;
		case OFFLINE:
			if (this.mc == null || this.mc.getCurrentServerData() == null) {
				Button join = button("Join PlebMc", x + w / 2 - 50, by, 100, 18, () -> PlebMenu.joinServer(this));
				join.style(Style.PRIMARY).icon("plebnite");
			}
			break;
		default:
			break;
		}
	}

	private static String host(String url) {
		return url.replaceFirst("^https?://", "").replaceAll("/.*$", "");
	}

	private void submit() {
		if (code == null) {
			return;
		}
		String c = code.getText().trim();
		if (!c.isEmpty()) {
			Cosmetics.link(c);
			rebuild();
		}
	}

	private void logout() {
		long now = System.currentTimeMillis();
		if (now - logoutArmedAt > 4000) {
			logoutArmedAt = now;
			rebuild();
			return;
		}
		logoutArmedAt = 0;
		Cosmetics.logout();
		rebuild();
	}

	@Override
	protected void tick() {
		ticks++;
		Account a = Cosmetics.account();
		if (a.version != seen || ticks == 101 || (logoutArmedAt != 0 && System.currentTimeMillis() - logoutArmedAt > 4000)) {
			if (logoutArmedAt != 0 && System.currentTimeMillis() - logoutArmedAt > 4000) {
				logoutArmedAt = 0;
			}
			rebuild();
		}
	}

	@Override
	protected void key(char c, int key) {
		if (key == PlebClient.KEY_RSHIFT) {
			back();
		}
	}

	@Override
	protected void keyTyped(char c, int key) {
		if (code != null && code.isFocused() && (key == 28 || key == 156)) { // Enter
			submit();
			return;
		}
		super.keyTyped(c, key);
	}

	@Override
	protected void drawContent(int mx, int my, float pt) {
		Account a = Cosmetics.account();
		Ui.window(wx, wy, ww, wh);
		State s = state();
		header(wx, wy, ww, "account", "Account", s == State.LINKED ? "Logged in with " + host(a.site) : host(a.site) + " login", false);
		int x = wx + 14, w = ww - 28;
		int y = wy + 48;
		switch (s) {
		case OFFLINE:
			center("account", "Not connected to PlebMc", "Your website login works on PlebMc. Join it to log in or see your profile.", y);
			break;
		case CHECKING:
			center("clock", "Checking your account...", "Asking " + host(a.site) + " who you are.", y);
			break;
		case DISABLED:
			center("lock", "Website login is off", "This server hasn't set up " + host(a.site) + " login yet.", y);
			break;
		case LINKED:
			profile(a, x, y, w);
			break;
		default:
			steps(a, x, y, w);
			break;
		}
		if (a.message != null && s != State.CHECKING) {
			int my2 = wy + wh - 48;
			Ui.icon(a.messageOk ? "check" : "close", x, my2 + 1, 1, a.messageOk ? Ui.GOOD : Ui.BAD);
			Ui.text(Ui.fit(a.message, w - 14), x + 12, my2 + 2, a.messageOk ? Ui.GOOD : Ui.BAD);
		}
	}

	private void profile(Account a, int x, int y, int w) {
		Ui.card(x, y, w, 64, Ui.RAISED, Ui.BORDER_SOFT);
		int acc = Ui.accent();
		// initials avatar
		String initial = a.displayName.isEmpty() ? "?" : a.displayName.substring(0, 1).toUpperCase();
		Ui.round(x + 8, y + 8, 30, 30, Ui.alpha(acc, 0x40));
		Ui.outline(x + 8, y + 8, 30, 30, acc);
		Ui.big(initial, x + 23 - Ui.width(initial), y + 16, 2f, Ui.TEXT, true);
		Ui.big(Ui.fit(a.displayName, (w - 60) * 2 / 3), x + 46, y + 9, 1.5f, Ui.TEXT, false);
		String sub = a.username.equals(a.displayName) ? "Logged in" : "@" + a.username;
		Ui.text(Ui.fit(sub, w - 60), x + 46, y + 24, Ui.MUTED);
		int px = x + 46;
		for (String g : a.groups) {
			int pw = Ui.pillWidth(g);
			if (px + pw > x + w - 6) {
				break;
			}
			boolean top = g.equals(a.rank());
			Ui.pill(g, px, y + 40, top ? Ui.ON_ACCENT : Ui.TEXT, top ? (a.staff ? Ui.GOLD : acc) : 0xFF0A0E12);
			px += pw + 4;
		}
		if (a.groups.isEmpty()) {
			Ui.text("No ranks yet", x + 46, y + 42, Ui.FAINT);
		}
		y += 72;
		Ui.icon("check", x, y + 1, 1, Ui.GOOD);
		Ui.text(Ui.fit("You're logged in on every join, no code needed.", w - 14), x + 12, y + 2, Ui.MUTED);
		Ui.icon("link", x, y + 13, 1, Ui.FAINT);
		Ui.text(Ui.fit("Linked to this Minecraft account on " + host(a.site), w - 14), x + 12, y + 14, Ui.FAINT);
	}

	private void steps(Account a, int x, int y, int w) {
		Ui.text("Log in with your " + host(a.site) + " account", x, y, Ui.TEXT);
		y += 14;
		String[] st = { "Log in on " + host(a.site) + " with your email and password",
				"Open Account, then Connections, then Link next to Minecraft",
				"Type the code it shows below (just the code)" };
		for (int i = 0; i < st.length; i++) {
			Ui.round(x, y + i * 14, 11, 11, Ui.alpha(Ui.accent(), 0x40));
			Ui.centered(Integer.toString(i + 1), x + 6, y + i * 14 + 2, Ui.accent());
			Ui.text(Ui.fit(st[i], w - 16), x + 16, y + i * 14 + 2, Ui.MUTED);
		}
		// code box
		int fy = wy + 136;
		boolean f = code != null && code.isFocused();
		Ui.card(x, fy, w - 80, 18, 0xFF0D1217, f ? Ui.accent() : Ui.BORDER);
		if (code != null && code.getText().isEmpty() && !f) {
			Ui.text("Code from the website", x + 6, fy + 5, Ui.FAINT);
		}
	}

	private void center(String icon, String title, String text, int y) {
		int cx = wx + ww / 2;
		Ui.icon(icon, cx - 13, y + 6, 3, Ui.alpha(Ui.accent(), 0x90));
		Ui.centered(title, cx, y + 40, Ui.TEXT);
		java.util.List<String> lines = Ui.wrap(text, ww - 60);
		for (int i = 0; i < lines.size(); i++) {
			Ui.centered(lines.get(i), cx, y + 52 + i * 10, Ui.MUTED);
		}
	}
}
