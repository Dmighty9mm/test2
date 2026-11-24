package net.plebmc.forge;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.plebmc.client.hud.CloakLayer;
import net.plebmc.client.hud.CosmeticLayer;
import net.plebmc.client.hud.PlebClient;

/**
 * PlebMc for Minecraft Java Edition 1.8.9 (Forge). The client code in
 * {@code net.plebmc.client} is the same as the Eaglercraft client's; this
 * package connects it to the game through Forge events and a few mixins.
 */
@Mod(modid = PlebMcMod.MODID, name = "PlebMc", version = PlebMcMod.VERSION, clientSideOnly = true,
		acceptedMinecraftVersions = "[1.8.9]")
public class PlebMcMod {
	public static final String MODID = "plebmc";
	public static final String VERSION = "1.0.0";

	@Mod.EventHandler
	public void init(FMLInitializationEvent e) {
		Minecraft mc = Minecraft.getMinecraft();
		PlebClient.init(mc);
		Zoom.key = new KeyBinding("Zoom", Keyboard.KEY_C, "PlebMc");
		ClientRegistry.registerKeyBinding(Zoom.key);
		ForgeHooks hooks = new ForgeHooks();
		MinecraftForge.EVENT_BUS.register(hooks);
		net.minecraftforge.fml.common.FMLCommonHandler.instance().bus().register(hooks);
	}

	@Mod.EventHandler
	public void postInit(FMLPostInitializationEvent e) {
		Minecraft mc = Minecraft.getMinecraft();
		for (RenderPlayer r : mc.getRenderManager().getSkinMap().values()) {
			r.addLayer(new CosmeticLayer(r));
			r.addLayer(new CloakLayer(r));
		}
		ServerPin.pin(mc);
	}
}
