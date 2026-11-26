package net.plebmc.mc.client.model;

import net.minecraft.client.model.HumanoidModel;

/** 1.8's ModelBiped over a 1.21.1 HumanoidModel. */
public class ModelBiped {
	public final HumanoidModel<?> real;
	public ModelRenderer bipedHead, bipedHeadwear, bipedBody, bipedRightArm, bipedLeftArm, bipedRightLeg, bipedLeftLeg;
	public boolean isChild, isSneak;
	public float swingProgress;

	public ModelBiped(HumanoidModel<?> m) {
		real = m;
		bipedHead = new ModelRenderer(m.head);
		bipedHeadwear = new ModelRenderer(m.hat);
		bipedBody = new ModelRenderer(m.body);
		bipedRightArm = new ModelRenderer(m.rightArm);
		bipedLeftArm = new ModelRenderer(m.leftArm);
		bipedRightLeg = new ModelRenderer(m.rightLeg);
		bipedLeftLeg = new ModelRenderer(m.leftLeg);
	}

	/** Re-reads the parts' poses (after the game posed the model this frame). */
	public void pull() {
		for (ModelRenderer r : parts()) {
			r.pull();
		}
	}

	/** Puts the 1.8 showModel flags on the real parts. */
	public void pushVisibility() {
		for (ModelRenderer r : parts()) {
			if (r.part != null) {
				r.part.visible = r.showModel;
			}
		}
	}

	protected ModelRenderer[] parts() {
		return new ModelRenderer[] { bipedHead, bipedHeadwear, bipedBody, bipedRightArm, bipedLeftArm, bipedRightLeg, bipedLeftLeg };
	}

	/**
	 * Poses the model like 1.8's setRotationAngles for a player standing
	 * still (no entity: the title screen and shop previews).
	 */
	public void setRotationAngles(float limbSwing, float limbAmount, float age, float headYaw, float headPitch, float scale,
			Object entity) {
		float d = (float) Math.PI / 180f;
		real.head.resetPose();
		real.head.yRot = headYaw * d;
		real.head.xRot = headPitch * d;
		real.hat.copyFrom(real.head);
		real.body.resetPose();
		real.rightArm.resetPose();
		real.leftArm.resetPose();
		real.rightLeg.resetPose();
		real.leftLeg.resetPose();
		real.rightArm.xRot = (float) Math.cos(limbSwing * 0.6662f + Math.PI) * 2f * limbAmount * 0.5f;
		real.leftArm.xRot = (float) Math.cos(limbSwing * 0.6662f) * 2f * limbAmount * 0.5f;
		real.rightArm.zRot = (float) Math.cos(age * 0.09f) * 0.05f + 0.05f;
		real.leftArm.zRot = -((float) Math.cos(age * 0.09f) * 0.05f + 0.05f);
		real.rightArm.xRot += (float) Math.sin(age * 0.067f) * 0.05f;
		real.leftArm.xRot -= (float) Math.sin(age * 0.067f) * 0.05f;
		real.rightLeg.xRot = (float) Math.cos(limbSwing * 0.6662f) * 1.4f * limbAmount;
		real.leftLeg.xRot = (float) Math.cos(limbSwing * 0.6662f + Math.PI) * 1.4f * limbAmount;
		afterPose();
		pull();
	}

	protected void afterPose() {
	}
}
