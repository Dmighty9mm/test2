
# Eagler Context Redacted Diff
# Copyright (c) 2024 lax1dude. All rights reserved.

# Version: 1.0
# Author: lax1dude

> INSERT  2 : 4  @  2

+ import net.lax1dude.eaglercraft.v1_8.sp.lan.LANClientNetworkManager;
+ import net.lax1dude.eaglercraft.v1_8.sp.socket.ClientIntegratedServerNetworkManager;

> DELETE  3  @  3 : 4

> DELETE  51  @  51 : 52

> INSERT  22 : 23  @  22

+ 	private StatFileWriter statWriter;

> CHANGE  1 : 2  @  1 : 2

~ 	public EntityPlayerSP(Minecraft mcIn, World worldIn, NetHandlerPlayClient netHandler, StatFileWriter statWriter) {

> DELETE  2  @  2 : 3

> INSERT  2 : 3  @  2

+ 		this.statWriter = statWriter;

> INSERT  17 : 25  @  17

+ 	public void moveEntity(double x, double y, double z) {
+ 		if (net.plebmc.client.hud.PlebClient.borderBlocks(this.posX, this.posZ, this.posX + x, this.posZ + z)) {
+ 			x = 0.0D;
+ 			z = 0.0D;
+ 		}
+ 		super.moveEntity(x, y, z);
+ 	}
+ 

> CHANGE  99 : 106  @  99 : 100

~ 		if (((sendQueue.getNetworkManager() instanceof ClientIntegratedServerNetworkManager)
~ 				|| (sendQueue.getNetworkManager() instanceof LANClientNetworkManager))
~ 				&& message.startsWith("/eagskull")) {
~ 			this.mc.eagskullCommand.openFileChooser();
~ 		} else {
~ 			this.sendQueue.addToSendQueue(new C01PacketChatMessage(message));
~ 		}

> CHANGE  358 : 360  @  358 : 359

~ 				&& !this.isPotionActive(Potion.blindness)
~ 				&& (this.mc.gameSettings.keyBindSprint.isKeyDown() || net.plebmc.client.hud.PlebClient.sprintHeld())) {

> EOF
