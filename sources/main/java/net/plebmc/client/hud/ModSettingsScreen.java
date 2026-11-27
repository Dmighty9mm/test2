package net.plebmc.client.hud;

import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.plebmc.client.mods.Mod;
import net.plebmc.client.mods.Mods;

/**
 * One mod's settings page: its on/off switch, every option it has, reset
 * to defaults, and (for HUD mods) a shortcut to place it in the HUD editor.
 */
public final class ModSettingsScreen extends PlebScreen {

	private final Mod mod;
	private final Scroll scroll = new Scroll();
	private int wx, wy, ww, wh;
	private int lx, ly, lw, lh;
	private final java.util.List<W> rows = new java.util.ArrayList<>();
	private final java.util.List<Integer> rowBase = new java.util.ArrayList<>();

	public ModSettingsScreen(GuiScreen parent, Mod mod) {
		super(parent);
		this.mod = mod;
	}

	@Override
	protected void build() {
		ww = Math.min(this.width - 16, 340);
		wh = Math.min(this.height - 16, 290);
		wx = (this.width - ww) / 2;
		wy = (this.height - wh) / 2;
		Button backBtn = button("Mods", wx + 8, wy + wh - 24, 60, 16, this::back);
		backBtn.icon("back").style(Style.GHOST);
		closeButton(wx + ww - 22, wy + 10);

		lx = wx + 10;
		lw = ww - 20;
		ly = wy + 46;
		lh = wy + wh - 30 - ly;
		rows.clear();
		rowBase.clear();
		int y = ly;
		int[] clip = { lx - 1, ly, lw + 2, lh };

		Toggle en = toggle("Enabled", mod.isBlockedByServer() ? "This server doesn't allow " + mod.name() : mod.description(),
				lx, y, lw, 26, () -> mod.isEnabled() && !mod.isBlockedByServer(), () -> {
					mod.setEnabled(!mod.isEnabled());
					PlebClient.saveSettings();
				});
		en.enabled = !mod.isBlockedByServer();
		add(en, clip);
		y += 32;

		List<Mod.Setting> ss = mod.settings();
		for (Mod.Setting st : ss) {
			W row;
			if (st.isText()) {
				row = new TextRow(st, field(0, 0, Math.min(lw / 2, 110) - 8, 10, 24));
				row.at(lx, y, lw, 26);
				widgets.add(row);
			} else if (st.labels == Mod.OFF_ON) {
				row = toggle(st.name, st.description, lx, y, lw, 26, st::on, () -> {
					st.next();
					PlebClient.saveSettings();
				});
			} else {
				row = choice(st.name, st.description, lx, y, lw, 26, st::label, () -> {
					st.next();
					PlebClient.saveSettings();
				}, () -> {
					st.set((st.get() - 1 + st.labels.length) % st.labels.length);
					PlebClient.saveSettings();
				});
			}
			add(row, clip);
			y += 30;
		}
		if (mod instanceof Mods.FpsBoost) {
			add(choice("Resolution", "How sharp the game draws (lower = faster). FPS Boost caps it at 75%.", lx, y, lw, 26,
					FpsBoost::scaleLabel, () -> FpsBoost.cycleScale(1), () -> FpsBoost.cycleScale(-1)), clip);
			y += 30;
		}
		scroll.set(ly, lh, y - ly - 4);

		int bx = wx + ww - 10;
		if (!ss.isEmpty()) {
			Button reset = button("Reset", bx - 64, wy + wh - 24, 64, 16, () -> {
				mod.resetSettings();
				PlebClient.saveSettings();
				rebuild();
			});
			reset.icon("reset").tip("Put " + mod.name() + "'s options back to their defaults");
			bx -= 70;
		}
		if (mod.hasHud()) {
			Button place = button("Move on HUD", bx - 86, wy + wh - 24, 86, 16,
					() -> this.mc.displayGuiScreen(new HudEditorScreen(this, mod)));
			place.icon("drag").style(Style.PRIMARY).tip("Drag, resize and recolor it in the HUD editor");
		}
	}

	/** A custom label: name on the left, a text box on the right. Saved as you type. */
	private final class TextRow extends W {
		final Mod.Setting st;
		final net.minecraft.client.gui.GuiTextField f;

		TextRow(Mod.Setting st, net.minecraft.client.gui.GuiTextField f) {
			this.st = st;
			this.f = f;
			f.setText(st.text());
			tip = st.description + " (default \"" + st.defText() + "\")";
		}

		@Override
		void draw(int mx, int my) {
			Ui.round(x, y, w, h, Ui.mix(Ui.RAISED, Ui.HOVER, hover));
			int bw = Math.min(w / 2, 110);
			Ui.text(Ui.fit(st.name, w - bw - 16), x + 8, y + (h - 18) / 2, Ui.TEXT);
			Ui.text(Ui.fit(st.description, w - bw - 16), x + 8, y + (h - 18) / 2 + 10, Ui.FAINT);
			int bx = x + w - bw - 6, by = y + (h - 14) / 2;
			Ui.card(bx, by, bw, 14, 0xFF0D1217, f.isFocused() ? Ui.accent() : Ui.mix(Ui.BORDER, Ui.accent(), hover));
			f.xPosition = bx + 4;
			f.yPosition = by + 3;
			boolean shown = clip == null || (y >= clip[1] && y + h <= clip[1] + clip[3]);
			f.setVisible(shown);
			if (!f.getText().equals(st.text())) {
				st.setText(f.getText());
				PlebClient.saveSettings();
			}
		}

		@Override
		void click(int mx, int my, int button) {
			f.setFocused(true);
		}
	}

	private void add(W w, int[] clip) {
		w.clip = clip;
		rows.add(w);
		rowBase.add(w.y);
	}

	@Override
	protected void scrolled(int mx, int my, int dir) {
		scroll.wheel(dir);
	}

	@Override
	protected void key(char c, int key) {
		if (key == PlebClient.KEY_RSHIFT) {
			back();
		}
	}

	@Override
	protected void drawContent(int mx, int my, float pt) {
		Ui.window(wx, wy, ww, wh);
		String state = mod.isBlockedByServer() ? "Blocked by this server" : mod.isEnabled() ? "On" : "Off";
		header(wx, wy, ww, ModsScreen.icon(mod.category()), mod.name(), mod.category() + "  ·  " + state, false);
		int off = scroll.y();
		for (int i = 0; i < rows.size(); i++) {
			rows.get(i).y = rowBase.get(i) - off;
		}
		scroll.drawBar(lx + lw + 3);
		if (mod.settings().isEmpty() && !(mod instanceof Mods.FpsBoost)) {
			Ui.centered("No other options. Just turn it on or off.", wx + ww / 2, ly + 50, Ui.FAINT);
		}
	}
}
