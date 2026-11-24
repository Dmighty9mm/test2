package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.renderer.entity.layers.LayerSheepWool;
import net.minecraft.entity.passive.EntitySheep;
import net.plebmc.client.hud.PlebEntities;

/** Apollo entity module: rainbow sheep. */
@Mixin(LayerSheepWool.class)
public abstract class MixinLayerSheepWool {

	@Redirect(method = "doRenderLayer(Lnet/minecraft/entity/passive/EntitySheep;FFFFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/passive/EntitySheep;hasCustomName()Z"))
	private boolean plebmc$hasName(EntitySheep sheep) {
		return PlebEntities.rainbowSheep(sheep) || sheep.hasCustomName();
	}

	@Redirect(method = "doRenderLayer(Lnet/minecraft/entity/passive/EntitySheep;FFFFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/passive/EntitySheep;getCustomNameTag()Ljava/lang/String;"))
	private String plebmc$name(EntitySheep sheep) {
		return PlebEntities.rainbowSheep(sheep) ? "jeb_" : sheep.getCustomNameTag();
	}
}
