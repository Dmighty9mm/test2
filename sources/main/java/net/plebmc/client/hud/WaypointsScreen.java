package net.plebmc.client.hud;

import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.plebmc.client.mods.OwnWaypoints;

/**
 * The Waypoints mod's list: add one where you stand (name and color), hide
 * or delete the ones you made on this server.
 */
public final class WaypointsScreen extends PlebScreen {

	private final Scroll scroll = new Scroll();
	private int wx, wy, ww, wh;
	private int lx, ly, lw, lh;
	private GuiTextField name;
	private static int color;
	private List<OwnWaypoints.Entry> here;
	private final java.util.List<W> rows = new java.util.ArrayList<>();
	private final java.util.List<Integer> rowBase = new java.util.ArrayList<>();

	public WaypointsScreen(GuiScreen parent) {
		super(parent);
	}

	@Override
	protected void build() {
		ww = Math.min(this.width - 16, 340);
		wh = Math.min(this.height - 16, 250);
		wx = (this.width - ww) / 2;
		wy = (this.height - wh) / 2;
		closeButton(wx + ww - 22, wy + 10);
		here = PlebWaypoints.ownHere();

		int x = wx + 10, y = wy + 46;
		name = field(x + 5, y + 5, ww - 20 - 150, 10, 24);
		W swatch = new W() {
			@Override
			void draw(int mx, int my) {
				Ui.card(this.x, this.y, this.w, this.h, Ui.mix(Ui.RAISED, Ui.HOVER, hover), Ui.mix(Ui.BORDER, Ui.accent(), hover));
				Ui.round(this.x + 4, this.y + 4, this.w - 8, this.h - 8, OwnWaypoints.COLORS[color]);
			}

			@Override
			void click(int mx, int my, int button) {
				color = (color + 1) % OwnWaypoints.COLORS.length;
			}
		};
		swatch.at(x + ww - 20 - 140, y, 18, 18);
		swatch.tip("Color (click to change)");
		widgets.add(swatch);
		Button add = button("Add here", x + ww - 20 - 116, y, 116, 18, this::addHere);
		add.style(Style.PRIMARY).icon("plus").tip("A waypoint where you're standing");
		add.enabled = this.mc.thePlayer != null && OwnWaypoints.all().size() < OwnWaypoints.MAX;

		lx = x;
		lw = ww - 20;
		ly = y + 26;
		lh = wy + wh - 10 - ly;
		rows.clear();
		rowBase.clear();
		int[] clip = { lx - 1, ly, lw + 2, lh };
		int ry = ly;
		for (final OwnWaypoints.Entry e : here) {
			Button hide = button(e.hidden ? "Show" : "Hide", lx + lw - 96, ry + 3, 44, 16, () -> {
				e.hidden = !e.hidden;
				save();
			});
			Button del = button("Delete", lx + lw - 48, ry + 3, 44, 16, () -> {
				OwnWaypoints.remove(e);
				save();
			});
			del.style(Style.DANGER);
			add(hide, clip);
			add(del, clip);
			ry += 24;
		}
		scroll.set(ly, lh, ry - ly);
	}

	private void add(W w, int[] clip) {
		w.clip = clip;
		rows.add(w);
		rowBase.add(w.y);
	}

	private void addHere() {
		if (this.mc.thePlayer == null) {
			return;
		}
		String n = name.getText().trim();
		if (n.isEmpty()) {
			n = "Waypoint " + (here.size() + 1);
		}
		OwnWaypoints.add(n, (int) Math.floor(this.mc.thePlayer.posX), (int) Math.floor(this.mc.thePlayer.posY),
				(int) Math.floor(this.mc.thePlayer.posZ), color, PlebWaypoints.place(), PlebWaypoints.dimension());
		name.setText("");
		color = (color + 1) % OwnWaypoints.COLORS.length;
		save();
	}

	private void save() {
		PlebClient.saveSettings();
		rebuild();
	}

	@Override
	protected void scrolled(int mx, int my, int dir) {
		scroll.wheel(dir);
	}

	@Override
	protected void keyTyped(char c, int key) {
		if (key == 28 && name != null && name.isFocused()) { // Enter
			addHere();
			return;
		}
		super.keyTyped(c, key);
	}

	@Override
	protected void drawContent(int mx, int my, float pt) {
		Ui.window(wx, wy, ww, wh);
		header(wx, wy, ww, "gameplay", "Waypoints", here.isEmpty() ? "Yours in this world" : here.size() + " in this world",
				false);
		int x = wx + 10, y = wy + 46;
		int fw = ww - 20 - 146;
		Ui.card(x, y, fw, 18, 0xFF0D1217, name.isFocused() ? Ui.accent() : Ui.BORDER);
		if (name.getText().isEmpty() && !name.isFocused()) {
			Ui.text("Name", x + 5, y + 5, Ui.FAINT);
		}
		int off = scroll.y();
		for (int i = 0; i < rows.size(); i++) {
			rows.get(i).y = rowBase.get(i) - off;
		}
		Ui.clip(lx - 1, ly, lw + 2, lh, this.width, this.height);
		int ry = ly - off;
		for (OwnWaypoints.Entry e : here) {
			Ui.round(lx, ry, lw, 22, Ui.RAISED);
			Ui.round(lx + 6, ry + 6, 10, 10, e.hidden ? Ui.alpha(e.argb(), 0x50) : e.argb());
			Ui.text(Ui.fit(e.name, lw - 130), lx + 22, ry + 3, e.hidden ? Ui.FAINT : Ui.TEXT);
			String where = e.x + ", " + e.y + ", " + e.z;
			if (this.mc.thePlayer != null) {
				double dx = e.x + 0.5 - this.mc.thePlayer.posX, dy = e.y - this.mc.thePlayer.posY,
						dz = e.z + 0.5 - this.mc.thePlayer.posZ;
				where += "  ·  " + (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)) + "m";
			}
			Ui.text(Ui.fit(where, lw - 130), lx + 22, ry + 12, Ui.FAINT);
			ry += 24;
		}
		Ui.unclip();
		scroll.drawBar(lx + lw + 3);
		if (here.isEmpty()) {
			Ui.centered("No waypoints here yet. Type a name and press Add here.", wx + ww / 2, ly + 40, Ui.FAINT);
		}
	}
}
