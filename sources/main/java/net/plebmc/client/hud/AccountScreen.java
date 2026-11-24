package net.plebmc.client.hud;

import net.lax1dude.eaglercraft.v1_8.EagRuntime;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.plebmc.client.cosmetics.Account;
import net.plebmc.client.cosmetics.Cosmetics;

/**
 * Log in with a plebmc.top account. The website can't take passwords from
 * the game, so the player links once with a code from the website's
 * Connections page; the server remembers it from then on.
 */
public final class AccountScreen extends GuiScreen {

	private static final int ID_LOGIN = 1, ID_SITE = 2, ID_DONE = 3;

	private final GuiScreen parent;
	private GuiTextField code;
	private int seen = -1;
	private int ticks;

	public AccountScreen(GuiScreen parent) {
		this.parent = parent;
	}

	@Override
	public void initGui() {
		Account a = Cosmetics.account();
		seen = a.version;
		String keep = code == null ? "" : code.getText();
		this.buttonList.clear();
		int half = this.width / 2;
		int y = this.height / 2 + 10;
		code = null;
		if (Cosmetics.serverSupported() && a.enabled && !a.linked) {
			code = new GuiTextField(0, this.fontRendererObj, half - 100, y, 140, 20);
			code.setMaxStringLength(64);
			code.setText(keep);
			code.setFocused(true);
			GuiButton login = new GuiButton(ID_LOGIN, half + 44, y, 56, 20, a.pending ? "..." : "Log in");
			login.enabled = !a.pending;
			this.buttonList.add(login);
		}
		String site = a.linked ? "Open plebmc.top" : "Get my code (website)";
		GuiButton open = new GuiButton(ID_SITE, half - 100, y + 26, 200, 20, site);
		open.enabled = !a.linkUrl.isEmpty();
		this.buttonList.add(open);
		this.buttonList.add(new GuiButton(ID_DONE, half - 100, this.height - 28, 200, 20, "Done"));
		if (Cosmetics.serverSupported() && !a.known) {
			Cosmetics.requestAccount();
		}
	}

	@Override
	protected void actionPerformed(GuiButton b) {
		Account a = Cosmetics.account();
		if (b.id == ID_LOGIN && code != null) {
			submit();
		} else if (b.id == ID_SITE) {
			EagRuntime.openLink(a.linked && !a.site.isEmpty() ? a.site : a.linkUrl);
		} else if (b.id == ID_DONE) {
			this.mc.displayGuiScreen(parent);
		}
	}

	private void submit() {
		String c = code.getText().trim();
		if (!c.isEmpty()) {
			Cosmetics.link(c);
			this.initGui();
		}
	}

	@Override
	public void updateScreen() {
		ticks++;
		if (code != null) {
			code.updateCursorCounter();
		}
		if (Cosmetics.account().version != seen) {
			this.initGui();
		}
	}

	@Override
	protected void keyTyped(char c, int key) {
		if (key == 1) {
			this.mc.displayGuiScreen(parent);
			return;
		}
		if (code != null) {
			if (key == 28 || key == 156) { // Enter
				submit();
				return;
			}
			code.textboxKeyTyped(c, key);
		}
	}

	@Override
	protected void mouseClicked(int x, int y, int button) {
		super.mouseClicked(x, y, button);
		if (code != null) {
			code.mouseClicked(x, y, button);
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawDefaultBackground();
		Account a = Cosmetics.account();
		int half = this.width / 2;
		int y = this.height / 2 - 70;
		this.drawCenteredString(this.fontRendererObj, "§b§lplebmc.top Account", half, y, 0xFFFFFF);
		y += 18;
		if (!Cosmetics.serverSupported() || (!a.known && ticks > 100)) {
			line("Join PlebMc to log in with your website account.", y, 0xAAAAAA);
		} else if (!a.known) {
			line("Checking your account...", y, 0xAAAAAA);
		} else if (!a.enabled) {
			line("Website login isn't set up on this server yet.", y, 0xAAAAAA);
		} else if (a.linked) {
			line("Logged in as §b" + a.displayName + (a.username.equals(a.displayName) ? "" : " §7(" + a.username + ")"), y, 0xFFFFFF);
			y += 12;
			if (!a.groups.isEmpty()) {
				line("Rank: §e" + String.join("§7, §e", a.groups), y, 0xAAAAAA);
				y += 12;
			}
			line("§8You stay logged in on every join. To log out, unlink", y + 6, 0xFFFFFF);
			line("§8Minecraft under Connections on the website.", y + 16, 0xFFFFFF);
		} else {
			line("1. Log in on §bplebmc.top§7 with your email and password", y, 0xAAAAAA);
			line("2. Go to Account > Connections > Minecraft > Link", y + 11, 0xAAAAAA);
			line("3. Type the code it shows here (just the code)", y + 22, 0xAAAAAA);
		}
		if (a.message != null) {
			line((a.messageOk ? "§a" : "§c") + a.message, this.height / 2 + 60, 0xFFFFFF);
		}
		if (code != null) {
			code.drawTextBox();
			if (code.getText().isEmpty() && !code.isFocused()) {
				this.fontRendererObj.drawStringWithShadow("§8code", half - 96, this.height / 2 + 16, 0xFFFFFF);
			}
		}
		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	private void line(String s, int y, int color) {
		this.drawCenteredString(this.fontRendererObj, Draw.fit(s, this.width - 10), this.width / 2, y, color);
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
