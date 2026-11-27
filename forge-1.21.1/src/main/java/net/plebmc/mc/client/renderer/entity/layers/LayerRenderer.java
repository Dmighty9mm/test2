package net.plebmc.mc.client.renderer.entity.layers;

/** 1.8's entity render layer. */
public interface LayerRenderer<T> {
	void doRenderLayer(T entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float headYaw,
			float headPitch, float scale);

	boolean shouldCombineTextures();
}
