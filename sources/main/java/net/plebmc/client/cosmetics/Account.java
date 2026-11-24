package net.plebmc.client.cosmetics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** The player's plebmc.top (website) login, as the server reports it. */
public final class Account {

	/** True once the server answered at all (it supports website login). */
	public boolean known;
	/** The server has website login set up. */
	public boolean enabled;
	public boolean linked;
	public String username = "";
	public String displayName = "";
	/** Website groups, highest rank first. */
	public List<String> groups = Collections.emptyList();
	public boolean staff;
	/** The server lets this player use the admin menu (website Owner). The server checks every action again. */
	public boolean admin;
	public static final String DEFAULT_SITE = "https://plebmc.top";
	public static final String DEFAULT_LINK = DEFAULT_SITE + "/index.php?route=/user/connections";

	public String site = DEFAULT_SITE;
	public String linkUrl = DEFAULT_LINK;
	/** The last result to show ("That code isn't right..."), or null. */
	public String message;
	public boolean messageOk;
	/** Waiting for the answer to a link. */
	public boolean pending;
	/** Changes on every update (screens redraw on it). */
	public int version;

	void clear() {
		known = enabled = linked = staff = pending = admin = false;
		username = displayName = "";
		site = DEFAULT_SITE;
		linkUrl = DEFAULT_LINK;
		groups = Collections.emptyList();
		message = null;
		version++;
	}

	void update(Map<?, ?> m) {
		known = true;
		pending = false;
		enabled = !Boolean.FALSE.equals(m.get("enabled"));
		linked = Boolean.TRUE.equals(m.get("linked"));
		username = linked ? str(m.get("username")) : "";
		displayName = linked ? str(m.get("displayName")) : "";
		if (displayName.isEmpty()) {
			displayName = username;
		}
		List<String> g = new ArrayList<>();
		if (linked && m.get("groups") instanceof List) {
			for (Object o : (List<?>) m.get("groups")) {
				if (o instanceof String && g.size() < 16) {
					g.add(((String) o).length() > 32 ? ((String) o).substring(0, 32) : (String) o);
				}
			}
		}
		groups = Collections.unmodifiableList(g);
		staff = linked && Boolean.TRUE.equals(m.get("staff"));
		admin = linked && Boolean.TRUE.equals(m.get("admin"));
		if (m.get("site") instanceof String) {
			site = (String) m.get("site");
		}
		if (m.get("linkUrl") instanceof String) {
			linkUrl = (String) m.get("linkUrl");
		}
		message = m.get("message") instanceof String ? (String) m.get("message") : null;
		messageOk = !Boolean.FALSE.equals(m.get("ok"));
		version++;
	}

	/** The highest website rank, or "". */
	public String rank() {
		return groups.isEmpty() ? "" : groups.get(0);
	}

	private static String str(Object o) {
		return o instanceof String ? (String) o : "";
	}
}
