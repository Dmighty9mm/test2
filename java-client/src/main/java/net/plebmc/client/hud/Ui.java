package net.plebmc.client.hud;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.plebmc.client.mods.ClientPrefs;

/**
 * The PlebMc design language: colors, panels, pills, progress bars and a
 * small pixel icon set, drawn with the vanilla GUI paths only (filled
 * rectangles and the Minecraft font), so it costs nothing extra on
 * Eaglercraft and looks at home in Minecraft.
 */
public final class Ui {

	private Ui() {
	}

	// ==================== palette ====================

	public static final int BASE = 0xF20E1217;
	public static final int RAISED = 0xF2151B22;
	public static final int RAISED_2 = 0xF21B232C;
	public static final int HOVER = 0xF2222C37;
	public static final int BORDER = 0xFF27313B;
	public static final int BORDER_SOFT = 0xFF1E262F;
	public static final int TEXT = 0xFFEAF2F5;
	public static final int MUTED = 0xFF93A1AB;
	public static final int FAINT = 0xFF5D6973;
	public static final int GOOD = 0xFF5EE38A;
	public static final int BAD = 0xFFFF6464;
	public static final int GOLD = 0xFFF5C542;
	public static final int WEEKLY = 0xFFB48CFF;
	public static final int PLEBNITE = 0xFFFF9A3C;
	public static final int ON_ACCENT = 0xFF071014;

	/** The accent color the player picked (PlebMc cyan by default). */
	public static int accent() {
		ClientPrefs p = ClientPrefs.get();
		return p.rainbowAccent ? EasterEggs.rainbow() : p.accentColor();
	}

	/** Color between a and b (t 0..1), alpha included. */
	public static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
				| (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
	}

	/** The color with a new alpha (0..255). */
	public static int alpha(int argb, int a) {
		return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0xFFFFFF);
	}

	// ==================== shapes ====================

	public static void rect(int x, int y, int w, int h, int c) {
		Draw.fill(x, y, w, h, c);
	}

	/** A rectangle with its corner pixels cut, which reads as rounded at GUI scale. */
	public static void round(int x, int y, int w, int h, int c) {
		if (w < 3 || h < 3) {
			rect(x, y, w, h, c);
			return;
		}
		rect(x + 1, y, w - 2, h, c);
		rect(x, y + 1, 1, h - 2, c);
		rect(x + w - 1, y + 1, 1, h - 2, c);
	}

	/** A 1px rounded outline. */
	public static void outline(int x, int y, int w, int h, int c) {
		rect(x + 1, y, w - 2, 1, c);
		rect(x + 1, y + h - 1, w - 2, 1, c);
		rect(x, y + 1, 1, h - 2, c);
		rect(x + w - 1, y + 1, 1, h - 2, c);
	}

	/** A card: filled, with a border. */
	public static void card(int x, int y, int w, int h, int fill, int border) {
		round(x, y, w, h, fill);
		outline(x, y, w, h, border);
	}

	/** The main window of a screen, with a thin accent line on top. */
	private static final int[] WINDOW_ALPHA = { 0xF4, 0xC8, 0x96 };

	public static void window(int x, int y, int w, int h) {
		int acc = accent();
		// soft shadow, then a glassy body that darkens toward the bottom
		round(x - 3, y - 2, w + 6, h + 6, 0x22000000);
		round(x - 1, y - 1, w + 2, h + 3, 0x50000000);
		int wa = WINDOW_ALPHA[Math.max(0, Math.min(2, net.plebmc.client.mods.ClientPrefs.get().windowOpacity))];
		PlebTheme.gradientRound(x, y, w, h, alpha(0xFF121A23, wa), alpha(0xFF0A0F15, Math.min(255, wa + 2)));
		outline(x, y, w, h, BORDER);
		// an accent line on top with a faint glow under it
		PlebTheme.gradient(x + 1, y + 1, w - 2, 16, alpha(acc, 0x1C), alpha(acc, 0x00));
		rect(x + 1, y, w - 2, 1, alpha(acc, 0xD0));
		rect(x + w / 4, y, w / 2, 1, mix(acc, 0xFFFFFFFF, 0.35f));
	}

	/** Vertical gradient in a few bands (no extra GL state). */
	public static void gradient(int x, int y, int w, int h, int top, int bottom) {
		int bands = Math.max(1, Math.min(16, h / 2));
		for (int i = 0; i < bands; i++) {
			int y0 = y + h * i / bands;
			int y1 = y + h * (i + 1) / bands;
			rect(x, y0, w, y1 - y0, mix(top, bottom, (i + 0.5f) / bands));
		}
	}

	/** A progress bar; {@code f} 0..1. */
	public static void bar(int x, int y, int w, int h, float f, int color) {
		round(x, y, w, h, 0xFF0A0E12);
		int fw = Math.round((w - 2) * Math.max(0f, Math.min(1f, f)));
		if (fw > 0) {
			rect(x + 1, y + 1, fw, h - 2, color);
			if (h > 3) {
				rect(x + 1, y + 1, fw, 1, mix(color, 0xFFFFFFFF, 0.35f));
			}
		}
	}

	/** A small rounded label ("OWNED", "3/5"); returns its width. */
	public static int pill(String s, int x, int y, int fg, int bg) {
		int w = Draw.width(s) + 6;
		round(x, y, w, 11, bg);
		Draw.text(s, x + 3, y + 2, fg, false);
		return w;
	}

	public static int pillWidth(String s) {
		return Draw.width(s) + 6;
	}

	// ==================== clipping ====================

	/**
	 * Hides everything drawn after this outside x,y,w,h (until {@link #unclip}),
	 * using the depth buffer: invisible "walls" are drawn in front around the
	 * area, so text and rectangles behind them fail the depth test.
	 */
	public static void clip(int x, int y, int w, int h, int screenW, int screenH) {
		GlStateManager.enableDepth();
		GlStateManager.depthMask(true);
		GlStateManager.depthFunc(519); // GL_ALWAYS
		GlStateManager.colorMask(false, false, false, false);
		GlStateManager.pushMatrix();
		GlStateManager.translate(0f, 0f, 200f);
		int big = 4000;
		rect(-big, -big, screenW + 2 * big, y + big, 0xFF000000); // above
		rect(-big, y + h, screenW + 2 * big, screenH + big, 0xFF000000); // below
		rect(-big, y, x + big, h, 0xFF000000); // left
		rect(x + w, y, screenW + big, h, 0xFF000000); // right
		GlStateManager.popMatrix();
		GlStateManager.colorMask(true, true, true, true);
		GlStateManager.depthFunc(515); // GL_LEQUAL
	}

	public static void unclip() {
		GlStateManager.clear(256); // GL_DEPTH_BUFFER_BIT
		GlStateManager.disableDepth();
	}

	// ==================== numbers ====================

	/** 12345 as "12,345". */
	public static String num(long n) {
		String s = Long.toString(Math.abs(n));
		StringBuilder b = new StringBuilder(n < 0 ? "-" : "");
		for (int i = 0; i < s.length(); i++) {
			if (i > 0 && (s.length() - i) % 3 == 0) {
				b.append(',');
			}
			b.append(s.charAt(i));
		}
		return b.toString();
	}

	/** Seconds as "2d 4h", "3h 12m", "5m" or "40s". */
	public static String time(long s) {
		long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
		return d > 0 ? d + "d " + h + "h" : h > 0 ? h + "h " + m + "m" : m > 0 ? m + "m" : Math.max(0, s) + "s";
	}

	// ==================== text ====================

	public static int text(String s, int x, int y, int c) {
		return Draw.text(s, x, y, c, false);
	}

	public static int shadow(String s, int x, int y, int c) {
		return Draw.text(s, x, y, c, true);
	}

	public static void centered(String s, int cx, int y, int c) {
		Draw.text(s, cx - Draw.width(s) / 2f, y, c, false);
	}

	/** Text drawn bigger (titles). */
	public static void big(String s, int x, int y, float scale, int c, boolean shadow) {
		GlStateManager.pushMatrix();
		GlStateManager.translate(x, y, 0f);
		GlStateManager.scale(scale, scale, 1f);
		Draw.text(s, 0, 0, c, shadow);
		GlStateManager.popMatrix();
	}

	public static int width(String s) {
		return Draw.width(s);
	}

	public static String fit(String s, int w) {
		return Draw.fit(s, w);
	}

	public static List<String> wrap(String s, int w) {
		return Draw.wrap(s, w);
	}

	// ==================== icons ====================

	/** 9x9 pixel icons ('#' = drawn). */
	private static final Map<String, String[]> ICONS = new HashMap<>();
	private static final Map<String, int[][]> RUNS = new HashMap<>();

	static {
		icon("mods", "..##.....", "#########", "..##.....", ".........", ".....##..", "#########", ".....##..",
				".........", ".........");
		icon("shop", "...###...", "..#...#..", "..#...#..", "#########", "#########", "#########", "#########",
				"#########", ".#######.");
		icon("quests", ".#######.", ".#.....#.", ".#.###.#.", ".#.....#.", ".#.###.#.", ".#.....#.", ".#.##..#.",
				".#.....#.", ".#######.");
		icon("hud", "#########", "#...#...#", "#...#...#", "#########", "#.......#", "#.......#", "#.......#",
				"#.......#", "#########");
		icon("account", "...###...", "..#####..", "..#####..", "...###...", ".........", ".#######.", "#########",
				"#########", "#########");
		icon("settings", "...#.#...", ".#.###.#.", "..#####..", "####.####", "###...###", "####.####", "..#####..",
				".#.###.#.", "...#.#...");
		icon("debug", "#########", "#.......#", "#.#.....#", "#..#....#", "#.#..##.#", "#.......#", "#.......#",
				"#########", ".........");
		icon("coin", "..#####..", ".#.....#.", "#..###..#", "#..#....#", "#..#....#", "#..###..#", ".#.....#.",
				"..#####..", ".........");
		icon("check", ".........", "........#", ".......#.", "#.....#..", ".#...#...", "..#.#....", "...#.....",
				".........", ".........");
		icon("lock", "..#####..", ".#.....#.", ".#.....#.", "#########", "#########", "####.####", "####.####",
				"#########", "#########");
		icon("search", ".####....", "#....#...", "#....#...", "#....#...", ".####....", ".....#...", "......#..",
				".......#.", "........#");
		icon("star", "....#....", "....#....", "...###...", "#########", ".#######.", "..#####..", "..##.##..",
				".##...##.", "#.......#");
		icon("general", "..#####..", ".#..#..#.", "#..#.#..#", "#########", "#..#.#..#", "#########", "#..#.#..#",
				".#..#..#.", "..#####..");
		icon("plebnite", ".......##", "......###", ".....###.", "....###..", "#..###...", ".####....", "..##.....",
				".#.##....", "#...#....");
		icon("all", "####.####", "####.####", "####.####", "####.####", ".........", "####.####", "####.####",
				"####.####", "####.####");
		icon("cloak", "..#####..", ".#######.", ".#######.", ".#######.", "#########", "#########", "#########",
				"#########", "##.#.#.##");
		icon("wings", ".........", "##.....##", "###...###", "####.####", "####.####", ".###.###.", "..##.##..",
				"...#.#...", ".........");
		icon("hat", ".........", "..#####..", "..#####..", "..#####..", "..#####..", "..#...#..", "..#####..",
				"#########", ".........");
		icon("trail", "#........", ".#...#...", "..#.###..", "...#####.", "..#.###..", ".#...#...", "#........",
				".........", ".........");
		icon("clock", "..#####..", ".#..#..#.", "#...#...#", "#...#...#", "#...###.#", "#.......#", "#.......#",
				".#.....#.", "..#####..");
		icon("week", ".#.....#.", "#########", "#.......#", "#########", "#.#.#.#.#", "#########", "#.#.#.#.#",
				"#########", ".........");
		icon("back", "...#.....", "..##.....", ".########", "#########", ".########", "..##.....", "...#.....",
				".........", ".........");
		icon("next", ".....#...", ".....##..", "########.", "#########", "########.", ".....##..", ".....#...",
				".........", ".........");
		icon("close", "#.......#", ".#.....#.", "..#...#..", "...#.#...", "....#....", "...#.#...", "..#...#..",
				".#.....#.", "#.......#");
		icon("reset", "..####...", ".#....#.#", "#......##", "#.....###", "#........", "#.......#", ".#.....#.",
				"..#####..", ".........");
		icon("eye", ".........", "..#####..", ".#.....#.", "#..###..#", "#..###..#", ".#.....#.", "..#####..",
				".........", ".........");
		icon("performance", "....###..", "...###...", "..###....", ".#######.", "....###..", "...###...", "..##.....",
				".#.......", ".........");
		icon("gameplay", "....#....", "....#....", "..#####..", "..#...#..", "###.#.###", "..#...#..", "..#####..",
				"....#....", "....#....");
		icon("squad", ".##...##.", "####.####", "####.####", ".##...##.", ".........", "###...###", "####.####",
				"####.####", "####.####");
		icon("bell", "....#....", "...###...", "..#####..", "..#####..", "..#####..", ".#######.", "#########",
				".........", "....#....");
		icon("palette", "..#####..", ".#.....#.", "#.#..#..#", "#.......#", "#..#....#", "#.....##.", ".#..##...",
				"..##.....", ".........");
		icon("link", ".........", "..###....", ".#...#...", ".#..#.#..", "..#.#..#.", "...#...#.", "....###..",
				".........", ".........");
		icon("logout", "#####....", "#........", "#....#...", "#.######.", "#....#...", "#........", "#####....",
				".........", ".........");
		icon("gift", "..#...#..", "...#.#...", "#########", "#...#...#", "#########", ".#..#..#.", ".#..#..#.",
				".#..#..#.", ".#######.");
		icon("shield", "#########", "#########", "##.....##", "##..#..##", "##.###.##", "##..#..##", ".##...##.",
				"..##.##..", "...###...");
		icon("drag", "...#.....", "..###....", "...#.....", "#.###.#..", "#######..", "#.###.#..", "...#.....",
				"..###....", "...#.....");
		icon("home", "....#....", "...###...", "..#####..", ".#######.", "#########", ".##...##.", ".##...##.",
				".##...##.", ".#######.");
		icon("play", ".#.......", ".##......", ".###.....", ".####....", ".#####...", ".####....", ".###.....",
				".##......", ".#.......");
		icon("world", "..#####..", ".#######.", "#########", "#...#...#", "#...#...#", "#...#...#", "##..#..##",
				"..#####..", ".........");
		icon("server", "#########", "#.......#", "#.##..#.#", "#########", "#.......#", "#.##..#.#", "#########",
				"....#....", "..#####..");
		icon("discord", ".........", ".##...##.", "#########", "#########", "#..###..#", "#..###..#", "#########", ".##...##.", ".........");
		icon("power", "....#....", "..#.#.#..", ".#..#..#.", "#...#...#", "#.......#", "#.......#", ".#.....#.",
				"..#####..", ".........");
		icon("news", "#######..", "#.....###", "#.###.#.#", "#.....#.#", "#.###.#.#", "#.....#.#", "#.###.#.#",
				"#######.#", ".#######.");
		icon("wardrobe", ".##...##.", "####.####", "#########", "#.#####.#", "..#####..", "..#####..", "..#####..",
				"..#####..", ".........");
		icon("partner", ".##...##.", "####.####", "#########", "#########", ".#######.", "..#####..", "...###...",
				"....#....", ".........");
		// 11x11 line icons for the title screen sidebar
		icon("nav_options", "....###....", ".##.###.##.", ".#########.", "..#######..", "####...####", "####...####", "####...####", "..#######..", ".#########.", ".##.###.##.", "....###....");
		icon("nav_language", "...#####...", ".##..#..##.", ".#..#.#..#.", "#...#.#...#", "#..#...#..#", "###########", "#..#...#..#", "#...#.#...#", ".#..#.#..#.", ".##..#..##.", "...#####...");
		icon("nav_quit", ".....#.....", "..#..#..#..", ".#...#...#.", "#....#....#", "#....#....#", "#.........#", "#.........#", ".#.......#.", "..#.....#..", "...#####...", "...........");
		icon("nav_play", "...........", "...#.......", "...##......", "...###.....", "...####....", "...#####...", "...####....", "...###.....", "...##......", "...#.......", "...........");
		icon("nav_single", "....###....", "..##...##..", "##.......##", "#.##...##.#", "#...###...#", "#....#....#", "#....#....#", "#....#....#", "##...#...##", "..##.#.##..", "....###....");
		icon("nav_multi", "###########", "#.........#", "#.##....#.#", "#.........#", "###########", "...........", "###########", "#.........#", "#.##....#.#", "#.........#", "###########");
		icon("nav_wardrobe", ".....##....", "....#..#...", ".......#...", "......#....", ".....#.....", "....#.#....", "...#...#...", "..#.....#..", ".#.......#.", "###########", "...........");
		icon("nav_shop", "....###....", "...#...#...", "...#...#...", "###########", "#.........#", "#.........#", "#...###...#", "#.........#", "#.........#", "#.........#", "###########");
		icon("nav_logout", "#######....", "#.....#....", "#.....#..#.", "#........##", "#...#######", "#........##", "#.....#..#.", "#.....#....", "#.....#....", "#######....", "...........");
		icon("nav_locker", ".#########.", ".#.......#.", ".#.#####.#.", ".#.......#.", ".#.#####.#.", ".#.......#.", ".#.....#.#.", ".#.....#.#.", ".#.......#.", ".#.......#.", ".#########.");
		icon("nav_mods", "...........", "..###......", "###########", "..###......", "......###..", "###########", "......###..", "...###.....", "###########", "...###.....", "...........");
		icon("nav_quests", "..#######..", "###.....###", "#.#.....#.#", "#.#.....#.#", ".##.....##.", "..#.....#..", "...#...#...", "....###....", ".....#.....", "....###....", "..#######..");
		icon("nav_customize", "........###", ".......####", "......####.", ".....####..", "....####...", "...####....", "..#.##.....", ".###.......", "####.......", "###........", "#..........");
		icon("nav_hud", "###########", "#...#.....#", "#...#.....#", "#...#.....#", "#####.....#", "#.........#", "#.........#", "#.....#####", "#.....#...#", "#.....#...#", "###########");
		icon("nav_video", "......####.", ".....####..", "....####...", "...####....", "..#######..", ".#######...", "....###....", "...###.....", "..###......", ".##........", "#..........");
		icon("nav_lock", "...#####...", "..#.....#..", "..#.....#..", "..#.....#..", "###########", "#.........#", "#....#....#", "#...###...#", "#....#....#", "#.........#", "###########");
		icon("nav_debug", "###########", "#.........#", "#.#.......#", "#..#......#", "#...#.....#", "#..#......#", "#.#..####.#", "#.........#", "#.........#", "#.........#", "###########");
		icon("nav_admin", "###########", "#.........#", "#....#....#", "#...###...#", "#....#....#", "#.........#", ".#.......#.", "..#.....#..", "...#...#...", "....#.#....", ".....#.....");
		icon("nav_profile", "....###....", "...#...#...", "...#...#...", "...#...#...", "....###....", "...........", "..#######..", ".#.......#.", "#.........#", "#.........#", "###########");
		icon("nav_discord", "...........", "..#.....#..", ".###...###.", ".#########.", "###########", "##..###..##", "##..###..##", "###########", ".###...###.", "..#.....#..", "...........");
		icon("nav_vote", "...........", "........##.", ".......##..", "......##...", ".##..##....", "..####.....", "...##......", "...........", "###########", "#.........#", "###########");
		icon("nav_forge", "...........", ".##########", "###########", ".#########.", "....###....", "....###....", "...#####...", ".#########.", ".#########.", "...........", "...........");
	}

	private static void icon(String name, String... rows) {
		ICONS.put(name, rows);
		// merge horizontal runs: a handful of rects per icon instead of 81
		java.util.List<int[]> runs = new java.util.ArrayList<>();
		for (int y = 0; y < rows.length; y++) {
			String r = rows[y];
			int x = 0;
			while (x < r.length()) {
				if (r.charAt(x) == '#') {
					int s = x;
					while (x < r.length() && r.charAt(x) == '#') {
						x++;
					}
					runs.add(new int[] { s, y, x - s });
				} else {
					x++;
				}
			}
		}
		RUNS.put(name, runs.toArray(new int[0][]));
	}

	/** The icon's horizontal runs {x, y, length}, or null. */
	static int[][] runs(String name) {
		return RUNS.get(name);
	}

	public static boolean hasIcon(String name) {
		return RUNS.containsKey(name) || "mascot".equals(name);
	}

	/** Draws a 9x9 icon at (x, y), {@code px} GUI pixels per icon pixel. */
	public static void icon(String name, int x, int y, int px, int color) {
		if ("mascot".equals(name)) {
			mascot(x - px, y - px, 11 * px, ((color >>> 24) & 0xFF) / 255f);
			return;
		}
		if (texturedIcon(name, x, y, px, color)) {
			return;
		}
		int[][] runs = RUNS.get(name);
		if (runs == null) {
			return;
		}
		for (int[] r : runs) {
			rect(x + r[0] * px, y + r[1] * px, r[2] * px, px, color);
		}
	}

	private static final ResourceLocation DISCORD = new ResourceLocation("plebitems", "textures/gui/discord.png");

	/**
	 * Icons drawn from a smooth texture instead of pixel runs (the Discord
	 * chat bubble, 4:3, centered in the icon box). False for other icons.
	 */
	public static boolean texturedIcon(String name, float x, float y, float px, int color) {
		if (true) {
			return false; // Discord is a pixel icon like the rest now
		}
		float box = ("discord".equals(name) ? 9 : 11) * px;
		float w = box * 1.06f, h = w * 0.75f;
		return tinted(DISCORD, x + (box - w) / 2f, y + (box - h) / 2f, w, h, color);
	}

	/** A white-on-transparent texture drawn in {@code color} (ARGB), smoothly scaled. False if it can't be bound. */
	public static boolean tinted(ResourceLocation loc, float x, float y, float w, float h, int color) {
		Minecraft mc = Minecraft.getMinecraft();
		try {
			mc.getTextureManager().bindTexture(loc);
			ITextureObject tex = mc.getTextureManager().getTexture(loc);
			if (tex == null) {
				return false;
			}
			tex.setBlurMipmap(true, false);
		} catch (Throwable t) {
			return false;
		}
		GlStateManager.enableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GlStateManager.enableAlpha();
		GlStateManager.alphaFunc(516, 0.01f);
		GlStateManager.color((color >> 16 & 0xFF) / 255f, (color >> 8 & 0xFF) / 255f, (color & 0xFF) / 255f,
				(color >>> 24) / 255f);
		WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
		wr.begin(7, DefaultVertexFormats.POSITION_TEX);
		wr.pos(x, y + h, 0).tex(0, 1).endVertex();
		wr.pos(x + w, y + h, 0).tex(1, 1).endVertex();
		wr.pos(x + w, y, 0).tex(1, 0).endVertex();
		wr.pos(x, y, 0).tex(0, 0).endVertex();
		Tessellator.getInstance().draw();
		GlStateManager.alphaFunc(516, 0.1f);
		GlStateManager.color(1f, 1f, 1f, 1f);
		return true;
	}

	private static final ResourceLocation MASCOT = new ResourceLocation("plebitems", "textures/gui/mascot.png");
	private static final ResourceLocation MASCOT_BIG = new ResourceLocation("plebitems", "textures/gui/mascot_big.png");

	/** The PlebMc mascot's head, {@code size} GUI pixels square, smoothly scaled. */
	public static void mascot(float x, float y, float size, float alpha) {
		if (alpha <= 0.01f) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		ResourceLocation loc = size > 28 ? MASCOT_BIG : MASCOT;
		try {
			mc.getTextureManager().bindTexture(loc);
			ITextureObject tex = mc.getTextureManager().getTexture(loc);
			if (tex != null) {
				tex.setBlurMipmap(true, false);
			}
		} catch (Throwable t) {
			return;
		}
		GlStateManager.enableTexture2D();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GlStateManager.enableAlpha();
		GlStateManager.alphaFunc(516, 0.01f);
		GlStateManager.color(1f, 1f, 1f, alpha);
		WorldRenderer wr = Tessellator.getInstance().getWorldRenderer();
		wr.begin(7, DefaultVertexFormats.POSITION_TEX);
		wr.pos(x, y + size, 0).tex(0, 1).endVertex();
		wr.pos(x + size, y + size, 0).tex(1, 1).endVertex();
		wr.pos(x + size, y, 0).tex(1, 0).endVertex();
		wr.pos(x, y, 0).tex(0, 0).endVertex();
		Tessellator.getInstance().draw();
		GlStateManager.alphaFunc(516, 0.1f);
		GlStateManager.color(1f, 1f, 1f, 1f);
	}
}
