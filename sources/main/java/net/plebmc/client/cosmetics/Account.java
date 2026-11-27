package net.plebmc.client.cosmetics;

import java.util.Map;

/** What the server says about this player's PlebMc account role (it decides the admin menu). */
public final class Account {

	/** True once the server answered. */
	public boolean known;
	/** The panel role ("player", "mod", "admin", "owner"), or "" when not signed in. */
	public String role = "";
	/** The server lets this player use the admin menu. The server checks every action again. */
	public boolean admin;
	/** Changes on every update (screens redraw on it). */
	public int version;

	void clear() {
		known = admin = false;
		role = "";
		version++;
	}

	void update(Map<?, ?> m) {
		known = true;
		role = m.get("role") instanceof String ? (String) m.get("role") : "";
		if (role.length() > 16) {
			role = role.substring(0, 16);
		}
		admin = Boolean.TRUE.equals(m.get("admin"));
		version++;
	}
}
