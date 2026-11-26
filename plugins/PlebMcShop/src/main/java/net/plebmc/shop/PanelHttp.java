package net.plebmc.shop;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** HTTP to the PlebMc Panel (a small interface so tests can answer without a network). */
interface PanelHttp {

	/** Returns { status (Integer), body (String) }. */
	Object[] request(String method, String url, String apiKey, String jsonBody) throws IOException;

	/** Answers up to 32 MB (a shop upload from the panel can be a few MB). */
	PanelHttp REAL = (method, url, apiKey, jsonBody) -> {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setRequestMethod(method);
		c.setConnectTimeout(8000);
		c.setReadTimeout(30000);
		c.setRequestProperty("Authorization", "Bearer " + apiKey);
		// some hosts drop Authorization
		c.setRequestProperty("X-API-Key", apiKey);
		c.setRequestProperty("Accept", "application/json");
		c.setRequestProperty("User-Agent", "PlebioAPI");
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
				while ((n = i.read(buf)) > 0 && b.size() < 32 << 20) {
					b.write(buf, 0, n);
				}
			}
		}
		return new Object[] { status, new String(b.toByteArray(), StandardCharsets.UTF_8) };
	};
}
