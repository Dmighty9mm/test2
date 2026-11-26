import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.plebmc.client.cosmetics.ClientSession;
import net.plebmc.client.mods.SettingsStore;

/**
 * JVM test for the client's start-screen sign-in: nothing is usable until
 * signed in, sign-in/sign-up store a token (never the password), a saved
 * sign-in is re-checked, a refused one is dropped, and a panel that can't be
 * reached keeps it.
 *
 * Exit 0 = holds; 1 = violation.
 */
public class ClientSessionTest {

	static int failures = 0;
	static int checks = 0;

	static void check(boolean ok, String what) {
		checks++;
		if (!ok) {
			System.out.println("FAIL: " + what);
			failures++;
		}
	}

	static final List<String[]> posts = new ArrayList<>();
	static int status = 200;
	static String reply = "{\"ok\":true}";
	static String profile;

	static Map<String, String> data;

	static SettingsStore store() {
		data = new HashMap<>();
		return new SettingsStore() {
			public String get(String k) {
				return data.get(k);
			}

			public void set(String k, String v) {
				if (v == null) {
					data.remove(k);
				} else {
					data.put(k, v);
				}
			}
		};
	}

	static void start(SettingsStore s) {
		posts.clear();
		ClientSession.setup((url, json, done) -> {
			posts.add(new String[] { url, json });
			done.done(status, reply);
		}, s, n -> profile = n);
	}

	public static void main(String[] args) {
		// fresh client: locked out, nothing sent
		start(store());
		check(!ClientSession.signedIn(), "fresh client is signed out");
		check(posts.isEmpty(), "nothing sent without a saved sign-in");

		// bad input never reaches the panel
		ClientSession.signIn("a", "password123");
		check(posts.isEmpty() && !ClientSession.messageOk, "short name refused locally");
		ClientSession.register("Pleb", "short");
		check(posts.isEmpty() && !ClientSession.signedIn(), "short password refused locally");

		// wrong password
		reply = "{\"ok\":false,\"error\":\"Wrong password.\"}";
		ClientSession.signIn("Pleb", "wrongpassword");
		check(posts.size() == 1 && posts.get(0)[0].endsWith("/api/client/login"), "sign-in posts to the panel");
		check(!ClientSession.signedIn() && "Wrong password.".equals(ClientSession.message), "wrong password stays out");
		check(!data.containsKey("account.token"), "no token stored on failure");

		// create account
		reply = "{\"ok\":true,\"name\":\"Pleb\",\"token\":\"tok1\",\"role\":\"player\"}";
		ClientSession.register("pleb", "hunter2hunter2");
		check(posts.get(posts.size() - 1)[0].endsWith("/api/client/register"), "create account posts to register");
		check(ClientSession.signedIn(), "signed in after creating an account");
		check("Pleb".equals(ClientSession.name()) && "Pleb".equals(profile), "plays under the account's name");
		check("tok1".equals(data.get("account.token")), "token stored");
		check(!data.toString().contains("hunter2"), "password never stored");
		check("tok1".equals(ClientSession.tokenFor("pleb")), "token offered for the same name");
		check(ClientSession.tokenFor("Other") == null, "token not offered for another name");

		// restart: saved sign-in is re-checked and kept
		SettingsStore saved = new SettingsStore() {
			final Map<String, String> m = new HashMap<>(data);

			public String get(String k) {
				return m.get(k);
			}

			public void set(String k, String v) {
				if (v == null) {
					m.remove(k);
				} else {
					m.put(k, v);
				}
			}
		};
		reply = "{\"ok\":true,\"role\":\"admin\"}";
		start(saved);
		check(ClientSession.signedIn() && posts.size() == 1 && posts.get(0)[0].endsWith("/api/client/check"),
				"saved sign-in re-checked on start");
		check("admin".equals(ClientSession.role()), "role refreshed from the panel");

		// restart with the panel down: keep the saved sign-in
		status = 0;
		reply = null;
		start(saved);
		check(ClientSession.signedIn(), "panel down keeps a saved sign-in");

		// restart with the token revoked: back to the start screen
		status = 200;
		reply = "{\"ok\":false,\"error\":\"Sign in again.\"}";
		start(saved);
		check(!ClientSession.signedIn(), "revoked token signs out");
		check(saved.get("account.token") == null, "revoked token forgotten");

		// sign out tells the panel
		reply = "{\"ok\":true,\"name\":\"Pleb\",\"token\":\"tok2\"}";
		ClientSession.signIn("Pleb", "hunter2hunter2");
		check(ClientSession.signedIn(), "signed in again");
		ClientSession.signOut();
		check(!ClientSession.signedIn() && posts.get(posts.size() - 1)[0].endsWith("/api/client/logout")
				&& posts.get(posts.size() - 1)[1].contains("tok2"), "sign out revokes the token");

		// Forge: the launcher's name is fixed; only that account can sign in
		ClientSession.fixName("Steve");
		start(store());
		int before = posts.size();
		ClientSession.signIn("Pleb", "hunter2hunter2");
		check(posts.size() == before && !ClientSession.signedIn(), "other name refused when the name is fixed");
		reply = "{\"ok\":true,\"name\":\"Steve\",\"token\":\"tok3\"}";
		ClientSession.signIn("steve", "hunter2hunter2");
		check(ClientSession.signedIn() && "Steve".equals(ClientSession.name()), "fixed name signs in");
		ClientSession.fixName("Alex");
		check(!ClientSession.signedIn(), "saved sign-in for another launcher name is not used");

		System.out.println(checks - failures + "/" + checks + " client sign-in checks passed");
		System.exit(failures == 0 ? 0 : 1);
	}
}
