package net.plebmc.client.hud;

import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;
import net.plebmc.client.mods.ClientPrefs;
import net.plebmc.client.mods.Secrets;

/**
 * Secrets. Found ones are remembered in {@link ClientPrefs#secrets} and
 * counted in Settings, with a hint for each one still hidden.
 *
 * The pure parts (matchers, holidays) are in {@link Secrets}.
 *
 * <ul>
 * <li>konami: the Konami code (title screen or in game) turns on rainbow menus</li>
 * <li>rain: type "pleb" on the title screen</li>
 * <li>roll: type "barrelroll" on the title screen</li>
 * <li>dinnerbone: type "dinnerbone" on the title screen</li>
 * <li>golden: click the little mascot ten times</li>
 * <li>plebium: click the Video Settings title five times</li>
 * <li>holiday: open the game on a holiday</li>
 * <li>fpslarp: type "fpslarp" in the Mods search</li>
 * </ul>
 */
public final class EasterEggs {

	public static final String[] IDS = Secrets.IDS;
	public static final String[] HINTS = Secrets.HINTS;

	private EasterEggs() {
	}

	// ==================== input ====================

	private static final Secrets keys = new Secrets();

	/** A key on the title screen. */
	public static void titleKey(char c, int key) {
		if (konami(key)) {
			return;
		}
		String id = keys.letter(c);
		if ("rain".equals(id)) {
			rain();
		} else if ("roll".equals(id)) {
			rollStart = System.currentTimeMillis();
			found("roll", "Do a barrel roll!", "Wheee");
		} else if ("dinnerbone".equals(id)) {
			flipUntil = System.currentTimeMillis() + 6000;
			found("dinnerbone", "Dinnerbone was here", "Everything is upside down for a bit");
		}
	}

	/** A key in game with no menu open. */
	public static void gameKey(int key) {
		konami(key);
	}

	/** Feeds one key to the Konami matcher; true when the code completes. */
	static boolean konami(int key) {
		if (!keys.konami(key)) {
			return false;
		}
		ClientPrefs p = ClientPrefs.get();
		p.rainbowAccent = !p.rainbowAccent;
		rainStart = System.currentTimeMillis();
		found("konami", "Konami code!", p.rainbowAccent ? "Rainbow menus on (do it again to turn off)" : "Rainbow menus off");
		return true;
	}

	static void rain() {
		rainStart = System.currentTimeMillis();
		found("rain", "It's raining plebs!", "Hallelujah");
	}

	// ==================== mascot and video screen ====================

	private static int buddyClicks;
	private static boolean golden;

	/** The title screen's little mascot was clicked. */
	static void buddyClicked() {
		if (++buddyClicks == 10) {
			golden = true;
			found("golden", "The Golden Pleb", "You poked him ten times. He's golden now.");
		}
	}

	static boolean goldenBuddy() {
		return golden;
	}

	private static int titleClicks;
	private static long titleClickAt;

	/** The Video Settings title was clicked. */
	static void videoTitleClicked() {
		long now = System.currentTimeMillis();
		titleClicks = now - titleClickAt > 1500 ? 1 : titleClicks + 1;
		titleClickAt = now;
		if (titleClicks >= 5) {
			titleClicks = 0;
			found("plebium", "Sodium? Never heard of her.", "This menu runs on pure Plebium");
		}
	}

	// ==================== holidays ====================

	private static Object[] today;
	private static long todayAt;

	static Object[] today() {
		long now = System.currentTimeMillis();
		if (now - todayAt > 60_000) {
			todayAt = now;
			try {
				java.util.Calendar c = java.util.Calendar.getInstance();
				today = Secrets.holiday(c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH),
						c.get(java.util.Calendar.DAY_OF_WEEK));
			} catch (Throwable t) {
				today = null;
			}
		}
		return today;
	}

	private static boolean holidaySeen;

	/** Holiday extras on the title screen: a greeting and falling confetti in the holiday's colors. */
	static void titleExtras(int w, int h, long now) {
		Object[] hd = today();
		if (hd == null) {
			return;
		}
		if (!holidaySeen) {
			holidaySeen = true;
			found("holiday", (String) hd[0], "From everyone at PlebMc");
		}
		int c1 = (Integer) hd[1], c2 = (Integer) hd[2];
		if (ClientPrefs.get().animations) {
			double sec = now / 1000.0;
			for (int i = 0; i < 28; i++) {
				double r1 = hash(i * 5 + 3), r2 = hash(i * 11 + 7), r3 = hash(i * 17 + 1);
				double speed = 12 + r2 * 16, life = (h + 20) / speed;
				double k = ((sec + r3 * life) % life) / life;
				int y = (int) (-10 + k * (h + 20));
				int x = (int) (r1 * w + Math.sin(sec * (0.8 + r2) + i) * 10);
				int s = r3 > 0.5 ? 3 : 2;
				int col = (i & 1) == 0 ? c1 : c2;
				Ui.rect(x, y, s, s - (int) (Math.abs(Math.sin(sec * 3 + i)) * (s - 1)), Ui.alpha(col, 0xB0));
			}
		}
		String g = (String) hd[0];
		int gw = Ui.width(g) + 16;
		Ui.card((w - gw) / 2, 4, gw, 14, 0xC00B1620, Ui.alpha(c1, 0xC0));
		Ui.centered(g, w / 2, 7, c1);
	}

	// ==================== rainbow menus ====================

	/** The accent color while rainbow menus are on. */
	static int rainbow() {
		float hue = (System.currentTimeMillis() % 9000L) / 9000f;
		return 0xFF000000 | hsv(hue, 0.62f, 1f);
	}

	static int hsv(float h, float s, float v) {
		float r, g, b;
		int i = (int) Math.floor(h * 6f);
		float f = h * 6f - i, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
		switch (((i % 6) + 6) % 6) {
		case 0:
			r = v; g = t; b = p;
			break;
		case 1:
			r = q; g = v; b = p;
			break;
		case 2:
			r = p; g = v; b = t;
			break;
		case 3:
			r = p; g = q; b = v;
			break;
		case 4:
			r = t; g = p; b = v;
			break;
		default:
			r = v; g = p; b = q;
			break;
		}
		return Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
	}

	// ==================== title screen spin ====================

	private static long rollStart;
	private static long flipUntil;
	private static boolean pushed;

	/** Start of the title screen's drawing: the barrel roll and Dinnerbone turn it. */
	static void beginTitle(int w, int h) {
		long now = System.currentTimeMillis();
		float ang = 0f;
		if (rollStart != 0) {
			float t = (now - rollStart) / 1400f;
			if (t >= 1f) {
				rollStart = 0;
			} else {
				ang += 360f * (t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2);
			}
		}
		if (flipUntil != 0) {
			long left = flipUntil - now;
			if (left <= 0) {
				flipUntil = 0;
			} else {
				float in = Math.min(1f, (6000 - left) / 400f), out = Math.min(1f, left / 400f);
				ang += 180f * Math.min(in, out);
			}
		}
		pushed = ang != 0f;
		if (pushed) {
			GlStateManager.pushMatrix();
			GlStateManager.translate(w / 2f, h / 2f, 0f);
			GlStateManager.rotate(ang, 0f, 0f, 1f);
			GlStateManager.translate(-w / 2f, -h / 2f, 0f);
		}
	}

	static void endTitle() {
		if (pushed) {
			pushed = false;
			GlStateManager.popMatrix();
		}
	}

	// ==================== overlay: mascot rain and the banner ====================

	private static final long RAIN_MS = 5000, BANNER_MS = 5000;
	private static long rainStart;
	private static String bannerTitle, bannerLine;
	private static long bannerAt;
	private static boolean bannerNew;

	/** Records a secret and shows its banner. */
	static void found(String id, String title, String line) {
		ClientPrefs p = ClientPrefs.get();
		boolean first = p.addSecret(id);
		bannerTitle = title;
		bannerLine = first ? line + "  §e(secret " + p.secretCount() + " of " + IDS.length + " found)" : line;
		bannerAt = 0;
		bannerNew = first;
		try {
			PlebClient.saveSettings();
		} catch (Throwable t) {
			// not running in a game (tests)
		}
	}

	/** Drawn last on the title screen, every PlebClient menu and the HUD. */
	public static void overlay(int w, int h) {
		long now = System.currentTimeMillis();
		if (rainStart == 0 && bannerTitle == null) {
			return;
		}
		GlStateManager.pushMatrix();
		GlStateManager.translate(0f, 0f, 400f);
		GlStateManager.enableBlend();
		if (rainStart != 0) {
			long t = now - rainStart;
			if (t > RAIN_MS) {
				rainStart = 0;
			} else {
				for (int i = 0; i < 36; i++) {
					double r1 = hash(i * 3 + 11), r2 = hash(i * 7 + 13), r3 = hash(i * 19 + 2);
					float delay = (float) (r3 * 2200);
					float k = (t - delay) / 1000f;
					if (k < 0) {
						continue;
					}
					float size = 12 + (float) r2 * 16;
					float y = -size + k * (70 + (float) r2 * 110);
					if (y > h) {
						continue;
					}
					float x = (float) (r1 * (w + size)) - size / 2f;
					float fade = Math.min(1f, (RAIN_MS - t) / 600f);
					GlStateManager.pushMatrix();
					GlStateManager.translate(x + size / 2f, y + size / 2f, 0f);
					GlStateManager.rotate((float) ((r1 - 0.5) * 360 * k), 0f, 0f, 1f);
					Ui.mascot(-size / 2f, -size / 2f, size, fade);
					GlStateManager.popMatrix();
				}
			}
		}
		if (bannerTitle != null) {
			if (bannerAt == 0) {
				bannerAt = now;
				sound(bannerNew ? "random.levelup" : "random.orb");
			}
			long t = now - bannerAt;
			if (t > BANNER_MS) {
				bannerTitle = null;
			} else {
				float in = Math.min(1f, t / 250f), out = Math.min(1f, (BANNER_MS - t) / 500f);
				float k = Math.min(in, out);
				int bw = Math.max(Ui.width(bannerTitle) * 2, Ui.width(bannerLine)) + 50;
				int bh = 40;
				int x = (w - bw) / 2;
				float e = 1f - (1f - in) * (1f - in) * (1f - in);
				int y = Math.round(-bh + (bh + 24) * e);
				int a = Math.round(255 * k);
				int c = ClientPrefs.get().rainbowAccent ? rainbow() : Ui.GOLD;
				Ui.card(x, y, bw, bh, Ui.alpha(0xFF0B1620, Math.round(0xE8 * k)), Ui.alpha(c, a));
				if (a > 8) {
					Ui.mascot(x + 8, y + 6, 28, k);
					Ui.big(bannerTitle, x + 42, y + 7, 2f, Ui.alpha(c, a), true);
					Ui.shadow(bannerLine, x + 42, y + 27, Ui.alpha(0xFFFFFFFF, a));
				}
			}
		}
		GlStateManager.popMatrix();
		GlStateManager.color(1f, 1f, 1f, 1f);
	}

	private static void sound(String name) {
		try {
			Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation(name), 1.0F));
		} catch (Throwable t) {
			// no sound
		}
	}

	private static double hash(int n) {
		long x = n * 0x9E3779B97F4A7C15L;
		x ^= x >>> 31;
		x *= 0xBF58476D1CE4E5B9L;
		x ^= x >>> 29;
		return (x >>> 11) / (double) (1L << 53);
	}
}
