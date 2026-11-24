package net.plebmc.client.hud;

import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.plebmc.client.cosmetics.Cosmetics;

/**
 * The secret dev panel (type "plebdev" in the shop). It only opens when the
 * server says this player has plebshop.dev, and every button is checked
 * again on the server, so a modified client gets nothing from it. Lets devs
 * test the economy on themselves: coins, quests, the login reward and
 * cosmetics, plus a live log of what the server sent.
 */
public final class DevScreen extends GuiScreen {

	private static final int ID_DONE = 1;
	private static final int ID_LOG = 2;
	private static final int ID_PREV_Q = 3;
	private static final int ID_NEXT_Q = 4;
	private static final int ID_ACTION = 100;

	/** {label, action, amount or null}. */
	private static final String[][] ACTIONS = { { "+100", "coins", "100" }, { "+1,000", "coins", "1000" },
			{ "+10,000", "coins", "10000" }, { "Coins to 0", "zero", null }, { "Finish all quests", "complete", null },
			{ "Reset my quests", "resetquests", null }, { "Claim login now", "claimlogin", null },
			{ "Reset login", "resetlogin", null }, { "Unlock all cosmetics", "unlockall", null },
			{ "Remove all cosmetics", "lockall", null } };

	private final GuiScreen parent;
	private boolean showLog;
	private int quest;
	private int seen = -1;

	public DevScreen(GuiScreen parent) {
		this.parent = parent;
	}

	@Override
	public void initGui() {
		seen = Cosmetics.devVersion();
		this.buttonList.clear();
		int colW = 96;
		int cols = 4;
		int x0 = this.width / 2 - (cols * (colW + 4) - 4) / 2;
		int y0 = 62;
		for (int i = 0; i < ACTIONS.length; i++) {
			int x = x0 + (i % cols) * (colW + 4);
			int y = y0 + (i / cols) * 24;
			this.buttonList.add(new GuiButton(ID_ACTION + i, x, y, colW, 20, ACTIONS[i][0]));
		}
		int qy = y0 + ((ACTIONS.length + cols - 1) / cols) * 24 + 6;
		List<String[]> qs = Cosmetics.devQuests();
		quest = qs.isEmpty() ? 0 : Math.min(quest, qs.size() - 1);
		this.buttonList.add(new GuiButton(ID_PREV_Q, x0, qy, 20, 20, "<"));
		this.buttonList.add(new GuiButton(ID_NEXT_Q, x0 + 24, qy, 20, 20, ">"));
		GuiButton finish = new GuiButton(ID_ACTION + 50, x0 + 48 + 4 * 0, qy, 96, 20, "Finish quest");
		GuiButton plus = new GuiButton(ID_ACTION + 51, x0 + 148, qy, 96, 20, "+1 progress");
		finish.enabled = plus.enabled = !qs.isEmpty();
		this.buttonList.add(finish);
		this.buttonList.add(plus);
		this.buttonList.add(new GuiButton(ID_LOG, x0 + 248, qy, 148, 20, showLog ? "Hide server log" : "Show server log"));
		this.buttonList.add(new GuiButton(ID_DONE, this.width / 2 - 100, this.height - 26, 200, 20, "Done"));
	}

	@Override
	protected void actionPerformed(GuiButton b) {
		List<String[]> qs = Cosmetics.devQuests();
		if (b.id == ID_DONE) {
			this.mc.displayGuiScreen(parent);
			return;
		}
		if (b.id == ID_LOG) {
			showLog = !showLog;
		} else if (b.id == ID_PREV_Q && !qs.isEmpty()) {
			quest = (quest + qs.size() - 1) % qs.size();
		} else if (b.id == ID_NEXT_Q && !qs.isEmpty()) {
			quest = (quest + 1) % qs.size();
		} else if (b.id == ID_ACTION + 50 && !qs.isEmpty()) {
			Cosmetics.dev("complete", "id", qs.get(quest)[0]);
		} else if (b.id == ID_ACTION + 51 && !qs.isEmpty()) {
			String[] q = qs.get(quest);
			Cosmetics.dev("progress", "questType", q[3], "category", q[2]);
		} else if (b.id >= ID_ACTION && b.id < ID_ACTION + ACTIONS.length) {
			String[] a = ACTIONS[b.id - ID_ACTION];
			if (a[2] != null) {
				Cosmetics.dev(a[1], "amount", Long.parseLong(a[2]));
			} else {
				Cosmetics.dev(a[1]);
			}
		}
		this.initGui();
	}

	@Override
	public void updateScreen() {
		if (Cosmetics.devVersion() != seen) {
			this.initGui();
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawDefaultBackground();
		this.drawCenteredString(this.fontRendererObj, "§c§lPlebMc Dev Panel", this.width / 2, 6, 0xFFFFFF);
		String bal = CosmeticsScreen.fmt(Math.max(0, Cosmetics.coins())) + " " + Cosmetics.currency();
		this.drawCenteredString(this.fontRendererObj, "§7Testing on yourself only. §e" + bal, this.width / 2, 18,
				0xFFFFFF);
		List<String> info = Cosmetics.devInfo();
		for (int i = 0; i < info.size() && i < 3; i++) {
			this.drawCenteredString(this.fontRendererObj, Draw.fit("§8" + info.get(i), this.width - 10), this.width / 2,
					30 + i * 10, 0xFFFFFF);
		}
		int y0 = 62;
		int qy = y0 + ((ACTIONS.length + 3) / 4) * 24 + 6;
		List<String[]> qs = Cosmetics.devQuests();
		int x0 = this.width / 2 - (4 * 100 - 4) / 2;
		String q = qs.isEmpty() ? "§7No quests today" : "§f" + qs.get(quest)[1] + " §8(" + qs.get(quest)[2] + ", "
				+ qs.get(quest)[3] + ")";
		this.fontRendererObj.drawStringWithShadow(Draw.fit(q, 396), x0, qy + 24, 0xFFFFFF);

		if (showLog) {
			int ly = qy + 38;
			int bottom = this.height - 32;
			Gui.drawRect(x0 - 2, ly - 2, x0 + 398, bottom, 0xA0000000);
			List<String> log = Cosmetics.protocolLog();
			int lines = Math.max(0, (bottom - ly) / 9);
			for (int i = 0; i < lines && i < log.size(); i++) {
				String l = log.get(log.size() - 1 - i);
				this.fontRendererObj.drawString(Draw.fit(l.replace('§', '&'), 396), x0, ly + i * 9, 0xA0E0A0);
			}
		}
		String m = Cosmetics.devMessage();
		if (m != null) {
			this.drawCenteredString(this.fontRendererObj, "§a" + m, this.width / 2, this.height - 40, 0xFFFFFF);
		}
		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	@Override
	protected void keyTyped(char c, int key) {
		if (key == 1) {
			this.mc.displayGuiScreen(parent);
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
