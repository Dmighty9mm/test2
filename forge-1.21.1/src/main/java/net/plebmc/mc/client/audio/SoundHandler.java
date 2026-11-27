package net.plebmc.mc.client.audio;

/** 1.8's SoundHandler. */
public class SoundHandler {
	public void playSound(PositionedSoundRecord s) {
		if (s != null && s.real != null) {
			net.minecraft.client.Minecraft.getInstance().getSoundManager().play(s.real);
		}
	}
}
