package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.plebmc.client.cosmetics.Cosmetics;

/**
 * The cosmetics shop. Your player on the left (drag to spin it), the items
 * of one category as cards on the right. Selecting a card tries it on (only
 * you see that, and only until you leave the shop); buying and equipping go
 * to the server, which owns the coins.
 */
public final class ShopScreen extends PlebScreen {

	/** Where the collection tabs start, so the header's subtitle stops before them. */
	private int tabsLeft = Integer.MAX_VALUE / 2;

	private String category = Cosmetics.HAT;
	private String collection = Cosmetics.collections().isEmpty() ? Cosmetics.MAIN : Cosmetics.collections().get(0).id;
	private int page;
	private String selectedId;
	private int seenVersion = -1;
	private static final float FRONT = -25f;
	private float yaw = FRONT;
	private float yawTarget = FRONT;
	private boolean spinning;
	private int spinLastX;

	private int wx, wy, ww, wh;
	private int pvx, pvy, pvw, pvh;
	private int gx, gy, gw, gh;
	private int cols, rows;

	public ShopScreen(GuiScreen parent) {
		super(parent);
		Cosmetics.requestState();
	}

	private List<Cosmetics.Item> items() {
		return items(category);
	}

	/** The "Owned" tab: everything you own in a category, from every collection. */
	private static final String OWNED = "_owned";

	private List<Cosmetics.Item> items(String cat) {
		if (OWNED.equals(collection)) {
			List<Cosmetics.Item> out = new java.util.ArrayList<>();
			for (Cosmetics.Item it : Cosmetics.catalog(cat)) {
				if (Cosmetics.owns(it.id)) {
					out.add(it);
				}
			}
			return out;
		}
		return Cosmetics.catalog(cat, collection);
	}

	/** The shop's collections plus an "Owned" tab, so anything you own can be worn from here. */
	private static List<Cosmetics.Collection> tabs() {
		List<Cosmetics.Collection> out = new java.util.ArrayList<>(Cosmetics.collections());
		out.add(new Cosmetics.Collection(OWNED, "Owned", "check", 0xFF5BD98A, "Everything you own, from every collection"));
		return out;
	}

	private static boolean hasTab(String id) {
		for (Cosmetics.Collection c : tabs()) {
			if (c.id.equals(id)) {
				return true;
			}
		}
		return false;
	}

	private static String categoryName(String c) {
		return Cosmetics.HAT.equals(c) ? "Hats" : Character.toUpperCase(c.charAt(0)) + c.substring(1) + "s";
	}

	@Override
	protected void build() {
		seenVersion = Cosmetics.version();
		ww = Math.min(this.width - 16, 480);
		wh = Math.min(this.height - 16, 300);
		wx = (this.width - ww) / 2;
		wy = (this.height - wh) / 2;
		closeButton(wx + ww - 22, wy + 10);

		pvx = wx + 8;
		pvy = wy + 44;
		pvw = Math.max(96, Math.min(130, ww / 4));
		pvh = wy + wh - 8 - pvy;

		gx = pvx + pvw + 10;
		gw = wx + ww - 10 - gx;
		gy = wy + 66;
		gh = wy + wh - 8 - 44 - gy;

		// collections (PlebMc, Partners, ...) as a switch in the header
		if (!hasTab(collection)) {
			collection = tabs().get(0).id;
		}
		int segW = 0;
		for (Cosmetics.Collection col : tabs()) {
			segW += Ui.width(col.name) + 13 + 14 + 2;
		}
		// left of the coin balance and the close button, so they never overlap
		int coinW = coinChipWidth();
		int sx = wx + ww - 30 - (coinW > 0 ? coinW + 6 : 0) - segW;
		tabsLeft = sx;
		for (Cosmetics.Collection col : tabs()) {
			int w = Ui.width(col.name) + 13 + 14;
			Tab t = tab(col.name, Ui.hasIcon(col.icon) ? col.icon : "shop", col.color, sx, wy + 11, w, 16,
					() -> collection.equals(col.id), () -> {
						if (!collection.equals(col.id)) {
							collection = col.id;
							page = 0;
							selectedId = null;
							rebuild();
						}
					});
			t.tip = col.description.isEmpty() ? col.name : col.description;
			sx += w + 2;
		}

		// category tabs (every category the shop knows)
		int tx = gx;
		for (String c : Cosmetics.CATEGORIES) {
			List<Cosmetics.Item> all = items(c);
			int owned = 0;
			for (Cosmetics.Item it : all) {
				if (Cosmetics.owns(it.id)) {
					owned++;
				}
			}
			String label = categoryName(c);
			int w = Ui.width(label) + 13 + Ui.pillWidth(owned + "/" + all.size()) + 16;
			Tab t = tab(label, Ui.hasIcon(c) ? c : "shop", 0, tx, wy + 44, w, 18, () -> category.equals(c), () -> {
				if (!category.equals(c)) {
					category = c;
					page = 0;
					selectedId = null;
					// cloaks are on your back: turn the preview around
					yawTarget = Cosmetics.CLOAK.equals(c) ? FRONT + 180f : FRONT;
					rebuild();
				}
			});
			t.badge = owned + "/" + all.size();
			t.tip = "You own " + owned + " of " + all.size();
			tx += w + 4;
		}

		// cards
		int cw = 78, ch = 50, gap = 5;
		cols = Math.max(1, (gw + gap) / (cw + gap));
		rows = Math.max(1, (gh + gap) / (ch + gap));
		cw = (gw - (cols - 1) * gap) / cols;
		List<Cosmetics.Item> its = items();
		int per = cols * rows;
		int pages = Math.max(1, (its.size() + per - 1) / per);
		page = Math.max(0, Math.min(page, pages - 1));
		for (int i = 0; i < per; i++) {
			int idx = page * per + i;
			if (idx >= its.size()) {
				break;
			}
			ItemCard card = new ItemCard(its.get(idx));
			card.at(gx + (i % cols) * (cw + gap), gy + (i / cols) * (ch + gap), cw, ch);
			widgets.add(card);
		}
		if (pages > 1) {
			int py = wy + 44;
			Button next = button("", wx + ww - 10 - 16, py + 1, 16, 16, () -> {
				page++;
				rebuild();
			});
			next.icon("next").enabled = page < pages - 1;
			next.tip = "Next page";
			Button prev = button("", wx + ww - 10 - 16 - 50, py + 1, 16, 16, () -> {
				page--;
				rebuild();
			});
			prev.icon("back").enabled = page > 0;
			prev.tip = "Previous page";
		}

		// the selected item's actions
		Cosmetics.Item sel = Cosmetics.item(selectedId);
		int ay = wy + wh - 8 - 34;
		if (sel != null && sel.category.equals(category)) {
			boolean online = Cosmetics.serverSupported();
			boolean owns = Cosmetics.owns(sel.id);
			boolean equipped = sel.id.equals(Cosmetics.equippedId(sel.category));
			boolean trying = sel.id.equals(Cosmetics.previewId(sel.category));
			int bx = gx + gw;
			Button main;
			if (owns) {
				main = button(equipped ? "Unequip" : "Equip", bx - 84, ay + 9, 84, 18, () -> {
					Cosmetics.equip(sel.category, equipped ? null : sel.id);
					Cosmetics.setPreview(sel.category, null);
				});
				main.style(equipped ? Style.SECONDARY : Style.PRIMARY).icon(equipped ? "close" : "check");
				main.enabled = online;
				main.tip = online ? (equipped ? "Take it off (everyone stops seeing it)" : "Wear it: everyone on PlebMc sees it")
						: "Join PlebMc to change what you wear";
			} else if (!sel.forSale) {
				main = button("Exclusive", bx - 84, ay + 9, 84, 18, null);
				main.icon("lock").enabled = false;
				main.tip = "Not sold in the shop: it's given out by staff or events";
			} else {
				long need = sel.price - Math.max(0, Cosmetics.coins());
				main = button("Buy " + Ui.num(sel.price), bx - 84, ay + 9, 84, 18, () -> Cosmetics.buy(sel.id));
				main.style(Style.PRIMARY).icon("coin");
				main.enabled = online && need <= 0;
				main.tip = !online ? "Join PlebMc to buy with " + Cosmetics.currency()
						: need > 0 ? "You need " + Ui.num(need) + " more " + Cosmetics.currency() + ". Do quests to earn them."
								: "Buy it for " + Ui.num(sel.price) + " " + Cosmetics.currency();
			}
			Button tryOn = button(trying ? "Take off" : "Try on", bx - 84 - 6 - 70, ay + 9, 70, 18,
					() -> Cosmetics.setPreview(sel.category, trying ? null : sel.id));
			tryOn.icon("eye").tip = "Only you see this, until you leave the shop";
			tryOn.visible = !equipped;
		}
		Button reset = button("", pvx + pvw - 18, pvy + 4, 14, 14, () -> yawTarget = FRONT);
		reset.icon("reset").style(Style.GHOST).tip("Face forward");
	}

	@Override
	protected void tick() {
		if (Cosmetics.version() != seenVersion) {
			rebuild();
		}
	}

	/** One item: its look, name, and price or owned/equipped state. Clicking selects and tries it on. */
	private final class ItemCard extends W {
		final Cosmetics.Item it;

		ItemCard(Cosmetics.Item it) {
			this.it = it;
			this.tip = (it.description.isEmpty() ? it.name : it.description) + ". Click to try it on.";
		}

		@Override
		void draw(int mx, int my) {
			int acc = Ui.accent();
			boolean sel = it.id.equals(selectedId);
			boolean owns = Cosmetics.owns(it.id);
			boolean equipped = it.id.equals(Cosmetics.equippedId(it.category));
			boolean trying = it.id.equals(Cosmetics.previewId(it.category));
			int border = sel ? acc : equipped ? Ui.alpha(Ui.GOOD, 0xB0) : Ui.mix(Ui.BORDER, Ui.alpha(acc, 0xB0), hover);
			Ui.card(x, y, w, h, Ui.mix(Ui.RAISED, Ui.HOVER, Math.max(hover, sel ? 0.6f : 0f)), border);
			// swatch with the item's look
			int tint = it.color >= 0 ? 0xFF000000 | it.color : acc;
			Ui.gradient(x + 1, y + 1, w - 2, 22, Ui.alpha(tint, 0x50), Ui.alpha(tint, 0x10));
			if (Cosmetics.CLOAK.equals(it.category)) {
				// the cloak's own art (HD once it has downloaded)
				Ui.rect(x + w / 2 - 8, y + 2, 16, 20, 0xFF000000);
				CloakLayer.thumb(it, x + w / 2 - 7, y + 3, 14, 18);
			} else {
				// the hat itself, turning while you point at it
				float spin = hover > 0.05f || sel ? (System.currentTimeMillis() % 7200L) / 20f : 0f;
				CosmeticLayer.thumb(it, x + w / 2f, y + 11f, 1.8f, 35f + spin, System.currentTimeMillis() / 50f);
			}
			Cosmetics.Collection col = Cosmetics.collection(it.collection);
			if (col != null && Cosmetics.collections().indexOf(col) > 0) {
				int cc = col.color == 0 ? acc : col.color;
				Ui.pill(col.name.toUpperCase().substring(0, Math.min(7, col.name.length())), x + w - Ui.pillWidth(
						col.name.toUpperCase().substring(0, Math.min(7, col.name.length()))) - 2, y + 2, Ui.ON_ACCENT, cc);
			}
			if (trying) {
				Ui.icon("eye", x + 4, y + 4, 1, acc);
			}
			Ui.text(Ui.fit(it.name, w - 8), x + 4, y + 25, Ui.TEXT);
			int sy = y + h - 13;
			if (equipped) {
				Ui.icon("check", x + 4, sy + 1, 1, Ui.GOOD);
				Ui.text("Equipped", x + 15, sy + 2, Ui.GOOD);
			} else if (owns) {
				Ui.text("Owned", x + 4, sy + 2, acc);
			} else if (!it.forSale) {
				Ui.icon("lock", x + 4, sy + 1, 1, Ui.FAINT);
				Ui.text("Exclusive", x + 15, sy + 2, Ui.FAINT);
			} else {
				boolean afford = Cosmetics.coins() >= it.price;
				Ui.icon("coin", x + 4, sy + 1, 1, Ui.GOLD);
				Ui.text(Ui.num(it.price), x + 15, sy + 2, afford || !Cosmetics.serverSupported() ? Ui.GOLD : Ui.mix(Ui.GOLD, Ui.FAINT, 0.6f));
			}
		}

		@Override
		void click(int mx, int my, int button) {
			if (it.id.equals(selectedId)) {
				return;
			}
			selectedId = it.id;
			if (Cosmetics.CLOAK.equals(it.category)) {
				yawTarget = FRONT + 180f;
			}
			// try it on right away (not if it's what you already wear)
			if (!it.id.equals(Cosmetics.equippedId(it.category))) {
				Cosmetics.setPreview(it.category, it.id);
			} else {
				Cosmetics.setPreview(it.category, null);
			}
			rebuild();
		}
	}

	/** What the offline preview wears: the item being tried on, else the equipped one, else the last worn. */
	private static Cosmetics.Item offlineWorn(String category) {
		String id = Cosmetics.previewId(category);
		if (id == null) {
			id = Cosmetics.equippedId(category);
		}
		Cosmetics.Item it = id == null ? null : Cosmetics.item(id);
		return it != null ? it : Cosmetics.lastWorn(category);
	}

	// ==================== drawing ====================

	@Override
	protected void drawContent(int mx, int my, float pt) {
		Ui.window(wx, wy, ww, wh);
		header(wx, wy, ww, "shop", "Shop", Ui.fit("Cosmetics everyone on PlebMc can see", Math.max(0, tabsLeft - wx - 40)), true);

		// preview
		Ui.card(pvx, pvy, pvw, pvh, 0xFF0B0F13, Ui.BORDER_SOFT);
		Ui.gradient(pvx + 1, pvy + 1, pvw - 2, pvh - 2, Ui.alpha(Ui.accent(), 0x22), 0x00000000);
		Ui.text("Preview", pvx + 6, pvy + 6, Ui.MUTED);
		yaw += (yawTarget - yaw) * (ClientPrefs().animations ? 0.25f : 1f);
		if (this.mc != null && this.mc.thePlayer != null) {
			int scale = Math.max(20, Math.min(60, (pvh - 52) * 10 / 21));
			drawModel(pvx + pvw / 2, pvy + pvh - 24, scale, yaw, this.mc.thePlayer);
		} else {
			// not in a world: your own skin, wearing what you're trying on
			int scale = Math.max(20, Math.min(60, (pvh - 52) * 10 / 21));
			TitleMenu.drawSkinModel(pvx + pvw / 2f, pvy + pvh - 24, scale, yaw, offlineWorn(Cosmetics.CLOAK), offlineWorn(Cosmetics.HAT));
		}
		Ui.icon("drag", pvx + 6, pvy + pvh - 14, 1, Ui.FAINT);
		Ui.text("Drag to spin", pvx + 18, pvy + pvh - 13, Ui.FAINT);
		tooltipArea(pvx, pvy + 20, pvw, pvh - 40, "Drag to spin");

		// page counter
		List<Cosmetics.Item> its = items();
		int per = cols * rows;
		int pages = Math.max(1, (its.size() + per - 1) / per);
		if (pages > 1) {
			String p = (page + 1) + " / " + pages;
			Ui.centered(p, wx + ww - 10 - 16 - 25 + 8, wy + 49, Ui.MUTED);
		}

		if (its.isEmpty()) {
			Cosmetics.Collection col = Cosmetics.collection(collection);
			boolean partner = col != null && Cosmetics.collections().indexOf(col) > 0;
			// the mascot, bobbing a little, keeps the empty page company
			float bob = (float) Math.sin(System.currentTimeMillis() / 420.0) * 1.2f;
			Ui.mascot(gx + gw / 2f - 14, gy + 16 + bob, 28, 0.95f);
			Ui.centered("No " + (partner ? "partner " : "") + categoryName(category).toLowerCase() + " yet",
					gx + gw / 2, gy + 52, Ui.MUTED);
			if (partner) {
				Ui.centered("Cosmetics made with our partners show up here", gx + gw / 2, gy + 64, Ui.FAINT);
			}
		}

		// details of the selected item
		Cosmetics.Item sel = Cosmetics.item(selectedId);
		int ay = wy + wh - 8 - 34;
		Ui.card(gx, ay, gw, 34, Ui.RAISED, Ui.BORDER_SOFT);
		String msg = Cosmetics.message();
		if (msg != null) {
			Ui.icon(Cosmetics.messageOk() ? "check" : "close", gx + 6, ay + 13, 1, Cosmetics.messageOk() ? Ui.GOOD : Ui.BAD);
			Ui.text(Ui.fit(msg, gw - 180), gx + 18, ay + 13, Cosmetics.messageOk() ? Ui.GOOD : Ui.BAD);
		} else if (sel != null && sel.category.equals(category)) {
			Ui.text(Ui.fit(sel.name, gw - 180), gx + 6, ay + 7, Ui.TEXT);
			String d = sel.description.isEmpty() ? categoryName(sel.category) : sel.description;
			if (sel.creator != null) {
				d = "by " + sel.creator + "  \u00b7  " + d;
			}
			Ui.text(Ui.fit(d, gw - 180), gx + 6, ay + 19, Ui.MUTED);
		} else {
			Ui.text("Pick something to try it on", gx + 6, ay + 7, Ui.TEXT);
			Ui.text(Ui.fit(Cosmetics.serverSupported() ? "Earn " + Cosmetics.currency() + " from quests and playing"
					: "Join PlebMc to buy. Trying on works anywhere.", gw - 12), gx + 6, ay + 19, Ui.MUTED);
		}
	}

	private static net.plebmc.client.mods.ClientPrefs ClientPrefs() {
		return net.plebmc.client.mods.ClientPrefs.get();
	}

	/** The player model turned to {@code yaw} degrees (like the inventory's, but any angle). */
	static void drawModel(int x, int y, int scale, float yaw, EntityLivingBase ent) {
		GlStateManager.enableDepth();
		GlStateManager.color(1f, 1f, 1f, 1f);
		GlStateManager.enableColorMaterial();
		GlStateManager.pushMatrix();
		GlStateManager.translate((float) x, (float) y, 50.0F);
		GlStateManager.scale((float) (-scale), (float) scale, (float) scale);
		GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
		float f = ent.renderYawOffset, f1 = ent.rotationYaw, f2 = ent.rotationPitch, f3 = ent.prevRotationYawHead,
				f4 = ent.rotationYawHead, f5 = ent.prevRenderYawOffset;
		GlStateManager.rotate(135.0F, 0.0F, 1.0F, 0.0F);
		RenderHelper.enableStandardItemLighting();
		GlStateManager.rotate(-135.0F, 0.0F, 1.0F, 0.0F);
		GlStateManager.rotate(-8f, 1.0F, 0.0F, 0.0F);
		GlStateManager.rotate(yaw, 0.0F, 1.0F, 0.0F);
		ent.renderYawOffset = ent.prevRenderYawOffset = 0f;
		ent.rotationYaw = 0f;
		ent.rotationPitch = 0f;
		ent.rotationYawHead = ent.prevRotationYawHead = 0f;
		RenderManager rm = Minecraft.getMinecraft().getRenderManager();
		rm.setPlayerViewY(180.0F);
		rm.setRenderShadow(false);
		rm.renderEntityWithPosYaw(ent, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F);
		rm.setRenderShadow(true);
		ent.renderYawOffset = f;
		ent.prevRenderYawOffset = f5;
		ent.rotationYaw = f1;
		ent.rotationPitch = f2;
		ent.prevRotationYawHead = f3;
		ent.rotationYawHead = f4;
		GlStateManager.popMatrix();
		RenderHelper.disableStandardItemLighting();
		GlStateManager.disableRescaleNormal();
		GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
		GlStateManager.disableTexture2D();
		GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
		GlStateManager.clear(256);
		GlStateManager.disableDepth();
	}

	// ==================== input ====================

	@Override
	protected void clicked(int mx, int my, int button) {
		if (mx >= pvx && mx < pvx + pvw && my >= pvy && my < pvy + pvh) {
			spinning = true;
			spinLastX = mx;
		}
	}

	@Override
	protected void mouseClickMove(int mx, int my, int button, long held) {
		if (spinning) {
			yawTarget += (mx - spinLastX) * 2.5f;
			yaw = yawTarget;
			spinLastX = mx;
		}
	}

	@Override
	protected void mouseReleased(int mx, int my, int button) {
		spinning = false;
		super.mouseReleased(mx, my, button);
	}

	@Override
	protected void key(char c, int key) {
		if (key == 203) { // left
			yawTarget -= 45f;
		} else if (key == 205) {
			yawTarget += 45f;
		}
	}

	@Override
	protected void back() {
		Cosmetics.clearPreview();
		super.back();
	}
}
