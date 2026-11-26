package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.plebmc.client.mods.Mod;

/**
 * Every mod as a card: what it does, whether it is on, and a settings
 * button when it has options. Categories on the left, search on top.
 * A mod the server forbids is shown locked and can't be turned on.
 */
public final class ModsScreen extends PlebScreen {

	static final String[] CATEGORIES = { null, Mod.PLEBNITE, Mod.HUD, Mod.GAMEPLAY, Mod.VISUAL, Mod.PERFORMANCE };
	private static final String SECRET = "fpslarp";

	private String category;
	private GuiTextField search;
	private final Scroll scroll = new Scroll();
	private final List<Card> cards = new ArrayList<>();
	private int wx, wy, ww, wh;
	private int gx, gy, gw, gh;

	public ModsScreen(GuiScreen parent) {
		super(parent);
	}

	static String icon(String category) {
		if (Mod.HUD.equals(category)) {
			return "hud";
		}
		if (Mod.GAMEPLAY.equals(category)) {
			return "gameplay";
		}
		if (Mod.PLEBNITE.equals(category)) {
			return "plebnite";
		}
		if (Mod.PERFORMANCE.equals(category)) {
			return "performance";
		}
		if (Mod.VISUAL.equals(category)) {
			return "eye";
		}
		return "all";
	}

	@Override
	protected void build() {
		ww = Math.min(this.width - 16, 470);
		wh = Math.min(this.height - 16, 300);
		wx = (this.width - ww) / 2;
		wy = (this.height - wh) / 2;
		closeButton(wx + ww - 22, wy + 10);

		int rx = wx + 8, ry = wy + 46, rw = 112;
		for (String c : CATEGORIES) {
			int on = 0, total = 0;
			for (Mod m : PlebClient.mods().all()) {
				if (c == null || c.equals(m.category())) {
					total++;
					if (m.isEnabled() && !m.isBlockedByServer()) {
						on++;
					}
				}
			}
			Tab t = tab(c == null ? "All" : c, icon(c), 0, rx, ry, rw, 18, () -> eq(category, c), () -> {
				category = c;
				scroll.reset();
				rebuild();
			});
			t.badge = on + "/" + total;
			t.rail = true;
			t.tip = on + " of " + total + " on";
			ry += 20;
		}
		// your hats and cloaks, one click from Right Shift
		Button cos = button("Cosmetics", rx, Math.max(ry + 6, wy + wh - 8 - 20), rw, 20,
				() -> this.mc.displayGuiScreen(new ShopScreen(this)));
		cos.style(Style.PRIMARY).icon("shop").tip = "Wear and try on your hats and cloaks";

		gx = rx + rw + 10;
		gw = wx + ww - 12 - gx;
		search = field(gx + 16, wy + 47, gw - 22, 12, 32);
		gy = wy + 66;
		gh = wy + wh - 8 - gy;

		cards.clear();
		int cols = gw >= 330 ? 3 : 2;
		int gap = 6;
		int cw = (gw - (cols - 1) * gap) / cols;
		int ch = 46;
		String q = search.getText().trim().toLowerCase(Locale.ROOT);
		int i = 0;
		for (Mod m : PlebClient.mods().all()) {
			if (category != null && !category.equals(m.category())) {
				continue;
			}
			if (!q.isEmpty() && !m.name().toLowerCase(Locale.ROOT).contains(q)
					&& !m.description().toLowerCase(Locale.ROOT).contains(q)
					&& !m.category().toLowerCase(Locale.ROOT).contains(q)) {
				continue;
			}
			Card card = new Card(m);
			card.at(gx + (i % cols) * (cw + gap), gy + (i / cols) * (ch + gap), cw, ch);
			card.baseY = card.y;
			card.clip = new int[] { gx - 1, gy, gw + 2, gh };
			widgets.add(card);
			cards.add(card);
			if (!m.settings().isEmpty() || m instanceof net.plebmc.client.mods.Mods.FpsBoost) {
				Button gear = new Button("", () -> open(m));
				gear.icon("settings").style(Style.GHOST).tip("Settings for " + m.name());
				gear.at(card.x + cw - 18, card.y + 4, 14, 14);
				gear.clip = card.clip;
				card.gear = gear;
				widgets.add(gear);
			}
			i++;
		}
		int rows = (i + cols - 1) / cols;
		scroll.set(gy, gh, rows * (ch + gap) - gap);
	}

	private static boolean eq(String a, String b) {
		return a == null ? b == null : a.equals(b);
	}

	private void open(Mod m) {
		this.mc.displayGuiScreen(new ModSettingsScreen(this, m));
	}

	/** One mod: icon, name, description, status and a switch. Clicking it toggles the mod. */
	private final class Card extends W {
		final Mod m;
		int baseY;
		Button gear;
		float knob = -1f;

		Card(Mod m) {
			this.m = m;
		}

		@Override
		void draw(int mx, int my) {
			boolean blocked = m.isBlockedByServer();
			boolean on = m.isEnabled() && !blocked;
			int acc = Ui.accent();
			int border = blocked ? Ui.alpha(Ui.BAD, 0x70) : on ? Ui.mix(Ui.alpha(acc, 0x80), acc, hover) : Ui.mix(Ui.BORDER, Ui.alpha(acc, 0xA0), hover);
			int fill = blocked ? 0xF2181416 : on ? Ui.mix(Ui.RAISED, acc, 0.07f + 0.05f * hover) : Ui.mix(Ui.RAISED, Ui.HOVER, hover);
			Ui.card(x, y, w, h, fill, border);
			if (on) {
				Ui.rect(x + 1, y, w - 2, 1, acc);
			}
			int ic = blocked ? Ui.FAINT : on ? acc : Ui.MUTED;
			Ui.round(x + 5, y + 5, 15, 15, Ui.alpha(ic, 0x26));
			Ui.icon(icon(m.category()), x + 8, y + 8, 1, ic);
			Ui.text(Ui.fit(m.name(), w - 46), x + 25, y + 6, blocked ? Ui.MUTED : Ui.TEXT);
			Ui.text(Ui.fit(m.category(), w - 46), x + 25, y + 15, Ui.FAINT);
			Ui.text(Ui.fit(m.description(), w - 12), x + 6, y + 24, blocked ? Ui.FAINT : Ui.MUTED);

			// status + switch on the bottom row
			int sy = y + h - 13;
			if (blocked) {
				Ui.icon("lock", x + 6, sy + 1, 1, Ui.BAD);
				Ui.text("Blocked by server", x + 18, sy + 2, Ui.BAD);
			} else {
				Ui.pill(on ? "ON" : "OFF", x + 6, sy, on ? Ui.ON_ACCENT : Ui.MUTED, on ? acc : 0xFF0A0E12);
			}
			float target = on ? 1f : 0f;
			knob = knob < 0 ? target : ease(knob, target, 18f);
			int tw = 18, th = 9;
			int tx = x + w - tw - 6, ty = sy + 1;
			Ui.round(tx, ty, tw, th, blocked ? 0xFF20262C : Ui.mix(0xFF2A333C, acc, knob));
			Ui.round(tx + 1 + Math.round((tw - 9) * knob), ty + 1, 7, 7, blocked ? 0xFF59636B : 0xFFF4FAFC);
		}

		@Override
		void click(int mx, int my, int button) {
			if (button == 1 && gear != null) {
				open(m);
				return;
			}
			if (button == 0 && !m.isBlockedByServer()) {
				m.setEnabled(!m.isEnabled());
				PlebClient.saveSettings();
				rebuildKeepScroll();
			}
		}
	}

	private void rebuildKeepScroll() {
		float t = scroll.target, o = scroll.offset;
		rebuild();
		scroll.target = t;
		scroll.offset = o;
		scroll.clamp();
	}

	@Override
	protected void scrolled(int mx, int my, int dir) {
		scroll.wheel(dir);
	}

	@Override
	protected void typed() {
		if (search.getText().trim().equalsIgnoreCase(SECRET)) {
			search.setText("");
			this.mc.displayGuiScreen(new FpsLarpScreen(this));
			return;
		}
		scroll.reset();
		rebuild();
		search.setFocused(true);
	}

	@Override
	protected void key(char c, int key) {
		if (key == PlebClient.KEY_RSHIFT) {
			back();
			return;
		}
		// start typing anywhere to search
		if (Character.isLetterOrDigit(c)) {
			search.setFocused(true);
			search.textboxKeyTyped(c, key);
			typed();
		}
	}

	@Override
	protected void drawContent(int mx, int my, float pt) {
		Ui.window(wx, wy, ww, wh);
		int on = 0;
		for (Mod m : PlebClient.mods().all()) {
			if (m.isEnabled() && !m.isBlockedByServer()) {
				on++;
			}
		}
		header(wx, wy, ww, "mods", "Mods", on + " of " + PlebClient.mods().all().size() + " on. Right-click a card for its settings.", false);

		// search box
		boolean focused = search.isFocused();
		Ui.card(gx, wy + 44, gw, 16, 0xFF0D1217, focused ? Ui.accent() : Ui.BORDER);
		Ui.icon("search", gx + 4, wy + 48, 1, focused ? Ui.accent() : Ui.FAINT);
		if (search.getText().isEmpty() && !focused) {
			Ui.text("Search mods", gx + 17, wy + 48, Ui.FAINT);
		}

		// move the cards with the scroll
		int off = scroll.y();
		for (Card c : cards) {
			c.y = c.baseY - off;
			if (c.gear != null) {
				c.gear.y = c.y + 4;
			}
		}
		scroll.drawBar(gx + gw + 3);
		if (cards.isEmpty()) {
			Ui.icon("search", gx + gw / 2 - 13, gy + 30, 3, Ui.alpha(Ui.accent(), 0x80));
			Ui.centered("No mods match \"" + Ui.fit(search.getText(), 120) + "\"", gx + gw / 2, gy + 62, Ui.MUTED);
		}
	}
}
