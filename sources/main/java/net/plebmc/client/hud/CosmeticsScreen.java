package net.plebmc.client.hud;

import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.plebmc.client.cosmetics.Cosmetics;

/**
 * The cosmetics shop: your coin balance, a live preview of your player,
 * and the hats and trails for sale. Buying and equipping go to the server
 * (it owns the coins); "Try on" only changes your preview, so it also works
 * offline and on other servers.
 */
public final class CosmeticsScreen extends GuiScreen {

	private static final int ID_DONE = 1;
	private static final int ID_TAB = 10; // + category index
	private static final int ID_PREV = 20;
	private static final int ID_NEXT = 21;
	private static final int ID_TRY = 100; // + row
	private static final int ID_ACT = 200; // + row

	private static final int ROWS = 5;

	private final GuiScreen parent;
	private int tab;
	private int page;
	private int seenVersion = -1;
	private List<Cosmetics.Item> items;
	private int listX;
	private int listY;
	private int listW;
	private int previewX;

	public CosmeticsScreen(GuiScreen parent) {
		this.parent = parent;
	}

	@Override
	public void initGui() {
		if (seenVersion < 0) {
			Cosmetics.requestState();
		}
		seenVersion = Cosmetics.version();
		this.buttonList.clear();
		items = Cosmetics.catalog(Cosmetics.CATEGORIES[tab]);
		int pages = Math.max(1, (items.size() + ROWS - 1) / ROWS);
		page = Math.min(page, pages - 1);

		// [ player preview 80 ] gap [ item list ]
		listW = Math.max(200, Math.min(330, this.width - 100));
		int startX = Math.max(4, (this.width - (88 + listW)) / 2);
		previewX = startX + 40;
		listX = startX + 88;
		listY = 52;

		// tabs
		String[] names = { "Hats", "Trails" };
		for (int i = 0; i < names.length; i++) {
			GuiButton b = new GuiButton(ID_TAB + i, listX + i * 74, 28, 70, 20, (i == tab ? "§e" : "") + names[i]);
			this.buttonList.add(b);
		}

		boolean online = Cosmetics.serverSupported();
		int rowH = 26;
		for (int r = 0; r < ROWS; r++) {
			int idx = page * ROWS + r;
			if (idx >= items.size()) {
				break;
			}
			Cosmetics.Item it = items.get(idx);
			int y = listY + r * rowH;
			boolean trying = it.id.equals(Cosmetics.previewId(it.category));
			GuiButton tryOn = new GuiButton(ID_TRY + r, listX + listW - 132, y + 2, 56, 20,
					trying ? "Take off" : "Try on");
			this.buttonList.add(tryOn);
			String act;
			boolean enabled = online;
			if (Cosmetics.owns(it.id)) {
				act = it.id.equals(Cosmetics.equippedId(it.category)) ? "§aEquipped" : "Equip";
			} else {
				act = "§6" + fmt(it.price);
				enabled = online && Cosmetics.coins() >= it.price;
			}
			GuiButton a = new GuiButton(ID_ACT + r, listX + listW - 72, y + 2, 70, 20, act);
			a.enabled = enabled;
			this.buttonList.add(a);
		}

		int by = listY + ROWS * rowH + 6;
		GuiButton prev = new GuiButton(ID_PREV, listX, by, 20, 20, "<");
		prev.enabled = page > 0;
		GuiButton next = new GuiButton(ID_NEXT, listX + 24, by, 20, 20, ">");
		next.enabled = page < pages - 1;
		this.buttonList.add(prev);
		this.buttonList.add(next);
		this.buttonList.add(new GuiButton(ID_DONE, this.width / 2 - 100, this.height - 26, 200, 20, "Done"));
	}

	@Override
	protected void actionPerformed(GuiButton b) {
		if (b.id == ID_DONE) {
			close();
			return;
		}
		if (b.id >= ID_TAB && b.id < ID_TAB + Cosmetics.CATEGORIES.length) {
			tab = b.id - ID_TAB;
			page = 0;
		} else if (b.id == ID_PREV) {
			page = Math.max(0, page - 1);
		} else if (b.id == ID_NEXT) {
			page++;
		} else if (b.id >= ID_TRY && b.id < ID_TRY + ROWS) {
			Cosmetics.Item it = row(b.id - ID_TRY);
			if (it != null) {
				boolean trying = it.id.equals(Cosmetics.previewId(it.category));
				Cosmetics.setPreview(it.category, trying ? null : it.id);
			}
		} else if (b.id >= ID_ACT && b.id < ID_ACT + ROWS) {
			Cosmetics.Item it = row(b.id - ID_ACT);
			if (it != null) {
				if (!Cosmetics.owns(it.id)) {
					Cosmetics.buy(it.id);
				} else if (it.id.equals(Cosmetics.equippedId(it.category))) {
					Cosmetics.equip(it.category, null);
				} else {
					Cosmetics.equip(it.category, it.id);
					Cosmetics.setPreview(it.category, null);
				}
			}
		}
		this.initGui();
	}

	private Cosmetics.Item row(int r) {
		int idx = page * ROWS + r;
		return items != null && idx < items.size() ? items.get(idx) : null;
	}

	@Override
	public void updateScreen() {
		if (Cosmetics.version() != seenVersion) {
			this.initGui();
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawDefaultBackground();
		this.drawCenteredString(this.fontRendererObj, "§lCosmetics", this.width / 2, 8, 0xFFFFFF);

		// balance, top right
		long coins = Cosmetics.coins();
		String bal = coins >= 0 ? fmt(coins) + " coins" : "";
		if (!bal.isEmpty()) {
			int w = this.fontRendererObj.getStringWidth(bal);
			int x = this.width - 8 - w;
			coin(x - 12, 7);
			this.fontRendererObj.drawStringWithShadow(bal, x, 8, 0xFFD54A);
		}

		// player preview on the left
		int px = previewX;
		int py = listY + 120;
		Gui.drawRect(px - 38, listY - 4, px + 38, py + 12, 0x60000000);
		if (this.mc.thePlayer != null) {
			net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager.color(1f, 1f, 1f, 1f);
			GuiInventory.drawEntityOnScreen(px, py, 50, px - mouseX, py - 75 - mouseY, this.mc.thePlayer);
		}

		// rows
		int rowH = 26;
		for (int r = 0; r < ROWS; r++) {
			Cosmetics.Item it = row(r);
			if (it == null) {
				break;
			}
			int y = listY + r * rowH;
			Gui.drawRect(listX, y, listX + listW, y + 24, 0x70000000);
			boolean eq = it.id.equals(Cosmetics.equippedId(it.category));
			int accent = eq ? 0xFF55FF55 : Cosmetics.owns(it.id) ? 0xFF3FA9F5 : 0xFF666666;
			Gui.drawRect(listX, y, listX + 2, y + 24, accent);
			int textW = listW - 140;
			this.fontRendererObj.drawStringWithShadow(Draw.fit(it.name, textW), listX + 6, y + 3, 0xFFFFFF);
			String sub = Cosmetics.owns(it.id) ? (eq ? "§aEquipped" : "§bOwned") : "§7" + it.description;
			this.fontRendererObj.drawStringWithShadow(Draw.fit(sub, textW), listX + 6, y + 13, 0xAAAAAA);
		}

		int infoY = listY + ROWS * rowH + 12;
		String msg = Cosmetics.message();
		if (msg != null) {
			this.drawCenteredString(this.fontRendererObj, (Cosmetics.messageOk() ? "§a" : "§c") + msg,
					this.width / 2, this.height - 40, 0xFFFFFF);
		} else if (!Cosmetics.serverSupported()) {
			String s = "§7Try anything on here. Buy with coins on PlebMc.";
			this.drawCenteredString(this.fontRendererObj, Draw.fit(s, this.width - 10), this.width / 2, this.height - 40,
					0xFFFFFF);
		}
		if (items.isEmpty()) {
			this.drawCenteredString(this.fontRendererObj, "§7Nothing here yet", listX + listW / 2, infoY - 60,
					0xFFFFFF);
		}
		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	/** A small gold coin. */
	private static void coin(int x, int y) {
		Gui.drawRect(x + 2, y, x + 8, y + 10, 0xFFB8860B);
		Gui.drawRect(x, y + 2, x + 10, y + 8, 0xFFB8860B);
		Gui.drawRect(x + 2, y + 1, x + 8, y + 9, 0xFFFFD54A);
		Gui.drawRect(x + 1, y + 2, x + 9, y + 8, 0xFFFFD54A);
		Gui.drawRect(x + 4, y + 3, x + 6, y + 7, 0xFFB8860B);
	}

	static String fmt(long n) {
		String s = Long.toString(n);
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			if (i > 0 && (s.length() - i) % 3 == 0) {
				b.append(',');
			}
			b.append(s.charAt(i));
		}
		return b.toString();
	}

	@Override
	protected void keyTyped(char c, int key) {
		if (key == 1) {
			close();
		}
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	private void close() {
		Cosmetics.clearPreview();
		this.mc.displayGuiScreen(parent);
		if (parent == null) {
			this.mc.setIngameFocus();
		}
	}
}
