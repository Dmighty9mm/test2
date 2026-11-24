package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.plebmc.client.cosmetics.Admin;
import net.plebmc.client.cosmetics.Cosmetics;

/**
 * The admin menu, for players logged in with a website Owner account: every
 * online player with their server, rank and coins; give, take or set coins;
 * give or take cosmetics; send a player to another server; kick; and
 * network-wide announcements and a config reload. The proxy decides who may
 * open it and checks every action again, so this screen only asks.
 */
public final class AdminScreen extends PlebScreen {

	private int wx, wy, ww, wh;
	private int listX, listY, listW, listH;
	private static final int ROW = 20;
	private final Scroll scroll = new Scroll();
	private GuiTextField search, amount, reason, announce;
	private String selected;
	private int itemIndex;
	private int serverIndex;
	private long kickArmedAt;
	private int seen = -1;
	private int ticks;

	public AdminScreen(GuiScreen parent) {
		super(parent);
		Cosmetics.adminOpen();
	}

	private Admin data() {
		return Cosmetics.admin();
	}

	private List<Admin.Player> filtered() {
		String q = search == null ? "" : search.getText().trim().toLowerCase();
		List<Admin.Player> out = new ArrayList<>();
		for (Admin.Player p : data().players) {
			if (q.isEmpty() || p.name.toLowerCase().contains(q) || p.account.toLowerCase().contains(q)
					|| p.server.toLowerCase().contains(q)) {
				out.add(p);
			}
		}
		return out;
	}

	private Admin.Player current() {
		Admin.Player p = selected == null ? null : data().player(selected);
		if (p == null && !data().players.isEmpty()) {
			p = data().players.get(0);
			selected = p.uuid;
		}
		return p;
	}

	@Override
	protected void build() {
		seen = data().version;
		ww = Math.min(this.width - 12, 430);
		wh = Math.min(this.height - 12, 268);
		wx = (this.width - ww) / 2;
		wy = (this.height - wh) / 2;
		closeButton(wx + ww - 22, wy + 10);
		Button refresh = button("", wx + ww - 42, wy + 10, 16, 16, () -> {
			Cosmetics.adminOpen();
		});
		refresh.icon("reset").style(Style.GHOST).tip("Refresh the player list");

		int top = wy + 44;
		listX = wx + 10;
		listW = Math.max(120, ww * 2 / 5 - 10);
		search = field(listX + 16, top + 4, listW - 20, 10, 32);
		listY = top + 20;
		listH = wy + wh - 58 - listY;

		Admin a = data();
		if (!a.known || !a.allowed) {
			return;
		}
		Admin.Player p = current();
		int px = listX + listW + 10;
		int pw = wx + ww - 10 - px;
		int y = top + 46;
		if (p != null) {
			// coins
			amount = field(px + 6, y + 4, 54, 10, 10);
			int bw = (pw - 72 - 8) / 3;
			button("Give", px + 70, y, bw, 18, () -> coins("give")).style(Style.PRIMARY).tip("Add coins");
			button("Take", px + 70 + bw + 4, y, bw, 18, () -> coins("take")).tip("Remove coins");
			button("Set", px + 70 + (bw + 4) * 2, y, bw, 18, () -> coins("set")).tip("Set the balance");
			y += 24;
			// cosmetics
			if (!a.items.isEmpty()) {
				itemIndex = Math.floorMod(itemIndex, a.items.size());
				Choice c = choice("Cosmetic", null, px, y, pw - 70, 18, () -> {
					Admin.ItemRef it = a.items.get(Math.floorMod(itemIndex, a.items.size()));
					return it.name;
				}, () -> {
					itemIndex++;
					rebuild();
				}, () -> {
					itemIndex--;
					rebuild();
				});
				c.tip = "Click the right side for the next item, the left side (or right-click) for the previous one";
				Admin.ItemRef it = a.items.get(itemIndex);
				boolean owns = p.owned.contains(it.id);
				Button give = button(owns ? "Take" : "Give", px + pw - 66, y, 66, 18,
						() -> Cosmetics.adminAction("item", p.uuid, "op", owns ? "remove" : "give", "id", it.id));
				give.style(owns ? Style.DANGER : Style.PRIMARY).icon(owns ? "close" : "gift");
				give.tip = (owns ? "Take " : "Give ") + it.name + (owns ? " from " : " to ") + p.name;
			}
			y += 24;
			// servers
			if (!a.servers.isEmpty()) {
				serverIndex = Math.floorMod(serverIndex, a.servers.size());
				choice("Server", null, px, y, pw - 70, 18, () -> a.servers.get(Math.floorMod(serverIndex, a.servers.size())),
						() -> {
							serverIndex++;
							rebuild();
						}, () -> {
							serverIndex--;
							rebuild();
						});
				String target = a.servers.get(serverIndex);
				Button send = button("Send", px + pw - 66, y, 66, 18,
						() -> Cosmetics.adminAction("send", p.uuid, "server", target));
				send.icon("next").enabled = !target.equals(p.server);
				send.tip = "Move " + p.name + " to " + target;
			}
			y += 24;
			// kick
			reason = field(px + 6, y + 4, pw - 84, 10, 100);
			boolean armed = System.currentTimeMillis() - kickArmedAt < 4000;
			Button kick = button(armed ? "Confirm" : "Kick", px + pw - 66, y, 66, 18, () -> kick(p));
			kick.style(Style.DANGER).icon("logout");
			kick.tip = "Disconnect " + p.name + " (click twice)";
		}
		// announcement and reload along the bottom
		int by = wy + wh - 30;
		announce = field(listX + 6, by + 5, ww - 20 - 170, 10, 200);
		Button send = button("Announce", wx + ww - 10 - 160 + 4, by, 76, 18, this::announce);
		send.style(Style.PRIMARY).icon("bell").tip = "A popup for everyone on the network (chat for other clients)";
		Button reload = button("Reload", wx + ww - 10 - 78, by, 78, 18, () -> Cosmetics.adminAction("reload", null));
		reload.icon("reset").tip = "Re-read the shop plugin's config.yml (items, hats, quests)";
	}

	private void coins(String op) {
		Admin.Player p = current();
		long n;
		try {
			n = Long.parseLong(amount.getText().trim().replace(",", ""));
		} catch (NumberFormatException e) {
			n = -1;
		}
		if (p == null || n < 0) {
			amount.setFocused(true);
			return;
		}
		Cosmetics.adminAction("coins", p.uuid, "op", op, "amount", n);
	}

	private void kick(Admin.Player p) {
		long now = System.currentTimeMillis();
		if (now - kickArmedAt > 4000) {
			kickArmedAt = now;
			rebuild();
			return;
		}
		kickArmedAt = 0;
		Cosmetics.adminAction("kick", p.uuid, "reason", reason == null ? "" : reason.getText().trim());
		rebuild();
	}

	private void announce() {
		String t = announce.getText().trim();
		if (t.isEmpty()) {
			announce.setFocused(true);
			return;
		}
		Cosmetics.adminAction("broadcast", null, "message", t);
		announce.setText("");
	}

	@Override
	protected void tick() {
		ticks++;
		if (data().version != seen || (kickArmedAt != 0 && System.currentTimeMillis() - kickArmedAt > 4000)) {
			if (kickArmedAt != 0 && System.currentTimeMillis() - kickArmedAt > 4000) {
				kickArmedAt = 0;
			}
			rebuild();
		}
		if (ticks % 100 == 0 && data().allowed) {
			Cosmetics.adminOpen(); // keep the list fresh every 5 seconds
		}
	}

	@Override
	protected void typed() {
		if (search != null && search.isFocused()) {
			scroll.reset();
		}
	}

	@Override
	protected void drawContent(int mx, int my, float pt) {
		Admin a = data();
		Ui.window(wx, wy, ww, wh);
		header(wx, wy, ww, "shield", "Admin", a.allowed ? a.players.size() + " online · " + Ui.num(a.registered)
				+ " players have " + a.currency : "Website Owners only", false);
		int top = wy + 44;
		if (!a.known) {
			center("clock", "Asking the server...", ticks > 100 ? "No answer. Is the PlebioAPI plugin up to date on the proxy?"
					: "Checking that you're an Owner.");
			return;
		}
		if (!a.allowed) {
			center("lock", "No access", a.message != null ? a.message : "Only website Owners can use the admin menu.");
			return;
		}
		// search box
		boolean f = search != null && search.isFocused();
		Ui.card(listX, top, listW, 18, 0xFF0D1217, f ? Ui.accent() : Ui.BORDER);
		Ui.icon("search", listX + 4, top + 5, 1, Ui.FAINT);
		if (search != null && search.getText().isEmpty() && !f) {
			Ui.text("Find a player", listX + 16, top + 5, Ui.FAINT);
		}
		// player list
		List<Admin.Player> ps = filtered();
		scroll.set(listY, listH, ps.size() * ROW);
		Ui.round(listX, listY, listW, listH, 0x40000000);
		int off = scroll.y();
		Ui.clip(listX, listY, listW, listH, this.width, this.height);
		for (int i = 0; i < ps.size(); i++) {
			int ry = listY + i * ROW - off;
			if (ry + ROW < listY || ry > listY + listH) {
				continue;
			}
			Admin.Player p = ps.get(i);
			boolean sel = p.uuid.equals(selected);
			boolean hov = mx >= listX && mx < listX + listW - 5 && my >= Math.max(ry, listY) && my < Math.min(ry + ROW, listY + listH);
			if (sel || hov) {
				Ui.round(listX + 2, ry + 1, listW - 9, ROW - 2, sel ? Ui.alpha(Ui.accent(), 0x40) : 0x20FFFFFF);
				if (sel) {
					Ui.rect(listX + 2, ry + 3, 2, ROW - 6, Ui.accent());
				}
			}
			Ui.round(listX + 8, ry + 7, 4, 4, p.client ? Ui.GOOD : Ui.FAINT);
			Ui.text(Ui.fit(p.name, listW - 70), listX + 16, ry + 3, Ui.TEXT);
			Ui.text(Ui.fit(p.server, listW - 70), listX + 16, ry + 11, Ui.FAINT);
			String c = Ui.num(p.coins);
			Ui.text(c, listX + listW - 10 - Ui.width(c), ry + 6, Ui.GOLD);
		}
		Ui.unclip();
		scroll.drawBar(listX + listW - 4);
		if (ps.isEmpty()) {
			Ui.centered("Nobody matches", listX + listW / 2, listY + 10, Ui.FAINT);
		}

		// the selected player
		Admin.Player p = current();
		int px = listX + listW + 10;
		int pw = wx + ww - 10 - px;
		if (p != null) {
			Ui.card(px, top, pw, 40, Ui.RAISED, Ui.BORDER_SOFT);
			Ui.big(Ui.fit(p.name, (pw - 90) * 2 / 3), px + 8, top + 6, 1.5f, Ui.TEXT, false);
			String sub = p.server + (p.account.isEmpty() ? " · not logged in" : " · @" + p.account)
					+ (p.client ? "" : " · other client");
			Ui.text(Ui.fit(sub, pw - 16), px + 8, top + 26, Ui.MUTED);
			if (!p.rank().isEmpty()) {
				int rw = Ui.pillWidth(p.rank());
				Ui.pill(p.rank(), px + pw - rw - 6, top + 6, Ui.ON_ACCENT, Ui.accent());
			}
			String c = Ui.num(p.coins);
			Ui.icon("coin", px + pw - Ui.width(c) - 18, top + 22, 1, Ui.GOLD);
			Ui.text(c, px + pw - Ui.width(c) - 6, top + 23, Ui.GOLD);
			int y = top + 46;
			boolean af = amount != null && amount.isFocused();
			Ui.card(px, y, 64, 18, 0xFF0D1217, af ? Ui.accent() : Ui.BORDER);
			if (amount != null && amount.getText().isEmpty() && !af) {
				Ui.text("Amount", px + 6, y + 5, Ui.FAINT);
			}
			y += 72;
			boolean rf = reason != null && reason.isFocused();
			Ui.card(px, y, pw - 72, 18, 0xFF0D1217, rf ? Ui.BAD : Ui.BORDER);
			if (reason != null && reason.getText().isEmpty() && !rf) {
				Ui.text("Kick reason (optional)", px + 6, y + 5, Ui.FAINT);
			}
			// what they own
			y += 26;
			int lines = Math.max(0, (wy + wh - 40 - y) / 10);
			if (lines > 0) {
				String eq = p.equipped.isEmpty() ? "Wearing nothing" : "Wearing " + String.join(", ", p.equipped.values());
				Ui.text(Ui.fit(eq, pw), px, y, Ui.FAINT);
				if (lines > 1) {
					String own = p.owned.isEmpty() ? "Owns no cosmetics" : "Owns " + p.owned.size() + ": " + String.join(", ", p.owned);
					List<String> wrapped = Ui.wrap(own, pw);
					for (int i = 0; i < wrapped.size() && i < lines - 1; i++) {
						Ui.text(wrapped.get(i), px, y + 10 + i * 10, Ui.FAINT);
					}
				}
			}
		} else {
			Ui.centered("Nobody online", px + pw / 2, top + 30, Ui.FAINT);
		}

		// bottom bar
		int by = wy + wh - 30;
		Ui.rect(wx + 8, by - 8, ww - 16, 1, Ui.BORDER_SOFT);
		boolean bf = announce != null && announce.isFocused();
		Ui.card(listX, by, ww - 20 - 164, 18, 0xFF0D1217, bf ? Ui.accent() : Ui.BORDER);
		if (announce != null && announce.getText().isEmpty() && !bf) {
			Ui.text("Announcement to everyone", listX + 6, by + 5, Ui.FAINT);
		}
		String msg = a.recentMessage();
		if (msg != null) {
			Ui.icon(a.messageOk ? "check" : "close", listX, by - 20, 1, a.messageOk ? Ui.GOOD : Ui.BAD);
			Ui.text(Ui.fit(msg, ww - 34), listX + 12, by - 19, a.messageOk ? Ui.GOOD : Ui.BAD);
		} else if (a.pending) {
			Ui.text("Working...", listX, by - 19, Ui.MUTED);
		}
	}

	private void center(String icon, String title, String text) {
		int cx = wx + ww / 2;
		int y = wy + 70;
		Ui.icon(icon, cx - 13, y, 3, Ui.alpha(Ui.accent(), 0x90));
		Ui.centered(title, cx, y + 34, Ui.TEXT);
		List<String> lines = Ui.wrap(text, ww - 60);
		for (int i = 0; i < lines.size(); i++) {
			Ui.centered(lines.get(i), cx, y + 46 + i * 10, Ui.MUTED);
		}
	}

	@Override
	protected void clicked(int mx, int my, int button) {
		if (mx >= listX && mx < listX + listW - 5 && my >= listY && my < listY + listH) {
			List<Admin.Player> ps = filtered();
			int i = (my - listY + scroll.y()) / ROW;
			if (i >= 0 && i < ps.size()) {
				selected = ps.get(i).uuid;
				kickArmedAt = 0;
				click();
				rebuild();
			}
		}
	}

	@Override
	protected void scrolled(int mx, int my, int dir) {
		if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
			scroll.wheel(dir);
		}
	}

	@Override
	protected void keyTyped(char c, int key) {
		if (key == 28 || key == 156) { // Enter
			if (announce != null && announce.isFocused()) {
				announce();
				return;
			}
			if (amount != null && amount.isFocused()) {
				coins("give");
				return;
			}
		}
		super.keyTyped(c, key);
	}
}
