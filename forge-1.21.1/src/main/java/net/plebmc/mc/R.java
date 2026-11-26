package net.plebmc.mc;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Where PlebClient's 1.8-style drawing goes this frame on 1.21.1: the
 * GuiGraphics of the screen or HUD being drawn, or a world PoseStack while
 * drawing in the world. Set by the hooks right before the shared code runs.
 */
public final class R {
	private R() {
	}

	public static GuiGraphics gg;
	public static PoseStack worldPose;
	public static MultiBufferSource worldBuffers;
	public static int worldLight = 0xF000F0;
	/** The texture last bound through the 1.8 TextureManager (what model parts are drawn with). */
	public static net.minecraft.resources.ResourceLocation tex;

	/** Where model parts go: the world's buffers while drawing in the world, else the screen's. */
	public static com.mojang.blaze3d.vertex.VertexConsumer modelBuffer() {
		var rt = net.minecraft.client.renderer.RenderType.entityTranslucent(
				tex != null ? tex : net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture());
		if (worldBuffers != null) {
			return worldBuffers.getBuffer(rt);
		}
		return gg != null ? gg.bufferSource().getBuffer(rt) : null;
	}

	/** Draws what the screen's buffers hold (model parts drawn in a GUI). */
	public static void endModels() {
		if (worldBuffers == null && gg != null) {
			gg.flush();
		}
	}
	private static final PoseStack SPARE = new PoseStack();

	public static PoseStack pose() {
		if (worldPose != null) {
			return worldPose;
		}
		return gg != null ? gg.pose() : SPARE;
	}

	/** Draws anything GuiGraphics has batched, so our own GL draws keep their order. */
	public static void flush() {
		if (gg != null && worldPose == null) {
			gg.flush();
		}
	}

	public static net.minecraft.client.Minecraft mc() {
		return net.minecraft.client.Minecraft.getInstance();
	}
}
