
# Eagler Context Redacted Diff
# Copyright (c) 2024 lax1dude. All rights reserved.

# Version: 1.0
# Author: lax1dude

> INSERT  2 : 4  @  2

+ import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
+ import net.lax1dude.eaglercraft.v1_8.opengl.ext.deferred.DeferredStateManager;

> DELETE  2  @  2 : 5

> INSERT  28 : 37  @  28

+ 		boolean light = entitytntprimed.fuse / 5 % 2 == 0;
+ 		boolean deferred = DeferredStateManager.isInDeferredPass();
+ 		if (light && deferred) {
+ 			DeferredStateManager.setEmissionConstant(1.0f);
+ 			DeferredStateManager.disableMaterialTexture();
+ 			GlStateManager.enableShaderBlendAdd();
+ 			GlStateManager.setShaderBlendSrc(0.0f, 0.0f, 0.0f, 0.0f);
+ 			GlStateManager.setShaderBlendAdd(1.0f, 1.0f, 1.0f, 1.0f);
+ 		}

> CHANGE  2 : 3  @  2 : 3

~ 		if (light && !deferred) {

> INSERT  17 : 21  @  17

+ 		String plebTnt = net.plebmc.client.hud.PlebEntities.tntLabel(entitytntprimed);
+ 		if (plebTnt != null) {
+ 			this.renderLivingLabel(entitytntprimed, plebTnt, d0, d1 + 0.5D, d2, 64);
+ 		}

> INSERT  1 : 7  @  1

+ 		if (light && deferred) {
+ 			DeferredStateManager.setEmissionConstant(0.0f);
+ 			DeferredStateManager.enableMaterialTexture();
+ 			GlStateManager.disableShaderBlendAdd();
+ 			GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
+ 		}

> EOF
