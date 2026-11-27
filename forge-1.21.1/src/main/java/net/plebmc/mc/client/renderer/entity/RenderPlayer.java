package net.plebmc.mc.client.renderer.entity;

import net.plebmc.mc.client.model.ModelPlayer;
import net.plebmc.mc.util.ResourceLocation;

/** 1.8's RenderPlayer: the player model the layers draw on. */
public class RenderPlayer {
	private final ModelPlayer model;

	public RenderPlayer(net.minecraft.client.model.PlayerModel<?> m) {
		model = new ModelPlayer(m);
	}

	public ModelPlayer getMainModel() {
		return model;
	}

	public void bindTexture(ResourceLocation rl) {
		net.plebmc.mc.client.Minecraft.getMinecraft().getTextureManager().bindTexture(rl);
	}
}
