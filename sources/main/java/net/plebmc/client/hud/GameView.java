package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import net.lax1dude.eaglercraft.v1_8.Mouse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.plebmc.client.mods.ClickCounter;
import net.plebmc.client.mods.ClientView;

/** {@link ClientView} over the running 1.8 client. */
final class GameView implements ClientView {

	private final Minecraft mc;
	private final ClickCounter clicks;

	GameView(Minecraft mc, ClickCounter clicks) {
		this.mc = mc;
		this.clicks = clicks;
	}

	private EntityPlayerSP player() {
		return mc.thePlayer;
	}

	@Override
	public int fps() {
		return Minecraft.getDebugFPS();
	}

	@Override
	public double x() {
		EntityPlayerSP p = player();
		return p == null ? 0 : p.posX;
	}

	@Override
	public double y() {
		EntityPlayerSP p = player();
		return p == null ? 0 : p.posY;
	}

	@Override
	public double z() {
		EntityPlayerSP p = player();
		return p == null ? 0 : p.posZ;
	}

	@Override
	public float yaw() {
		EntityPlayerSP p = player();
		return p == null ? 0 : p.rotationYaw;
	}

	@Override
	public int pingMs() {
		EntityPlayerSP p = player();
		NetHandlerPlayClient nh = mc.getNetHandler();
		if (p == null || nh == null || mc.isSingleplayer()) {
			return -1;
		}
		NetworkPlayerInfo info = nh.getPlayerInfo(p.getUniqueID());
		return info == null ? -1 : info.getResponseTime();
	}

	@Override
	@SuppressWarnings("deprecation")
	public String localTime() {
		Date d = new Date();
		int h = d.getHours();
		int m = d.getMinutes();
		return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
	}

	@Override
	public boolean keyDown(String key) {
		if (mc.currentScreen != null) {
			return false;
		}
		switch (key) {
		case "forward":
			return mc.gameSettings.keyBindForward.isKeyDown();
		case "left":
			return mc.gameSettings.keyBindLeft.isKeyDown();
		case "back":
			return mc.gameSettings.keyBindBack.isKeyDown();
		case "right":
			return mc.gameSettings.keyBindRight.isKeyDown();
		case "jump":
			return mc.gameSettings.keyBindJump.isKeyDown();
		default:
			return false;
		}
	}

	@Override
	public boolean mouseDown(int button) {
		return mc.currentScreen == null && Mouse.isButtonDown(button == 0 ? 0 : 1);
	}

	@Override
	public int cps(int button) {
		return clicks.cps(button, System.currentTimeMillis());
	}

	@Override
	public List<String> armor() {
		EntityPlayerSP p = player();
		if (p == null) {
			return Collections.emptyList();
		}
		List<String> out = new ArrayList<>(4);
		for (int slot = 3; slot >= 0; slot--) {
			ItemStack s = p.inventory.armorItemInSlot(slot);
			if (s == null) {
				out.add("");
			} else if (s.isItemStackDamageable()) {
				out.add(s.getDisplayName() + " " + (s.getMaxDamage() - s.getItemDamage()) + "/" + s.getMaxDamage());
			} else {
				out.add(s.getDisplayName());
			}
		}
		return out;
	}

	@Override
	public List<String> effects() {
		EntityPlayerSP p = player();
		if (p == null) {
			return Collections.emptyList();
		}
		Collection<PotionEffect> active = p.getActivePotionEffects();
		List<String> out = new ArrayList<>(active.size());
		for (PotionEffect e : active) {
			Potion potion = Potion.potionTypes[e.getPotionID()];
			if (potion == null) {
				continue;
			}
			String name = I18n.format(potion.getName());
			int amp = e.getAmplifier();
			if (amp >= 1 && amp <= 3) {
				name += " " + I18n.format("enchantment.level." + (amp + 1));
			} else if (amp > 3) {
				name += " " + (amp + 1);
			}
			out.add(name + " " + Potion.getDurationString(e));
		}
		return out;
	}
}
