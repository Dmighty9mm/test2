
# Eagler Context Redacted Diff
# Copyright (c) 2024 lax1dude. All rights reserved.

# Version: 1.0
# Author: lax1dude

> INSERT  2 : 3  @  2

+ import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;

> DELETE  1  @  1 : 4

> INSERT  65 : 70  @  65

+ 			if (net.plebmc.client.hud.PlebTheme.enabled()) {
+ 				net.plebmc.client.hud.PlebTheme.sliderKnob(this,
+ 						this.xPosition + (int) (this.sliderPosition * (float) (this.width - 8)), this.isMouseDown);
+ 				return;
+ 			}

> EOF
