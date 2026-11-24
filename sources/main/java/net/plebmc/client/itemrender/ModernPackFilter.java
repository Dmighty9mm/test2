package net.plebmc.client.itemrender;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import net.lax1dude.eaglercraft.v1_8.opengl.ImageData;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;

/**
 * A 1.13+ resource pack as 1.8 sees it: everything except its vanilla
 * models, block states and chest textures, whose newer format would turn
 * vanilla items and blocks into "missing" boxes. PlebClient reads those
 * models itself for custom items. Packs made for 1.8-1.12 are not wrapped.
 */
final class ModernPackFilter implements IResourcePack {

	private final IResourcePack pack;

	ModernPackFilter(IResourcePack pack) {
		this.pack = pack;
	}

	IResourcePack inner() {
		return pack;
	}

	static boolean hidden(ResourceLocation rl) {
		if (!"minecraft".equals(rl.getResourceDomain())) {
			return false;
		}
		String p = rl.getResourcePath();
		return p.startsWith("models/") || p.startsWith("blockstates/") || p.startsWith("textures/entity/chest/")
				|| p.startsWith("textures/entity/bed/") || p.startsWith("textures/entity/shulker/");
	}

	@Override
	public InputStream getInputStream(ResourceLocation rl) throws IOException {
		if (hidden(rl)) {
			throw new FileNotFoundException(rl.toString());
		}
		return pack.getInputStream(rl);
	}

	@Override
	public boolean resourceExists(ResourceLocation rl) {
		return !hidden(rl) && pack.resourceExists(rl);
	}

	@Override
	public Set<String> getResourceDomains() {
		return pack.getResourceDomains();
	}

	@Override
	public <T extends IMetadataSection> T getPackMetadata(IMetadataSerializer s, String section) throws IOException {
		return pack.getPackMetadata(s, section);
	}

	@Override
	public ImageData getPackImage() throws IOException {
		return pack.getPackImage();
	}

	@Override
	public String getPackName() {
		return pack.getPackName();
	}
}
