package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.plebmc.client.mods.HudBox;
import net.plebmc.client.mods.Mod;

/**
 * The HUD editor, drawn over the live game. Drag an element to move it
 * (it snaps to edges, the center and other elements), drag its corner or
 * scroll over it to resize, and pick colors and backgrounds in the side
 * panel. Elements always stay fully on screen, and an element dropped on
 * top of another is moved to the nearest free spot. Positions are stored
 * as fractions of the screen, so a layout survives resizes and GUI scales.
 */
public final class HudEditorScreen extends PlebScreen {

	private static final int SNAP = 4;
	private static final int PANEL_W = 140;

	private Mod selected;
	private Mod dragging;
	private boolean resizing;
	private int grabDX, grabDY;
	private float dragFromX, dragFromY;
	private int resizeStartX;
	private int resizeStartScale;
	/** Snap guide lines for this frame (x or y, -1 = none). */
	private int guideX = -1, guideY = -1;
	private boolean panelLeft;
	private String notice;
	private long noticeAt;
	private long resetAllArmedAt;

	public HudEditorScreen(GuiScreen parent) {
		this(parent, null);
	}

	public HudEditorScreen(GuiScreen parent, Mod select) {
		super(parent);
		this.selected = select;
	}

	private static List<Mod> hudMods() {
		List<Mod> out = new ArrayList<>();
		for (Mod m : PlebClient.mods().all()) {
			if (m.hasHud()) {
				out.add(m);
			}
		}
		return out;
	}

	// ==================== geometry ====================

	/** On-screen {x, y, w, h} of a mod's element, or null when it draws nothing. */
	private int[] rectOf(Mod m) {
		if (!m.isEnabled()) {
			return null;
		}
		HudBox box = m.preview(PlebClient.view(), PlebClient.MEASURE);
		if (box == null || box.width <= 0) {
			return null;
		}
		float sc = m.hudScale();
		int w = Math.max(1, Math.round(box.width * sc));
		int h = Math.max(1, Math.round(box.height * sc));
		int[] p = net.plebmc.client.mods.ModRegistry.place(m, w, h, this.width, this.height);
		return new int[] { p[0], p[1], w, h };
	}

	/** Puts the element's top-left at x,y (clamped on screen). */
	private void moveTo(Mod m, int x, int y, int w, int h) {
		x = Math.max(0, Math.min(this.width - w, x));
		y = Math.max(0, Math.min(this.height - h, y));
		m.setPosition((x + (m.centerAnchored() ? w / 2f : 0f)) / this.width, y / (float) this.height);
	}

	private static boolean overlaps(int[] a, int[] b) {
		return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
	}

	/** The first other element this one overlaps, or null. */
	private Mod overlapping(Mod m, int[] r) {
		for (Mod o : hudMods()) {
			if (o == m) {
				continue;
			}
			int[] or = rectOf(o);
			if (or != null && overlaps(r, or)) {
				return o;
			}
		}
		return null;
	}

	/**
	 * Moves an element off any element it covers: the nearest free spot
	 * (searching outward), or back where the drag started if there is none.
	 */
	private void resolveOverlap(Mod m) {
		int[] r = rectOf(m);
		if (r == null) {
			return;
		}
		Mod hit = overlapping(m, r);
		if (hit == null) {
			return;
		}
		for (int d = 2; d <= Math.max(this.width, this.height); d += 2) {
			int[][] tries = { { r[0], r[1] - d }, { r[0], r[1] + d }, { r[0] - d, r[1] }, { r[0] + d, r[1] },
					{ r[0] - d, r[1] - d }, { r[0] + d, r[1] - d }, { r[0] - d, r[1] + d }, { r[0] + d, r[1] + d } };
			for (int[] t : tries) {
				if (t[0] < 0 || t[1] < 0 || t[0] + r[2] > this.width || t[1] + r[3] > this.height) {
					continue;
				}
				int[] c = { t[0], t[1], r[2], r[3] };
				if (overlapping(m, c) == null) {
					moveTo(m, t[0], t[1], r[2], r[3]);
					notice("Moved " + m.name() + " so it doesn't cover " + hit.name());
					return;
				}
			}
		}
		m.setPosition(dragFromX, dragFromY);
		notice("No free space there, " + m.name() + " went back");
	}

	private void notice(String s) {
		notice = s;
		noticeAt = System.currentTimeMillis();
	}

	// ==================== panel ====================

	private int px() {
		return panelLeft ? 6 : this.width - PANEL_W - 6;
	}

	@Override
	protected void build() {
		int x = px();
		int w = PANEL_W;
		int y = 6;
		int bottom = this.height - 6;
		Button side = button("", x + w - 34, y + 5, 14, 14, () -> {
			panelLeft = !panelLeft;
			rebuild();
		});
		side.icon("drag").style(Style.GHOST).tip("Move this panel to the other side");
		Button close = button("", x + w - 18, y + 5, 14, 14, this::back);
		close.icon("close").style(Style.GHOST).tip("Done (Esc)");
		y += 24;

		if (selected == null) {
			for (Mod m : hudMods()) {
				final Mod mm = m;
				ElementRow row = new ElementRow(mm);
				row.at(x + 4, y, w - 8, 14);
				widgets.add(row);
				y += 15;
			}
			listEnd = y;
			Button resetAll = button("Reset all", x + 4, bottom - 38, w - 8, 15, () -> {
				long now = System.currentTimeMillis();
				if (now - resetAllArmedAt > 3000) {
					resetAllArmedAt = now;
					notice("Click Reset all again to put every element back");
					return;
				}
				resetAllArmedAt = 0;
				for (Mod m : hudMods()) {
					m.resetPosition();
					m.resetSettings();
				}
				PlebClient.saveSettings();
				notice("Every element is back to its default place and look");
			});
			resetAll.icon("reset").style(Style.DANGER).tip("Every element's position, size and colors back to default");
		} else {
			Mod m = selected;
			Button backBtn = button("All elements", x + 4, y, w - 8, 14, () -> {
				selected = null;
				rebuild();
			});
			backBtn.icon("back").style(Style.GHOST);
			y += 20;
			Toggle show = toggle("Show", m.isBlockedByServer() ? "Blocked by this server" : null, x + 4, y, w - 8, 18,
					() -> m.isEnabled(), () -> {
						m.setEnabled(!m.isEnabled());
						PlebClient.saveSettings();
					});
			show.enabled = !m.isBlockedByServer();
			y += 22;
			for (String key : new String[] { "scale", "color", "background" }) {
				Mod.Setting st = m.setting(key);
				if (st == null) {
					continue;
				}
				choice(st.name, null, x + 4, y, w - 8, 18, st::label, () -> {
					st.next();
					PlebClient.saveSettings();
				}, () -> {
					st.set((st.get() - 1 + st.labels.length) % st.labels.length);
					PlebClient.saveSettings();
				}).tip(st.description);
				y += 21;
			}
			y += 4;
			Button rp = button("Reset position", x + 4, y, w - 8, 15, () -> {
				m.resetPosition();
				resolveOverlapFromDefault(m);
				PlebClient.saveSettings();
			});
			rp.icon("reset");
			y += 18;
			Button rl = button("Reset look", x + 4, y, w - 8, 15, () -> {
				for (String key : new String[] { "scale", "color", "background" }) {
					Mod.Setting st = m.setting(key);
					if (st != null) {
						st.set(st.def);
					}
				}
				PlebClient.saveSettings();
			});
			rl.icon("palette");
			y += 18;
			if (!m.settings().isEmpty()) {
				Button more = button("More options", x + 4, y, w - 8, 15, () -> this.mc.displayGuiScreen(new ModSettingsScreen(this, m)));
				more.icon("settings").style(Style.GHOST).tip("Everything else " + m.name() + " can do");
			}
		}
		Button done = button("Done", x + 4, bottom - 20, w - 8, 15, this::back);
		done.style(Style.PRIMARY).icon("check");
	}

	private int listEnd;

	private void resolveOverlapFromDefault(Mod m) {
		dragFromX = m.fx();
		dragFromY = m.fy();
		resolveOverlap(m);
	}

	/** A row in the element list: eye (show/hide) and name; click selects. */
	private final class ElementRow extends W {
		final Mod m;

		ElementRow(Mod m) {
			this.m = m;
			this.tip = m.description();
		}

		@Override
		void draw(int mx, int my) {
			boolean blocked = m.isBlockedByServer();
			boolean on = m.isEnabled();
			Ui.round(x, y, w, h, Ui.mix(0x00000000, Ui.HOVER, hover));
			boolean eyeHover = mx >= x && mx < x + 14 && my >= y && my < y + h;
			Ui.icon(blocked ? "lock" : on ? "eye" : "close", x + 3, y + 3, 1,
					blocked ? Ui.BAD : on ? (eyeHover ? Ui.TEXT : Ui.accent()) : Ui.FAINT);
			Ui.text(Ui.fit(m.name(), w - 20), x + 16, y + 3, on && !blocked ? Ui.TEXT : Ui.FAINT);
		}

		@Override
		void click(int mx, int my, int button) {
			if (mx < x + 14 && !m.isBlockedByServer()) {
				m.setEnabled(!m.isEnabled());
				if (m.isEnabled()) {
					resolveOverlapFromDefault(m);
				}
				PlebClient.saveSettings();
				return;
			}
			selected = m;
			rebuild();
		}
	}

	// ==================== drawing ====================

	@Override
	protected void drawBackdrop() {
		Ui.rect(0, 0, this.width, this.height, 0x38000000);
		// faint center lines to line things up with
		Ui.rect(this.width / 2, 0, 1, this.height, 0x18FFFFFF);
		Ui.rect(0, this.height / 2, this.width, 1, 0x18FFFFFF);
	}

	@Override
	protected float opened() {
		return 1f; // the HUD shouldn't slide
	}

	@Override
	protected void drawContent(int mx, int my, float pt) {
		guideX = guideY = -1;
		if (dragging != null) {
			drag(mx, my);
		}
		int acc = Ui.accent();
		Mod hover = elementAt(mx, my);
		for (Mod m : hudMods()) {
			int[] r = rectOf(m);
			if (r == null) {
				continue;
			}
			HudBox box = m.preview(PlebClient.view(), PlebClient.MEASURE);
			boolean sel = m == selected;
			boolean bad = m.isBlockedByServer() || (m == dragging && overlapping(m, r) != null);
			int outline = bad ? Ui.BAD : sel ? acc : m == hover ? 0xC0FFFFFF : 0x50FFFFFF;
			PlebClient.drawMod(m, box, this.width, this.height, outline);
			if (sel) {
				// resize handle
				Ui.rect(r[0] + r[2] - 3, r[1] + r[3] - 3, 5, 5, acc);
				Ui.rect(r[0] + r[2] - 2, r[1] + r[3] - 2, 3, 3, 0xFFFFFFFF);
			}
		}
		if (guideX >= 0) {
			Ui.rect(guideX, 0, 1, this.height, Ui.alpha(acc, 0xC0));
		}
		if (guideY >= 0) {
			Ui.rect(0, guideY, this.width, 1, Ui.alpha(acc, 0xC0));
		}
		if (dragging != null && !resizing) {
			int[] r = rectOf(dragging);
			if (r != null) {
				String pos = r[0] + ", " + r[1];
				Ui.pill(pos, Math.min(r[0], this.width - Ui.pillWidth(pos)), Math.max(0, r[1] - 13), Ui.TEXT, 0xE0000000);
			}
		} else if (resizing && selected != null) {
			String s = Mod.SCALE_NAMES[selected.setting("scale").get()];
			int[] r = rectOf(selected);
			if (r != null) {
				Ui.pill(s, r[0] + r[2] + 4, r[1] + r[3] - 6, Ui.ON_ACCENT, acc);
			}
		}

		// the panel
		int x = px(), w = PANEL_W, h = this.height - 12;
		Ui.window(x, 6, w, h);
		Ui.icon("hud", x + 6, 13, 1, acc);
		Ui.text(selected == null ? "HUD Editor" : Ui.fit(selected.name(), w - 56), x + 19, 14, Ui.TEXT);
		if (selected == null) {
			String[] help = { "Drag to move", "Corner or wheel: size", "Arrows nudge" };
			int hy = this.height - 46 - help.length * 10;
			if (hy >= listEnd + 4) {
				for (int i = 0; i < help.length; i++) {
					Ui.text(help[i], x + 6, hy + i * 10, Ui.FAINT);
				}
			}
		}
		if (notice != null && System.currentTimeMillis() - noticeAt < 3500) {
			int nw = Math.min(this.width - PANEL_W - 30, Ui.width(notice) + 12);
			int nx = panelLeft ? PANEL_W + 14 + (this.width - PANEL_W - 14 - nw) / 2 : (this.width - PANEL_W - 14 - nw) / 2;
			Ui.card(nx, this.height - 24, nw, 16, 0xF0101418, Ui.alpha(acc, 0x90));
			Ui.text(Ui.fit(notice, nw - 10), nx + 6, this.height - 20, Ui.TEXT);
		}
		if (hover != null && dragging == null && !overPanel(mx, my)) {
			tooltip(hover.name() + (hover.isBlockedByServer() ? " (blocked by this server)" : "") + ": drag to move, scroll to resize");
		}
	}

	private boolean overPanel(int mx, int my) {
		return mx >= px() && mx < px() + PANEL_W;
	}

	/** Topmost element under the mouse (not under the panel). */
	private Mod elementAt(int mx, int my) {
		if (overPanel(mx, my)) {
			return null;
		}
		Mod hit = null;
		for (Mod m : hudMods()) {
			int[] r = rectOf(m);
			if (r != null && mx >= r[0] - 1 && mx < r[0] + r[2] + 2 && my >= r[1] - 1 && my < r[1] + r[3] + 2) {
				hit = m;
			}
		}
		return hit;
	}

	private void drag(int mx, int my) {
		int[] r = rectOf(dragging);
		if (r == null) {
			dragging = null;
			return;
		}
		if (resizing) {
			Mod.Setting st = dragging.setting("scale");
			float base = r[2] / Mod.SCALES[st.get()];
			float want = (base * Mod.SCALES[resizeStartScale] + (mx - resizeStartX)) / Math.max(1f, base);
			int best = 0;
			for (int i = 0; i < Mod.SCALES.length; i++) {
				if (Math.abs(Mod.SCALES[i] - want) < Math.abs(Mod.SCALES[best] - want)) {
					best = i;
				}
			}
			if (best != st.get()) {
				st.set(best);
				int[] nr = rectOf(dragging);
				if (nr != null) {
					moveTo(dragging, r[0], r[1], nr[2], nr[3]); // grow from the top-left, stay on screen
				}
			}
			return;
		}
		int x = mx - grabDX, y = my - grabDY;
		// snap: screen edges and center, then other elements' edges
		int cx = this.width / 2;
		if (Math.abs(x + r[2] / 2 - cx) <= SNAP) {
			x = cx - r[2] / 2;
			guideX = cx;
		} else if (Math.abs(x) <= SNAP) {
			x = 0;
		} else if (Math.abs(x + r[2] - this.width) <= SNAP) {
			x = this.width - r[2];
		}
		if (Math.abs(y) <= SNAP) {
			y = 0;
		} else if (Math.abs(y + r[3] - this.height) <= SNAP) {
			y = this.height - r[3];
		}
		for (Mod o : hudMods()) {
			int[] or = o == dragging ? null : rectOf(o);
			if (or == null) {
				continue;
			}
			if (guideX < 0 && Math.abs(x - or[0]) <= SNAP) {
				x = or[0];
				guideX = x;
			}
			if (guideY < 0) {
				if (Math.abs(y - (or[1] + or[3] + 1)) <= SNAP) {
					y = or[1] + or[3] + 1; // just below it
					guideY = y;
				} else if (Math.abs(y + r[3] + 1 - or[1]) <= SNAP) {
					y = or[1] - r[3] - 1; // just above it
					guideY = or[1];
				}
			}
		}
		moveTo(dragging, x, y, r[2], r[3]);
	}

	// ==================== input ====================

	@Override
	protected void clicked(int mx, int my, int button) {
		Mod m = elementAt(mx, my);
		if (m == null) {
			if (selected != null && !overPanel(mx, my)) {
				selected = null;
				rebuild();
			}
			return;
		}
		if (selected != m) {
			selected = m;
			rebuild();
		}
		if (button != 0 || m.isBlockedByServer()) {
			return;
		}
		int[] r = rectOf(m);
		dragging = m;
		dragFromX = m.fx();
		dragFromY = m.fy();
		resizing = mx >= r[0] + r[2] - 4 && my >= r[1] + r[3] - 4;
		if (resizing) {
			resizeStartX = mx;
			resizeStartScale = m.setting("scale") == null ? 2 : m.setting("scale").get();
			if (m.setting("scale") == null) {
				resizing = false;
			}
		}
		grabDX = mx - r[0];
		grabDY = my - r[1];
	}

	@Override
	protected void mouseReleased(int mx, int my, int button) {
		if (dragging != null) {
			Mod m = dragging;
			dragging = null;
			resizing = false;
			resolveOverlap(m);
			PlebClient.saveSettings();
		}
		super.mouseReleased(mx, my, button);
	}

	@Override
	protected void scrolled(int mx, int my, int dir) {
		Mod m = elementAt(mx, my);
		if (m == null || m.setting("scale") == null) {
			return;
		}
		Mod.Setting st = m.setting("scale");
		int v = Math.max(0, Math.min(Mod.SCALES.length - 1, st.get() + dir));
		if (v != st.get()) {
			int[] r = rectOf(m);
			st.set(v);
			int[] nr = rectOf(m);
			if (r != null && nr != null) {
				moveTo(m, r[0], r[1], nr[2], nr[3]);
			}
			resolveOverlapFromDefault(m);
			selected = m;
			rebuild();
			PlebClient.saveSettings();
		}
	}

	@Override
	protected void key(char c, int key) {
		if (selected == null) {
			return;
		}
		int dx = key == 203 ? -1 : key == 205 ? 1 : 0; // left / right
		int dy = key == 200 ? -1 : key == 208 ? 1 : 0; // up / down
		if (dx != 0 || dy != 0) {
			int[] r = rectOf(selected);
			if (r != null) {
				moveTo(selected, r[0] + dx, r[1] + dy, r[2], r[3]);
				PlebClient.saveSettings();
			}
		} else if (key == 211) { // Delete hides it
			selected.setEnabled(false);
			PlebClient.saveSettings();
		}
	}

	@Override
	protected void back() {
		dragging = null;
		super.back();
	}
}
