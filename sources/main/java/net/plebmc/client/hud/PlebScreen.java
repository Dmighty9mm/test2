package net.plebmc.client.hud;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import net.lax1dude.eaglercraft.v1_8.Mouse;
import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.ResourceLocation;
import net.plebmc.client.cosmetics.Cosmetics;
import net.plebmc.client.mods.ClientPrefs;

/**
 * Base for every PlebMc screen: the backdrop, an opening animation, PlebMc
 * widgets (buttons, toggles, tabs, choice buttons) with eased hover states,
 * tooltips, mouse-wheel scrolling and a shared header. Each screen builds
 * its own layout in {@link #build}; this class only gives them the same
 * look and feel.
 */
public abstract class PlebScreen extends GuiScreen {

	protected final GuiScreen parent;
	private long openedAt;
	private long lastFrame;
	protected final List<W> widgets = new ArrayList<>();
	protected final List<GuiTextField> fields = new ArrayList<>();
	private String tooltip;
	protected int mouseX;
	protected int mouseY;

	protected PlebScreen(GuiScreen parent) {
		this.parent = parent;
	}

	// ==================== widgets ====================

	/** A clickable area with an eased hover value. */
	public abstract class W {
		public int x, y, w, h;
		public boolean visible = true;
		public boolean enabled = true;
		public String tip;
		float hover;
		/** When set {x, y, w, h}: only drawn and clickable inside it (scrolling lists). */
		public int[] clip;

		W at(int x, int y, int w, int h) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			return this;
		}

		public W tip(String t) {
			tip = t;
			return this;
		}

		boolean in(int mx, int my) {
			if (clip != null && (mx < clip[0] || my < clip[1] || mx >= clip[0] + clip[2] || my >= clip[1] + clip[3])) {
				return false;
			}
			return visible && mx >= x && my >= y && mx < x + w && my < y + h;
		}

		abstract void draw(int mx, int my);

		void click(int mx, int my, int button) {
		}

		boolean clickSound() {
			return true;
		}
	}

	public enum Style {
		PRIMARY, SECONDARY, GHOST, DANGER
	}

	/** A button: label, optional icon, a style and an action. */
	public class Button extends W {
		public String label;
		public String icon;
		public Style style = Style.SECONDARY;
		public Runnable action;
		public Runnable rightAction;

		Button(String label, Runnable action) {
			this.label = label;
			this.action = action;
		}

		public Button icon(String i) {
			icon = i;
			return this;
		}

		public Button style(Style s) {
			style = s;
			return this;
		}

		@Override
		void draw(int mx, int my) {
			int acc = Ui.accent();
			int fill, border, fg;
			float hv = enabled ? hover : 0f;
			switch (style) {
			case PRIMARY:
				fill = enabled ? Ui.mix(acc, 0xFFFFFFFF, hv * 0.18f) : Ui.RAISED_2;
				border = enabled ? Ui.mix(acc, 0xFFFFFFFF, 0.25f) : Ui.BORDER;
				fg = enabled ? Ui.ON_ACCENT : Ui.FAINT;
				break;
			case DANGER:
				fill = Ui.mix(0xF2321A1E, 0xF2512228, hv);
				border = Ui.mix(0xFF5A2A30, Ui.BAD, hv);
				fg = enabled ? 0xFFFFB3B3 : Ui.FAINT;
				break;
			case GHOST:
				fill = Ui.mix(0x00000000, 0x30FFFFFF, hv * 0.6f);
				border = 0;
				fg = enabled ? Ui.mix(Ui.MUTED, Ui.TEXT, hv) : Ui.FAINT;
				break;
			default:
				fill = Ui.mix(Ui.RAISED, Ui.HOVER, hv);
				border = Ui.mix(Ui.BORDER, acc, hv * 0.8f);
				fg = enabled ? Ui.TEXT : Ui.FAINT;
				break;
			}
			if (style == Style.PRIMARY && enabled) {
				if (hv > 0.01f) {
					Ui.round(x - 1, y - 1, w + 2, h + 2, Ui.alpha(acc, Math.round(0x50 * hv)));
				}
				PlebTheme.gradientRound(x, y, w, h, Ui.mix(fill, 0xFFFFFFFF, 0.12f), Ui.mix(fill, 0xFF000000, 0.18f));
				Ui.rect(x + 1, y, w - 2, 1, 0x50FFFFFF);
			} else if (border != 0) {
				PlebTheme.gradientRound(x, y, w, h, Ui.mix(fill, 0xFFFFFFFF, 0.04f), Ui.mix(fill, 0xFF000000, 0.12f));
				Ui.outline(x, y, w, h, border);
				if (style == Style.SECONDARY && enabled && hv > 0.01f) {
					int uw = Math.round((w - 4) * hv);
					Ui.rect(x + w / 2 - uw / 2, y + h - 1, uw, 1, acc);
				}
			} else {
				Ui.round(x, y, w, h, fill);
			}
			int iw = icon == null ? 0 : 9 + (label.isEmpty() ? 0 : 4);
			int tw = Ui.width(label);
			int cx = x + (w - iw - tw) / 2;
			if (icon != null) {
				Ui.icon(icon, cx, y + (h - 9) / 2, 1, style == Style.PRIMARY && enabled ? Ui.ON_ACCENT : Ui.mix(fg, acc, style == Style.SECONDARY ? hv : 0f));
			}
			if (!label.isEmpty()) {
				Ui.text(Ui.fit(label, w - 6 - iw), cx + iw, y + (h - 8) / 2, fg);
			}
		}

		@Override
		void click(int mx, int my, int button) {
			if (button == 1 && rightAction != null) {
				rightAction.run();
			} else if (button == 0 && action != null) {
				action.run();
			}
		}
	}

	/** A tab / filter pill: icon, label, optional count, and a selected state. */
	public class Tab extends W {
		public String label;
		public String icon;
		public String badge;
		public int color;
		public BooleanSupplier selected;
		public Runnable action;
		/** Draws a small dot (something new). */
		public boolean dot;
		/** Icon and label on the left, badge on the right (side rails). */
		public boolean rail;

		Tab(String label, String icon, int color, BooleanSupplier selected, Runnable action) {
			this.label = label;
			this.icon = icon;
			this.color = color;
			this.selected = selected;
			this.action = action;
		}

		@Override
		void draw(int mx, int my) {
			boolean sel = selected.getAsBoolean();
			int c = color == 0 ? Ui.accent() : color;
			int fill = sel ? Ui.mix(Ui.RAISED, c, 0.16f) : Ui.mix(0x00000000, Ui.RAISED_2, hover);
			Ui.round(x, y, w, h, fill);
			if (sel) {
				Ui.outline(x, y, w, h, Ui.alpha(c, 0xB0));
				Ui.rect(x + 3, y + h - 2, w - 6, 1, c);
			}
			int fg = sel ? Ui.TEXT : Ui.mix(Ui.MUTED, Ui.TEXT, hover);
			int bw = badge == null ? 0 : Ui.pillWidth(badge) + 4;
			int iw = icon == null ? 0 : 13;
			int tw = Ui.width(label);
			int cx = rail ? x + 6 : x + (w - iw - tw - bw) / 2;
			if (icon != null) {
				Ui.icon(icon, cx, y + (h - 9) / 2, 1, sel ? c : Ui.mix(Ui.FAINT, c, hover));
			}
			Ui.text(rail ? Ui.fit(label, w - 12 - iw - bw) : label, cx + iw, y + (h - 8) / 2, fg);
			if (badge != null) {
				int bx = rail ? x + w - bw - 2 : cx + iw + tw + 4;
				Ui.pill(badge, bx, y + (h - 11) / 2, sel ? Ui.ON_ACCENT : Ui.MUTED, sel ? c : 0xFF0A0E12);
			}
			if (dot) {
				int dx = rail && badge != null ? x + w - bw - 6 : x + w - 6;
				Ui.round(dx, rail ? y + (h - 3) / 2 : y + 3, 3, 3, Ui.GOLD);
			}
		}

		@Override
		void click(int mx, int my, int button) {
			if (button == 0 && action != null) {
				action.run();
			}
		}
	}

	/** An on/off switch with a label and description. */
	public class Toggle extends W {
		public String label;
		public String sub;
		public BooleanSupplier get;
		public Runnable flip;
		float knob = -1f;

		Toggle(String label, String sub, BooleanSupplier get, Runnable flip) {
			this.label = label;
			this.sub = sub;
			this.get = get;
			this.flip = flip;
		}

		@Override
		void draw(int mx, int my) {
			boolean on = get.getAsBoolean();
			Ui.round(x, y, w, h, Ui.mix(Ui.RAISED, Ui.HOVER, hover));
			int ty = sub == null ? y + (h - 8) / 2 : y + (h - 18) / 2;
			Ui.text(Ui.fit(label, w - 40), x + 8, ty, enabled ? Ui.TEXT : Ui.FAINT);
			if (sub != null) {
				Ui.text(Ui.fit(sub, w - 44), x + 8, ty + 10, Ui.FAINT);
			}
			float target = on ? 1f : 0f;
			knob = knob < 0 ? target : ease(knob, target, 18f);
			int sw = 20, sh = 10;
			int sx = x + w - sw - 8, sy = y + (h - sh) / 2;
			int track = enabled ? Ui.mix(0xFF2A333C, Ui.accent(), knob) : 0xFF20262C;
			Ui.round(sx, sy, sw, sh, track);
			int kx = sx + 1 + Math.round((sw - 10) * knob);
			Ui.round(kx, sy + 1, 8, 8, enabled ? 0xFFF4FAFC : 0xFF59636B);
		}

		@Override
		void click(int mx, int my, int button) {
			if (button == 0 && flip != null) {
				flip.run();
			}
		}
	}

	/** A setting that cycles through values: "Name ........ < Value >". */
	public class Choice extends W {
		public String label;
		public String sub;
		public java.util.function.Supplier<String> value;
		public Runnable next;
		public Runnable prev;

		Choice(String label, String sub, java.util.function.Supplier<String> value, Runnable next, Runnable prev) {
			this.label = label;
			this.sub = sub;
			this.value = value;
			this.next = next;
			this.prev = prev;
		}

		@Override
		void draw(int mx, int my) {
			Ui.round(x, y, w, h, Ui.mix(Ui.RAISED, Ui.HOVER, hover));
			int ty = sub == null ? y + (h - 8) / 2 : y + (h - 18) / 2;
			String v = value.get();
			int vw = Math.min(w / 2, Ui.width(v) + 22);
			Ui.text(Ui.fit(label, w - vw - 16), x + 8, ty, enabled ? Ui.TEXT : Ui.FAINT);
			if (sub != null) {
				Ui.text(Ui.fit(sub, w - vw - 16), x + 8, ty + 10, Ui.FAINT);
			}
			int vx = x + w - vw - 6;
			int vy = y + (h - 14) / 2;
			Ui.card(vx, vy, vw, 14, 0xFF0D1217, Ui.mix(Ui.BORDER, Ui.accent(), hover));
			Ui.text("<", vx + 4, vy + 3, Ui.FAINT);
			Ui.text(">", vx + vw - 9, vy + 3, Ui.FAINT);
			Ui.centered(Ui.fit(v, vw - 20), vx + vw / 2, vy + 3, Ui.accent());
		}

		@Override
		void click(int mx, int my, int button) {
			int vw = Math.min(w / 2, Ui.width(value.get()) + 22);
			boolean left = button == 1 || (mx < x + w - vw - 6 + vw / 3 && mx >= x + w - vw - 6);
			if (left && prev != null) {
				prev.run();
			} else if (next != null) {
				next.run();
			}
		}
	}

	protected Button button(String label, int x, int y, int w, int h, Runnable action) {
		Button b = new Button(label, action);
		b.at(x, y, w, h);
		widgets.add(b);
		return b;
	}

	protected Tab tab(String label, String icon, int color, int x, int y, int w, int h, BooleanSupplier selected,
			Runnable action) {
		Tab t = new Tab(label, icon, color, selected, action);
		t.at(x, y, w, h);
		widgets.add(t);
		return t;
	}

	protected Toggle toggle(String label, String sub, int x, int y, int w, int h, BooleanSupplier get, Runnable flip) {
		Toggle t = new Toggle(label, sub, get, flip);
		t.at(x, y, w, h);
		widgets.add(t);
		return t;
	}

	protected Choice choice(String label, String sub, int x, int y, int w, int h,
			java.util.function.Supplier<String> value, Runnable next, Runnable prev) {
		Choice c = new Choice(label, sub, value, next, prev);
		c.at(x, y, w, h);
		widgets.add(c);
		return c;
	}

	protected GuiTextField field(int x, int y, int w, int h, int max) {
		GuiTextField f = new GuiTextField(fields.size(), this.fontRendererObj, x, y, w, h);
		f.setMaxStringLength(max);
		if (fields.size() < keepText.size()) {
			f.setText(keepText.get(fields.size())); // typed text survives a rebuild (and build() can read it)
			if (fields.size() == keepFocus) {
				f.setFocused(true);
			}
		}
		f.setEnableBackgroundDrawing(false);
		fields.add(f);
		return f;
	}

	// ==================== lifecycle ====================

	/** Lays out the widgets for the current size (called on open, resize and {@link #rebuild}). */
	protected abstract void build();

	/** Draws everything that isn't a widget; widgets and tooltips are drawn after it. */
	protected abstract void drawContent(int mx, int my, float pt);

	@Override
	public final void initGui() {
		if (openedAt == 0) {
			openedAt = System.currentTimeMillis();
		}
		rebuild();
	}

	private List<String> keepText = new ArrayList<>();
	private int keepFocus = -1;

	protected void rebuild() {
		List<String> keep = new ArrayList<>();
		keepFocus = -1;
		for (GuiTextField f : fields) {
			if (f.isFocused()) {
				keepFocus = keep.size();
			}
			keep.add(f.getText());
		}
		keepText = keep;
		widgets.clear();
		fields.clear();
		build();
		keepText = new ArrayList<>();
		keepFocus = -1;
	}

	/** 0..1 for the opening animation. */
	protected float opened() {
		if (!ClientPrefs.get().animations) {
			return 1f;
		}
		float t = (System.currentTimeMillis() - openedAt) / 200f;
		t = Math.max(0f, Math.min(1f, t));
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}

	/** Moves {@code v} toward {@code target}; {@code speed} per second. Instant when animations are off. */
	protected float ease(float v, float target, float speed) {
		if (!ClientPrefs.get().animations) {
			return target;
		}
		float dt = Math.min(0.1f, frameDt);
		return v + (target - v) * Math.min(1f, dt * speed);
	}

	private float frameDt;

	@Override
	public void drawScreen(int mx, int my, float pt) {
		long now = System.currentTimeMillis();
		frameDt = lastFrame == 0 ? 0f : (now - lastFrame) / 1000f;
		lastFrame = now;
		mouseX = mx;
		mouseY = my;
		drawBackdrop();
		float o = opened();
		GlStateManager.pushMatrix();
		GlStateManager.translate(0f, (1f - o) * 10f, 0f);
		drawContent(mx, my, pt);
		tooltip = null;
		W top = hit(mx, my);
		for (W w : widgets) {
			if (!w.visible) {
				continue;
			}
			w.hover = ease(w.hover, w == top && w.enabled ? 1f : 0f, 16f);
			if (w.clip != null) {
				if (w.y + w.h <= w.clip[1] || w.y >= w.clip[1] + w.clip[3]) {
					continue; // scrolled out of view
				}
				Ui.clip(w.clip[0], w.clip[1], w.clip[2], w.clip[3], this.width, this.height);
				w.draw(mx, my);
				Ui.unclip();
			} else {
				w.draw(mx, my);
			}
			if (w == top && w.tip != null) {
				tooltip = w.tip;
			}
		}
		for (GuiTextField f : fields) {
			f.drawTextBox();
		}
		drawOverlay(mx, my, pt);
		GlStateManager.popMatrix();
		if (tooltip != null) {
			drawTooltip(tooltip, mx, my);
		}
	}

	/** Drawn above the widgets (popups, drag previews). */
	protected void drawOverlay(int mx, int my, float pt) {
	}

	protected void drawBackdrop() {
		if (this.mc != null && this.mc.theWorld != null) {
			Ui.rect(0, 0, this.width, this.height, ClientPrefs.get().backdropColor());
			if (PlebTheme.enabled()) {
				PlebTheme.glow(this.width / 2f, -this.height * 0.2f, this.width * 0.6f, Ui.alpha(Ui.accent(), 0x28));
			}
		} else {
			this.drawDefaultBackground();
			Ui.rect(0, 0, this.width, this.height, 0x90080B10);
		}
	}

	/** Shows a tooltip this frame (for things that aren't widgets). */
	protected void tooltip(String s) {
		tooltip = s;
	}

	protected void drawTooltip(String s, int mx, int my) {
		List<String> lines = Ui.wrap(s, 170);
		int w = 0;
		for (String l : lines) {
			w = Math.max(w, Ui.width(l));
		}
		int h = lines.size() * 10 + 6;
		int x = Math.min(mx + 10, this.width - w - 12);
		int y = Math.max(4, Math.min(my - h - 4, this.height - h - 4));
		if (my - h - 4 < 4) {
			y = my + 14;
		}
		Ui.card(x, y, w + 8, h, 0xF5090C10, Ui.alpha(Ui.accent(), 0x90));
		for (int i = 0; i < lines.size(); i++) {
			Ui.text(lines.get(i), x + 4, y + 4 + i * 10, Ui.TEXT);
		}
	}

	/**
	 * The shared header: icon, title, a short subtitle, and the player's
	 * coins on the right. Returns the y below it.
	 */
	protected int header(int x, int y, int w, String icon, String title, String subtitle, boolean coins) {
		int acc = Ui.accent();
		Ui.round(x + 8, y + 8, 20, 20, Ui.alpha(acc, 0x30));
		Ui.icon(icon, x + 10, y + 9, 2, acc);
		Ui.big(title, x + 34, y + 8, 1.5f, Ui.TEXT, false);
		if (subtitle != null) {
			Ui.text(Ui.fit(subtitle, w - 140), x + 34, y + 22, Ui.MUTED);
		}
		if (coins && Cosmetics.coins() >= 0) {
			String c = Ui.num(Cosmetics.coins());
			int cw = Ui.width(c) + 24;
			int cx = x + w - cw - 30;
			Ui.card(cx, y + 10, cw, 16, 0xFF0D1217, Ui.alpha(Ui.GOLD, 0x70));
			Ui.icon("coin", cx + 5, y + 14, 1, Ui.GOLD);
			Ui.text(c, cx + 17, y + 14, Ui.GOLD);
			tooltipArea(cx, y + 10, cw, 16, Cosmetics.currency() + ": earn them from quests, the daily login and playing");
		}
		Ui.rect(x + 8, y + 34, w - 16, 1, Ui.BORDER_SOFT);
		return y + 40;
	}

	/** A tooltip for a plain area (not a widget). */
	protected void tooltipArea(int x, int y, int w, int h, String tip) {
		if (mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h) {
			tooltip = tip;
		}
	}

	/** The close (X) button for a window's top right corner. */
	protected Button closeButton(int x, int y) {
		Button b = button("", x, y, 16, 16, this::back);
		b.icon("close").style(Style.GHOST).tip("Close (Esc)");
		return b;
	}

	// ==================== input ====================

	private W hit(int mx, int my) {
		W top = null;
		for (W w : widgets) {
			if (w.in(mx, my)) {
				top = w;
			}
		}
		return top;
	}

	@Override
	protected void mouseClicked(int mx, int my, int button) {
		for (GuiTextField f : fields) {
			f.mouseClicked(mx, my, button);
		}
		W w = hit(mx, my);
		if (w != null && w.enabled) {
			if (w.clickSound()) {
				click();
			}
			w.click(mx, my, button);
			return;
		}
		clicked(mx, my, button);
	}

	/** A click that hit no widget. */
	protected void clicked(int mx, int my, int button) {
	}

	public void click() {
		if (ClientPrefs.get().uiSounds && this.mc != null) {
			this.mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
		}
	}

	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int d = Mouse.getEventDWheel();
		if (d != 0) {
			scrolled(mouseX, mouseY, d > 0 ? 1 : -1);
		}
	}

	/** Mouse wheel: +1 up, -1 down. */
	protected void scrolled(int mx, int my, int dir) {
	}

	@Override
	protected void keyTyped(char c, int key) {
		for (GuiTextField f : fields) {
			if (f.isFocused()) {
				if (key == 1) {
					f.setFocused(false);
					return;
				}
				f.textboxKeyTyped(c, key);
				typed();
				return;
			}
		}
		if (key == 1 || key == PlebClient.KEY_RSHIFT) {
			back();
			return;
		}
		key(c, key);
	}

	/** A key with no text field focused. */
	protected void key(char c, int key) {
	}

	/** A text field changed. */
	protected void typed() {
	}

	@Override
	public void updateScreen() {
		for (GuiTextField f : fields) {
			f.updateCursorCounter();
		}
		tick();
	}

	protected void tick() {
	}

	/** Closes this screen and saves settings. */
	protected void back() {
		PlebClient.saveSettings();
		this.mc.displayGuiScreen(parent);
		if (parent == null) {
			this.mc.setIngameFocus();
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	// ==================== scrolling ====================

	/** A vertical scroll area: smooth offset, wheel and a draggable bar. */
	public final class Scroll {
		public int top;
		public int height;
		public int content;
		float offset;
		float target;

		public void set(int top, int height, int content) {
			this.top = top;
			this.height = height;
			this.content = content;
			clamp();
		}

		public int max() {
			return Math.max(0, content - height);
		}

		void clamp() {
			target = Math.max(0, Math.min(max(), target));
			offset = Math.max(0, Math.min(max(), offset));
		}

		public void wheel(int dir) {
			target -= dir * 28;
			clamp();
		}

		public void reset() {
			target = offset = 0;
		}

		/** The pixel offset to draw at this frame. */
		public int y() {
			offset = ease(offset, target, 20f);
			if (Math.abs(offset - target) < 0.5f) {
				offset = target;
			}
			return Math.round(offset);
		}

		/** True when a row from y to y+h (already scrolled) is fully inside the view. */
		public boolean shows(int y, int h) {
			return y >= top && y + h <= top + height;
		}

		public void drawBar(int x) {
			if (max() <= 0) {
				return;
			}
			int bh = Math.max(16, height * height / Math.max(1, content));
			int by = top + Math.round((height - bh) * (offset / max()));
			Ui.round(x, top, 3, height, 0x40000000);
			Ui.round(x, by, 3, bh, Ui.alpha(Ui.accent(), 0xB0));
		}
	}
}
