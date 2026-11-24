package net.plebmc.client.itemrender;

import java.util.ArrayList;
import java.util.List;

import net.lax1dude.eaglercraft.v1_8.internal.vfs2.VFile2;
import net.lax1dude.eaglercraft.v1_8.minecraft.EaglerFolderResourcePack;
import net.plebmc.client.items.PackFiles;

/** A resource pack stored in the browser (server packs and imported packs). */
final class FolderPackFiles implements PackFiles {

	private final EaglerFolderResourcePack pack;

	FolderPackFiles(EaglerFolderResourcePack pack) {
		this.pack = pack;
	}

	EaglerFolderResourcePack pack() {
		return pack;
	}

	@Override
	public byte[] read(String path) {
		try {
			VFile2 f = new VFile2(pack.plebPrefix(), pack.plebFolder(), path);
			return f.exists() ? f.getAllBytes() : null;
		} catch (Throwable t) {
			return null;
		}
	}

	@Override
	public List<String> list(String folder) {
		List<String> out = new ArrayList<>();
		try {
			for (String full : new VFile2(pack.plebPrefix(), pack.plebFolder(), folder).listFilenames(false)) {
				int i = full.lastIndexOf('/');
				out.add(i < 0 ? full : full.substring(i + 1));
			}
		} catch (Throwable t) {
			// an unreadable folder lists as empty
		}
		return out;
	}

	@Override
	public String name() {
		return pack.getDisplayName();
	}

	/** pack_format from pack.mcmeta (1 when missing or unreadable). */
	int packFormat() {
		try {
			Object o = net.plebmc.client.items.ModelConverter.parse(read("pack.mcmeta"));
			if (o instanceof java.util.Map) {
				Object p = ((java.util.Map<?, ?>) o).get("pack");
				if (p instanceof java.util.Map) {
					Object f = ((java.util.Map<?, ?>) p).get("pack_format");
					if (f instanceof Number) {
						return ((Number) f).intValue();
					}
				}
			}
		} catch (Throwable t) {
			// fall through
		}
		return 1;
	}
}
