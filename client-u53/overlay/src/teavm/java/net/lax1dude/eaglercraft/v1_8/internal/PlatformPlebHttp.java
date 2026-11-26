package net.lax1dude.eaglercraft.v1_8.internal;

import org.teavm.jso.ajax.XMLHttpRequest;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;

import net.lax1dude.eaglercraft.v1_8.internal.teavm.TeaVMUtils;

/**
 * PlebMc: a small JSON POST for the account panel (browser build). Sent as
 * text/plain so the browser needs no CORS preflight; the answer comes back on
 * the browser's event loop.
 */
public class PlatformPlebHttp {

	public interface Done {
		/** status 0 = couldn't connect; body may be null. */
		void done(int status, String body);
	}

	public static void post(String url, String json, final Done cb) {
		final XMLHttpRequest request;
		try {
			request = XMLHttpRequest.create();
			request.open("POST", url, true);
			request.setRequestHeader("Content-Type", "text/plain;charset=UTF-8");
		} catch (Throwable t) {
			cb.done(0, null);
			return;
		}
		TeaVMUtils.addEventListener(request, "load", new EventListener<Event>() {
			@Override
			public void handleEvent(Event evt) {
				cb.done(request.getStatus(), request.getResponseText());
			}
		});
		TeaVMUtils.addEventListener(request, "error", new EventListener<Event>() {
			@Override
			public void handleEvent(Event evt) {
				cb.done(0, null);
			}
		});
		try {
			request.send(json);
		} catch (Throwable t) {
			cb.done(0, null);
		}
	}

	/** Answers arrive by themselves here; nothing to poll. */
	public static void poll() {
	}
}
