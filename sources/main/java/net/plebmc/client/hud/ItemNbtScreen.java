package net.plebmc.client.hud;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.lax1dude.eaglercraft.v1_8.EagRuntime;
import net.minecraft.client.gui.GuiScreen;
import net.plebmc.client.items.ItemNbtLog;
import net.plebmc.client.mods.ClientPrefs;

/**
 * The item NBT log (Settings &gt; Developer): what the "Log item NBT" switch has
 * recorded, newest at the bottom, with Copy and Save .txt for sending it on.
 */
public final class ItemNbtScreen extends PlebScreen {

	private int scroll;
	private int wx, wy, ww, wh;
	private String status;
	private long statusAt;

	public ItemNbtScreen(GuiScreen parent) {
		super(parent);
	}

	@Override
	protected void build() {
		ww = this.width - 16;
		wh = this.height - 16;
		wx = 8;
		wy = 8;
		closeButton(wx + ww - 22, wy + 10);
		int by = wy + wh - 24;
		Button back = button("Back", wx + 8, by, 50, 16, this::back);
		back.icon("back").style(Style.GHOST);
		Button copy = button("Copy all", wx + 64, by, 64, 16, () -> {
			EagRuntime.setClipboard(ItemNbtWatch.export(this.mc));
			say("Copied " + ItemNbtWatch.LOG_DATA.size() + " entries");
		});
		copy.style(Style.PRIMARY);
		copy.tip = "Copy the whole log, then paste it to whoever asked for it";
		Button save = button("Save .txt", wx + 134, by, 64, 16, () -> {
			EagRuntime.downloadFileWithName("item-nbt-log.txt",
					ItemNbtWatch.export(this.mc).getBytes(StandardCharsets.UTF_8));
			say("Saved item-nbt-log.txt");
		});
		save.tip = "Download the log as a text file";
		Button clear = button("Clear", wx + 204, by, 50, 16, () -> {
			ItemNbtWatch.LOG_DATA.clear();
			ItemNbtWatch.reset();
			scroll = 0;
			say("Cleared. Items you hold now are logged again.");
		});
		clear.icon("reset");
		ClientPrefs pr = ClientPrefs.get();
		toggle("Logging", null, wx + ww - 108, by - 1, 100, 18, () -> pr.itemNbtLog, () -> {
			pr.itemNbtLog = !pr.itemNbtLog;
			PlebClient.saveSettings();
		}).tip = "Off by default. While on, item changes are recorded (and printed to the browser console)";
	}

	private void say(String s) {
		status = s;
		statusAt = System.currentTimeMillis();
	}

	@Override
	protected void scrolled(int mx, int my, int dir) {
		scroll = Math.max(0, Math.min(lines().size(), scroll + dir * 3));
	}

	/** Every entry as screen lines (summary, hints, NBT), oldest first. */
	private List<int[]> colors;
	private List<String> cached;
	private String cacheKey;

	/** Rebuilt only when the log or the window width changes (wrapping long NBT is not free). */
	private List<String> lines() {
		ItemNbtLog log = ItemNbtWatch.LOG_DATA;
		String key = log.total() + "/" + log.size() + "/" + ww;
		if (cached != null && key.equals(cacheKey)) {
			return cached;
		}
		cacheKey = key;
		List<String> out = new ArrayList<>();
		colors = new ArrayList<>();
		int width = ww - 24;
		for (ItemNbtLog.Entry e : ItemNbtWatch.LOG_DATA.entries()) {
			out.add(Ui.fit(e.summary(), width));
			colors.add(new int[] { Ui.TEXT });
			for (String l : e.lore) {
				out.add(Ui.fit("   lore: " + l, width));
				colors.add(new int[] { Ui.MUTED });
			}
			for (String h : e.hints) {
				out.add(Ui.fit("   " + h, width));
				colors.add(new int[] { Ui.GOOD });
			}
			for (String w : Ui.wrap("   nbt: " + (e.nbt == null ? "(none)" : e.nbt), width)) {
				if (out.size() > 4000) {
					break;
				}
				out.add(w);
				colors.add(new int[] { Ui.FAINT });
			}
		}
		cached = out;
		return out;
	}

	@Override
	protected void drawContent(int mx, int my, float pt) {
		Ui.window(wx, wy, ww, wh);
		ItemNbtLog log = ItemNbtWatch.LOG_DATA;
		String head = (ClientPrefs.get().itemNbtLog ? "Logging" : "Not logging") + "  ·  " + log.size() + " entries"
				+ (log.total() > log.size() ? " (" + (log.total() - log.size()) + " oldest dropped)" : "");
		header(wx, wy, ww, "debug", "Item NBT log", head, false);
		int top = wy + 44, bottom = wy + wh - 30;
		List<String> lines = lines();
		if (lines.isEmpty()) {
			String msg = ClientPrefs.get().itemNbtLog
					? "Nothing yet. Hold or pick up items (guns, ammo, medkits) and they show up here."
					: "Turn on Logging, then hold or pick up items (guns, ammo, medkits).";
			for (String l : Ui.wrap(msg, ww - 24)) {
				Ui.text(l, wx + 12, top, Ui.MUTED);
				top += 10;
			}
		} else {
			int rows = (bottom - top) / 9;
			int start = Math.max(0, lines.size() - rows - scroll);
			int y = top;
			for (int i = start; i < lines.size() && y <= bottom - 9; i++) {
				Ui.text(lines.get(i), wx + 12, y, colors.get(i)[0]);
				y += 9;
			}
		}
		if (status != null && System.currentTimeMillis() - statusAt < 3000) {
			Ui.text(status, wx + 262, wy + wh - 20, Ui.GOOD);
		}
	}
}
