package net.plebmc.client.mods;

import java.util.ArrayList;
import java.util.List;

/**
 * More Lunar-style mods: info HUDs (speed, day, biome, server, arrows,
 * light, session time) and visual tweaks (crosshair, scoreboard, chat,
 * hurt camera). Plain logic here; the game hooks read the settings.
 */
public final class MoreMods {

	private MoreMods() {
	}

	// ==================== info HUDs ====================

	/** How fast you move, from your position over the last quarter second. */
	public static final class Speed extends Mods.TextMod {
		public Speed() {
			super("speed", "Speed", "How fast you're moving", false, 0.005f, 0.30f);
			hudLook(true);
			unit = setting("unit", "Unit", "Blocks per second or km/h", new String[] { "Blocks/s", "km/h" }, 0);
			vertical = setting("vertical", "Count falling", "Include up and down movement", OFF_ON, 0);
			label = textSetting("label", "Label", "Text before the number", "Speed: ");
		}

		private final Setting unit, vertical, label;
		private double lx, ly, lz = Double.NaN;
		private long lt;
		private double speed;

		/** Blocks per second, updated from positions (time in ms). */
		public double sample(double x, double y, double z, long now) {
			if (Double.isNaN(lz) || now < lt || now - lt > 2000) {
				lx = x;
				ly = y;
				lz = z;
				lt = now;
				speed = 0;
				return speed;
			}
			long dt = now - lt;
			if (dt >= 250) {
				double dx = x - lx, dy = vertical.on() ? y - ly : 0, dz = z - lz;
				double s = Math.sqrt(dx * dx + dy * dy + dz * dz) * 1000.0 / dt;
				speed = s > 200 ? speed : s; // a teleport is not speed
				lx = x;
				ly = y;
				lz = z;
				lt = now;
			}
			return speed;
		}

		@Override
		protected List<String> lines(ClientView v) {
			if (!v.inWorld()) {
				return new ArrayList<String>();
			}
			double s = sample(v.x(), v.y(), v.z(), System.currentTimeMillis());
			return one(label.text() + format(s, unit.get()));
		}

		@Override
		public HudBox preview(ClientView v, HudBox.Measure m) {
			return HudBox.lines(one(label.text() + format(4.32, unit.get())), m);
		}

		public static String format(double bps, int unit) {
			return unit == 1 ? Mods.Coordinates.fmt(bps * 3.6) + " km/h" : Mods.Coordinates.fmt(bps) + " m/s";
		}
	}

	/** Which in-game day it is, and the in-game time. */
	public static final class DayCounter extends Mods.TextMod {
		public DayCounter() {
			super("day-counter", "Day Counter", "The in-game day and time", false, 0.005f, 0.34f);
			hudLook(true);
			time = setting("time", "Show time", "Also show the in-game clock", OFF_ON, 1);
		}

		private final Setting time;

		@Override
		protected List<String> lines(ClientView v) {
			long t = v.worldTime();
			return t < 0 ? new ArrayList<String>() : one(text(t, time.on()));
		}

		@Override
		public HudBox preview(ClientView v, HudBox.Measure m) {
			return HudBox.lines(one(text(24000L * 11 + 7000, time.on())), m);
		}

		/** Tick 0 is 6:00; day 1 is the first. */
		public static String text(long ticks, boolean clock) {
			long day = ticks / 24000L + 1;
			if (!clock) {
				return "Day " + day;
			}
			long tod = (ticks % 24000L + 6000L) % 24000L;
			long h = tod / 1000L, mi = (tod % 1000L) * 60L / 1000L;
			return "Day " + day + "  " + h + ":" + (mi < 10 ? "0" : "") + mi;
		}
	}

	public static final class Biome extends Mods.TextMod {
		public Biome() {
			super("biome", "Biome", "The biome you're standing in", false, 0.005f, 0.38f);
			hudLook(true);
			label = textSetting("label", "Label", "Text before the biome", "Biome: ");
		}

		private final Setting label;

		@Override
		protected List<String> lines(ClientView v) {
			String b = v.biome();
			return b == null ? new ArrayList<String>() : one(label.text() + b);
		}

		@Override
		public HudBox preview(ClientView v, HudBox.Measure m) {
			return HudBox.lines(one(label.text() + "Plains"), m);
		}
	}

	public static final class Server extends Mods.TextMod {
		public Server() {
			super("server-address", "Server Address", "The server you're playing on", false, 0.005f, 0.42f);
			hudLook(true);
			label = textSetting("label", "Label", "Text before the address", "");
		}

		private final Setting label;

		@Override
		protected List<String> lines(ClientView v) {
			String s = v.server();
			return s == null ? new ArrayList<String>() : one(label.text() + strip(s));
		}

		@Override
		public HudBox preview(ClientView v, HudBox.Measure m) {
			return HudBox.lines(one(label.text() + "eagler.plebmc.top"), m);
		}

		/** "wss://eagler.plebmc.top/" -> "eagler.plebmc.top". */
		public static String strip(String s) {
			int i = s.indexOf("://");
			if (i >= 0) {
				s = s.substring(i + 3);
			}
			while (s.endsWith("/")) {
				s = s.substring(0, s.length() - 1);
			}
			return s;
		}
	}

	public static final class Arrows extends Mods.TextMod {
		public Arrows() {
			super("arrow-counter", "Arrow Counter", "Arrows in your inventory", false, 0.005f, 0.46f);
			hudLook(true);
			hideEmpty = setting("hide", "Hide at zero", "Only show it when you carry arrows", OFF_ON, 1);
			label = textSetting("label", "Label", "Text before the count", "Arrows: ");
		}

		private final Setting hideEmpty, label;

		@Override
		protected List<String> lines(ClientView v) {
			int n = v.supply("arrows");
			if (!v.inWorld() || (n == 0 && hideEmpty.on())) {
				return new ArrayList<String>();
			}
			return one(label.text() + n);
		}

		@Override
		public HudBox preview(ClientView v, HudBox.Measure m) {
			return HudBox.lines(one(label.text() + "64"), m);
		}
	}

	public static final class LightLevel extends Mods.TextMod {
		public LightLevel() {
			super("light-level", "Light Level", "Block light where you stand (mobs spawn below 8)", false, 0.005f, 0.50f);
			hudLook(true);
			label = textSetting("label", "Label", "Text before the level", "Light: ");
		}

		private final Setting label;

		@Override
		protected List<String> lines(ClientView v) {
			int l = v.light();
			return l < 0 ? new ArrayList<String>() : one(label.text() + l + (l < 8 ? " (mobs)" : ""));
		}

		@Override
		public HudBox preview(ClientView v, HudBox.Measure m) {
			return HudBox.lines(one(label.text() + "12"), m);
		}
	}

	/** How long you've been in this world or server. */
	public static final class SessionTimer extends Mods.TextMod {
		public SessionTimer() {
			super("session-timer", "Session Timer", "How long you've been playing this session", false, 0.005f, 0.54f);
			hudLook(true);
			label = textSetting("label", "Label", "Text before the time", "Session: ");
		}

		private final Setting label;
		private long start = -1;
		private String where;

		@Override
		protected List<String> lines(ClientView v) {
			if (!v.inWorld()) {
				start = -1;
				return new ArrayList<String>();
			}
			long now = System.currentTimeMillis();
			String s = v.server();
			if (start < 0 || (s != null && !s.equals(where))) {
				start = now;
				where = s;
			}
			return one(label.text() + duration((now - start) / 1000L));
		}

		@Override
		public HudBox preview(ClientView v, HudBox.Measure m) {
			return HudBox.lines(one(label.text() + duration(754)), m);
		}

		public static String duration(long sec) {
			long h = sec / 3600, m = sec / 60 % 60, s = sec % 60;
			String ms = (m < 10 && h > 0 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s;
			return h > 0 ? h + ":" + ms : ms;
		}
	}

	// ==================== visual tweaks (drawn by the game hooks) ====================

	public static final class Crosshair extends Mod {
		public static final String[] STYLES = { "Cross", "Thin cross", "Dot", "Circle", "Square", "X", "Arrow" };
		public static final String[] SIZES = { "Small", "Medium", "Large", "Huge" };
		public static final String[] GAPS = { "None", "Small", "Medium", "Large" };
		public static final String[] OUTLINES = { "Off", "On" };

		public Crosshair() {
			super("crosshair", "Crosshair", "Your own crosshair: style, color, size and gap", false, 0f, 0f);
			category(VISUAL);
			style = setting("style", "Style", "The crosshair's shape", STYLES, 0);
			color = setting("color", "Color", "The crosshair's color", COLOR_NAMES, 0);
			size = setting("size", "Size", "How big it is", SIZES, 1);
			gap = setting("gap", "Gap", "Space in the middle", GAPS, 1);
			dot = setting("dot", "Center dot", "A dot in the middle", OFF_ON, 0);
			outline = setting("outline", "Outline", "A dark edge so it shows on any background", OUTLINES, 1);
			invert = setting("invert", "Invert colors", "Vanilla-style: inverts what's behind it (ignores color)", OFF_ON, 0);
		}

		public final Setting style, color, size, gap, dot, outline, invert;

		public int argb() {
			return COLORS[Math.max(0, Math.min(COLORS.length - 1, color.get()))];
		}

		public int length() {
			return new int[] { 3, 5, 7, 10 }[size.get()];
		}

		public int gapPx() {
			return new int[] { 0, 1, 2, 4 }[gap.get()];
		}
	}

	public static final class Scoreboard extends Mod {
		public static final String[] BACKGROUNDS = { "None", "Light", "Normal", "Dark" };
		public static final String[] POSITIONS = { "Right", "Left" };

		public Scoreboard() {
			super("scoreboard", "Scoreboard", "Hide the red numbers, resize, move and restyle the sidebar", false, 0f, 0f);
			category(VISUAL);
			numbers = setting("numbers", "Red numbers", "The score numbers on the right", OFF_ON, 0);
			background = setting("background", "Background", "How dark the panel is", BACKGROUNDS, 2);
			scale = setting("scale", "Size", "How big it is drawn", SCALE_NAMES, 2);
			side = setting("side", "Side", "Which side of the screen", POSITIONS, 0);
			shadow = setting("shadow", "Text shadow", "Shadow under the text", OFF_ON, 1);
			hide = setting("hide", "Hide it", "Don't draw the scoreboard at all", OFF_ON, 0);
		}

		public final Setting numbers, background, scale, side, shadow, hide;

		public int backgroundAlpha(boolean title) {
			int[] a = title ? new int[] { 0, 0x40, 0x60, 0xA0 } : new int[] { 0, 0x30, 0x50, 0x90 };
			return a[background.get()];
		}

		public float scaleF() {
			return SCALES[Math.max(0, Math.min(SCALES.length - 1, scale.get()))];
		}
	}

	public static final class Chat extends Mod {
		public static final String[] TIMESTAMPS = { "Off", "24-hour", "12-hour" };

		public Chat() {
			super("chat", "Chat", "Timestamps and stacking repeated messages", false, 0f, 0f);
			category(VISUAL);
			timestamps = setting("timestamps", "Timestamps", "The time before each message", TIMESTAMPS, 1);
			stack = setting("stack", "Stack repeats", "Show a repeated message once with a (x3) count", OFF_ON, 1);
		}

		public final Setting timestamps, stack;

		private String last;
		private int count;
		private int id;

		/** The chat line id to print this message with, and how many times it has repeated now. */
		public int[] place(String plainText) {
			if (stack.on() && plainText.equals(last)) {
				count++;
			} else {
				last = plainText;
				count = 1;
				id = (id + 1) & 0xFFFFF;
			}
			return new int[] { 0x50000000 + id, count };
		}

		public static String stamp(int hour, int minute, int mode) {
			String mm = (minute < 10 ? "0" : "") + minute;
			if (mode == 2) {
				int h = hour % 12 == 0 ? 12 : hour % 12;
				return h + ":" + mm + (hour < 12 ? " AM" : " PM");
			}
			return (hour < 10 ? "0" : "") + hour + ":" + mm;
		}
	}

	public static final class HurtCam extends Mod {
		public static final String[] AMOUNTS = { "Full", "Half", "Quarter", "None" };

		public HurtCam() {
			super("hurt-cam", "Hurt Camera", "Less (or no) screen shake when you take damage", false, 0f, 0f);
			category(VISUAL);
			amount = setting("amount", "Shake", "How much the camera tilts when hit", AMOUNTS, 3);
		}

		public final Setting amount;

		public float factor() {
			return new float[] { 1f, 0.5f, 0.25f, 0f }[amount.get()];
		}
	}
}
