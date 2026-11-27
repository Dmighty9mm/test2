package net.lax1dude.eaglercraft.v1_8.internal;

import java.util.ArrayList;
import java.util.List;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/**
 * PlebMc: a small JSON POST for the account panel (WASM-GC build). The
 * browser does the request; the game polls it every tick (PlebClient.tick),
 * so no JavaScript calls back into the game. Sent as text/plain so the
 * browser needs no CORS preflight.
 */
public class PlatformPlebHttp {

	public interface Done {
		/** status 0 = couldn't connect; body may be null. */
		void done(int status, String body);
	}

	private static final class Pending {
		final JSObject req;
		final Done cb;

		Pending(JSObject req, Done cb) {
			this.req = req;
			this.cb = cb;
		}
	}

	private static final List<Pending> pending = new ArrayList<>();

	@JSBody(params = { "url", "body" }, script = "var r = { s: -1, t: null };"
			+ "try { var x = new XMLHttpRequest(); x.open(\"POST\", url, true);"
			+ "x.setRequestHeader(\"Content-Type\", \"text/plain;charset=UTF-8\");"
			+ "x.onload = function() { r.t = x.responseText; r.s = x.status; };"
			+ "x.onerror = function() { r.s = 0; };"
			+ "x.ontimeout = function() { r.s = 0; };"
			+ "x.timeout = 20000; x.send(body); } catch (e) { r.s = 0; }"
			+ "return r;")
	private static native JSObject start(String url, String body);

	@JSBody(params = { "r" }, script = "return r.s;")
	private static native int status(JSObject r);

	@JSBody(params = { "r" }, script = "return (typeof r.t === \"string\") ? r.t : null;")
	private static native String text(JSObject r);

	public static void post(String url, String json, Done cb) {
		JSObject r;
		try {
			r = start(url, json);
		} catch (Throwable t) {
			cb.done(0, null);
			return;
		}
		pending.add(new Pending(r, cb));
	}

	public static void poll() {
		for (int i = 0; i < pending.size(); i++) {
			Pending p = pending.get(i);
			int s = status(p.req);
			if (s >= 0) {
				pending.remove(i--);
				p.cb.done(s, s > 0 ? text(p.req) : null);
			}
		}
	}
}
