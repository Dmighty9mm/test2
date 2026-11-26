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

	private static final String[] SECTIONS = { "Interface", "Title screen", "Notifications", "HUD", "Performance", "Behavior",
			"Developer" };
	/** The Title screen section, for {@link #SettingsScreen(GuiScreen, int)}. */
	public static final int TITLE_SCREEN = 1;
	private static final String[] ICONS = { "palette", "home", "bell", "hud", "performance", "settings", "debug" };

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

	/** Opens on one section (for example {@link #TITLE_SCREEN}). */
	public SettingsScreen(GuiScreen parent, int section) {
		super(parent);
		this.section = Math.max(0, Math.min(SECTIONS.length - 1, section));
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

	private static String transferMsg;
	private static long transferAt;
	private static boolean transferExport;

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

		// every PlebClient setting as one line of text (never your login)
		boolean fresh = System.currentTimeMillis() - transferAt < 3500;
		Button exp = button(fresh && transferMsg != null && transferExport ? transferMsg : "Export all", rx, wy + wh - 26 - 40, rw, 16, () -> {
			transferExport = true;
			transferMsg = PlebClient.exportSettings() ? "Copied!" : "Couldn't copy";
			transferAt = System.currentTimeMillis();
			rebuild();
		});
		exp.icon("next").tip = "Copies your mods, HUD layout and settings to the clipboard (not your login)";
		Button imp = button(fresh && transferMsg != null && !transferExport ? transferMsg : "Import all", rx, wy + wh - 26 - 20, rw, 16, () -> {
			transferExport = false;
			int n = PlebClient.importSettings();
			transferMsg = n < 0 ? "Not a PlebClient export" : "Imported!";
			transferAt = System.currentTimeMillis();
			rebuild();
		});
		imp.icon("back").tip = "Copy an export, then click to load it (replaces your mods, HUD layout and settings)";

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
			add(choice("Menu background", "What menus show behind them on the title screen", lx, y, lw, h,
					() -> ClientPrefs.MENU_BACKGROUNDS[pr.menuBackground],
					saved(() -> pr.menuBackground = wrap(pr.menuBackground + 1, ClientPrefs.MENU_BACKGROUNDS.length)),
					saved(() -> pr.menuBackground = wrap(pr.menuBackground - 1, ClientPrefs.MENU_BACKGROUNDS.length))), clip);
			y += step;
			add(choice("Window style", "How see-through PlebClient windows are", lx, y, lw, h,
					() -> ClientPrefs.WINDOW_OPACITY[pr.windowOpacity],
					saved(() -> pr.windowOpacity = wrap(pr.windowOpacity + 1, ClientPrefs.WINDOW_OPACITY.length)),
					saved(() -> pr.windowOpacity = wrap(pr.windowOpacity - 1, ClientPrefs.WINDOW_OPACITY.length))), clip);
			y += step;
			add(choice("In-game backdrop", "How much of the game shows behind menus while you play", lx, y, lw, h,
					() -> ClientPrefs.BACKDROPS[pr.backdrop], saved(() -> pr.backdrop = wrap(pr.backdrop + 1, ClientPrefs.BACKDROPS.length)),
					saved(() -> pr.backdrop = wrap(pr.backdrop - 1, ClientPrefs.BACKDROPS.length))), clip);
			y += step;
			add(toggle("Animations", "Menus slide in and hovers fade. Turn off to reduce motion.", lx, y, lw, h,
					() -> pr.animations, saved(() -> pr.animations = !pr.animations)), clip);
			y += step;
			add(toggle("PlebClient menus", "The PlebClient look on the title screen and every Minecraft menu", lx, y, lw, h,
					() -> pr.modernMenus, saved(() -> pr.modernMenus = !pr.modernMenus)), clip);
			y += step;
			add(toggle("Click sounds", "Play a click in PlebClient menus", lx, y, lw, h, () -> pr.uiSounds,
					saved(() -> pr.uiSounds = !pr.uiSounds)), clip);
			y += step;
			break;
		case 1: // Title screen
			add(choice("Sidebar", "Open the sidebar when you point at it, keep it open, or show icons only", lx, y, lw, h,
					() -> ClientPrefs.SIDEBAR_MODES[pr.titleSidebar],
					saved(() -> pr.titleSidebar = wrap(pr.titleSidebar + 1, ClientPrefs.SIDEBAR_MODES.length)),
					saved(() -> pr.titleSidebar = wrap(pr.titleSidebar - 1, ClientPrefs.SIDEBAR_MODES.length))), clip);
			y += step;
			add(choice("Background dim", "How much the title screen darkens its background", lx, y, lw, h,
					() -> ClientPrefs.PANORAMA_DIMS[pr.titlePanorama],
					saved(() -> pr.titlePanorama = wrap(pr.titlePanorama + 1, ClientPrefs.PANORAMA_DIMS.length)),
					saved(() -> pr.titlePanorama = wrap(pr.titlePanorama - 1, ClientPrefs.PANORAMA_DIMS.length))), clip);
			y += step;
			add(choice("Background", "The PlebMc wallpaper or the classic Minecraft panorama", lx, y, lw, h,
					() -> ClientPrefs.BACKGROUNDS[pr.titleBackground],
					saved(() -> pr.titleBackground = wrap(pr.titleBackground + 1, ClientPrefs.BACKGROUNDS.length)),
					saved(() -> pr.titleBackground = wrap(pr.titleBackground - 1, ClientPrefs.BACKGROUNDS.length))), clip);
			y += step;
			add(toggle("Mini mascot", "The little mascot in the corner (click him)", lx, y, lw, h, () -> pr.titleBuddy,
					saved(() -> pr.titleBuddy = !pr.titleBuddy)), clip);
			y += step;
			add(choice("Particles", "Snow, embers or fireflies drifting over the title screen", lx, y, lw, h,
					() -> ClientPrefs.PARTICLES[pr.titleParticles],
					saved(() -> pr.titleParticles = wrap(pr.titleParticles + 1, ClientPrefs.PARTICLES.length)),
					saved(() -> pr.titleParticles = wrap(pr.titleParticles - 1, ClientPrefs.PARTICLES.length))), clip);
			y += step;
			add(toggle("Mascot", "The PlebMc mascot at the top of the sidebar", lx, y, lw, h, () -> pr.titleMascot,
					saved(() -> pr.titleMascot = !pr.titleMascot)), clip);
			y += step;
			add(toggle("Clock", "The time in the bottom corner", lx, y, lw, h, () -> pr.titleClock,
					saved(() -> pr.titleClock = !pr.titleClock)), clip);
			y += step;
			add(toggle("Splash text", "The yellow Minecraft splash line at the bottom", lx, y, lw, h, () -> pr.titleSplash,
					saved(() -> pr.titleSplash = !pr.titleSplash)), clip);
			y += step;
			add(choice("Accent color", "The color of highlights in every PlebClient menu", lx, y, lw, h,
					() -> ClientPrefs.ACCENT_NAMES[pr.accent], saved(() -> pr.accent = wrap(pr.accent + 1, ClientPrefs.ACCENTS.length)),
					saved(() -> pr.accent = wrap(pr.accent - 1, ClientPrefs.ACCENTS.length))), clip);
			y += step;
			break;
		case 2: // Notifications
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
		case 3: // HUD
			add(toggle("Text shadow", "A shadow under HUD text, easier to read on bright ground", lx, y, lw, h,
					() -> pr.hudShadow, saved(() -> pr.hudShadow = !pr.hudShadow)), clip);
			y += step;
			Button edit = button("Open HUD editor", lx, y, lw, 20, () -> this.mc.displayGuiScreen(new HudEditorScreen(this)));
			edit.style(Style.PRIMARY).icon("drag");
			add(edit, clip);
			y += 26;
			break;
		case 4: // Performance
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
		case 5: // Behavior
			add(choice("Right Shift opens", "The screen Right Shift takes you to", lx, y, lw, h,
					() -> ClientPrefs.OPENS[pr.shiftOpens], saved(() -> pr.shiftOpens = wrap(pr.shiftOpens + 1, ClientPrefs.OPENS.length)),
					saved(() -> pr.shiftOpens = wrap(pr.shiftOpens - 1, ClientPrefs.OPENS.length))), clip);
			y += step;
			add(toggle("RGB text colors", "Show hex colors and gradients from newer servers instead of the 16 old colors",
					lx, y, lw, h, () -> pr.rgbText, saved(() -> pr.rgbText = !pr.rgbText)), clip);
			y += step;
			add(toggle("Clean holograms", "Hide \"Armor Stand\" labels and armor stand bodies on server holograms", lx,
					y, lw, h, () -> pr.cleanHolograms, saved(() -> pr.cleanHolograms = !pr.cleanHolograms)), clip);
			y += step;
			add(toggle("Custom item models", net.plebmc.client.itemrender.PlebItems.status(), lx, y, lw, h,
					() -> pr.customItemModels, saved(() -> pr.customItemModels = !pr.customItemModels)), clip);
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
		if (section == 0 || section == TITLE_SCREEN) {
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
		if ((section == 0 || section == TITLE_SCREEN) && !rows.isEmpty()) {
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
