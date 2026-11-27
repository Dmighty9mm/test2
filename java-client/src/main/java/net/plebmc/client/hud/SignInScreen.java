package net.plebmc.client.hud;

import net.plebmc.forge.compat.EagRuntime;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.plebmc.client.cosmetics.Cosmetics;
import net.plebmc.client.cosmetics.PlebAccount;

/**
 * Right Shift > Sign in: the PlebMc account that owns your name. On a cracked
 * server anyone can type your name, so the server only lets you spend coins,
 * buy or equip after you sign in here. "Remember me"
 * keeps a sign-in token in this browser so next time it happens by itself.
 */
public final class SignInScreen extends PlebScreen {

	private GuiTextField password;
	private GuiTextField again;
	private boolean remember = true;
	private int seen = -1;
	private int ticks;
	private long signOutArmedAt;
	private int wx, wy, ww, wh;

	public SignInScreen(GuiScreen parent) {
		super(parent);
	}

	private enum State {
		OFFLINE, CHECKING, DISABLED, SIGN_IN, REGISTER, SIGNED_IN, LOCKED
	}

	private State state() {
		PlebAccount a = Cosmetics.plebAccount();
		if (!Cosmetics.serverSupported() || (!a.known && ticks > 100)) {
			return State.OFFLINE;
		}
		if (!a.known || (a.enabled && !a.checked && !a.signedIn)) {
			return State.CHECKING;
		}
		if (!a.enabled) {
			return State.DISABLED;
		}
		if (a.signedIn) {
			return State.SIGNED_IN;
		}
		if (a.locked) {
			return State.LOCKED;
		}
		return a.exists ? State.SIGN_IN : State.REGISTER;
	}

	private GuiTextField passwordField(int x, int y, int w) {
		GuiTextField plain = field(x, y, w, 12, 128);
		PasswordField f = new PasswordField(plain.getId(), this.fontRendererObj, x, y, w, 12);
		f.setMaxStringLength(128);
		f.setEnableBackgroundDrawing(false);
		f.setText(plain.getText());
		f.setFocused(plain.isFocused());
		fields.set(fields.indexOf(plain), f);
		return f;
	}

	@Override
	protected void build() {
		PlebAccount a = Cosmetics.plebAccount();
		seen = a.version;
		ww = Math.min(this.width - 16, 330);
		wh = Math.min(this.height - 16, 230);
		wx = (this.width - ww) / 2;
		wy = (this.height - wh) / 2;
		closeButton(wx + ww - 22, wy + 10);
		int x = wx + 14, w = ww - 28;
		int by = wy + wh - 26;
		State s = state();
		password = again = null;
		switch (s) {
		case SIGN_IN:
		case REGISTER: {
			boolean reg = s == State.REGISTER;
			int fy = wy + (reg ? 104 : 112);
			password = passwordField(x + 6, fy + 4, w - 12);
			if (reg) {
				again = passwordField(x + 6, fy + 30, w - 12);
			}
			if (!password.isFocused() && (again == null || !again.isFocused())) {
				password.setFocused(true);
			}
			toggle("Remember me on this browser", "Signs in by itself next time (never saves the password)", x, by - 26,
					w, 20, () -> remember, () -> remember = !remember);
			Button go = button(a.pending ? "One moment..." : reg ? "Create account" : "Sign in", x + w - 110, by, 110, 18,
					this::submit);
			go.style(Style.PRIMARY).icon("account").enabled = !a.pending;
			if (!a.panel.isEmpty()) {
				Button site = button(reg ? "Make it on the website" : "Forgot password?", x, by, 130, 18,
						() -> EagRuntime.openLink(a.panel));
				site.icon("link").tip = reg ? "Make your account on " + a.panel
						: "An admin can reset it on " + a.panel;
			}
			break;
		}
		case SIGNED_IN: {
			if (!a.panel.isEmpty()) {
				Button open = button("My account", x, by, (w - 6) / 2, 18, () -> EagRuntime.openLink(a.panel));
				open.icon("link").tip = "Coins, cosmetics and password on " + a.panel;
			}
			boolean armed = System.currentTimeMillis() - signOutArmedAt < 4000;
			Button out = button(a.pending ? "Signing out..." : armed ? "Click to confirm" : "Sign out", x + (w + 6) / 2, by,
					(w - 6) / 2, 18, this::signOut);
			out.style(Style.DANGER).icon("logout").enabled = !a.pending;
			out.tip = "Signs out and forgets this browser's saved sign-in.";
			break;
		}
		case OFFLINE:
			if (this.mc == null || this.mc.getCurrentServerData() == null) {
				Button join = button("Join PlebMc", x + (w + 6) / 2, by, (w - 6) / 2, 18, () -> PlebMenu.joinServer(this));
				join.style(Style.PRIMARY).icon("plebnite");
				Button out = button("Sign out", x, by, (w - 6) / 2, 18, () -> {
					net.plebmc.client.cosmetics.ClientSession.signOut();
					this.mc.displayGuiScreen(new LoginGateScreen(new net.minecraft.client.gui.GuiMainMenu()));
				});
				out.style(Style.DANGER).icon("logout").tip = "Sign out of " + net.plebmc.client.cosmetics.ClientSession.name()
						+ " on this client";
			}
			break;
		default:
			break;
		}
	}

	private void submit() {
		if (password == null) {
			return;
		}
		PlebAccount a = Cosmetics.plebAccount();
		String pw = password.getText();
		if (pw.length() < 8) {
			a.message = "Passwords are at least 8 characters.";
			a.messageOk = false;
			return;
		}
		if (again != null) {
			if (!pw.equals(again.getText())) {
				a.message = "The two passwords don't match.";
				a.messageOk = false;
				return;
			}
			Cosmetics.register(pw, remember);
		} else {
			Cosmetics.signIn(pw, remember);
		}
		password.setText("");
		if (again != null) {
			again.setText("");
		}
		rebuild();
	}

	private void signOut() {
		long now = System.currentTimeMillis();
		if (now - signOutArmedAt > 4000) {
			signOutArmedAt = now;
			rebuild();
			return;
		}
		signOutArmedAt = 0;
		Cosmetics.signOut();
		rebuild();
	}

	@Override
	protected void tick() {
		ticks++;
		PlebAccount a = Cosmetics.plebAccount();
		boolean disarm = signOutArmedAt != 0 && System.currentTimeMillis() - signOutArmedAt > 4000;
		if (a.version != seen || ticks == 101 || disarm) {
			if (disarm) {
				signOutArmedAt = 0;
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
		boolean typing = (password != null && password.isFocused()) || (again != null && again.isFocused());
		if (typing && (key == 28 || key == 156)) { // Enter
			submit();
			return;
		}
		if (typing && key == 15 && again != null) { // Tab between the two fields
			boolean first = password.isFocused();
			password.setFocused(!first);
			again.setFocused(first);
			return;
		}
		super.keyTyped(c, key);
	}

	@Override
	protected void drawContent(int mx, int my, float pt) {
		PlebAccount a = Cosmetics.plebAccount();
		Ui.window(wx, wy, ww, wh);
		State s = state();
		header(wx, wy, ww, "lock", "Sign in", s == State.SIGNED_IN ? "Signed in as " + a.name : "Your PlebMc account", false);
		int x = wx + 14, w = ww - 28;
		int y = wy + 48;
		switch (s) {
		case OFFLINE:
			center("account", "Signed in as " + net.plebmc.client.cosmetics.ClientSession.name(),
					"Your coins, shop and quests show up when you join PlebMc.", y);
			break;
		case CHECKING:
			center("clock", "Checking your account...", "Asking the server about " + nameOr("this name") + ".", y);
			break;
		case DISABLED:
			center("lock", "Accounts are off", "This server doesn't use PlebMc accounts.", y);
			break;
		case LOCKED:
			center("lock", "This account is locked", "Ask an admin to unlock " + nameOr("it") + ".", y);
			break;
		case SIGNED_IN:
			signedIn(a, x, y, w);
			break;
		case SIGN_IN:
			Ui.text("Sign in as " + a.name, x, y, Ui.TEXT);
			line("Your coins and cosmetics stay locked until you sign in.", x, y + 14, w);
			long left = a.secondsLeft();
			if (left >= 0) {
				Ui.icon("clock", x, y + 30, 1, Ui.GOLD);
				Ui.text(Ui.fit("Sign in within " + left + "s or the server kicks this name.", w - 14), x + 12, y + 31, Ui.GOLD);
			}
			box(password, x, wy + 112, w, "Password");
			break;
		case REGISTER:
			Ui.text("Make your PlebMc account", x, y, Ui.TEXT);
			line("Then nobody else on your name can spend your coins.", x, y + 14, w);
			line("Pick a password (8+ characters):", x, y + 28, w);
			box(password, x, wy + 104, w, "Password");
			box(again, x, wy + 130, w, "Password again");
			break;
		default:
			break;
		}
		if (a.message != null && s != State.CHECKING && s != State.OFFLINE) {
			int my2 = wy + wh - 72;
			Ui.icon(a.messageOk ? "check" : "close", x, my2 + 1, 1, a.messageOk ? Ui.GOOD : Ui.BAD);
			Ui.text(Ui.fit(a.message, w - 14), x + 12, my2 + 2, a.messageOk ? Ui.GOOD : Ui.BAD);
		}
	}

	private String nameOr(String def) {
		String n = Cosmetics.plebAccount().name;
		return n.isEmpty() ? def : n;
	}

	private void signedIn(PlebAccount a, int x, int y, int w) {
		Ui.card(x, y, w, 52, Ui.RAISED, Ui.BORDER_SOFT);
		int acc = Ui.accent();
		String initial = a.name.isEmpty() ? "?" : a.name.substring(0, 1).toUpperCase();
		Ui.round(x + 8, y + 8, 30, 30, Ui.alpha(acc, 0x40));
		Ui.outline(x + 8, y + 8, 30, 30, acc);
		Ui.big(initial, x + 23 - Ui.width(initial), y + 16, 2f, Ui.TEXT, true);
		Ui.big(Ui.fit(a.name, (w - 60) * 2 / 3), x + 46, y + 9, 1.5f, Ui.TEXT, false);
		Ui.text(a.role.equals("player") ? "Signed in" : "Signed in · " + a.role, x + 46, y + 26,
				a.role.equals("player") ? Ui.MUTED : Ui.GOLD);
		y += 60;
		Ui.icon("check", x, y + 1, 1, Ui.GOOD);
		Ui.text(Ui.fit("You can spend your coins and use the shop.", w - 14), x + 12, y + 2, Ui.MUTED);
		Ui.icon(a.hasSavedToken() ? "check" : "lock", x, y + 13, 1, Ui.FAINT);
		Ui.text(Ui.fit(a.hasSavedToken() ? "This browser signs you in by itself." : "You'll sign in again next time.", w - 14),
				x + 12, y + 14, Ui.FAINT);
	}

	private void line(String s, int x, int y, int w) {
		Ui.text(Ui.fit(s, w), x, y, Ui.MUTED);
	}

	private void box(GuiTextField f, int x, int fy, int w, String hint) {
		boolean focused = f != null && f.isFocused();
		Ui.card(x, fy, w, 18, 0xFF0D1217, focused ? Ui.accent() : Ui.BORDER);
		if (f != null && f.getText().isEmpty() && !focused) {
			Ui.text(hint, x + 6, fy + 5, Ui.FAINT);
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
