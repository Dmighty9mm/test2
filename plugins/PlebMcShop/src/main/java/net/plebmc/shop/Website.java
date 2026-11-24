package net.plebmc.shop;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;

/**
 * Logging in with a plebmc.top (NamelessMC) account. NamelessMC's API can't
 * check passwords, so this uses its own Minecraft account linking: the
 * player clicks "Link" under Connections on the website, gets a code, and
 * types it into the client once. After that the website knows this
 * Minecraft account, and the player is logged in on every join.
 *
 * Calls go to {@code <url>/index.php?route=/api/v2/...} with the site's API
 * key. The key stays on the proxy; clients never see it.
 */
final class Website {

	/** One website account, as the client shows it. */
	static final class Account {
		final int id;
		final String username;
		final String displayName;
		final List<String> groups;
		final boolean staff;

		Account(int id, String username, String displayName, List<String> groups, boolean staff) {
			this.id = id;
			this.username = username;
			this.displayName = displayName;
			this.groups = groups;
			this.staff = staff;
		}
	}

	/** The answer to a call: an account, "not linked", or an error to show. */
	static final class Result {
		final Account account;
		final String error;

		Result(Account account, String error) {
			this.account = account;
			this.error = error;
		}
	}

	/** HTTP, swappable for tests. Returns {status, body}. */
	interface Http {
		Object[] request(String method, String url, String apiKey, String jsonBody) throws IOException;
	}

	static final Http REAL = (method, url, apiKey, jsonBody) -> {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setRequestMethod(method);
		c.setConnectTimeout(8000);
		c.setReadTimeout(8000);
		c.setRequestProperty("Authorization", "Bearer " + apiKey);
		// some hosts drop Authorization; NamelessMC also reads this one
		c.setRequestProperty("X-API-Key", apiKey);
		c.setRequestProperty("Accept", "application/json");
		c.setRequestProperty("User-Agent", "PlebMcShop");
		if (jsonBody != null) {
			c.setDoOutput(true);
			c.setRequestProperty("Content-Type", "application/json");
			try (OutputStream o = c.getOutputStream()) {
				o.write(jsonBody.getBytes(StandardCharsets.UTF_8));
			}
		}
		int status = c.getResponseCode();
		InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		if (in != null) {
			try (InputStream i = in) {
				byte[] buf = new byte[8192];
				int n;
				while ((n = i.read(buf)) > 0 && b.size() < 1 << 20) {
					b.write(buf, 0, n);
				}
			}
		}
		return new Object[] { status, new String(b.toByteArray(), StandardCharsets.UTF_8) };
	};

	private final Gson gson = new Gson();
	private final Http http;
	final String url;
	private final String apiKey;
	final String integration;

	Website(Cfg cfg, Http http) {
		this.http = http;
		String u = cfg.str("website.url", "").trim();
		while (u.endsWith("/")) {
			u = u.substring(0, u.length() - 1);
		}
		this.url = u;
		this.apiKey = cfg.str("website.api-key", "").trim();
		this.integration = cfg.str("website.integration", "Minecraft").trim();
	}

	boolean configured() {
		return !url.isEmpty() && !apiKey.isEmpty();
	}

	/** Where players get their link code. */
	String connectionsUrl() {
		return url + "/index.php?route=/user/connections";
	}

	static String identifier(UUID id) {
		return id.toString().replace("-", "");
	}

	private String api(String route) {
		return url + "/index.php?route=/api/v2/" + route;
	}

	/** The website account linked to this Minecraft account, or a null account if there is none. */
	Result lookup(UUID player) {
		String route;
		try {
			route = "users/integration_id:" + URLEncoder.encode(integration.toLowerCase(Locale.ROOT), "UTF-8") + ":"
					+ identifier(player);
		} catch (IOException e) {
			return new Result(null, "bad integration name");
		}
		Map<?, ?> m;
		try {
			m = call("GET", route, null);
		} catch (IOException e) {
			return new Result(null, "The website didn't answer. Try again in a minute.");
		}
		if (m.get("error") != null) {
			String err = String.valueOf(m.get("error"));
			if (err.contains("cannot_find_user")) {
				return new Result(null, null);
			}
			return new Result(null, describe(err));
		}
		// only a verified link counts (an unfinished link already has an identifier)
		boolean verified = false;
		if (m.get("integrations") instanceof List) {
			for (Object o : (List<?>) m.get("integrations")) {
				if (o instanceof Map) {
					Map<?, ?> i = (Map<?, ?>) o;
					if (integration.equalsIgnoreCase(String.valueOf(i.get("integration")))
							&& identifier(player).equalsIgnoreCase(String.valueOf(i.get("identifier")))
							&& Boolean.TRUE.equals(i.get("verified"))) {
						verified = true;
					}
				}
			}
		}
		if (!verified) {
			return new Result(null, null);
		}
		if (Boolean.TRUE.equals(m.get("banned")) || "1".equals(String.valueOf(m.get("banned")))) {
			return new Result(null, "That website account is banned.");
		}
		return new Result(account(m), null);
	}

	/** Links this Minecraft account with the code the website showed, then looks the account up. */
	Result link(UUID player, String name, String code) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("integration", integration);
		body.put("code", code);
		body.put("identifier", identifier(player));
		body.put("username", name);
		Map<?, ?> m;
		try {
			m = call("POST", "integration/verify", gson.toJson(body));
		} catch (IOException e) {
			return new Result(null, "The website didn't answer. Try again in a minute.");
		}
		if (m.get("error") != null) {
			return new Result(null, describe(String.valueOf(m.get("error"))));
		}
		Result r = lookup(player);
		return r.account == null && r.error == null ? new Result(null, "Linked, but the website didn't confirm it yet.") : r;
	}

	/**
	 * Logs out: removes the Minecraft link from the website account (the
	 * player can link again with a new code). Returns null when done, or why not.
	 */
	String unlink(UUID player) {
		String route;
		try {
			route = "users/integration_id:" + URLEncoder.encode(integration.toLowerCase(Locale.ROOT), "UTF-8") + ":"
					+ identifier(player) + "/integrations/unlink";
		} catch (IOException e) {
			return "bad integration name";
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("integration", integration);
		Map<?, ?> m;
		try {
			m = call("POST", route, gson.toJson(body));
		} catch (IOException e) {
			return "The website didn't answer. Try again in a minute.";
		}
		if (m.get("error") != null) {
			String err = String.valueOf(m.get("error"));
			// already not linked (or no such user): that is logged out too
			return err.contains("not_linked") || err.contains("cannot_find_user") ? null : describe(err);
		}
		return null;
	}

	private Map<?, ?> call(String method, String route, String body) throws IOException {
		Object[] r = http.request(method, api(route), apiKey, body);
		Object parsed;
		try {
			parsed = gson.fromJson((String) r[1], Object.class);
		} catch (RuntimeException e) {
			throw new IOException("not JSON (HTTP " + r[0] + ")");
		}
		if (!(parsed instanceof Map)) {
			throw new IOException("not a JSON object (HTTP " + r[0] + ")");
		}
		return (Map<?, ?>) parsed;
	}

	private Account account(Map<?, ?> m) {
		List<String> groups = new ArrayList<>();
		boolean staff = false;
		if (m.get("groups") instanceof List) {
			List<Map<?, ?>> gs = new ArrayList<>();
			for (Object o : (List<?>) m.get("groups")) {
				if (o instanceof Map) {
					gs.add((Map<?, ?>) o);
				}
			}
			// NamelessMC: a lower "order" is a higher rank
			gs.sort((a, b) -> Double.compare(num(a.get("order")), num(b.get("order"))));
			for (Map<?, ?> g : gs) {
				groups.add(String.valueOf(g.get("name")));
				staff |= Boolean.TRUE.equals(g.get("staff"));
			}
		}
		String username = String.valueOf(m.get("username"));
		Object dn = m.get("displayname");
		return new Account((int) num(m.get("id")), username, dn == null || String.valueOf(dn).isEmpty() ? username : String.valueOf(dn),
				groups, staff);
	}

	private static double num(Object o) {
		return o instanceof Number ? ((Number) o).doubleValue() : 0;
	}

	/** NamelessMC error codes in words. */
	static String describe(String err) {
		if (err.contains("invalid_code")) {
			return "That code isn't right. Get a new one on the website.";
		}
		if (err.contains("already_verified")) {
			return "That code was already used.";
		}
		if (err.contains("identifier_errors") || err.contains("username_errors")) {
			return "This Minecraft account is already linked to another website account.";
		}
		if (err.contains("invalid_integration")) {
			return "Minecraft linking is turned off on the website.";
		}
		if (err.contains("invalid_api_key") || err.contains("api_disabled")) {
			return "The server's website key is wrong. Tell an admin.";
		}
		return "The website said: " + err;
	}
}
