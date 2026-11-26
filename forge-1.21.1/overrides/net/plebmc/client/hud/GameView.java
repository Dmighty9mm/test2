package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import net.plebmc.mc.input.Mouse;
import net.plebmc.mc.client.Minecraft;
import net.plebmc.mc.client.entity.EntityPlayerSP;
import net.plebmc.mc.client.network.NetHandlerPlayClient;
import net.plebmc.mc.client.network.NetworkPlayerInfo;
import net.plebmc.mc.client.resources.I18n;
import net.plebmc.mc.item.ItemStack;
import net.plebmc.mc.potion.Potion;
import net.plebmc.mc.potion.PotionEffect;
import net.plebmc.client.mods.ClickCounter;
import net.plebmc.client.mods.ClientView;
import net.plebmc.client.mods.Gear;
import net.plebmc.client.mods.Mate;
import net.plebmc.client.mods.Storm;

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
		// the camera's yaw for this very frame (what you see), not the last tick's
		net.plebmc.mc.entity.Entity cam = mc.getRenderViewEntity();
		if (cam != null) {
			float pt = PlebClient.framePartialTicks;
			return cam.prevRotationYaw + (cam.rotationYaw - cam.prevRotationYaw) * pt;
		}
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

	/** The registry name without "minecraft:" ("diamond_helmet"). */
	static String itemId(ItemStack s) {
		try {
			return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.real.getItem()).getPath();
		} catch (Throwable t) {
			return "";
		}
	}

	static Gear gear(ItemStack s) {
		if (s == null || s.getItem() == null) {
			return null;
		}
		boolean dmg = s.isItemStackDamageable();
		return new Gear(s, itemId(s), s.getDisplayName(), s.stackSize, dmg ? s.getMaxDamage() - s.getItemDamage() : 0,
				dmg ? s.getMaxDamage() : 0);
	}

	@Override
	public List<Gear> armor() {
		EntityPlayerSP p = player();
		if (p == null) {
			return Collections.emptyList();
		}
		List<Gear> out = new ArrayList<>(4);
		for (int slot = 3; slot >= 0; slot--) {
			out.add(gear(p.inventory.armorItemInSlot(slot)));
		}
		return out;
	}

	@Override
	public Gear held() {
		EntityPlayerSP p = player();
		return p == null ? null : gear(p.getHeldItem());
	}

	@Override
	public float health() {
		EntityPlayerSP p = player();
		return p == null || mc.playerController == null || !mc.playerController.gameIsSurvivalOrAdventure() ? -1f
				: p.getHealth();
	}

	@Override
	public float maxHealth() {
		EntityPlayerSP p = player();
		return p == null ? 20f : p.getMaxHealth();
	}

	@Override
	public float absorption() {
		EntityPlayerSP p = player();
		return p == null ? 0f : p.getAbsorptionAmount();
	}

	private long suppliesAt = -1;
	private final java.util.Map<String, Integer> supplies = new java.util.HashMap<>();

	@Override
	public int supply(String group) {
		EntityPlayerSP p = player();
		if (p == null) {
			return 0;
		}
		long now = System.currentTimeMillis();
		if (now - suppliesAt > 250L || now < suppliesAt) {
			// count once a few times a second, not once per group per frame
			suppliesAt = now;
			supplies.clear();
			var inv = p.lp.getInventory().items;
			for (int i = 0; i < inv.size(); i++) {
				ItemStack s = ItemStack.of(inv.get(i));
				if (s == null) {
					continue;
				}
				String g = group(s);
				if (g != null) {
					Integer n = supplies.get(g);
					supplies.put(g, (n == null ? 0 : n) + s.stackSize);
				}
			}
		}
		Integer n = supplies.get(group);
		return n == null ? 0 : n;
	}

	private static String group(ItemStack s) {
		net.minecraft.world.item.Item it = s.real.getItem();
		if (it == net.minecraft.world.item.Items.ARROW || it == net.minecraft.world.item.Items.SPECTRAL_ARROW
				|| it == net.minecraft.world.item.Items.TIPPED_ARROW) {
			return "arrows";
		}
		if (it == net.minecraft.world.item.Items.GOLDEN_APPLE || it == net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE) {
			return "gapples";
		}
		if (it == net.minecraft.world.item.Items.ENDER_PEARL) {
			return "pearls";
		}
		if (it == net.minecraft.world.item.Items.POTION || it == net.minecraft.world.item.Items.SPLASH_POTION
				|| it == net.minecraft.world.item.Items.LINGERING_POTION) {
			return "potions";
		}
		if (it instanceof net.minecraft.world.item.BlockItem) {
			net.minecraft.world.level.block.Block b = ((net.minecraft.world.item.BlockItem) it).getBlock();
			if (b == net.minecraft.world.level.block.Blocks.TNT) {
				return "tnt";
			}
			// solid blocks you can build with (not torches, flowers, rails...)
			var st = b.defaultBlockState();
			return st.isSolidRender(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO) ? "blocks" : null;
		}
		return null;
	}

	@Override
	public Storm storm() {
		EntityPlayerSP p = player();
		if (p == null || mc.theWorld == null) {
			return null;
		}
		net.plebmc.mc.world.border.WorldBorder wb = mc.theWorld.getWorldBorder();
		if (wb == null || wb.getDiameter() >= Storm.NO_STORM) {
			return null;
		}
		return new Storm(wb.getCenterX(), wb.getCenterZ(), wb.getDiameter(), wb.getTargetSize(),
				wb.getStatus() == net.plebmc.mc.world.border.EnumBorderStatus.STATIONARY ? 0L : wb.getTimeUntilTarget(),
				wb.real.getDistanceToBorder(p.posX, p.posZ));
	}

	@Override
	public List<Mate> squad() {
		EntityPlayerSP p = player();
		net.plebmc.client.apollo.module.TeamModule tm = net.plebmc.client.apollo.Apollo
				.module(net.plebmc.client.apollo.module.TeamModule.class);
		if (p == null || mc.theWorld == null || tm == null || !tm.isEnabled() || tm.size() == 0) {
			return Collections.emptyList();
		}
		String self = PlebEntities.uuid(p);
		List<Mate> out = new ArrayList<>();
		java.util.Map<String, net.plebmc.mc.entity.player.EntityPlayer> loaded = null;
		for (net.plebmc.client.apollo.module.TeamModule.Member m : tm.members()) {
			if (m.uuid.equals(self)) {
				continue;
			}
			if (loaded == null) {
				loaded = new java.util.HashMap<>();
				for (net.plebmc.mc.entity.player.EntityPlayer e : mc.theWorld.playerEntities) {
					loaded.put(PlebEntities.uuid(e), e);
				}
			}
			net.plebmc.mc.entity.player.EntityPlayer e = loaded.get(m.uuid);
			double x, z, y;
			float hp = -1f, max = 20f;
			if (e != null && !e.isDead) {
				x = e.posX;
				y = e.posY;
				z = e.posZ;
				hp = e.getHealth();
				max = e.getMaxHealth();
			} else if (m.hasPos) {
				x = m.pos.x;
				y = m.pos.y;
				z = m.pos.z;
			} else {
				out.add(new Mate(net.plebmc.client.apollo.ApolloText.plain(m.name), m.argb, -1, -1f, 20f, Float.NaN));
				continue;
			}
			double dx = x - p.posX, dy = y - p.posY, dz = z - p.posZ;
			int dist = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
			out.add(new Mate(net.plebmc.client.apollo.ApolloText.plain(m.name), m.argb, dist, hp, max,
					(float) Math.toDegrees(Math.atan2(-dx, dz))));
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

	@Override
	public long worldTime() {
		return mc.theWorld == null ? -1 : mc.theWorld.getWorldTime();
	}

	@Override
	public String biome() {
		EntityPlayerSP p = player();
		if (p == null || mc.theWorld == null) {
			return null;
		}
		try {
			var key = mc.theWorld.level.getBiome(p.lp.blockPosition()).unwrapKey();
			if (key.isEmpty()) {
				return null;
			}
			String k = "biome." + key.get().location().getNamespace() + "." + key.get().location().getPath();
			return net.minecraft.client.resources.language.I18n.get(k);
		} catch (Throwable t) {
			return null;
		}
	}

	@Override
	public String server() {
		if (mc.theWorld == null) {
			return null;
		}
		if (mc.isSingleplayer()) {
			return "Singleplayer";
		}
		net.plebmc.mc.client.multiplayer.ServerData d = mc.getCurrentServerData();
		return d == null ? "Multiplayer" : d.serverIP;
	}

	@Override
	public int light() {
		EntityPlayerSP p = player();
		if (p == null || mc.theWorld == null) {
			return -1;
		}
		try {
			return mc.theWorld.level.getMaxLocalRawBrightness(p.lp.blockPosition());
		} catch (Throwable t) {
			return -1;
		}
	}
}
