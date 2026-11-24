package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.plebmc.client.mods.ClientPrefs;
import net.plebmc.client.mods.Mod;

/**
 * Client settings, in sections. Every option here is read by the code it
 * describes; mod options live on each mod's own page.
 */
public final class SettingsScreen extends PlebScreen {

	private static final String[] SECTIONS = { "Interface", "Notifications", "HUD", "Performance", "Behavior", "Developer" };
	private static final String[] ICONS = { "palette", "bell", "hud", "performance", "settings", "debug" };

	private int section;
	private final Scroll scroll = new Scroll();
	private final List<W> rows = new ArrayList<>();
	private final List<Integer> rowBase = new ArrayList<>();
	private int wx, wy, ww, wh;
	private int lx, ly, lw, lh;
	private long resetArmedAt;

	public SettingsScreen(GuiScreen parent) {
		super(parent);
	}

	private static ClientPrefs p() {
		return ClientPrefs.get();
	}

	private static Runnable saved(Runnable r) {
		return () -> {
			r.run();
			PlebClient.saveSettings();
		};
	}

	private static int wrap(int v, int n) {
		return ((v % n) + n) % n;
	}

	@Override
	protected void build() {
		ww = Math.min(this.width - 16, 440);
		wh = Math.min(this.height - 16, 290);
		wx = (this.width - ww) / 2;
		wy = (this.height - wh) / 2;
		closeButton(wx + ww - 22, wy + 10);
		int rx = wx + 8, ry = wy + 46, rw = 100;
		for (int i = 0; i < SECTIONS.length; i++) {
			final int idx = i;
			Tab t = tab(SECTIONS[i], ICONS[i], 0, rx, ry, rw, 18, () -> section == idx, () -> {
				section = idx;
				scroll.reset();
				rebuild();
			});
			t.rail = true;
			ry += 20;
		}
		boolean armed = System.currentTimeMillis() - resetArmedAt < 4000;
		Button reset = button(armed ? "Click to confirm" : "Reset settings", rx, wy + wh - 26, rw, 16, () -> {
			if (System.currentTimeMillis() - resetArmedAt > 4000) {
				resetArmedAt = System.currentTimeMillis();
			} else {
				resetArmedAt = 0;
				p().reset();
				PlebClient.saveSettings();
			}
			rebuild();
		});
		reset.style(Style.DANGER).icon("reset").tip = "Puts every setting on this screen back to default (not your mods or HUD)";

		lx = rx + rw + 10;
		lw = wx + ww - 12 - lx;
		ly = wy + 46;
		lh = wy + wh - 8 - ly;
		rows.clear();
		rowBase.clear();
		int[] clip = { lx - 1, ly, lw + 2, lh };
		int y = ly;
		final int h = 26, step = 30;
		ClientPrefs pr = p();
		switch (section) {
		case 0: // Interface (incl. sound, motion)
			add(choice("Accent color", "The color of highlights in every PlebClient menu", lx, y, lw, h,
					() -> ClientPrefs.ACCENT_NAMES[pr.accent], saved(() -> pr.accent = wrap(pr.accent + 1, ClientPrefs.ACCENTS.length)),
					saved(() -> pr.accent = wrap(pr.accent - 1, ClientPrefs.ACCENTS.length))), clip);
			y += step;
			add(choice("Menu background", "How much of the game shows behind menus", lx, y, lw, h,
					() -> ClientPrefs.BACKDROPS[pr.backdrop], saved(() -> pr.backdrop = wrap(pr.backdrop + 1, ClientPrefs.BACKDROPS.length)),
					saved(() -> pr.backdrop = wrap(pr.backdrop - 1, ClientPrefs.BACKDROPS.length))), clip);
			y += step;
			add(toggle("Animations", "Menus slide in and hovers fade. Turn off to reduce motion.", lx, y, lw, h,
					() -> pr.animations, saved(() -> pr.animations = !pr.animations)), clip);
			y += step;
			add(toggle("Click sounds", "Play a click in PlebClient menus", lx, y, lw, h, () -> pr.uiSounds,
					saved(() -> pr.uiSounds = !pr.uiSounds)), clip);
			y += step;
			break;
		case 1: // Notifications
			add(toggle("Popups", "Coins, finished quests and the daily login reward", lx, y, lw, h, () -> pr.toasts,
					saved(() -> pr.toasts = !pr.toasts)), clip);
			y += step;
			Choice dur = choice("Popup time", "How long a popup stays on screen", lx, y, lw, h,
					() -> ClientPrefs.TOAST_SECONDS[pr.toastTime] + " seconds",
					saved(() -> pr.toastTime = wrap(pr.toastTime + 1, ClientPrefs.TOAST_SECONDS.length)),
					saved(() -> pr.toastTime = wrap(pr.toastTime - 1, ClientPrefs.TOAST_SECONDS.length)));
			dur.enabled = pr.toasts;
			add(dur, clip);
			y += step;
			break;
		case 2: // HUD
			add(toggle("Text shadow", "A shadow under HUD text, easier to read on bright ground", lx, y, lw, h,
					() -> pr.hudShadow, saved(() -> pr.hudShadow = !pr.hudShadow)), clip);
			y += step;
			Button edit = button("Open HUD editor", lx, y, lw, 20, () -> this.mc.displayGuiScreen(new HudEditorScreen(this)));
			edit.style(Style.PRIMARY).icon("drag");
			add(edit, clip);
			y += 26;
			break;
		case 3: // Performance
			add(choice("Resolution", "How sharp the game draws. Lower is faster.", lx, y, lw, h, FpsBoost::scaleLabel,
					() -> FpsBoost.cycleScale(1), () -> FpsBoost.cycleScale(-1)), clip);
			y += step;
			Mod boost = PlebClient.mods().fpsBoost;
			Toggle tb = toggle("FPS Boost", boost.description(), lx, y, lw, h, () -> boost.isEnabled() && !boost.isBlockedByServer(),
					saved(() -> boost.setEnabled(!boost.isEnabled())));
			tb.enabled = !boost.isBlockedByServer();
			add(tb, clip);
			y += step;
			Mod fps = PlebClient.mods().fps;
			add(toggle("FPS counter", "Show your frame rate on the HUD", lx, y, lw, h, fps::isEnabled,
					saved(() -> fps.setEnabled(!fps.isEnabled()))), clip);
			y += step;
			break;
		case 4: // Behavior
			add(choice("Right Shift opens", "The screen Right Shift takes you to", lx, y, lw, h,
					() -> ClientPrefs.OPENS[pr.shiftOpens], saved(() -> pr.shiftOpens = wrap(pr.shiftOpens + 1, ClientPrefs.OPENS.length)),
					saved(() -> pr.shiftOpens = wrap(pr.shiftOpens - 1, ClientPrefs.OPENS.length))), clip);
			y += step;
			break;
		default: // Developer
			add(toggle("Developer mode", "Adds Apollo debug to the home screen", lx, y, lw, h, () -> pr.developer,
					saved(() -> {
						pr.developer = !pr.developer;
						rebuildLater = true;
					})), clip);
			y += step;
			if (pr.developer) {
				Button dbg = button("Open Apollo debug", lx, y, lw, 20, () -> this.mc.displayGuiScreen(new ApolloDebugScreen(this)));
				dbg.icon("debug");
				add(dbg, clip);
				y += 26;
			}
			add(toggle("Log item NBT", "Records the data of items you and nearby players hold (for custom items)", lx, y,
					lw, h, () -> pr.itemNbtLog, saved(() -> {
						pr.itemNbtLog = !pr.itemNbtLog;
						rebuildLater = true;
					})), clip);
			y += step;
			Button nbt = button("Open item NBT log (" + ItemNbtWatch.LOG_DATA.size() + ")", lx, y, lw, 20,
					() -> this.mc.displayGuiScreen(new ItemNbtScreen(this)));
			nbt.icon("debug");
			add(nbt, clip);
			y += 26;
			break;
		}
		scroll.set(ly, lh, y - ly);
	}

	private boolean rebuildLater;

	private void add(W w, int[] clip) {
		w.clip = clip;
		rows.add(w);
		rowBase.add(w.y);
	}

	@Override
	protected void tick() {
		if (rebuildLater || (resetArmedAt != 0 && System.currentTimeMillis() - resetArmedAt > 4000)) {
			rebuildLater = false;
			if (resetArmedAt != 0 && System.currentTimeMillis() - resetArmedAt > 4000) {
				resetArmedAt = 0;
			}
			rebuild();
		}
	}

	@Override
	protected void scrolled(int mx, int my, int dir) {
		scroll.wheel(dir);
	}

	@Override
	protected void drawContent(int mx, int my, float pt) {
		Ui.window(wx, wy, ww, wh);
		header(wx, wy, ww, "settings", "Settings", SECTIONS[section], false);
		int off = scroll.y();
		for (int i = 0; i < rows.size(); i++) {
			rows.get(i).y = rowBase.get(i) - off;
		}
		scroll.drawBar(lx + lw + 3);
		if (section == 0) {
			// accent swatches under the options
			int y = rowBase.get(rows.size() - 1) - off + 34;
			if (y + 12 < ly + lh) {
				for (int i = 0; i < ClientPrefs.ACCENTS.length; i++) {
					int x = lx + i * 18;
					Ui.round(x, y, 14, 10, ClientPrefs.ACCENTS[i]);
					if (i == p().accent) {
						Ui.outline(x - 1, y - 1, 16, 12, Ui.TEXT);
					}
					tooltipArea(x, y, 14, 10, ClientPrefs.ACCENT_NAMES[i]);
				}
			}
		}
	}

	@Override
	protected void clicked(int mx, int my, int button) {
		if (section == 0 && !rows.isEmpty()) {
			int y = rowBase.get(rows.size() - 1) - scroll.y() + 34;
			for (int i = 0; i < ClientPrefs.ACCENTS.length; i++) {
				int x = lx + i * 18;
				if (mx >= x && mx < x + 14 && my >= y && my < y + 10) {
					p().accent = i;
					PlebClient.saveSettings();
					click();
					return;
				}
			}
		}
	}
}
