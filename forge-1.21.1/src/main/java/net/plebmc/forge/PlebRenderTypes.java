package net.plebmc.forge;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderType;

/** Render types PlebClient draws in the world with: outline lines of any width, see-through fills. */
public final class PlebRenderTypes extends RenderType {
	private PlebRenderTypes() {
		super("plebmc_dummy", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 256, false, false, () -> {
		}, () -> {
		});
	}

	private static final Map<Integer, RenderType> LINES = new HashMap<>();
	private static final Map<Integer, RenderType> LINES_THROUGH = new HashMap<>();

	/** Block outline lines, {@code width} pixels wide. */
	public static RenderType lines(float width) {
		int key = Math.round(width * 4f);
		return LINES.computeIfAbsent(key, k -> create("plebmc_lines_" + k, DefaultVertexFormat.POSITION_COLOR_NORMAL,
				VertexFormat.Mode.LINES, 1536, false, false,
				CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER)
						.setLineState(new LineStateShard(OptionalDouble.of(Math.max(0.5, k / 4.0))))
						.setLayeringState(VIEW_OFFSET_Z_LAYERING).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
						.setOutputState(ITEM_ENTITY_TARGET).setWriteMaskState(COLOR_DEPTH_WRITE).setCullState(NO_CULL)
						.createCompositeState(false)));
	}

	/** Lines drawn on top of everything (outlines seen through walls). */
	public static RenderType linesThrough(float width) {
		int key = Math.round(width * 4f);
		return LINES_THROUGH.computeIfAbsent(key, k -> create("plebmc_lines_xray_" + k, DefaultVertexFormat.POSITION_COLOR_NORMAL,
				VertexFormat.Mode.LINES, 1536, false, false,
				CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER)
						.setLineState(new LineStateShard(OptionalDouble.of(Math.max(0.5, k / 4.0))))
						.setLayeringState(VIEW_OFFSET_Z_LAYERING).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
						.setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL)
						.createCompositeState(false)));
	}

	/** See-through colored quads (the block outline fill). */
	public static final RenderType FILL = create("plebmc_fill", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536,
			false, true,
			CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setLayeringState(VIEW_OFFSET_Z_LAYERING)
					.setTransparencyState(TRANSLUCENT_TRANSPARENCY).setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL)
					.createCompositeState(false));
}
