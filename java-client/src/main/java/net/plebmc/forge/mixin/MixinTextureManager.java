package net.plebmc.forge.mixin;

import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import net.plebmc.client.hud.PlebTheme;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Inventories and other containers get the Pleb look (unless a texture pack has its own). */
@Mixin(TextureManager.class)
public abstract class MixinTextureManager {

	@ModifyVariable(method = "bindTexture", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private ResourceLocation plebmc$containerSkin(ResourceLocation resource) {
		return resource == null ? null : PlebTheme.containerSkin(resource);
	}
}
