
# Eagler Context Redacted Diff
# Copyright (c) 2024 lax1dude. All rights reserved.

# Version: 1.0
# Author: lax1dude

> INSERT  2 : 8  @  2

+ import net.lax1dude.eaglercraft.v1_8.Mouse;
+ import net.lax1dude.eaglercraft.v1_8.internal.EnumCursorType;
+ import net.lax1dude.eaglercraft.v1_8.log4j.LogManager;
+ import net.lax1dude.eaglercraft.v1_8.log4j.Logger;
+ import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
+ import net.lax1dude.eaglercraft.v1_8.opengl.WorldRenderer;

> DELETE  1  @  1 : 4

> DELETE  1  @  1 : 2

> DELETE  2  @  2 : 3

> INSERT  2 : 5  @  2

+ 
+ 	private static final Logger excLogger = LogManager.getLogger("GuiSlotRenderer");
+ 

> CHANGE  64 : 65  @  64 : 65

~ 		return (this.getSize() + 1) * this.slotHeight + this.headerPadding;

> INSERT  82 : 84  @  82

+ 			boolean plebList = net.plebmc.client.hud.PlebTheme.slotBackground(this.left, this.top, this.right,
+ 					this.bottom);

> CHANGE  3 : 23  @  3 : 21

~ 			if (!plebList) {
~ 				worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
~ 				worldrenderer.pos((double) this.left, (double) this.bottom, 0.0D)
~ 						.tex((double) ((float) this.left / f),
~ 								(double) ((float) (this.bottom + (int) this.amountScrolled) / f))
~ 						.color(32, 32, 32, 255).endVertex();
~ 				worldrenderer.pos((double) this.right, (double) this.bottom, 0.0D)
~ 						.tex((double) ((float) this.right / f),
~ 								(double) ((float) (this.bottom + (int) this.amountScrolled) / f))
~ 						.color(32, 32, 32, 255).endVertex();
~ 				worldrenderer.pos((double) this.right, (double) this.top, 0.0D)
~ 						.tex((double) ((float) this.right / f),
~ 								(double) ((float) (this.top + (int) this.amountScrolled) / f))
~ 						.color(32, 32, 32, 255).endVertex();
~ 				worldrenderer.pos((double) this.left, (double) this.top, 0.0D)
~ 						.tex((double) ((float) this.left / f),
~ 								(double) ((float) (this.top + (int) this.amountScrolled) / f))
~ 						.color(32, 32, 32, 255).endVertex();
~ 				tessellator.draw();
~ 			}

> CHANGE  6 : 7  @  6 : 7

~ 			this.drawSelectionBox(k, l, mouseXIn, mouseYIn, this.getSize());

> CHANGE  168 : 169  @  168 : 170

~ 	protected void drawSelectionBox(int mouseXIn, int mouseYIn, int parInt3, int parInt4, int i) {

> INSERT  3 : 6  @  3

+ 		int mx = Mouse.getX();
+ 		int my = Mouse.getY();
+ 

> INSERT  31 : 34  @  31

+ 				if (parInt3 >= i1 && parInt3 <= j1 && parInt4 >= k - 2 && parInt4 <= k + l + 1) {
+ 					Mouse.showCursor(EnumCursorType.HAND);
+ 				}

> CHANGE  2 : 9  @  2 : 3

~ 			try {
~ 				this.drawSlot(j, mouseXIn, k, l, parInt3, parInt4);
~ 			} catch (Throwable t) {
~ 				excLogger.error(
~ 						"Exception caught rendering a slot of a list on the screen! Game will continue running due to the suspicion that this could be an intentional crash attempt, and therefore it would be inconvenient if the user were to be locked out of this gui due to repeatedly triggering a full crash report");
~ 				excLogger.error(t);
~ 			}

> INSERT  9 : 12  @  9

+ 		if (net.plebmc.client.hud.PlebTheme.slotOverlay(this.left, this.width, startY, endY, this.height)) {
+ 			return;
+ 		}

> EOF
