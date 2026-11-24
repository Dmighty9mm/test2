
# Eagler Context Redacted Diff
# Copyright (c) 2024 lax1dude. All rights reserved.

# Version: 1.0
# Author: lax1dude

> INSERT  2 : 3  @  2

+ import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;

> DELETE  2  @  2 : 5

> CHANGE  36 : 38  @  36 : 37

~ 		return entityarmorstand.getAlwaysRenderNameTag()
~ 				&& net.plebmc.client.hud.PlebEntities.showArmorStandName(entityarmorstand);

> INSERT  1 : 8  @  1

+ 
+ 	protected void renderModel(EntityArmorStand entityarmorstand, float f, float f1, float f2, float f3, float f4,
+ 			float f5) {
+ 		if (!net.plebmc.client.hud.PlebEntities.isHologramStand(entityarmorstand)) {
+ 			super.renderModel(entityarmorstand, f, f1, f2, f3, f4, f5);
+ 		}
+ 	}

> EOF
