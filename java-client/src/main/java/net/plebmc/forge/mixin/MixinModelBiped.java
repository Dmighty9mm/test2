package net.plebmc.forge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.plebmc.client.hud.SecretMods;

/** The Big Heads secret mod: heads (and the hat layer) drawn bigger. */
@Mixin(ModelBiped.class)
public abstract class MixinModelBiped {

	@Shadow
	public ModelRenderer bipedHead;
	@Shadow
	public ModelRenderer bipedHeadwear;

	private Entity plebmc$entity;

	@Inject(method = "render", at = @At("HEAD"))
	private void plebmc$see(Entity entity, float a, float b, float c, float d, float e, float scale, CallbackInfo ci) {
		this.plebmc$entity = entity;
	}

	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelRenderer;render(F)V"))
	private void plebmc$part(ModelRenderer part, float scale) {
		if ((part == this.bipedHead || part == this.bipedHeadwear) && !((ModelBiped) (Object) this).isChild) {
			SecretMods.renderHead(part, scale, this.plebmc$entity);
		} else {
			part.render(scale);
		}
	}
}
