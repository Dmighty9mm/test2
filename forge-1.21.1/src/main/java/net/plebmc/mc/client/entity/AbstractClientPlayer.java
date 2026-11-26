package net.plebmc.mc.client.entity;

import net.plebmc.mc.entity.player.EntityPlayer;
import net.plebmc.mc.util.ResourceLocation;

/** 1.8's AbstractClientPlayer over 1.21.1's. */
public class AbstractClientPlayer extends EntityPlayer {
	public final net.minecraft.client.player.AbstractClientPlayer acp;

	public AbstractClientPlayer(net.minecraft.client.player.AbstractClientPlayer p) {
		super(p);
		this.acp = p;
	}

	public ResourceLocation getLocationSkin() {
		return new ResourceLocation(acp.getSkin().texture());
	}

	public ResourceLocation getLocationCape() {
		var c = acp.getSkin().capeTexture();
		return c == null ? null : new ResourceLocation(c);
	}

	public boolean hasSkin() {
		return true;
	}

	public String getSkinType() {
		return acp.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM ? "slim" : "default";
	}

	public net.plebmc.mc.client.network.NetworkPlayerInfo getPlayerInfo() {
		var c = net.minecraft.client.Minecraft.getInstance().getConnection();
		var i = c == null ? null : c.getPlayerInfo(acp.getUUID());
		return i == null ? null : new net.plebmc.mc.client.network.NetworkPlayerInfo(i);
	}
}
