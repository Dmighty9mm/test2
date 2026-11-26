package net.plebmc.forge.compat;

import java.io.File;
import java.nio.file.Files;


/**
 * Java Edition stand-ins for the few Eaglercraft runtime calls PlebMc uses:
 * opening links and the small key/value store (browser storage on Eaglercraft,
 * files in {@code .minecraft/config/plebmc/} here).
 */
public final class EagRuntime {
	private EagRuntime() {
	}

	public static void openLink(String url) {
		if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
			return;
		}
		try {
			net.minecraft.Util.getPlatform().openUri(url);
		} catch (Throwable ignored) {
		}
	}

	static File dir() {
		File base;
		try {
			base = net.minecraft.client.Minecraft.getInstance().gameDirectory;
		} catch (Throwable t) {
			base = new File(".");
		}
		File d = new File(new File(base, "config"), "plebmc");
		d.mkdirs();
		return d;
	}

	private static File file(String key) {
		return new File(dir(), key.replaceAll("[^A-Za-z0-9._-]", "_") + ".dat");
	}

	public static byte[] getStorage(String key) {
		try {
			File f = file(key);
			return f.isFile() ? Files.readAllBytes(f.toPath()) : null;
		} catch (Throwable t) {
			return null;
		}
	}

	public static void setStorage(String key, byte[] data) {
		try {
			File f = file(key);
			if (data == null) {
				f.delete();
			} else {
				File tmp = new File(f.getPath() + ".tmp");
				Files.write(tmp.toPath(), data);
				if (!tmp.renameTo(f)) {
					f.delete();
					tmp.renameTo(f);
				}
			}
		} catch (Throwable ignored) {
		}
	}
}
