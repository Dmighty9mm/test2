package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.plebmc.client.mods.FpsLarp;

/**
 * Settings for the secret Fps Larp mod (type "fpslarp" in Mods search). Only
 * the number on the FPS counters changes; the game runs exactly as before.
 */
public final class FpsLarpScreen extends PlebScreen {

	private static final int PINK = 0xFFFF6FD8;
	private final Scroll scroll = new Scroll();
	private final List<W> rows = new ArrayList<>();
	private final List<Integer> rowBase = new ArrayList<>();
	private int wx, wy, ww, wh, lx, ly, lw, lh;

	public FpsLarpScreen(GuiScreen parent) {
		super(parent);
	}

	private static FpsLarp l() {
		return FpsLarp.INSTANCE;
	}

	private static Runnable changed(Runnable r) {
		return () -> {
			r.run();
			l().changed();
			PlebClient.saveSettings();
		};
	}

	/** A choice over an option index (n values). */
	private Choice opt(String name, String sub, int y, int n, IntSupplier get, IntConsumer set, Supplier<String> label) {
		return choice(name, sub, lx, y, lw, 24, label, changed(() -> set.accept((get.getAsInt() + 1) % n)),
				changed(() -> set.accept((get.getAsInt() - 1 + n) % n)));
	}

	@Override
	protected void build() {
		ww = Math.min(this.width - 16, 340);
		wh = Math.min(this.height - 16, 300);
		wx = (this.width - ww) / 2;
		wy = (this.height - wh) / 2;
		closeButton(wx + ww - 22, wy + 10);
		lx = wx + 10;
		lw = ww - 20;
		ly = wy + 70;
		lh = wy + wh - 30 - ly;
		rows.clear();
		rowBase.clear();
		int[] clip = { lx - 1, ly, lw + 2, lh };
		FpsLarp l = l();
		int y = ly;
		add(toggle("Fps Larp", "Only the number changes. Your game runs the same.", lx, y, lw, 24, () -> l.enabled,
				changed(() -> l.enabled = !l.enabled)), clip);
		y += 28;
		boolean locked = FpsLarp.TARGETS[l.target] > 0;
		Choice mult = opt("Multiplier", "Shown = real x this", y, FpsLarp.MULTIPLIERS.length, () -> l.multiplier, v -> l.multiplier = v,
				() -> FpsLarp.fmt(FpsLarp.MULTIPLIERS[l.multiplier]) + "x");
		mult.enabled = !locked;
		add(mult, clip);
		y += 28;
		Choice bonus = opt("Extra", "Added on top", y, FpsLarp.BONUSES.length, () -> l.bonus, v -> l.bonus = v,
				() -> "+" + FpsLarp.BONUSES[l.bonus]);
		bonus.enabled = !locked;
		add(bonus, clip);
		y += 28;
		add(opt("Lock at", "Always show about this number", y, FpsLarp.TARGETS.length, () -> l.target, v -> l.target = v,
				() -> FpsLarp.TARGETS[l.target] > 0 ? Integer.toString(FpsLarp.TARGETS[l.target]) : "Off"), clip);
		y += 28;
		add(opt("Max", "Never show more than this", y, FpsLarp.CAPS.length, () -> l.cap, v -> l.cap = v,
				() -> FpsLarp.CAPS[l.cap] > 0 ? Integer.toString(FpsLarp.CAPS[l.cap]) : "None"), clip);
		y += 28;
		add(opt("Jitter", "Wobble so it looks real", y, FpsLarp.JITTERS.length, () -> l.jitter, v -> l.jitter = v,
				() -> FpsLarp.JITTER_NAMES[l.jitter]), clip);
		y += 28;
		add(opt("Updates", "How often the number changes", y, FpsLarp.UPDATES.length, () -> l.update, v -> l.update = v,
				() -> FpsLarp.UPDATE_NAMES[l.update]), clip);
		y += 28;
		add(opt("Text", "How the counter reads", y, FpsLarp.FORMATS.length, () -> l.format, v -> l.format = v,
				() -> String.format(FpsLarp.FORMATS[l.format], 240)), clip);
		y += 28;
		add(opt("Color", "Counter color", y, FpsLarp.COLORS.length, () -> l.color, v -> l.color = v,
				() -> FpsLarp.COLOR_NAMES[l.color]), clip);
		y += 28;
		add(toggle("FPS mod", "Larp the HUD counter", lx, y, lw, 24, () -> l.inHud, changed(() -> l.inHud = !l.inHud)), clip);
		y += 28;
		add(toggle("F3 screen", "Larp the debug screen too", lx, y, lw, 24, () -> l.inDebug, changed(() -> l.inDebug = !l.inDebug)), clip);
		y += 28;
		scroll.set(ly, lh, y - ly - 4);
		Button reset = button("Reset", wx + ww - 74, wy + wh - 24, 64, 16, changed(l::reset));
		reset.icon("reset");
		Button backBtn = button("Mods", wx + 8, wy + wh - 24, 60, 16, this::back);
		backBtn.icon("back").style(Style.GHOST);
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
	protected void drawContent(int mx, int my, float pt) {
		Ui.window(wx, wy, ww, wh);
		Ui.rect(wx + 1, wy, ww - 2, 1, PINK);
		Ui.round(wx + 8, wy + 8, 20, 20, Ui.alpha(PINK, 0x30));
		Ui.icon("performance", wx + 10, wy + 9, 2, PINK);
		Ui.big("Fps Larp", wx + 34, wy + 8, 1.5f, Ui.TEXT, false);
		Ui.text("Secret. " + l().summary(), wx + 34, wy + 22, Ui.MUTED);
		int real = Minecraft.getDebugFPS();
		String shown = l().enabled ? l().hudText(real, System.currentTimeMillis()) : "§7(off)";
		Ui.card(wx + 10, wy + 40, ww - 20, 24, 0xFF0D1217, Ui.alpha(PINK, 0x60));
		Ui.text("Real", wx + 16, wy + 44, Ui.FAINT);
		Ui.text(Integer.toString(real), wx + 16, wy + 53, Ui.TEXT);
		Ui.text("Shown", wx + 80, wy + 44, Ui.FAINT);
		Draw.text(shown, wx + 80, wy + 53, 0xFFFFFFFF, true);
		int off = scroll.y();
		for (int i = 0; i < rows.size(); i++) {
			rows.get(i).y = rowBase.get(i) - off;
		}
		scroll.drawBar(lx + lw + 3);
	}
}
