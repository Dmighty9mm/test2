package net.plebmc.forge;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.plebmc.client.mods.ClientPrefs;

/** Entity render events: the Big Heads secret mod. */
public final class EntityHooks {

	private static final float BIG = 1.75f;

	@SubscribeEvent
	public void pre(RenderLivingEvent.Pre<?, ?> e) {
		if (e.getRenderer().getModel() instanceof HumanoidModel<?> m) {
			float k = ClientPrefs.get().bigHeadMod && !(e.getEntity() instanceof ArmorStand) && !m.young ? BIG : 1f;
			scale(m.head, k);
			scale(m.hat, k);
		}
	}

	@SubscribeEvent
	public void post(RenderLivingEvent.Post<?, ?> e) {
		if (e.getRenderer().getModel() instanceof HumanoidModel<?> m) {
			scale(m.head, 1f);
			scale(m.hat, 1f);
		}
	}

	private static void scale(ModelPart p, float k) {
		p.xScale = k;
		p.yScale = k;
		p.zScale = k;
	}
}
