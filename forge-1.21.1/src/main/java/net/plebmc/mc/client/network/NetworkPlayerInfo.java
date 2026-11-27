package net.plebmc.mc.client.network;

/** 1.8's NetworkPlayerInfo over 1.21.1's PlayerInfo. */
public class NetworkPlayerInfo {
	public final net.minecraft.client.multiplayer.PlayerInfo real;

	public NetworkPlayerInfo(net.minecraft.client.multiplayer.PlayerInfo p) {
		real = p;
	}

	public int getResponseTime() {
		return real.getLatency();
	}

	public com.mojang.authlib.GameProfile getGameProfile() {
		return real.getProfile();
	}

	public net.plebmc.mc.util.ResourceLocation getLocationSkin() {
		return new net.plebmc.mc.util.ResourceLocation(real.getSkin().texture());
	}

	public String getSkinType() {
		return real.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM ? "slim" : "default";
	}

	public net.plebmc.mc.util.IChatComponent getDisplayName() {
		var d = real.getTabListDisplayName();
		return d == null ? null : net.plebmc.mc.util.IChatComponent.wrap(d);
	}
}
