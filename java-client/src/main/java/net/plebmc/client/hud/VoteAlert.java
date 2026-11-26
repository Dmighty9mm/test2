package net.plebmc.client.hud;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;
import net.plebmc.client.cosmetics.Cosmetics;

/**
 * The big "thanks for voting" banner at the top of the screen when a vote on a
 * server list pays out. Its clock starts the first time it is drawn, so a vote
 * made in another browser tab still shows in full when you come back.
 */
public final class VoteAlert {

	private static final long SHOW_MS = 8000;
	private static long amount = -1;
	private static String currency = "";
	private static long start;

	private VoteAlert() {
	}

	static void render(int guiW, int guiH) {
		long got = Cosmetics.takeVoteAlert();
		if (got >= 0) {
			amount = got;
			currency = Cosmetics.voteAlertCurrency();
			start = 0;
		}
		if (amount < 0) {
			return;
		}
		long now = System.currentTimeMillis();
		if (start == 0) {
			start = now;
			try {
				Minecraft.getMinecraft().getSoundHandler()
						.playSound(PositionedSoundRecord.create(new ResourceLocation("random.levelup"), 1.0F));
			} catch (Throwable t) {
				// no sound
			}
		}
		long t = now - start;
		if (t > SHOW_MS) {
			amount = -1;
			return;
		}
		// slide in, hold, fade out
		float in = Math.min(1f, t / 250f), out = Math.min(1f, (SHOW_MS - t) / 600f);
		float k = Math.min(in, out);
		String title = "Thanks for voting!";
		String line = "+" + String.format("%,d", amount) + " " + currency;
		int w = Math.max(Ui.width(title) * 2, Ui.width(line)) + 50;
		int h = 40;
		int x = (guiW - w) / 2;
		int y = Math.round(-h + (h + 14) * ease(in));
		int a = Math.round(255 * k);
		GlStateManager.pushMatrix();
		GlStateManager.translate(0f, 0f, 300f);
		GlStateManager.enableBlend();
		Ui.card(x, y, w, h, Ui.alpha(0xFF0B1620, Math.round(0xE8 * k)), Ui.alpha(Ui.GOLD, a));
		Ui.rect(x + 1, y + 1, w - 2, 1, Ui.alpha(Ui.GOLD, a));
		if (a > 8) {
			Ui.mascot(x + 8, y + 6, 28, k);
			Ui.big(title, x + 42, y + 7, 2f, Ui.alpha(Ui.GOLD, a), true);
			Ui.shadow(line, x + 42, y + 27, Ui.alpha(0xFFFFFFFF, a));
		}
		GlStateManager.popMatrix();
		GlStateManager.color(1f, 1f, 1f, 1f);
	}

	private static float ease(float f) {
		return 1f - (1f - f) * (1f - f) * (1f - f);
	}
}
