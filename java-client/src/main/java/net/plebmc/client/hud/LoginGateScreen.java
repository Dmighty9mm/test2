package net.plebmc.client.hud;

import net.plebmc.forge.compat.EagRuntime;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.plebmc.client.cosmetics.ClientSession;

/**
 * The start screen: sign in to your PlebMc account, create one, or play as a
 * guest (the client and its mods, without coins, quests, the shop or
 * cosmetics). A saved sign-in or guest choice skips this screen next time; a
 * guest comes back here from any "Sign in" button.
 */
public final class LoginGateScreen extends PlebScreen {

	private boolean register;
	/** The third tab: play without an account. */
	private boolean guestTab;
	private GuiTextField name;
	private GuiTextField password;
	private GuiTextField again;
	private int seen = -1;
	private int wx, wy, ww, wh;
	/** What a guest can't do, said on screens that send them here. */
	public static final String GUEST_NOTE = "Guests only get the mods. Sign in for coins, quests, the shop and cosmetics.";

	public LoginGateScreen(GuiScreen parent) {
		super(parent);
		register = ClientSession.name().isEmpty();
	}

	/**
	 * The screen for something only accounts have (the shop, quests, the locker):
	 * the screen itself when signed in, the sign-in screen for a guest.
	 */
	public static GuiScreen orSignIn(GuiScreen back, java.util.function.Supplier<GuiScreen> screen) {
		return ClientSession.guest() ? new LoginGateScreen(back) : screen.get();
	}

	private void guest() {
		if (ClientSession.pending) {
			return;
		}
		ClientSession.playAsGuest();
		this.mc.displayGuiScreen(parent);
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
		seen = ClientSession.version;
		ww = Math.min(this.width - 16, 300);
		wh = Math.min(this.height - 16, register ? 236 : 210);
		wx = (this.width - ww) / 2;
		wy = (this.height - wh) / 2;
		int x = wx + 14, w = ww - 28;
		int third = (w - 8) / 3;
		tab("Sign in", "account", Ui.accent(), x, wy + 40, third, 18, () -> !register && !guestTab, () -> switchTo(false, false));
		tab("Create", "check", Ui.accent(), x + third + 4, wy + 40, third, 18, () -> register && !guestTab,
				() -> switchTo(true, false));
		tab("Guest", "nav_mods", Ui.accent(), x + 2 * (third + 4), wy + 40, w - 2 * (third + 4), 18, () -> guestTab,
				() -> switchTo(register, true));
		if (guestTab) {
			name = password = again = null;
			Button play = button(ClientSession.guest() ? "Keep playing as a guest" : "Play as a guest", x + w - 150, wy + wh - 26, 150,
					18, this::guest);
			play.style(Style.PRIMARY).icon("nav_mods").enabled = !ClientSession.pending;
			return;
		}
		boolean keepName = name != null;
		String fixed = ClientSession.fixedName();
		String typed = fixed != null ? fixed : keepName ? name.getText() : ClientSession.name();
		name = field(x + 6, wy + 84, w - 12, 12, 16);
		if (!keepName || name.getText().isEmpty() || fixed != null) {
			name.setText(typed);
		}
		password = passwordField(x + 6, wy + 118, w - 12);
		again = register ? passwordField(x + 6, wy + 152, w - 12) : null;
		if (!name.isFocused() && !password.isFocused() && (again == null || !again.isFocused())) {
			(name.getText().isEmpty() ? name : password).setFocused(true);
		}
		int by = wy + wh - 26;
		boolean busy = ClientSession.pending;
		Button go = button(busy ? "One moment..." : register ? "Create account" : "Sign in", x + w - 120, by, 120, 18,
				this::submit);
		go.style(Style.PRIMARY).icon(register ? "check" : "account").enabled = !busy;
		Button site = button("Account website", x, by, 110, 18, () -> EagRuntime.openLink(ClientSession.panel()));
		site.icon("link").tip = register ? "Your coins and cosmetics, and your password, at " + ClientSession.panel()
				: "Forgot your password? An admin can reset it. " + ClientSession.panel();
	}

	private void switchTo(boolean reg, boolean guest) {
		if (register != reg || guestTab != guest) {
			register = reg;
			guestTab = guest;
			ClientSession.message = null;
			rebuild();
		}
	}

	private void submit() {
		if (guestTab) {
			guest();
			return;
		}
		if (ClientSession.pending) {
			return;
		}
		String n = name.getText().trim();
		String pw = password.getText();
		if (register) {
			if (again != null && !pw.equals(again.getText())) {
				ClientSession.message = "The two passwords don't match.";
				ClientSession.messageOk = false;
				return;
			}
			ClientSession.register(n, pw);
		} else {
			ClientSession.signIn(n, pw);
		}
		rebuild();
	}

	@Override
	protected void tick() {
		if (ClientSession.signedIn() && !ClientSession.pending) {
			// in: on to the title screen (it shows your account's name)
			this.mc.displayGuiScreen(parent);
			return;
		}
		if (ClientSession.version != seen) {
			if (!ClientSession.pending && ClientSession.message != null && !ClientSession.messageOk) {
				if (password != null) {
					password.setText("");
				}
				if (again != null) {
					again.setText("");
				}
			}
			rebuild();
		}
	}

	@Override
	protected void key(char c, int key) {
		// Esc and Right Shift do nothing the first time; a guest who came here to sign in can go back
		if (key == 1 && ClientSession.guest() && !ClientSession.pending) {
			this.mc.displayGuiScreen(parent);
		}
	}

	@Override
	protected void keyTyped(char c, int key) {
		if (key == 1 || key == PlebClient.KEY_RSHIFT) {
			for (GuiTextField f : fields) {
				f.setFocused(false);
			}
			key(c, key);
			return;
		}
		if (key == 28 || key == 156) { // Enter
			submit();
			return;
		}
		if (key == 15 && !guestTab) { // Tab: next field
			GuiTextField[] order = again != null ? new GuiTextField[] { name, password, again }
					: new GuiTextField[] { name, password };
			int at = -1;
			for (int i = 0; i < order.length; i++) {
				if (order[i].isFocused()) {
					at = i;
				}
				order[i].setFocused(false);
			}
			order[(at + 1) % order.length].setFocused(true);
			return;
		}
		super.keyTyped(c, key);
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	@Override
	protected void drawContent(int mx, int my, float pt) {
		Ui.window(wx, wy, ww, wh);
		header(wx, wy, ww, "lock", "PlebMc",
				guestTab ? "Play without an account" : register ? "Make your account to play" : "Sign in to play", false);
		int x = wx + 14, w = ww - 28;
		if (guestTab) {
			int y = wy + 70;
			Ui.text("Use PlebClient for its mods, without an account.", x, y, Ui.TEXT);
			y += 16;
			String[][] rows = { { "check", "Every mod, the HUD editor and all client settings" },
					{ "check", "Your own name and skin, on any server" }, { "close", "No coins, quests or daily rewards" },
					{ "close", "No shop, hats, cloaks or wings" } };
			for (String[] r : rows) {
				boolean yes = "check".equals(r[0]);
				Ui.icon(r[0], x, y, 1, yes ? Ui.GOOD : Ui.BAD);
				Ui.text(Ui.fit(r[1], w - 14), x + 12, y + 1, Ui.MUTED);
				y += 13;
			}
			y += 4;
			for (String line : Ui.wrap("Sign in whenever you like: \"Sign in\" on the title screen, or Right Shift in game.", w)) {
				Ui.text(line, x, y, Ui.FAINT);
				y += 11;
			}
			return;
		}
		label("Minecraft name (you play as this)", x, wy + 68);
		box(name, x, wy + 80, w, register ? "3-16 letters, digits or _" : "Your name");
		label("Password", x, wy + 102);
		box(password, x, wy + 114, w, register ? "8 or more characters" : "Password");
		if (again != null) {
			label("Password again", x, wy + 136);
			box(again, x, wy + 148, w, "Same password");
		}
		int my2 = wy + wh - 46;
		String msg = ClientSession.message;
		if (ClientSession.pending) {
			Ui.icon("clock", x, my2 + 1, 1, Ui.MUTED);
			Ui.text("Talking to the account server...", x + 12, my2 + 2, Ui.MUTED);
		} else if (msg != null) {
			boolean ok = ClientSession.messageOk;
			Ui.icon(ok ? "check" : "close", x, my2 + 1, 1, ok ? Ui.GOOD : Ui.BAD);
			Ui.text(Ui.fit(msg, w - 14), x + 12, my2 + 2, ok ? Ui.GOOD : Ui.BAD);
		} else {
			Ui.text(Ui.fit(ClientSession.guest() ? "Sign in for coins, quests and cosmetics." : "Your account keeps your coins and cosmetics safe.", w), x,
					my2 + 2, Ui.FAINT);
		}
	}

	private void label(String s, int x, int y) {
		Ui.text(s, x, y, Ui.MUTED);
	}

	private void box(GuiTextField f, int x, int fy, int w, String hint) {
		boolean focused = f != null && f.isFocused();
		Ui.card(x, fy, w, 18, 0xFF0D1217, focused ? Ui.accent() : Ui.BORDER);
		if (f != null && f.getText().isEmpty() && !focused) {
			Ui.text(hint, x + 6, fy + 5, Ui.FAINT);
		}
	}
}
