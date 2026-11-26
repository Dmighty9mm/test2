package net.plebmc.mc.client.entity;

/** 1.8's EntityPlayerSP over 1.21.1's LocalPlayer. */
public class EntityPlayerSP extends AbstractClientPlayer {
	public final net.minecraft.client.player.LocalPlayer lp;

	public EntityPlayerSP(net.minecraft.client.player.LocalPlayer p) {
		super(p);
		this.lp = p;
	}

	@Override
	protected void sync() {
		super.sync();
		inventory.currentItem = lp.getInventory().selected;
	}

	public void sendChatMessage(String s) {
		if (s.startsWith("/")) {
			lp.connection.sendCommand(s.substring(1));
		} else {
			lp.connection.sendChat(s);
		}
	}

	public void addChatMessage(net.plebmc.mc.util.IChatComponent c) {
		lp.displayClientMessage(c.toComponent(), false);
	}

	public void playSound(String name, float vol, float pitch) {
		net.plebmc.mc.client.audio.PositionedSoundRecord.play(name, vol, pitch);
	}

	public void closeScreen() {
		lp.closeContainer();
	}
}
