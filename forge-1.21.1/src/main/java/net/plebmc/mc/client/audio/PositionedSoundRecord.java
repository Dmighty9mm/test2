package net.plebmc.mc.client.audio;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.plebmc.mc.util.ResourceLocation;

/** 1.8's PositionedSoundRecord: 1.8 sound names mapped to 1.21.1 sound events. */
public class PositionedSoundRecord {
	final SimpleSoundInstance real;

	private PositionedSoundRecord(SimpleSoundInstance s) {
		real = s;
	}

	static SoundEvent event(String name) {
		switch (name) {
		case "gui.button.press":
			return SoundEvents.UI_BUTTON_CLICK.value();
		case "random.levelup":
			return SoundEvents.PLAYER_LEVELUP;
		case "random.orb":
			return SoundEvents.EXPERIENCE_ORB_PICKUP;
		case "random.pop":
			return SoundEvents.ITEM_PICKUP;
		case "note.pling":
			return SoundEvents.NOTE_BLOCK_PLING.value();
		case "random.click":
			return SoundEvents.UI_BUTTON_CLICK.value();
		default:
			try {
				return SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.parse(name.replace("minecraft:", "")));
			} catch (Throwable t) {
				return SoundEvents.UI_BUTTON_CLICK.value();
			}
		}
	}

	public static PositionedSoundRecord create(ResourceLocation rl, float pitch) {
		return new PositionedSoundRecord(SimpleSoundInstance.forUI(event(rl.getResourcePath()), pitch));
	}

	public static PositionedSoundRecord create(ResourceLocation rl) {
		return create(rl, 1f);
	}

	public static void play(String name, float vol, float pitch) {
		net.minecraft.client.Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event(name), pitch, vol));
	}
}
