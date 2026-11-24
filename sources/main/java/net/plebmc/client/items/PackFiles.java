package net.plebmc.client.items;

import java.util.List;

/**
 * Read access to one resource pack's files, by path from the pack root
 * ("assets/minecraft/models/item/diamond_hoe.json").
 */
public interface PackFiles {

	/** The file, or null when the pack doesn't have it. */
	byte[] read(String path);

	/** File names (not paths) directly inside a folder, or an empty list. */
	List<String> list(String folder);

	/** A short name for logs. */
	String name();
}
