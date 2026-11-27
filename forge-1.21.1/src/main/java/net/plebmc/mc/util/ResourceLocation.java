package net.plebmc.mc.util;

/** 1.8's ResourceLocation over 1.21.1's (paths are lowercased: 1.21 refuses capitals). */
public class ResourceLocation {
	public final net.minecraft.resources.ResourceLocation rl;

	public ResourceLocation(String s) {
		this(net.minecraft.resources.ResourceLocation.parse(s.toLowerCase(java.util.Locale.ROOT)));
	}

	public ResourceLocation(String ns, String path) {
		this(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ns.toLowerCase(java.util.Locale.ROOT),
				path.toLowerCase(java.util.Locale.ROOT)));
	}

	public ResourceLocation(net.minecraft.resources.ResourceLocation rl) {
		this.rl = rl;
	}

	public String getResourcePath() {
		return rl.getPath();
	}

	public String getResourceDomain() {
		return rl.getNamespace();
	}

	@Override
	public String toString() {
		return rl.toString();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof ResourceLocation && ((ResourceLocation) o).rl.equals(rl);
	}

	@Override
	public int hashCode() {
		return rl.hashCode();
	}
}
