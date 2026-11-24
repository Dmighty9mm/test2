package net.plebmc.client.itemrender;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;

import net.lax1dude.eaglercraft.v1_8.EagRuntime;
import net.lax1dude.eaglercraft.v1_8.opengl.ImageData;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;
import net.plebmc.client.items.ModelConverter;
import net.plebmc.client.items.NewItems;

/**
 * PlebClient's own resource domain ("plebitems"): the converted custom item
 * models, the pack textures they use (under their own names, so they never
 * replace a vanilla texture) and the built-in textures for totems and
 * netherite.
 */
final class PlebItemPack implements IResourcePack {

	private static final Set<String> DOMAINS = Collections.singleton(ModelConverter.DOMAIN);

	private byte[] find(ResourceLocation rl) {
		if (!ModelConverter.DOMAIN.equals(rl.getResourceDomain())) {
			return null;
		}
		String p = rl.getResourcePath();
		if (p.startsWith("models/") && p.endsWith(".json")) {
			String id = p.substring(7, p.length() - 5);
			if (id.startsWith("new/")) {
				String n = id.substring(4);
				for (String known : NewItems.ids()) {
					if (known.equals(n)) {
						return NewItems.modelJson(n).getBytes(StandardCharsets.UTF_8);
					}
				}
				return null;
			}
			ModelConverter.Result r = PlebItems.findModel(id);
			return r == null ? null : r.json.getBytes(StandardCharsets.UTF_8);
		}
		if (p.startsWith("textures/p/")) {
			String suffix = p.endsWith(".png.mcmeta") ? ".png.mcmeta" : p.endsWith(".png") ? ".png" : null;
			if (suffix == null) {
				return null;
			}
			return PlebItems.findSprite(p.substring(9, p.length() - suffix.length()), suffix);
		}
		return EagRuntime.getResourceBytes("/assets/" + ModelConverter.DOMAIN + "/" + p);
	}

	@Override
	public InputStream getInputStream(ResourceLocation rl) throws IOException {
		byte[] b = find(rl);
		if (b == null) {
			throw new FileNotFoundException(rl.toString());
		}
		return new ByteArrayInputStream(b);
	}

	@Override
	public boolean resourceExists(ResourceLocation rl) {
		return find(rl) != null;
	}

	@Override
	public Set<String> getResourceDomains() {
		return DOMAINS;
	}

	@Override
	public <T extends IMetadataSection> T getPackMetadata(IMetadataSerializer s, String section) throws IOException {
		return null;
	}

	@Override
	public ImageData getPackImage() throws IOException {
		return null;
	}

	@Override
	public String getPackName() {
		return "PlebClient items";
	}
}
