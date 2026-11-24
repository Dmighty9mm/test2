package net.plebmc.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.plebmc.client.mods.FpsLarp;

/**
 * Settings for the secret Fps Larp mod (type "fpslarp" in the mod menu). Only
 * the number on the FPS counters changes; the game runs exactly as before.
 */
public final class FpsLarpScreen extends GuiScreen {

	private static final int ON = 1, MULT = 2, BONUS = 3, TARGET = 4, CAP = 5, JITTER = 6, UPDATE = 7, FORMAT = 8,
			COLOR = 9, HUD = 10, DEBUG = 11, RESET = 12, DONE = 13;

	private final GuiScreen parent;

	public FpsLarpScreen(GuiScreen parent) {
		this.parent = parent;
	}

	private static FpsLarp larp() {
		return FpsLarp.INSTANCE;
	}

	@Override
	public void initGui() {
		this.buttonList.clear();
		FpsLarp l = larp();
		int half = this.width / 2;
		int y = 52;
		int w = 150;
		this.buttonList.add(new GuiButton(ON, half - 100, y, 200, 20, "Fps Larp: " + (l.enabled ? "§aON" : "§cOFF")));
		y += 26;
		boolean locked = FpsLarp.TARGETS[l.target] > 0;
		GuiButton mult = new GuiButton(MULT, half - w - 2, y, w, 20, "Multiplier: " + FpsLarp.fmt(FpsLarp.MULTIPLIERS[l.multiplier]) + "x");
		GuiButton bonus = new GuiButton(BONUS, half + 2, y, w, 20, "Extra: +" + FpsLarp.BONUSES[l.bonus]);
		mult.enabled = bonus.enabled = !locked;
		this.buttonList.add(mult);
		this.buttonList.add(bonus);
		y += 24;
		this.buttonList.add(new GuiButton(TARGET, half - w - 2, y, w, 20,
				"Lock at: " + (locked ? FpsLarp.TARGETS[l.target] : "Off")));
		this.buttonList.add(new GuiButton(CAP, half + 2, y, w, 20,
				"Max: " + (FpsLarp.CAPS[l.cap] > 0 ? FpsLarp.CAPS[l.cap] : "None")));
		y += 24;
		this.buttonList.add(new GuiButton(JITTER, half - w - 2, y, w, 20, "Jitter: " + FpsLarp.JITTER_NAMES[l.jitter]));
		this.buttonList.add(new GuiButton(UPDATE, half + 2, y, w, 20, "Updates: " + FpsLarp.UPDATE_NAMES[l.update]));
		y += 24;
		this.buttonList.add(new GuiButton(FORMAT, half - w - 2, y, w, 20,
				"Text: " + String.format(FpsLarp.FORMATS[l.format], 240)));
		this.buttonList.add(new GuiButton(COLOR, half + 2, y, w, 20, "Color: " + FpsLarp.COLOR_NAMES[l.color]));
		y += 24;
		this.buttonList.add(new GuiButton(HUD, half - w - 2, y, w, 20, "FPS mod: " + (l.inHud ? "Larped" : "Real")));
		this.buttonList.add(new GuiButton(DEBUG, half + 2, y, w, 20, "F3 / debug: " + (l.inDebug ? "Larped" : "Real")));
		y += 30;
		this.buttonList.add(new GuiButton(RESET, half - w - 2, y, w, 20, "Reset settings"));
		this.buttonList.add(new GuiButton(DONE, half + 2, y, w, 20, "Done"));
	}

	@Override
	protected void actionPerformed(GuiButton b) {
		FpsLarp l = larp();
		switch (b.id) {
		case ON:
			l.enabled = !l.enabled;
			break;
		case MULT:
			l.multiplier = (l.multiplier + 1) % FpsLarp.MULTIPLIERS.length;
			break;
		case BONUS:
			l.bonus = (l.bonus + 1) % FpsLarp.BONUSES.length;
			break;
		case TARGET:
			l.target = (l.target + 1) % FpsLarp.TARGETS.length;
			break;
		case CAP:
			l.cap = (l.cap + 1) % FpsLarp.CAPS.length;
			break;
		case JITTER:
			l.jitter = (l.jitter + 1) % FpsLarp.JITTERS.length;
			break;
		case UPDATE:
			l.update = (l.update + 1) % FpsLarp.UPDATES.length;
			break;
		case FORMAT:
			l.format = (l.format + 1) % FpsLarp.FORMATS.length;
			break;
		case COLOR:
			l.color = (l.color + 1) % FpsLarp.COLORS.length;
			break;
		case HUD:
			l.inHud = !l.inHud;
			break;
		case DEBUG:
			l.inDebug = !l.inDebug;
			break;
		case RESET:
			l.reset();
			break;
		case DONE:
			close();
			return;
		default:
			return;
		}
		l.changed();
		PlebClient.saveSettings();
		this.initGui();
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		this.drawDefaultBackground();
		this.drawCenteredString(this.fontRendererObj, "§d§lFps Larp", this.width / 2, 8, 0xFFFFFF);
		int real = Minecraft.getDebugFPS();
		long now = System.currentTimeMillis();
		FpsLarp l = larp();
		String preview = l.enabled ? l.hudText(real, now) : "§7(off)";
		this.drawCenteredString(this.fontRendererObj, "Real: §f" + real + "§7   Shown: " + preview + "§7   " + l.summary(),
				this.width / 2, 22, 0xAAAAAA);
		this.drawCenteredString(this.fontRendererObj, "§8Only the number changes. Your game runs the same.", this.width / 2,
				36, 0xFFFFFF);
		super.drawScreen(mouseX, mouseY, partialTicks);
	}

	@Override
	protected void keyTyped(char c, int key) {
		if (key == 1 || key == PlebClient.KEY_RSHIFT) {
			close();
		}
	}

	private void close() {
		PlebClient.saveSettings();
		this.mc.displayGuiScreen(parent);
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
