package net.plebmc.mc.client.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.plebmc.mc.R;

/** 1.8's ModelPlayer over a 1.21.1 PlayerModel. */
public class ModelPlayer extends ModelBiped {
	public final PlayerModel<?> player;
	public ModelRenderer bipedBodyWear, bipedRightArmwear, bipedLeftArmwear, bipedRightLegwear, bipedLeftLegwear;

	public ModelPlayer(PlayerModel<?> m) {
		super(m);
		player = m;
		bipedBodyWear = new ModelRenderer(m.jacket);
		bipedRightArmwear = new ModelRenderer(m.rightSleeve);
		bipedLeftArmwear = new ModelRenderer(m.leftSleeve);
		bipedRightLegwear = new ModelRenderer(m.rightPants);
		bipedLeftLegwear = new ModelRenderer(m.leftPants);
	}

	/** A model of its own (previews), like 1.8's {@code new ModelPlayer(0, slim)}. */
	public ModelPlayer(float expand, boolean slim) {
		this(new PlayerModel<LivingEntity>(
				Minecraft.getInstance().getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim));
	}

	@Override
	protected ModelRenderer[] parts() {
		return new ModelRenderer[] { bipedHead, bipedHeadwear, bipedBody, bipedRightArm, bipedLeftArm, bipedRightLeg, bipedLeftLeg,
				bipedBodyWear, bipedRightArmwear, bipedLeftArmwear, bipedRightLegwear, bipedLeftLegwear };
	}

	@Override
	protected void afterPose() {
		player.jacket.copyFrom(player.body);
		player.rightSleeve.copyFrom(player.rightArm);
		player.leftSleeve.copyFrom(player.leftArm);
		player.rightPants.copyFrom(player.rightLeg);
		player.leftPants.copyFrom(player.leftLeg);
	}

	/** The cape, with the texture last bound. */
	public void renderCape(float scale) {
		var vc = R.modelBuffer();
		if (vc != null) {
			player.renderCloak(R.pose(), vc, R.worldLight, OverlayTexture.NO_OVERLAY);
			R.endModels();
		}
	}
}
