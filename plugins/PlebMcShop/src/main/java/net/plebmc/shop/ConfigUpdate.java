package net.plebmc.shop;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Brings an older config.yml up to date: settings a new version added (with
 * their comments) are written in, and nothing already in the file is changed
 * or removed. A copy of the old file is kept first.
 *
 * Whole sections that are missing are added at the end. Inside the settings
 * sections listed in {@link #MERGE_INSIDE}, missing keys are added at the end
 * of your section. Lists you edit yourself (items, collections, quests) are
 * never touched, so a hat or quest you deleted doesn't come back.
 */
final class ConfigUpdate {

	/** Sections whose missing keys are filled in one by one. */
	static final Set<String> MERGE_INSIDE = new HashSet<>(Arrays.asList("website", "login-rewards", "playtime",
			"admin-menu", "staff-lock", "accounts", "messages", "voting"));

	private static final Pattern TOP = Pattern.compile("^([A-Za-z0-9_.-]+):.*$");
	private static final Pattern CHILD = Pattern.compile("^  ([A-Za-z0-9_.-]+):.*$");

	private ConfigUpdate() {
	}

	/** A key with the comment lines right above it and the lines under it. */
	private static final class Block {
		final String key;
		final List<String> lines = new ArrayList<>();

		Block(String key) {
			this.key = key;
		}
	}

	/** Result of {@link #merge}: the new text and what was added. */
	static final class Result {
		final String text;
		final List<String> added;

		Result(String text, List<String> added) {
			this.text = text;
			this.added = added;
		}
	}

	/** Updates the file in place. Returns what was added (empty: nothing to do). */
	static List<String> update(File file, byte[] defaults, Logger log) throws IOException {
		String mine = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
		Result r = merge(mine, new String(defaults, StandardCharsets.UTF_8));
		if (r.added.isEmpty()) {
			return r.added;
		}
		// never write something that doesn't load
		try {
			Cfg.load(new ByteArrayInputStream(r.text.getBytes(StandardCharsets.UTF_8)));
		} catch (IOException | RuntimeException e) {
			log.warning("config.yml has new settings (" + String.join(", ", r.added)
					+ ") but they couldn't be added automatically: " + e + ". Copy them from the jar's config.yml.");
			return new ArrayList<>();
		}
		File backup = new File(file.getParentFile(), "config.yml.before-update-" + System.currentTimeMillis());
		Files.copy(file.toPath(), backup.toPath());
		Files.write(file.toPath(), r.text.getBytes(StandardCharsets.UTF_8));
		log.info("config.yml: added new settings " + String.join(", ", r.added) + " (nothing of yours was changed; old file kept as "
				+ backup.getName() + ")");
		return r.added;
	}

	static Result merge(String mine, String defaults) {
		String nl = mine.contains("\r\n") ? "\r\n" : "\n";
		List<String> my = new ArrayList<>(Arrays.asList(mine.replace("\r\n", "\n").split("\n", -1)));
		List<Block> def = blocks(Arrays.asList(defaults.replace("\r\n", "\n").split("\n", -1)), TOP, 0);
		List<String> added = new ArrayList<>();

		// 1. missing keys inside sections both files have
		for (Block d : def) {
			if (!MERGE_INSIDE.contains(d.key)) {
				continue;
			}
			int start = findTop(my, d.key);
			if (start < 0) {
				continue;
			}
			int end = sectionEnd(my, start);
			Set<String> have = new LinkedHashSet<>();
			for (int i = start + 1; i < end; i++) {
				Matcher m = CHILD.matcher(my.get(i));
				if (m.matches()) {
					have.add(m.group(1));
				}
			}
			List<String> insert = new ArrayList<>();
			for (Block c : blocks(d.lines.subList(indexOfKey(d.lines, d.key) + 1, d.lines.size()), CHILD, 2)) {
				if (!have.contains(c.key)) {
					insert.addAll(trimBlank(c.lines));
					added.add(d.key + "." + c.key);
				}
			}
			if (!insert.isEmpty()) {
				my.addAll(end, insert);
			}
		}

		// 2. new built-in items (wings) go into your items once; 'added-item-packs' (added
		// below as a new section) remembers it so deleted ones don't come back
		int items = findTop(my, "items");
		if (items >= 0 && findTop(my, "added-item-packs") < 0) {
			Set<String> have = new LinkedHashSet<>();
			int end = sectionEnd(my, items);
			for (int i = items + 1; i < end; i++) {
				Matcher m = CHILD.matcher(my.get(i));
				if (m.matches()) {
					have.add(m.group(1));
				}
			}
			List<String> insert = new ArrayList<>();
			for (Block d : def) {
				if (!"items".equals(d.key)) {
					continue;
				}
				for (Block c : blocks(d.lines.subList(indexOfKey(d.lines, d.key) + 1, d.lines.size()), CHILD, 2)) {
					String line = c.lines.get(c.lines.size() - 1);
					for (String l : c.lines) {
						if (CHILD.matcher(l).matches()) {
							line = l;
						}
					}
					if (!have.contains(c.key) && line.contains("category: wings")) {
						insert.add(line);
						added.add("items." + c.key);
					}
				}
			}
			if (!insert.isEmpty()) {
				insert.add(0, "");
				my.addAll(end, insert);
			}
		}

		// 3. whole sections that are missing
		List<String> tail = new ArrayList<>();
		for (Block d : def) {
			if (findTop(my, d.key) < 0) {
				tail.add("");
				tail.addAll(trimBlank(d.lines));
				added.add(d.key);
			}
		}
		if (!tail.isEmpty()) {
			while (!my.isEmpty() && my.get(my.size() - 1).trim().isEmpty()) {
				my.remove(my.size() - 1);
			}
			my.add("");
			my.add("# ---- New settings added by a PlebioAPI update (your settings above weren't changed) ----");
			my.addAll(tail);
			my.add("");
		}
		return new Result(String.join(nl, my), added);
	}

	/** Splits lines into blocks at keys matching the pattern; comments directly above a key belong to it. */
	private static List<Block> blocks(List<String> lines, Pattern key, int indent) {
		List<Block> out = new ArrayList<>();
		List<String> pending = new ArrayList<>();
		Block cur = null;
		String pad = indent == 0 ? "" : String.format("%" + indent + "s", "");
		for (String line : lines) {
			Matcher m = key.matcher(line);
			boolean comment = line.startsWith(pad + "#") && (indent == 0 || !line.startsWith(pad + " "));
			if (m.matches()) {
				cur = new Block(m.group(1));
				cur.lines.addAll(pending);
				pending.clear();
				cur.lines.add(line);
				out.add(cur);
			} else if (comment || line.trim().isEmpty()) {
				pending.add(line);
				if (line.trim().isEmpty()) {
					// a blank line ends a comment block that isn't attached to the next key
					if (cur != null) {
						cur.lines.addAll(pending);
					}
					pending.clear();
				}
			} else if (cur != null) {
				cur.lines.addAll(pending);
				pending.clear();
				cur.lines.add(line);
			}
		}
		return out;
	}

	private static int indexOfKey(List<String> lines, String key) {
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).startsWith(key + ":")) {
				return i;
			}
		}
		return lines.size() - 1;
	}

	private static int findTop(List<String> lines, String key) {
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).startsWith(key + ":")) {
				return i;
			}
		}
		return -1;
	}

	/** The line after a section's last indented line (before trailing blanks and the next section's comments). */
	private static int sectionEnd(List<String> lines, int start) {
		int last = start;
		for (int i = start + 1; i < lines.size(); i++) {
			String l = lines.get(i);
			if (l.trim().isEmpty() || l.startsWith("#")) {
				continue;
			}
			if (!l.startsWith(" ") && !l.startsWith("\t") && !l.startsWith("-")) {
				break;
			}
			last = i;
		}
		return last + 1;
	}

	private static List<String> trimBlank(List<String> lines) {
		int a = 0, b = lines.size();
		while (a < b && lines.get(a).trim().isEmpty()) {
			a++;
		}
		while (b > a && lines.get(b - 1).trim().isEmpty()) {
			b--;
		}
		return new ArrayList<>(lines.subList(a, b));
	}
}
