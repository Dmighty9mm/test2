package net.plebmc.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.plebmc.client.mods.MoreMods;
import net.plebmc.client.mods.Passwords;

/**
 * The chat box side of the Password Hider and Password Manager mods (hooked
 * from GuiChat and GuiScreen.sendChatMessage):
 * <ul>
 * <li>what you type after /login, /l, /register or /reg is drawn as stars;</li>
 * <li>those commands stay out of the up-arrow history;</li>
 * <li>with a password saved for the server, "/login" shows a hint and Tab
 * fills the password in;</li>
 * <li>after you log in, a chat line offers to save the password ([Save] runs
 * {@code /plebpass save}, which never leaves the client).</li>
 * </ul>
 */
public final class PlebChat {

	private PlebChat() {
	}

	private static MoreMods.PasswordHider hider() {
		return PlebClient.mods() == null ? null : PlebClient.mods().passwordHider;
	}

	private static MoreMods.PasswordManager manager() {
		return PlebClient.mods() == null ? null : PlebClient.mods().passwordManager;
	}

	/** The server passwords are saved for: its address, or null in singleplayer. */
	static String server() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.isSingleplayer()) {
			return null;
		}
		ServerData sd = mc.getCurrentServerData();
		return sd == null || sd.serverIP == null || sd.serverIP.isEmpty() ? null : MoreMods.Server.strip(sd.serverIP);
	}

	// ==================== hiding ====================

	/**
	 * GuiChat: draws the chat's text box. While a login command is in it, the
	 * box shows stars for a moment (same length, so the cursor and selection
	 * stay put) and gets its real text back right after.
	 */
	public static void drawInput(GuiTextField field) {
		String real = field.getText();
		String shown = real;
		try {
			MoreMods.PasswordHider h = hider();
			if (h != null && h.isActive()) {
				shown = Passwords.mask(real);
			}
		} catch (Throwable t) {
			shown = real;
		}
		if (shown.equals(real)) {
			field.drawTextBox();
			drawHint(field);
			return;
		}
		int cursor = field.getCursorPosition();
		int selection = field.getSelectionEnd();
		try {
			field.setText(shown);
			field.setCursorPosition(cursor);
			field.setSelectionPos(selection);
			field.drawTextBox();
		} finally {
			field.setText(real);
			field.setCursorPosition(cursor);
			field.setSelectionPos(selection);
		}
		drawHint(field);
	}

	/** GuiNewChat.addToSentMessages: false keeps a login command out of the up-arrow history. */
	public static boolean keepInHistory(String message) {
		try {
			MoreMods.PasswordHider h = hider();
			return h == null || !h.skipHistory() || !Passwords.isSecret(message);
		} catch (Throwable t) {
			return true;
		}
	}

	// ==================== filling in ====================

	private static String completion(GuiTextField field) {
		MoreMods.PasswordManager m = manager();
		if (m == null || !m.isActive() || field == null) {
			return null;
		}
		String s = server();
		return s == null ? null : Passwords.complete(field.getText(), s);
	}

	/** GuiChat.keyTyped, Tab: fills in the saved password; true when it did (the key is used up). */
	public static boolean tab(GuiTextField field) {
		try {
			String full = completion(field);
			if (full == null) {
				return false;
			}
			field.setText(full);
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	/** The "press Tab" line over the chat box while a saved password can be filled in. */
	private static void drawHint(GuiTextField field) {
		try {
			if (completion(field) == null) {
				return;
			}
			String key = "Tab";
			String text = " fills in your saved password";
			int x = field.xPosition, y = field.yPosition - 15;
			int kw = Draw.width(key) + 6;
			int w = kw + Draw.width(text) + 5;
			Draw.fill(x - 2, y - 2, w + 2, 13, 0xD0101418);
			Draw.fill(x - 2, y - 2, 1, 13, Ui.accent());
			Draw.fill(x + 1, y, kw, 9, Ui.accent());
			Draw.text(key, x + 4, y + 1, Ui.ON_ACCENT, false);
			Draw.text(text, x + 1 + kw, y + 1, 0xFFE8EEF2, true);
			net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager.color(1f, 1f, 1f, 1f);
		} catch (Throwable t) {
			// the chat box works without the hint
		}
	}

	// ==================== saving ====================

	private static void say(IChatComponent c) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.ingameGUI != null) {
			mc.ingameGUI.getChatGUI().printChatMessage(c);
		}
	}

	private static IChatComponent prefix() {
		ChatComponentText p = new ChatComponentText("[" + net.plebmc.client.mods.ClientInfo.NAME + "] ");
		p.getChatStyle().setColor(EnumChatFormatting.AQUA);
		return p;
	}

	private static void say(String text) {
		ChatComponentText t = new ChatComponentText(text);
		t.getChatStyle().setColor(EnumChatFormatting.GRAY);
		say(prefix().appendSibling(t));
	}

	private static IChatComponent button(String label, String command, EnumChatFormatting color) {
		ChatComponentText b = new ChatComponentText(label);
		b.getChatStyle().setColor(color).setBold(Boolean.TRUE)
				.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
		return b;
	}

	/**
	 * GuiScreen.sendChatMessage, before anything is sent. True when the message
	 * was one of the client's own password commands and must not go to the
	 * server. A login or register command goes on as usual, and the client
	 * offers to remember its password.
	 */
	public static boolean outgoing(String message) {
		try {
			if (message == null) {
				return false;
			}
			String m = message.trim();
			String lower = m.toLowerCase(java.util.Locale.ROOT);
			if (lower.equals(Passwords.COMMAND) || lower.startsWith(Passwords.COMMAND + " ")) {
				command(lower.substring(Passwords.COMMAND.length()).trim());
				return true;
			}
			MoreMods.PasswordManager pm = manager();
			String s = server();
			if (pm != null && pm.asks() && s != null && Passwords.sent(m, s, System.currentTimeMillis())) {
				ChatComponentText q = new ChatComponentText(
						(Passwords.saved(s) == null ? "Save this password for " : "Update the saved password for ") + s + "? ");
				q.getChatStyle().setColor(EnumChatFormatting.GRAY);
				ChatComponentText gap = new ChatComponentText(" ");
				say(prefix().appendSibling(q).appendSibling(button("[Save]", Passwords.COMMAND + " save", EnumChatFormatting.GREEN))
						.appendSibling(gap).appendSibling(button("[No]", Passwords.COMMAND + " no", EnumChatFormatting.RED)));
			}
		} catch (Throwable t) {
			// never get between the player and the server
		}
		return false;
	}

	private static void command(String arg) {
		long now = System.currentTimeMillis();
		String here = server();
		if (arg.equals("save")) {
			String s = Passwords.savePending(now);
			if (s == null) {
				say("There is no password to save. Log in with /login first, then press [Save].");
			} else {
				PlebClient.saveSettings();
				say("Saved for " + s + ". Next time type /login and press Tab.");
			}
		} else if (arg.equals("no")) {
			Passwords.clearPending();
			say("Not saved.");
		} else if (arg.equals("forget")) {
			if (here != null && Passwords.forget(here)) {
				PlebClient.saveSettings();
				say("Forgot the password for " + here + ".");
			} else {
				say("No password is saved for this server.");
			}
		} else if (arg.equals("forget all")) {
			Passwords.forgetAll();
			PlebClient.saveSettings();
			say("Forgot every saved password.");
		} else {
			boolean have = here != null && Passwords.saved(here) != null;
			say((here == null ? "Passwords are saved per server."
					: have ? "A password is saved for " + here + "." : "No password is saved for " + here + ".")
					+ " " + Passwords.COMMAND + " save, forget, forget all");
		}
	}
}
