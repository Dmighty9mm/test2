package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.gui.GuiScreen;
import net.plebmc.client.cosmetics.Cosmetics;

/**
 * The quest journal: All / General / PlebNite on the left, and each
 * category's Daily and Weekly quests on the right. Rewards are paid by the
 * server the moment a quest is finished (once), so a finished quest shows
 * as claimed and folds into a compact row under the open ones.
 */
public final class QuestsScreen extends PlebScreen {

	/** null = All. */
	private String category;
	private int seenVersion = -1;
	/** Quests that were new when the screen opened (they keep their NEW tag while it is open). */
	private final Set<String> fresh = new HashSet<>();
	private final Scroll scroll = new Scroll();

	private int wx, wy, ww, wh;
	private int listX, listY, listW, listH;

	public QuestsScreen(GuiScreen parent) {
		super(parent);
		for (Cosmetics.Quest q : Cosmetics.quests(null)) {
			if (Cosmetics.isNew(q)) {
				fresh.add(q.key());
			}
		}
		if (Cosmetics.markSeen(Cosmetics.quests(null))) {
			PlebClient.saveQuestsSeen();
		}
		Cosmetics.requestState();
	}

	@Override
	protected void build() {
		seenVersion = Cosmetics.version();
		ww = Math.min(this.width - 16, 460);
		wh = Math.min(this.height - 16, 300);
		wx = (this.width - ww) / 2;
		wy = (this.height - wh) / 2;
		closeButton(wx + ww - 22, wy + 10);

		// the category rail
		int rx = wx + 8;
		int ry = wy + 46;
		int rw = 96;
		addRail(null, "All", "all", 0, rx, ry, rw);
		ry += 20;
		for (Cosmetics.QuestCategory c : Cosmetics.questCategories()) {
			addRail(c.id, c.tab, Ui.hasIcon(c.icon) ? c.icon : "quests", color(c), rx, ry, rw);
			ry += 20;
		}
		if (category != null && cat(category) == null) {
			category = null;
		}

		// voting pays coins too
		long reward = Cosmetics.voteReward();
		Button vote = button(reward > 0 ? "Vote +" + reward : "Vote", rx, wy + wh - 28, rw, 18,
				() -> TitleActions.openLink(Cosmetics.voteUrl()));
		vote.style(Style.PRIMARY).icon("gift").tip("Vote for PlebMc once a day" + (reward > 0 ? " and get " + reward + " " + Cosmetics.currency() : ""));

		listX = rx + rw + 10;
		listY = wy + 44;
		listW = wx + ww - 10 - listX;
		listH = wy + wh - 8 - listY;

		if (!Cosmetics.serverSupported()) {
			Button join = button("Join PlebMc", listX + listW / 2 - 50, listY + listH / 2 + 30, 100, 18,
					() -> PlebMenu.joinServer(this));
			join.style(Style.PRIMARY).icon("plebnite");
			join.visible = this.mc == null || this.mc.getCurrentServerData() == null;
		}
	}

	private void addRail(String id, String label, String icon, int color, int x, int y, int w) {
		Tab t = tab(label, icon, color, x, y, w, 18, () -> eq(category, id), () -> {
			category = id;
			scroll.reset();
		});
		List<Cosmetics.Quest> qs = Cosmetics.quests(id);
		int done = 0;
		boolean anyFresh = false;
		for (Cosmetics.Quest q : qs) {
			if (q.done) {
				done++;
			}
			anyFresh |= fresh.contains(q.key()) && !q.done;
		}
		t.badge = qs.isEmpty() ? null : done + "/" + qs.size();
		t.rail = true;
		t.dot = anyFresh;
		t.tip = id == null ? "Every quest in one list" : "general".equals(id) ? "Quests that count anywhere on PlebMc"
				: "Quests that only count on " + label;
	}

	private static boolean eq(String a, String b) {
		return a == null ? b == null : a.equals(b);
	}

	private static Cosmetics.QuestCategory cat(String id) {
		for (Cosmetics.QuestCategory c : Cosmetics.questCategories()) {
			if (c.id.equals(id)) {
				return c;
			}
		}
		return null;
	}

	private static int color(Cosmetics.QuestCategory c) {
		if (c == null) {
			return Ui.accent();
		}
		return c.color != 0 ? c.color : "plebnite".equals(c.id) ? Ui.PLEBNITE : Ui.accent();
	}

	@Override
	protected void tick() {
		if (Cosmetics.version() != seenVersion) {
			rebuild();
		}
	}

	@Override
	protected void scrolled(int mx, int my, int dir) {
		scroll.wheel(dir);
	}

	// ==================== drawing ====================

	@Override
	protected void drawContent(int mx, int my, float pt) {
		Ui.window(wx, wy, ww, wh);
		Cosmetics.QuestCategory c = category == null ? null : cat(category);
		String sub = category == null ? "Daily and weekly quests for " + Cosmetics.currency()
				: "general".equals(category) ? "Quests that count anywhere on PlebMc" : "Quests that only count on " + c.tab;
		header(wx, wy, ww, "quests", "Quests", sub, true);
		drawRailFooter();

		if (!Cosmetics.serverSupported()) {
			empty("quests", "Quests live on PlebMc", "Join PlebMc to get daily and weekly quests and earn " + Cosmetics.currency() + ".");
			return;
		}
		List<Cosmetics.Quest> all = Cosmetics.quests(category);
		if (all.isEmpty()) {
			empty(c == null ? "quests" : c.icon, Cosmetics.questCategories().isEmpty() ? "Loading quests..." : "No quests here right now",
					"New quests come with the next daily reset.");
			return;
		}

		List<Cosmetics.Quest> daily = new ArrayList<>(), weekly = new ArrayList<>();
		for (Cosmetics.Quest q : all) {
			("weekly".equals(q.period) ? weekly : daily).add(q);
		}
		sortOpenFirst(daily);
		sortOpenFirst(weekly);

		int content = sectionHeight(daily) + 6 + sectionHeight(weekly);
		scroll.set(listY, listH, content);
		int y = listY - scroll.y();
		Ui.clip(listX - 2, listY, listW + 4, listH, this.width, this.height);
		y = section(y, "Daily", "clock", Cosmetics.dailyResetSeconds(), daily, Ui.accent());
		y += 6;
		section(y, "Weekly", "week", Cosmetics.weeklyResetSeconds(), weekly, Ui.WEEKLY);
		Ui.unclip();
		scroll.drawBar(listX + listW + 3);
	}

	/** Unfinished first (then the login row on top), finished last, otherwise the server's order. */
	private static void sortOpenFirst(List<Cosmetics.Quest> qs) {
		List<Cosmetics.Quest> open = new ArrayList<>(), done = new ArrayList<>();
		for (Cosmetics.Quest q : qs) {
			(q.done ? done : open).add(q);
		}
		qs.clear();
		qs.addAll(open);
		qs.addAll(done);
	}

	private static final int ROW = 36;
	private static final int ROW_DONE = 16;
	private static final int SECTION_HEAD = 16;

	private static int sectionHeight(List<Cosmetics.Quest> qs) {
		int h = SECTION_HEAD;
		if (qs.isEmpty()) {
			return h + 18;
		}
		for (Cosmetics.Quest q : qs) {
			h += (q.done ? ROW_DONE : ROW) + 3;
		}
		return h;
	}

	private int section(int y, String title, String icon, long resetSeconds, List<Cosmetics.Quest> qs, int color) {
		int done = 0;
		for (Cosmetics.Quest q : qs) {
			if (q.done) {
				done++;
			}
		}
		Ui.icon(icon, listX, y + 3, 1, color);
		Ui.text(title, listX + 13, y + 4, Ui.TEXT);
		int tx = listX + 16 + Ui.width(title);
		if (!qs.isEmpty()) {
			Ui.pill(done + "/" + qs.size(), tx, y + 2, done == qs.size() ? Ui.ON_ACCENT : Ui.MUTED,
					done == qs.size() ? Ui.GOOD : 0xFF0A0E12);
		}
		if (resetSeconds >= 0) {
			String r = "Resets in " + Ui.time(resetSeconds);
			Ui.text(r, listX + listW - Ui.width(r), y + 4, Ui.FAINT);
		}
		y += SECTION_HEAD;
		if (qs.isEmpty()) {
			Ui.text("No " + title.toLowerCase() + " quests here right now.", listX + 4, y + 4, Ui.FAINT);
			return y + 18;
		}
		for (Cosmetics.Quest q : qs) {
			if (q.done) {
				doneRow(q, y);
				y += ROW_DONE + 3;
			} else {
				row(q, y, color);
				y += ROW + 3;
			}
		}
		return y;
	}

	private void row(Cosmetics.Quest q, int y, int periodColor) {
		boolean login = "login".equals(q.period);
		int c = login ? Ui.GOLD : periodColor;
		boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= Math.max(y, listY)
				&& mouseY < Math.min(y + ROW, listY + listH);
		Ui.card(listX, y, listW, ROW, hover ? Ui.RAISED_2 : Ui.RAISED, hover ? Ui.alpha(c, 0x90) : Ui.BORDER);
		Ui.rect(listX + 1, y + 2, 2, ROW - 4, c);

		// reward on the right
		String reward = "+" + Ui.num(q.reward);
		int rw = Ui.width(reward) + 18;
		int rx = listX + listW - rw - 6;
		Ui.card(rx, y + 5, rw, 14, 0xFF0D1217, Ui.alpha(Ui.GOLD, 0x60));
		Ui.icon("coin", rx + 4, y + 8, 1, Ui.GOLD);
		Ui.text(reward, rx + 15, y + 8, Ui.GOLD);

		int x = listX + 8;
		int textW = rx - x - 6;
		String name = q.name;
		int nx = x;
		if (category == null && q.category != null) {
			Cosmetics.QuestCategory qc = cat(q.category);
			if (qc != null) {
				nx += Ui.pill(qc.tab, nx, y + 4, Ui.ON_ACCENT, color(qc)) + 4;
			}
		}
		if (fresh.contains(q.key())) {
			nx += Ui.pill("NEW", nx, y + 4, Ui.ON_ACCENT, Ui.GOLD) + 4;
		}
		Ui.text(Ui.fit(name, textW - (nx - x)), nx, y + 6, Ui.TEXT);

		if (login) {
			// streak pips: today's reward is the next one
			int n = Math.max(1, q.streakLength);
			int pw = Math.max(6, Math.min(16, (textW - 70) / n - 2));
			int px = x;
			for (int i = 0; i < n; i++) {
				Ui.round(px, y + 21, pw, 6, i < q.streak ? Ui.GOLD : 0xFF0A0E12);
				px += pw + 2;
			}
			String s = q.streak > 0 ? "Day " + q.streak + " streak: claim today's to keep it" : "Join each day to build a streak";
			Ui.text(Ui.fit(s, listX + listW - px - 10), px + 4, y + 20, Ui.FAINT);
			tooltipArea(listX, Math.max(y, listY), listW, ROW, "Paid when you join PlebMc, once a day. Miss two days and the streak starts over.");
			return;
		}
		Ui.text(Ui.fit(q.description, textW), x, y + 16, Ui.MUTED);
		float f = q.goal <= 0 ? 0f : q.progress / (float) q.goal;
		int bx = x;
		int bw = listW - 16 - 60;
		Ui.bar(bx, y + 27, bw, 5, f, c);
		String count = Ui.num(q.progress) + " / " + Ui.num(q.goal);
		Ui.text(count, listX + listW - 8 - Ui.width(count), y + 25, Ui.TEXT);
		if (hover) {
			tooltip(q.name + ": " + q.description + ". Pays " + Ui.num(q.reward) + " " + Cosmetics.currency()
					+ " as soon as it's done.");
		}
	}

	private void doneRow(Cosmetics.Quest q, int y) {
		Ui.round(listX, y, listW, ROW_DONE, 0xC0121A16);
		Ui.icon("check", listX + 5, y + 4, 1, Ui.GOOD);
		String paid = "+" + Ui.num(q.reward) + " claimed";
		int pw = Ui.width(paid);
		Ui.text(paid, listX + listW - pw - 6, y + 4, Ui.GOOD);
		String name = "login".equals(q.period) && q.streak > 0 ? q.name + " (day " + q.streak + ")" : q.name;
		Ui.text(Ui.fit(name, listW - pw - 30), listX + 18, y + 4, Ui.MUTED);
		tooltipArea(listX, Math.max(y, listY), listW, Math.min(ROW_DONE, listY + listH - y),
				"Done. The reward was added to your " + Cosmetics.currency() + ".");
	}

	private void drawRailFooter() {
		int x = wx + 8, w = 96;
		int y = wy + wh - 58;
		Ui.card(x, y, w, 50, Ui.RAISED, Ui.BORDER_SOFT);
		Ui.icon("star", x + 5, y + 5, 1, Ui.GOLD);
		int streak = Cosmetics.loginStreak();
		Ui.text("Login streak", x + 17, y + 6, Ui.TEXT);
		Ui.big(streak + (streak == 1 ? " day" : " days"), x + 6, y + 19, 1.25f, Ui.GOLD, false);
		long d = Cosmetics.dailyResetSeconds();
		if (d >= 0) {
			Ui.text(Ui.fit("Next in " + Ui.time(d), w - 10), x + 6, y + 37, Ui.FAINT);
		}
	}

	private void empty(String icon, String title, String text) {
		int cx = listX + listW / 2;
		int cy = listY + listH / 2 - 30;
		Ui.icon(Ui.hasIcon(icon) ? icon : "quests", cx - 13, cy - 8, 3, Ui.alpha(Ui.accent(), 0x90));
		Ui.centered(title, cx, cy + 24, Ui.TEXT);
		List<String> lines = Ui.wrap(text, listW - 40);
		for (int i = 0; i < lines.size(); i++) {
			Ui.centered(lines.get(i), cx, cy + 36 + i * 10, Ui.MUTED);
		}
	}
}
