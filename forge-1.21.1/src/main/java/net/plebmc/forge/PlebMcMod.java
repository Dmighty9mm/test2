package net.plebmc.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.plebmc.client.hud.PlebClient;

/**
 * PlebMc for Minecraft Java Edition 1.21.1 (Forge). The client code in
 * {@code net.plebmc.client} is the same as the Eaglercraft and 1.8.9 clients';
 * it reaches the game through the small 1.8-style layer in
 * {@code net.plebmc.mc}, and this package hooks it in with Forge events and
 * a few mixins.
 */
@Mod(PlebMcMod.MODID)
public class PlebMcMod {
	public static final String MODID = "plebmc";

	@SuppressWarnings("removal")
	public PlebMcMod() {
		FMLJavaModLoadingContext ctx = FMLJavaModLoadingContext.get();
		if (FMLEnvironment.dist != Dist.CLIENT) {
			return; // a client mod: nothing to do on a server
		}
		IEventBus modBus = ctx.getModEventBus();
		modBus.addListener(this::setup);
		modBus.addListener(this::keys);
		modBus.addListener(this::layers);
		modBus.addListener(this::overlay);
		Net.init();
		MinecraftForge.EVENT_BUS.register(new ForgeHooks());
		MinecraftForge.EVENT_BUS.register(new EntityHooks());
		MinecraftForge.EVENT_BUS.register(new TitleHooks());
	}

	private void setup(FMLClientSetupEvent e) {
		e.enqueueWork(() -> {
			PlebClient.init(net.plebmc.mc.client.Minecraft.getMinecraft());
			ServerPin.pin(Minecraft.getInstance());
		});
	}

	private void keys(RegisterKeyMappingsEvent e) {
		e.register(Zoom.KEY);
	}

	/** Hats, wings and cloaks on every player model. */
	private void layers(EntityRenderersEvent.AddLayers e) {
		for (var skin : e.getSkins()) {
			if (e.getPlayerSkin(skin) instanceof PlayerRenderer r) {
				r.addLayer(new CosmeticsLayer(r));
			}
		}
	}

	/** The HUD mods and Apollo overlays, drawn over everything else on the HUD. */
	private void overlay(AddGuiOverlayLayersEvent e) {
		e.getLayeredDraw().add(ResourceLocation.fromNamespaceAndPath(MODID, "hud"), ForgeHooks::renderHud);
	}
}
