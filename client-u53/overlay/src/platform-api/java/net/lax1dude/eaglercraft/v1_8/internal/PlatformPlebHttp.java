package net.lax1dude.eaglercraft.v1_8.internal;

/**
 * PlebMc: a small JSON POST for the account panel. Compile-time stub; each
 * platform (teavm, wasm-gc-teavm, lwjgl) has the real one.
 */
public class PlatformPlebHttp {

	public interface Done {
		/** status 0 = couldn't connect; body may be null. */
		void done(int status, String body);
	}

	public static void post(String url, String json, Done cb) {
		cb.done(0, null);
	}

	/** Called every tick; finishes requests on builds that poll them. */
	public static void poll() {
	}
}
