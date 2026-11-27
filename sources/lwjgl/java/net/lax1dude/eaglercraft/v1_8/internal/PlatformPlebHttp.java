package net.lax1dude.eaglercraft.v1_8.internal;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** PlebMc: a small JSON POST for the account panel (desktop build), on its own thread. */
public class PlatformPlebHttp {

	public interface Done {
		/** status 0 = couldn't connect; body may be null. */
		void done(int status, String body);
	}

	public static void post(final String url, final String json, final Done cb) {
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				int status = 0;
				String body = null;
				try {
					HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
					c.setRequestMethod("POST");
					c.setConnectTimeout(10000);
					c.setReadTimeout(10000);
					c.setDoOutput(true);
					c.setRequestProperty("Content-Type", "text/plain;charset=UTF-8");
					try (OutputStream o = c.getOutputStream()) {
						o.write(json.getBytes(StandardCharsets.UTF_8));
					}
					status = c.getResponseCode();
					InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
					if (in != null) {
						ByteArrayOutputStream b = new ByteArrayOutputStream();
						byte[] buf = new byte[4096];
						int n;
						while ((n = in.read(buf)) > 0 && b.size() < 1 << 20) {
							b.write(buf, 0, n);
						}
						in.close();
						body = new String(b.toByteArray(), StandardCharsets.UTF_8);
					}
				} catch (Throwable e) {
					status = 0;
				}
				cb.done(status, body);
			}
		}, "PlebMc account");
		t.setDaemon(true);
		t.start();
	}

	/** Answers arrive by themselves here; nothing to poll. */
	public static void poll() {
	}
}
