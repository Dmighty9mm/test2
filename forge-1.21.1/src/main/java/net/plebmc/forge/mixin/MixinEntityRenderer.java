package net.plebmc.forge.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.plebmc.client.hud.PlebEntities;
import net.plebmc.client.hud.PlebPerf;
import net.plebmc.mc.R;

/**
 * Apollo nametags (server lines, server render distance), the Pleb head
 * next to the names of players on PlebClient, and the entity draw distance
 * from the Video Settings screen.
 */
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer<T extends Entity> {

	@Shadow
	@Final
	protected EntityRenderDispatcher entityRenderDispatcher;

	@Shadow
	protected abstract void renderNameTag(T entity, Component name, PoseStack pose, MultiBufferSource buf, int light, float pt);

	@Unique
	private static boolean plebmc$inTag;

	@Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
	private void plebmc$nametag(T entity, Component name, PoseStack pose, MultiBufferSource buf, int light, float pt,
			CallbackInfo ci) {
		if (plebmc$inTag) {
			return;
		}
		try {
			net.plebmc.mc.entity.Entity w = net.plebmc.mc.entity.Entity.wrap(entity);
			double range = PlebEntities.nametagRange(entity.isDiscrete() ? 32.0 : 64.0);
			if (this.entityRenderDispatcher.distanceToSqr(entity) >= range * range) {
				ci.cancel();
				return;
			}
			List<String> tag = PlebEntities.nametag(w);
			if (tag == null) {
				return;
			}
			plebmc$inTag = true;
			try {
				for (int line = 0; line < tag.size(); line++) {
					pose.pushPose();
					pose.translate(0.0, 0.25 * (tag.size() - 1 - line), 0.0);
					this.renderNameTag(entity, Component.literal(tag.get(line)), pose, buf, light, pt);
					pose.popPose();
				}
			} finally {
				plebmc$inTag = false;
			}
			ci.cancel();
		} catch (Throwable t) {
			// the vanilla nametag
		}
	}

	/** The Pleb head left of the name (drawn in the label's own pixels). */
	@Inject(method = "renderNameTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I", ordinal = 0, shift = At.Shift.AFTER))
	private void plebmc$badge(T entity, Component name, PoseStack pose, MultiBufferSource buf, int light, float pt,
			CallbackInfo ci) {
		if (!(entity instanceof net.minecraft.world.entity.player.Player)) {
			return;
		}
		PoseStack prev = R.worldPose;
		R.worldPose = pose;
		try {
			String label = name.getString();
			int half = net.minecraft.client.Minecraft.getInstance().font.width(name) / 2;
			PlebEntities.nametagBadge(net.plebmc.mc.entity.Entity.wrap(entity), label, half, "deadmau5".equals(label) ? -10 : 0);
		} catch (Throwable t) {
			// just the name
		} finally {
			R.worldPose = prev;
		}
	}

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void plebmc$distance(T entity, Frustum f, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (PlebPerf.cullEntity(entity, x, y, z)) {
			cir.setReturnValue(false);
		}
	}
}
