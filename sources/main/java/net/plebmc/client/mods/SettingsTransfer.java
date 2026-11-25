package net.plebmc.client.mods;

import java.util.LinkedHashMap;
import java.util.Map;

import net.plebmc.client.apollo.proto.Json;

/**
 * Exports and imports every PlebClient setting (mods, HUD positions, client
 * preferences, FPS Larp) as one line of text you can copy, share or keep.
 * Your plebmc.top login, your shop look and seen quests are never included.
 */
public final class SettingsTransfer {

	private SettingsTransfer() {
	}

	/** The store keys a settings export carries. */
	static final String[] KEYS = { ModRegistry.STORE_KEY, ClientPrefs.STORE_KEY, FpsLarp.STORE_KEY };
	static final String MARK = "plebclient-settings";

	public static String export(SettingsStore store) {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put(MARK, 1.0);
		for (String k : KEYS) {
			String raw = store.get(k);
			if (raw != null && !raw.isEmpty()) {
				try {
					root.put(k, Json.parse(raw));
				} catch (RuntimeException e) {
					// a broken value is left out
				}
			}
		}
		return Json.write(root);
	}

	/**
	 * Writes an export back into the store. Returns how many parts were
	 * imported, or -1 when the text isn't a PlebClient settings export.
	 */
	public static int importInto(SettingsStore store, String text) {
		if (text == null) {
			return -1;
		}
		Object o;
		try {
			o = Json.parse(text.trim());
		} catch (RuntimeException e) {
			return -1;
		}
		if (!(o instanceof Map) || !((Map<?, ?>) o).containsKey(MARK)) {
			return -1;
		}
		Map<?, ?> m = (Map<?, ?>) o;
		int n = 0;
		for (String k : KEYS) {
			if (m.get(k) instanceof Map) {
				store.set(k, Json.write(m.get(k)));
				n++;
			}
		}
		return n;
	}
}
