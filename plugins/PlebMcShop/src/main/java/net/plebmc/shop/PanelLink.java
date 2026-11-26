package net.plebmc.shop;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

/**
 * Talks to the PlebMc Panel (panel/app.py) on the VPS: checks account
 * passwords and remembered client tokens, hands out website registration
 * codes, sends the panel everyone's coins and cosmetics, and picks up changes
 * admins made there. Every call blocks, so callers run it off the proxy's
 * event threads. The server key never leaves the proxy.
 */
final class PanelLink {

	private final Gson gson = new Gson();
	private final PanelHttp http;
	final String url;
	private final String key;

	PanelLink(PanelHttp http, String url, String key) {
		this.http = http;
		this.url = url == null ? "" : url.trim().replaceAll("/+$", "");
		this.key = key == null ? "" : key.trim();
	}

	boolean configured() {
		return !url.isEmpty() && key.length() >= 24;
	}

	/** {exists, name, role, locked} */
	Map<?, ?> lookup(String name) throws IOException {
		return call("GET", "account?name=" + URLEncoder.encode(name, "UTF-8"), null);
	}

	/** {ok, name, role, token?} or {ok:false, error} */
	Map<?, ?> login(String name, String password, boolean remember) throws IOException {
		return call("POST", "login", body("name", name, "password", password, "remember", remember));
	}

	Map<?, ?> token(String name, String token) throws IOException {
		return call("POST", "token", body("name", name, "token", token));
	}

	void forget(String token) throws IOException {
		call("POST", "forget", body("token", token));
	}

	Map<?, ?> register(String name, String password) throws IOException {
		return call("POST", "register", body("name", name, "password", password));
	}

	/** {ok, code, url, minutes} */
	Map<?, ?> claim(String name) throws IOException {
		return call("POST", "claim", body("name", name));
	}

	void sync(Map<String, Object> snapshot) throws IOException {
		call("POST", "sync", snapshot);
	}

	/** Changes queued by admins on the panel: [{id, target, action, args, by}] */
	List<Map<?, ?>> commands() throws IOException {
		Object c = call("GET", "commands", null).get("commands");
		List<Map<?, ?>> out = new ArrayList<>();
		if (c instanceof List) {
			for (Object o : (List<?>) c) {
				if (o instanceof Map) {
					out.add((Map<?, ?>) o);
				}
			}
		}
		return out;
	}

	void ack(List<Map<String, Object>> results) throws IOException {
		Map<String, Object> b = new LinkedHashMap<>();
		b.put("results", results);
		call("POST", "ack", b);
	}

	/** A coins/cosmetics change from the panel as /pleb arguments, or null if it isn't one. */
	static String[] plebshopArgs(String target, String action, List<String> args) {
		switch (action) {
		case "coins":
			return args.size() == 2 ? new String[] { "coins", args.get(0), target, args.get(1) } : null;
		case "give":
		case "remove":
			return args.size() == 1 ? new String[] { action, target, args.get(0) } : null;
		case "equip":
			return args.size() == 2 ? new String[] { "equip", target, args.get(0), args.get(1) } : null;
		default:
			return null;
		}
	}

	private static Map<String, Object> body(Object... kv) {
		Map<String, Object> m = new LinkedHashMap<>();
		for (int i = 0; i + 1 < kv.length; i += 2) {
			m.put((String) kv[i], kv[i + 1]);
		}
		return m;
	}

	private Map<?, ?> call(String method, String route, Object body) throws IOException {
		if (!configured()) {
			throw new IOException("panel not configured");
		}
		Object[] r = http.request(method, url + "/api/server/" + route, key, body == null ? null : gson.toJson(body));
		int status = (Integer) r[0];
		if (status == 401) {
			throw new IOException("the panel refused the server key (accounts.server-key must match PANEL_SERVER_KEY)");
		}
		if (status >= 300) {
			throw new IOException("panel answered HTTP " + status);
		}
		Object o;
		try {
			o = gson.fromJson((String) r[1], Object.class);
		} catch (RuntimeException e) {
			throw new IOException("panel sent something that isn't JSON");
		}
		if (!(o instanceof Map)) {
			throw new IOException("panel sent something unexpected");
		}
		return (Map<?, ?>) o;
	}
}
