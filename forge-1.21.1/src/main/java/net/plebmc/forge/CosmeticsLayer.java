package net.plebmc.forge;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.plebmc.client.hud.CloakLayer;
import net.plebmc.client.hud.CosmeticLayer;
import net.plebmc.mc.R;
import net.plebmc.mc.client.renderer.entity.RenderPlayer;
import net.plebmc.mc.entity.Entity;

/**
 * The player render layer on 1.21.1: runs the shared cloak and hat/wings
 * layers with the drawing aimed at this player's pose.
 */
public final class CosmeticsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private final RenderPlayer shim;
	private final CosmeticLayer hats;
	private final CloakLayer cloak;

	public CosmeticsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
		this.shim = new RenderPlayer(parent.getModel());
		this.hats = new CosmeticLayer(shim);
		this.cloak = new CloakLayer(shim);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limbSwing,
			float limbSwingAmount, float partialTicks, float ageInTicks, float headYaw, float headPitch) {
		Entity w = Entity.wrap(p);
		if (!(w instanceof net.plebmc.mc.client.entity.AbstractClientPlayer acp)) {
			return;
		}
		PoseStack prevPose = R.worldPose;
		MultiBufferSource prevBuf = R.worldBuffers;
		int prevLight = R.worldLight;
		R.worldPose = pose;
		R.worldBuffers = buffers;
		R.worldLight = light;
		try {
			shim.getMainModel().pull();
			cloak.doRenderLayer(acp, limbSwing, limbSwingAmount, partialTicks, ageInTicks, headYaw, headPitch, 0.0625f);
			hats.doRenderLayer(acp, limbSwing, limbSwingAmount, partialTicks, ageInTicks, headYaw, headPitch, 0.0625f);
		} catch (Throwable t) {
			// a cosmetic must never break the frame
		} finally {
			R.worldPose = prevPose;
			R.worldBuffers = prevBuf;
			R.worldLight = prevLight;
		}
	}
}
