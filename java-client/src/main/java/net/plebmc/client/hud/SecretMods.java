package net.plebmc.client.hud;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.plebmc.client.mods.ClientPrefs;

/**
 * The secret mods found by typing in the Mods search (see EasterEggs):
 * <ul>
 * <li>Let It Snow: snow falls over the game and menus, and piles up on the hotbar.</li>
 * <li>Best Friend: Plebio sits next to your hotbar (in a corner on menus) and
 * talks about what happens to you.</li>
 * <li>Big Heads: every head is bigger (ModelBiped draws heads through
 * {@link #renderHead}). Only you see it.</li>
 * </ul>
 */
public final class SecretMods {

	private SecretMods() {
	}

	/** Drawn on the HUD (in game, no menu over it). */
	static void hud(int w, int h) {
		ClientPrefs p = ClientPrefs.get();
		if (!p.snowMod && !p.buddyMod) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer == null || mc.gameSettings.hideGUI) {
			return;
		}
		long now = System.currentTimeMillis();
		GlStateManager.enableBlend();
		if (p.snowMod) {
			snow(w, h, now, true);
		}
		if (p.buddyMod) {
			buddy(w, h, now, mc.thePlayer, w / 2 + 91 + 6);
		}
		GlStateManager.color(1f, 1f, 1f, 1f);
	}

	/**
	 * Drawn over every menu (title screen, inventory, chat, PlebClient screens...).
	 * {@code chat}: the chat screen is open (kept for the hooks; in any world screen the
	 * hotbar shows).
	 */
	public static void gui(int w, int h, boolean chat) {
		ClientPrefs p = ClientPrefs.get();
		if (!p.snowMod && !p.buddyMod) {
			return;
		}
		Minecraft mc = Minecraft.getMinecraft();
		if (w <= 0 || h <= 0) {
			return;
		}
		long now = System.currentTimeMillis();
		GlStateManager.disableDepth();
		GlStateManager.enableBlend();
		GlStateManager.pushMatrix();
		GlStateManager.translate(0f, 0f, 400f);
		// in a world the hotbar is still there under the menu (inventory, chat...), so the
		// snow pile and Plebio stay where the HUD has them; on the title screen menus
		// Plebio sits in the corner
		boolean world = mc.thePlayer != null && mc.theWorld != null;
		if (p.snowMod) {
			snow(w, h, now, world);
		}
		// the title screen has its own Plebio when Title buddy is on
		boolean titleBuddy = mc.currentScreen instanceof net.minecraft.client.gui.GuiMainMenu && p.titleBuddy;
		if (p.buddyMod && !titleBuddy) {
			buddy(w, h, now, mc.thePlayer, world ? w / 2 + 91 + 6 : w - 18 - 6);
		}
		GlStateManager.popMatrix();
		GlStateManager.color(1f, 1f, 1f, 1f);
	}

	// ==================== Let It Snow ====================

	private static long snowSince;

	private static void snow(int w, int h, long now, boolean pileOnHotbar) {
		if (snowSince == 0) {
			snowSince = now;
		}
		double sec = now / 1000.0;
		for (int i = 0; i < 90; i++) {
			double r1 = hash(i * 3 + 1), r2 = hash(i * 7 + 2), r3 = hash(i * 13 + 5);
			double speed = 9 + r2 * 16, life = (h + 20) / speed;
			double k = ((sec + r3 * life) % life) / life;
			int y = (int) (-10 + k * (h + 20));
			int x = (int) (r1 * w + Math.sin(sec * (0.5 + r2) + i) * 9);
			int s = r2 > 0.75 ? 2 : 1;
			Ui.rect(x, y, s, s, Ui.alpha(0xFFFFFFFF, 0x70 + (int) (r3 * 0x70)));
		}
		if (!pileOnHotbar) {
			return;
		}
		// it piles up on top of the hotbar over a minute
		float pile = Math.min(1f, (now - snowSince) / 60000f);
		int hx = w / 2 - 91, hy = h - 22;
		for (int x = 0; x < 182; x++) {
			double n = Math.sin(x * 0.21) * 0.6 + Math.sin(x * 0.07 + 1.3) * 0.8 + 1.6;
			int ph = (int) Math.round(n * pile * 1.6);
			if (ph > 0) {
				Ui.rect(hx + x, hy - ph, 1, ph, 0xF0F4F8FF);
			}
		}
	}

	// ==================== Best Friend ====================

	private static final String[] IDLE = { "We make a great team", "Nice weather for TNT", "I'm watching your back",
			"Have you voted today?", "Quests give you coins, you know", "You're doing great", "Want to hear a joke? No? OK.",
			"I could go for a cookie", "Is that a creeper? Kidding.", "Plebio, reporting for duty" };
	private static String line;
	private static long lineAt, nextIdle;
	private static float lastHealth = -1;
	private static int lastLevel = -1;
	private static boolean warnedHealth, warnedFood, saidNight;

	private static void say(String s, long now) {
		line = s;
		lineAt = now;
		nextIdle = now + 90_000 + (long) (Math.random() * 60_000);
	}

	private static void buddy(int w, int h, long now, EntityPlayerSP pl, int x) {
		// what Plebio reacts to (just small talk without a player)
		if (pl != null) {
			react(now, pl);
		}
		if (nextIdle == 0) {
			say("Let's go!", now);
		} else if (now > nextIdle) {
			say(IDLE[(int) (now / 1000 % IDLE.length)], now);
		}
		drawBuddy(w, h, now, x);
	}

	private static void react(long now, EntityPlayerSP pl) {
		float hp = pl.getHealth();
		if (lastHealth >= 0 && hp < lastHealth - 0.5f) {
			say(hp <= 6 ? "That one hurt. Eat something!" : pickOuch(now), now);
		}
		if (hp <= 6 && !warnedHealth) {
			warnedHealth = true;
			say("Low health! Run!", now);
		} else if (hp > 10) {
			warnedHealth = false;
		}
		lastHealth = hp;
		int food = pl.getFoodStats().getFoodLevel();
		if (food <= 6 && !warnedFood) {
			warnedFood = true;
			say("My tummy is rumbling. Yours too.", now);
		} else if (food > 10) {
			warnedFood = false;
		}
		if (lastLevel >= 0 && pl.experienceLevel > lastLevel) {
			say("Level " + pl.experienceLevel + "! Nice!", now);
		}
		lastLevel = pl.experienceLevel;
		try {
			long t = pl.worldObj.getWorldTime() % 24000L;
			boolean night = t > 13000 && t < 23000;
			if (night && !saidNight) {
				say("It's getting dark... spooky", now);
			}
			saidNight = night;
		} catch (Throwable e) {
			// no world time
		}
	}

	/** Plebio at {@code x}, sitting on the bottom of the screen, with his speech bubble. */
	private static void drawBuddy(int w, int h, long now, int x) {
		int size = 18;
		int y = h - size - 2;
		float bob = (float) Math.abs(Math.sin(now / 420.0)) * -1.5f;
		boolean talking = line != null && now - lineAt < 4500;
		if (talking && now - lineAt < 300) {
			bob -= (float) Math.sin((now - lineAt) / 300.0 * Math.PI) * 4;
		}
		Ui.mascot(x, y + bob, size, 1f);
		if (talking) {
			float a = Math.min(1f, Math.min((now - lineAt) / 150f, (4500 - (now - lineAt)) / 400f));
			int tw = Ui.width(line);
			int bw = tw + 10, bh = 14;
			int bx = Math.min(w - bw - 2, x + size / 2 - 8), by = y - bh - 4;
			int al = Math.round(255 * a);
			Ui.round(bx - 1, by - 1, bw + 2, bh + 2, Ui.alpha(0xFF15181C, al));
			Ui.round(bx, by, bw, bh, Ui.alpha(0xFFFFFFFF, al));
			Ui.rect(x + size / 2 - 2, by + bh, 3, 2, Ui.alpha(0xFFFFFFFF, al));
			if (al > 8) {
				Ui.text(line, bx + 5, by + 3, Ui.alpha(0xFF15181C, al));
			}
		}
	}

	private static String pickOuch(long now) {
		String[] o = { "Ouch!", "Hey, that hurt!", "Watch out!", "Hit them back!" };
		return o[(int) (now / 97 % o.length)];
	}

	// ==================== Big Heads ====================

	private static final float BIG = 1.75f;

	/** ModelBiped: draws a head (or the hat layer on it), bigger when Big Heads is on. */
	public static void renderHead(ModelRenderer part, float scale, Entity entity) {
		if (!bigHeads(entity)) {
			part.render(scale);
			return;
		}
		GlStateManager.pushMatrix();
		GlStateManager.translate(part.rotationPointX * scale, part.rotationPointY * scale, part.rotationPointZ * scale);
		GlStateManager.scale(BIG, BIG, BIG);
		GlStateManager.translate(-part.rotationPointX * scale, -part.rotationPointY * scale, -part.rotationPointZ * scale);
		part.render(scale);
		GlStateManager.popMatrix();
	}

	static boolean bigHeads(Entity entity) {
		return ClientPrefs.get().bigHeadMod && entity != null && !(entity instanceof EntityArmorStand);
	}

	/** How much bigger a head is (for hats drawn on it). */
	static float headScale(Entity entity) {
		return bigHeads(entity) ? BIG : 1f;
	}

	private static double hash(int i) {
		double x = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
		return x - Math.floor(x);
	}
}
