package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.plebmc.client.mods.ClientPrefs;
import net.plebmc.client.mods.Mod;

/**
 * Video Settings, laid out like the Sodium mod's: pages along the top,
 * one compact row per option, and a panel that explains the option under the
 * mouse and how much it costs. Minecraft's options are found by name, so an
 * option this version of the game doesn't have is simply left out.
 */
public final class VideoOptionsScreen extends PlebScreen {

	private static final String[] PAGES = { "General", "Quality", "Performance", "Advanced" };
	private static final String[] PAGE_ICONS = { "settings", "palette", "performance", "debug" };
	private static final String[] IMPACT = { "None", "Low", "Medium", "High", "Varies" };
	private static final int[] IMPACT_COLOR = { 0xFF8A96A0, 0xFF4FE39A, 0xFFF5B942, 0xFFFF6A5A, 0xFF4C9BFF };

	/** Lets {@link PlebClient#swapScreen} open Minecraft's own list once. */
	static boolean openVanilla;

	private static int page;
	private final Scroll scroll = new Scroll();
	private final List<W> rows = new ArrayList<>();
	private final List<Integer> rowBase = new ArrayList<>();
	/** What each row is: its label, description and performance impact. */
	private static final class Info {
		final String label, desc;
		final int impact;

		Info(String label, String desc, int impact) {
			this.label = label;
			this.desc = desc == null ? "" : desc;
			this.impact = impact;
		}
	}

	/** One per row, in the same order as {@link #rows}. */
	private final List<Info> info = new ArrayList<>();
	private int wx, wy, ww, wh;
	private int lx, ly, lw, lh;
	private int dx, dw;
	private W lastHover;
	private long resetArmedAt;

	public VideoOptionsScreen(GuiScreen parent) {
		super(parent);
	}

	private GameSettings gs() {
		return this.mc.gameSettings;
	}

	private static GameSettings.Options option(String name) {
		try {
			return GameSettings.Options.valueOf(name);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** The value part of Minecraft's "Name: value" text for an option. */
	private String valueOf(GameSettings.Options o) {
		String s = gs().getKeyBinding(o);
		int i = s == null ? -1 : s.lastIndexOf(": ");
		return s == null ? "" : i >= 0 ? s.substring(i + 2) : s;
	}

	@Override
	protected void build() {
		ww = Math.min(this.width - 16, 480);
		wh = Math.min(this.height - 16, 300);
		wx = (this.width - ww) / 2;
		wy = (this.height - wh) / 2;
		closeButton(wx + ww - 22, wy + 10);
		rows.clear();
		rowBase.clear();
		info.clear();

		// pages along the top
		int tx = wx + 8, ty = wy + 40;
		int tw = (ww - 16 - 6 * (PAGES.length - 1)) / PAGES.length;
		for (int i = 0; i < PAGES.length; i++) {
			final int idx = i;
			tab(PAGES[i], PAGE_ICONS[i], 0, tx + i * (tw + 6), ty, tw, 18, () -> page == idx, () -> {
				page = idx;
				scroll.reset();
				rebuild();
			});
		}

		boolean wide = ww >= 360;
		dw = wide ? Math.max(120, ww * 2 / 7) : 0;
		lx = wx + 8;
		ly = ty + 24;
		lw = ww - 16 - (wide ? dw + 8 : 0);
		lh = wy + wh - 30 - ly;
		dx = lx + lw + 8;

		Button done = button("Done", wx + ww - 8 - 90, wy + wh - 24, 90, 16, this::back);
		done.style(Style.PRIMARY);

		int[] clip = { lx - 1, ly, lw + 2, lh };
		int y = ly;
		final int h = 20, step = 22;
		ClientPrefs pr = ClientPrefs.get();
		switch (page) {
		case 0:
			y = opt(y, clip, "RENDER_DISTANCE", "Render distance", "How many chunks you can see in every direction. The biggest cost in the game.", 3);
			y = opt(y, clip, "FRAMERATE_LIMIT", "Max framerate", "The most frames drawn each second. Lower saves battery and keeps laptops cool.", 2);
			y = opt(y, clip, "EAGLER_VSYNC", "VSync", "Matches frames to your screen's refresh rate. Smoother, but caps your FPS and adds a little input delay.", 2);
			y = opt(y, clip, "ENABLE_VSYNC", "VSync", "Matches frames to your screen's refresh rate. Smoother, but caps your FPS and adds a little input delay.", 2);
			y = opt(y, clip, "GAMMA", "Brightness", "How bright dark places look. Free.", 0);
			y = opt(y, clip, "FOV", "Field of view", "How wide you can see. Wider draws a few more chunks at the edges.", 1);
			y = opt(y, clip, "GUI_SCALE", "GUI scale", "The size of menus and the HUD.", 0);
			y = opt(y, clip, "VIEW_BOBBING", "View bobbing", "Your view sways as you walk.", 0);
			y = opt(y, clip, "FULLSCREEN", "Fullscreen", "Fill the whole screen with the game.", 0);
			y = opt(y, clip, "USE_FULLSCREEN", "Fullscreen", "Fill the whole screen with the game.", 0);
			break;
		case 1:
			y = opt(y, clip, "GRAPHICS", "Graphics", "Fancy draws see-through leaves, nicer water and shadows. Fast is much quicker in forests.", 3);
			y = opt(y, clip, "RENDER_CLOUDS", "Clouds", "Off is fastest, Fast draws flat clouds, Fancy draws 3D ones.", 1);
			y = opt(y, clip, "AMBIENT_OCCLUSION", "Smooth lighting", "Soft shadows in corners and under blocks. Costs a little when chunks load.", 1);
			y = opt(y, clip, "PARTICLES", "Particles", "How many particles you see. Fewer is faster in fights and near farms.", 2);
			y = opt(y, clip, "ENTITY_SHADOWS", "Entity shadows", "The round shadow under mobs and players.", 1);
			y = opt(y, clip, "MIPMAP_LEVELS", "Mipmap levels", "Smooths far away block textures. Changing it reloads textures.", 1);
			y = opt(y, clip, "FXAA", "Antialiasing (FXAA)", "Smooths jagged edges. Costs a little on slow graphics chips.", 2);
			y = opt(y, clip, "FOG", "Fog", "The haze at the edge of your render distance.", 1);
			y = opt(y, clip, "EAGLER_DYNAMIC_LIGHTS", "Dynamic lights", "Torches in hand and burning mobs light up around them. Costs a lot.", 3);
			y = opt(y, clip, "OF_BETTER_GRASS", "Better grass", "Grass color on the sides of grass blocks.", 1);
			y = opt(y, clip, "OF_CONNECTED_TEXTURES", "Connected textures", "Glass and other blocks join up into one texture (with a resource pack that has them).", 1);
			y = opt(y, clip, "OF_SMART_LEAVES", "Smart leaves", "Hides leaf faces you can't see. Faster with Fancy graphics.", 0);
			y = opt(y, clip, "OF_CUSTOM_SKIES", "Custom skies", "Skies from your resource pack.", 1);
			y = opt(y, clip, "OF_CUSTOM_ITEMS", "Custom items", "Item textures from your resource pack.", 1);
			y = opt(y, clip, "BLOCK_ALTERNATIVES", "Block variations", "Random rotations for grass, stone and other blocks so the ground looks less tiled.", 0);
			break;
		case 2:
			y = add(y, clip, choice("Resolution", null, lx, y, lw, h, FpsBoost::scaleLabel, () -> FpsBoost.cycleScale(1),
					() -> FpsBoost.cycleScale(-1)), "Resolution",
					"Draws the game at a lower resolution and stretches it. Big FPS gain on weak computers and big screens.", 3);
			Mod boost = PlebClient.mods().fpsBoost;
			Toggle tb = toggle("FPS Boost", null, lx, y, lw, h, () -> boost.isEnabled() && !boost.isBlockedByServer(),
					saved(() -> boost.setEnabled(!boost.isEnabled())));
			tb.enabled = !boost.isBlockedByServer();
			y = add(y, clip, tb, "FPS Boost", boost.description(), 3);
			y = add(y, clip, choice("Entity distance", null, lx, y, lw, h, () -> ClientPrefs.ENTITY_DISTANCES[pr.entityDistance],
					saved(() -> pr.entityDistance = wrap(pr.entityDistance + 1, ClientPrefs.ENTITY_DISTANCES.length)),
					saved(() -> pr.entityDistance = wrap(pr.entityDistance - 1, ClientPrefs.ENTITY_DISTANCES.length))),
					"Entity distance",
					"Mobs, dropped items and armor stands farther than this aren't drawn. Players always are. Great near big farms.", 2);
			y = add(y, clip, choice("Block entity distance", null, lx, y, lw, h,
					() -> ClientPrefs.BLOCK_ENTITY_DISTANCES[pr.blockEntityDistance],
					saved(() -> pr.blockEntityDistance = wrap(pr.blockEntityDistance + 1, ClientPrefs.BLOCK_ENTITY_DISTANCES.length)),
					saved(() -> pr.blockEntityDistance = wrap(pr.blockEntityDistance - 1, ClientPrefs.BLOCK_ENTITY_DISTANCES.length))),
					"Block entity distance",
					"Chests, signs, banners and heads farther than this aren't drawn. Huge in spawns full of signs and chests.", 2);
			y = add(y, clip, choice("Max particles", null, lx, y, lw, h, () -> ClientPrefs.PARTICLE_CAPS[pr.maxParticles],
					saved(() -> pr.maxParticles = wrap(pr.maxParticles + 1, ClientPrefs.PARTICLE_CAPS.length)),
					saved(() -> pr.maxParticles = wrap(pr.maxParticles - 1, ClientPrefs.PARTICLE_CAPS.length))),
					"Max particles", "The most particles of one kind alive at once. Lower keeps explosions and fights smooth.", 2);
			y = add(y, clip, choice("Menu framerate", null, lx, y, lw, h, () -> ClientPrefs.MENU_FPS[pr.menuFps],
					saved(() -> pr.menuFps = wrap(pr.menuFps + 1, ClientPrefs.MENU_FPS.length)),
					saved(() -> pr.menuFps = wrap(pr.menuFps - 1, ClientPrefs.MENU_FPS.length))), "Menu framerate",
					"The frame cap on the title screen and menus outside a world. Higher makes the menus smoother.", 1);
			y = add(y, clip, choice("Background framerate", null, lx, y, lw, h, () -> ClientPrefs.IDLE_FPS[pr.idleFps],
					saved(() -> pr.idleFps = wrap(pr.idleFps + 1, ClientPrefs.IDLE_FPS.length)),
					saved(() -> pr.idleFps = wrap(pr.idleFps - 1, ClientPrefs.IDLE_FPS.length))), "Background framerate",
					"Caps frames while you're in another window, so the game stops eating your computer. Works with VSync on too.", 0);
			y = opt(y, clip, "CHUNK_FIX", "Chunk fix", "Fixes chunks that load with holes in them, at a small cost.", 1);
			y = opt(y, clip, "USE_VBO", "Use VBOs", "Sends chunks to the graphics card in one go. Usually faster.", 4);
			y = opt(y, clip, "FBO_ENABLE", "Framebuffer", "Needed by some effects. Leave on unless your graphics driver has trouble.", 4);
			break;
		default:
			y = add(y, clip, toggle("PlebClient video menu", null, lx, y, lw, h, () -> pr.videoMenu,
					saved(() -> pr.videoMenu = !pr.videoMenu)), "PlebClient video menu",
					"Video Settings opens this screen. Turn off to get Minecraft's own list back.", 0);
			Mod fps = PlebClient.mods().fps;
			y = add(y, clip, toggle("FPS counter", null, lx, y, lw, h, fps::isEnabled, saved(() -> fps.setEnabled(!fps.isEnabled()))),
					"FPS counter", "Shows your frame rate on the HUD, so you can see what each option does.", 0);
			Button van = button("Open Minecraft's video settings", lx, y, lw, h, () -> {
				openVanilla = true;
				PlebClient.saveSettings();
				this.mc.displayGuiScreen(VideoCompat.vanilla(this.mc, this.parent));
			});
			van.icon("settings");
			y = add(y, clip, van, "Minecraft's video settings", "The game's own list, with every option it has.", 0);
			boolean armed = System.currentTimeMillis() - resetArmedAt < 4000;
			Button reset = button(armed ? "Click again to confirm" : "Reset performance options", lx, y, lw, h, () -> {
				if (System.currentTimeMillis() - resetArmedAt > 4000) {
					resetArmedAt = System.currentTimeMillis();
				} else {
					resetArmedAt = 0;
					ClientPrefs d = new ClientPrefs();
					pr.entityDistance = d.entityDistance;
					pr.blockEntityDistance = d.blockEntityDistance;
					pr.maxParticles = d.maxParticles;
					pr.menuFps = d.menuFps;
					pr.idleFps = d.idleFps;
					PlebClient.saveSettings();
				}
				rebuild();
			});
			reset.style(Style.DANGER).icon("reset");
			y = add(y, clip, reset, "Reset", "Puts the PlebClient performance options back to default (not Minecraft's).", 0);
			break;
		}
		scroll.set(ly, lh, y - ly);
	}

	private static int wrap(int v, int n) {
		return ((v % n) + n) % n;
	}

	private static Runnable saved(Runnable r) {
		return () -> {
			r.run();
			PlebClient.saveSettings();
		};
	}

	private int add(int y, int[] clip, W w, String label, String desc, int impact) {
		w.clip = clip;
		rows.add(w);
		rowBase.add(w.y);
		info.add(new Info(label, desc, impact));
		return y + 22;
	}

	/** One of Minecraft's options, as a slider, switch or list; skipped if this game doesn't have it. */
	private int opt(int y, int[] clip, String name, String label, String desc, int impact) {
		final GameSettings.Options o = option(name);
		if (o == null) {
			return y;
		}
		final int h = 20;
		W w;
		if (o.getEnumFloat()) {
			double span = o.denormalizeValue(1f) - o.denormalizeValue(0f);
			double unit = "FRAMERATE_LIMIT".equals(name) ? 10 : 1;
			int steps = "GAMMA".equals(name) ? 0 : (int) Math.max(1, Math.round(span / unit));
			Slider s = slider(label, null, lx, y, lw, h, () -> o.normalizeValue(gs().getOptionFloatValue(o)), v -> {
				float val = o.denormalizeValue((float) v);
				if (val != gs().getOptionFloatValue(o)) {
					gs().setOptionFloatValue(o, val);
				}
			}, v -> valueOf(o), steps);
			w = s;
		} else if (o.getEnumBoolean()) {
			w = toggle(label, null, lx, y, lw, h, () -> gs().getOptionOrdinalValue(o), () -> {
				gs().setOptionValue(o, 1);
				gs().saveOptions();
			});
		} else {
			Runnable next = () -> {
				int scale = gs().guiScale;
				gs().setOptionValue(o, 1);
				gs().saveOptions();
				if (gs().guiScale != scale) {
					VideoCompat.guiScaleChanged(this.mc, this);
				}
			};
			w = choice(label, null, lx, y, lw, h, () -> valueOf(o), next, next);
		}
		w.enabled = VideoCompat.available(name);
		return add(y, clip, w, label, VideoCompat.available(name) ? desc : desc + " (Not available on this device.)", impact);
	}

	@Override
	protected void scrolled(int mx, int my, int dir) {
		scroll.wheel(dir);
	}

	@Override
	protected void tick() {
		if (resetArmedAt != 0 && System.currentTimeMillis() - resetArmedAt > 4000) {
			resetArmedAt = 0;
			rebuild();
		}
	}

	@Override
	protected void drawContent(int mx, int my, float pt) {
		Ui.window(wx, wy, ww, wh);
		header(wx, wy, ww, "performance", "Video Settings", "PlebClient " + PAGES[page].toLowerCase() + " options", false);
		int off = scroll.y();
		for (int i = 0; i < rows.size(); i++) {
			rows.get(i).y = rowBase.get(i) - off;
		}
		scroll.drawBar(lx + lw + 2);
		Ui.text("Changes save as you make them", wx + 10, wy + wh - 20, Ui.FAINT);
		if (dw <= 0) {
			return;
		}
		// the description panel: what the option under the mouse does
		W hv = hovered();
		if (hv != null && rows.contains(hv)) {
			lastHover = hv;
		} else if (lastHover != null && !rows.contains(lastHover)) {
			lastHover = null;
		}
		int py = ly, ph = lh;
		Ui.card(dx, py, dw, ph, 0xFF0D1217, Ui.BORDER);
		int at = lastHover == null ? -1 : rows.indexOf(lastHover);
		Info in = at < 0 || at >= info.size() ? null : info.get(at);
		if (in == null) {
			Ui.icon("performance", dx + 8, py + 8, 1, Ui.accent());
			Ui.text("Point at an option", dx + 20, py + 9, Ui.TEXT);
			int yy = py + 24;
			for (String line : Ui.wrap("to see what it does and how much it costs.", dw - 16)) {
				Ui.text(line, dx + 8, yy, Ui.MUTED);
				yy += 10;
			}
			return;
		}
		Ui.text(Ui.fit(in.label, dw - 16), dx + 8, py + 8, Ui.accent());
		int yy = py + 22;
		for (String line : Ui.wrap(in.desc, dw - 16)) {
			if (yy > py + ph - 34) {
				break;
			}
			Ui.text(line, dx + 8, yy, Ui.MUTED);
			yy += 10;
		}
		int imp = in.impact;
		Ui.rect(dx + 8, py + ph - 30, dw - 16, 1, Ui.BORDER_SOFT);
		Ui.text("Performance impact", dx + 8, py + ph - 24, Ui.FAINT);
		Ui.text(IMPACT[imp], dx + 8, py + ph - 13, IMPACT_COLOR[imp]);
	}

	@Override
	protected void clicked(int mx, int my, int button) {
		if (mx >= wx + 30 && mx < wx + 170 && my >= wy + 6 && my < wy + 32) {
			EasterEggs.videoTitleClicked();
		}
	}

	@Override
	protected void back() {
		this.mc.gameSettings.saveOptions();
		PlebClient.saveSettings();
		this.mc.displayGuiScreen(VideoCompat.closeTo(this.mc, this.parent));
		if (this.parent == null && this.mc.theWorld != null) {
			this.mc.setIngameFocus();
		}
	}
}
