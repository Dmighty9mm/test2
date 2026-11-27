package net.plebmc.mc.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.plebmc.mc.R;

/** 1.8's ModelRenderer over a 1.21.1 ModelPart. */
public class ModelRenderer {
	public final ModelPart part;
	public float rotationPointX, rotationPointY, rotationPointZ;
	public float rotateAngleX, rotateAngleY, rotateAngleZ;
	public boolean showModel = true;
	public boolean isHidden;

	public ModelRenderer(ModelPart part) {
		this.part = part;
		pull();
	}

	/** Copies the part's pose into the 1.8 fields. */
	public void pull() {
		if (part != null) {
			rotationPointX = part.x;
			rotationPointY = part.y;
			rotationPointZ = part.z;
			rotateAngleX = part.xRot;
			rotateAngleY = part.yRot;
			rotateAngleZ = part.zRot;
			showModel = part.visible;
		}
	}

	/** Copies the 1.8 fields back into the part. */
	public void push() {
		if (part != null) {
			part.x = rotationPointX;
			part.y = rotationPointY;
			part.z = rotationPointZ;
			part.xRot = rotateAngleX;
			part.yRot = rotateAngleY;
			part.zRot = rotateAngleZ;
			part.visible = showModel;
		}
	}

	public void render(float scale) {
		if (part == null || !showModel || isHidden) {
			return;
		}
		push();
		var vc = R.modelBuffer();
		if (vc != null) {
			float[] c = net.plebmc.mc.client.renderer.GlStateManager.currentColor();
			int argb = Math.round(c[3] * 255) << 24 | Math.round(c[0] * 255) << 16 | Math.round(c[1] * 255) << 8 | Math.round(c[2] * 255);
			part.render(R.pose(), vc, R.worldLight, OverlayTexture.NO_OVERLAY, argb);
			R.endModels();
		}
	}

	/** Moves the matrix to this part's joint (1.8 postRender; the scale is always 1/16). */
	public void postRender(float scale) {
		if (part != null) {
			push();
			part.translateAndRotate(R.pose());
		}
	}
}
