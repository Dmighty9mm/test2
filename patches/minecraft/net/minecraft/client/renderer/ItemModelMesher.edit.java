
# Eagler Context Redacted Diff
# Copyright (c) 2026 lax1dude. All rights reserved.

# Version: 1.0
# Author: lax1dude

> DELETE  2  @  2 : 3

> CHANGE  2 : 6  @  2 : 4

~ 
~ import com.google.common.collect.Maps;
~ 
~ import net.lax1dude.eaglercraft.v1_8.minecraft.EaglerTextureAtlasSprite;

> CHANGE  16 : 17  @  16 : 17

~ 	public EaglerTextureAtlasSprite getParticleIcon(Item item) {

> CHANGE  3 : 4  @  3 : 4

~ 	public EaglerTextureAtlasSprite getParticleIcon(Item item, int meta) {

> INSERT  4 : 8  @  4

+ 		IBakedModel plebModel = net.plebmc.client.itemrender.PlebItems.itemModel(stack, this.modelManager);
+ 		if (plebModel != null) {
+ 			return plebModel;
+ 		}

> CHANGE  45 : 46  @  45 : 46

~ 			this.simpleShapesCache.put((Integer) entry.getKey(),

> EOF
