package net.plebmc.mc.client.multiplayer;

import net.plebmc.mc.client.Minecraft;
import net.plebmc.mc.client.gui.GuiScreen;

/** 1.8's GuiConnecting: opening it starts 1.21.1's connect screen to that server. */
public class GuiConnecting extends GuiScreen.Vanilla {
	private final GuiScreen parent;
	private final ServerData data;

	public GuiConnecting(GuiScreen parent, Minecraft mc, ServerData data) {
		super(null);
		this.parent = parent;
		this.data = data;
	}

	@Override
	public net.minecraft.client.gui.screens.Screen toScreen() {
		final net.minecraft.client.gui.screens.Screen back = parent == null ? new net.minecraft.client.gui.screens.TitleScreen()
				: parent.toScreen();
		final net.minecraft.client.multiplayer.ServerData sd = new net.minecraft.client.multiplayer.ServerData(data.serverName,
				data.serverIP, net.minecraft.client.multiplayer.ServerData.Type.OTHER);
		return new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("")) {
			private boolean started;

			@Override
			protected void init() {
				if (!started) {
					started = true;
					net.minecraft.client.Minecraft m = net.minecraft.client.Minecraft.getInstance();
					m.execute(() -> net.minecraft.client.gui.screens.ConnectScreen.startConnecting(back, m,
							net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(sd.ip), sd, false, null));
				}
			}
		};
	}
}
