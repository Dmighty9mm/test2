package net.plebmc.shop;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Password lock for staff names on an offline-mode (cracked) network.
 * <p>
 * In offline mode the proxy can't tell who someone is: the UUID is made from
 * the name, so anyone who types an Owner's name gets the Owner's UUID, their
 * website link (admin menu), their LuckPerms rank and their BungeeCord
 * permissions. This keeps a list of protected names, each with a password,
 * and the proxy refuses a protected name unless the password was given at
 * login (the Eaglercraft password screen, see {@link EaglerStaffAuth}).
 * <p>
 * Passwords are stored the way EaglerXBungee's own auth system stores them
 * (EAGLER_SHA256), so the Eaglercraft client's built-in password screen works
 * and the password itself never crosses the network: the client answers a
 * random challenge with a hash. Names compare ignoring case, so "owner" can't
 * slip past "Owner".
 * <p>
 * Kept free of BungeeCord and EaglerXBungee classes so it can be tested.
 */
final class StaffLock {

	/** EaglerXBungee's fixed salts (auth/HashUtils.java); the client uses the same ones. */
	static final byte[] SALT_BASE = bytes(117, 43, 1, 112, 75, 3, 188, 61, 121, 31, 34, 181, 234, 31, 247, 72, 12, 168,
			138, 45, 143, 77, 118, 245, 187, 242, 188, 219, 160, 235, 235, 68);
	static final byte[] SALT_SAVE = bytes(49, 25, 39, 38, 253, 85, 70, 245, 71, 150, 253, 206, 4, 26, 198, 249, 145,
			251, 232, 174, 186, 98, 27, 232, 55, 144, 83, 21, 36, 55, 170, 118);

	/** How long a correct password at the Eaglercraft screen counts for the login that follows. */
	static final long VERIFY_WINDOW_MS = 30_000;
	/** Wrong passwords per address before it has to wait. */
	static final int MAX_FAILS = 5;
	static final long FAIL_WINDOW_MS = 10 * 60_000;
	static final int MIN_PASSWORD = 8;

	/** One protected name. hash/salt are null until a password is set (the name is then locked). */
	static final class Entry {
		final String name;
		final byte[] hash;
		final byte[] salt;

		Entry(String name, byte[] hash, byte[] salt) {
			this.name = name;
			this.hash = hash;
			this.salt = salt;
		}

		boolean hasPassword() {
			return hash != null && salt != null;
		}
	}

	private static final class Verified {
		final String address;
		final long at;

		Verified(String address, long at) {
			this.address = address;
			this.at = at;
		}
	}

	private final File file;
	private final Logger log;
	private final SecureRandom random = new SecureRandom();
	private final Map<String, Entry> entries = new LinkedHashMap<>();
	private final Map<String, Verified> verified = new HashMap<>();
	private final Map<String, List<Long>> fails = new HashMap<>();
	/** Clock (tests replace it). */
	java.util.function.LongSupplier clock = System::currentTimeMillis;

	StaffLock(File file, Logger log) {
		this.file = file;
		this.log = log;
		load();
	}

	// ==================== the list ====================

	static String key(String name) {
		return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
	}

	synchronized boolean isProtected(String name) {
		return entries.containsKey(key(name));
	}

	synchronized Entry get(String name) {
		return entries.get(key(name));
	}

	synchronized List<Entry> all() {
		return new ArrayList<>(entries.values());
	}

	/**
	 * Protects a name and sets its password (console only). Returns null when
	 * done, or why not.
	 */
	synchronized String setPassword(String name, String password) {
		if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) {
			return "Names are 3 to 16 letters, digits or _.";
		}
		if (password == null || password.length() < MIN_PASSWORD) {
			return "Use a password of at least " + MIN_PASSWORD + " characters.";
		}
		if (password.length() > 64) {
			return "Use a password of at most 64 characters.";
		}
		byte[] salt = new byte[32];
		random.nextBytes(salt);
		Entry old = entries.get(key(name));
		// keep the spelling it was first protected with unless this is new
		String canonical = old != null ? old.name : name;
		entries.put(key(name), new Entry(canonical, storedHash(password, salt), salt));
		save();
		return null;
	}

	/**
	 * Protects a name without a password (it can't log in until one is set).
	 * Used when a website Owner/staff account shows up on a name nobody
	 * protected yet. True if it was new.
	 */
	synchronized boolean protect(String name) {
		if (name == null || name.isEmpty() || entries.containsKey(key(name))) {
			return false;
		}
		entries.put(key(name), new Entry(name, null, null));
		save();
		return true;
	}

	synchronized boolean remove(String name) {
		if (entries.remove(key(name)) == null) {
			return false;
		}
		verified.remove(key(name));
		save();
		return true;
	}

	// ==================== the Eaglercraft challenge ====================

	/**
	 * The 64 bytes sent to the client: the name's stored salt, then 32 fresh
	 * random bytes. For a name without a password, all random (it is refused
	 * anyway).
	 */
	synchronized byte[] challenge(String name) {
		byte[] out = new byte[64];
		random.nextBytes(out);
		Entry e = entries.get(key(name));
		if (e != null && e.hasPassword()) {
			System.arraycopy(e.salt, 0, out, 0, 32);
		}
		return out;
	}

	/** Too many wrong passwords from this address lately. */
	synchronized boolean throttled(String address) {
		List<Long> f = fails.get(address);
		if (f == null) {
			return false;
		}
		long now = clock.getAsLong();
		f.removeIf(t -> now - t > FAIL_WINDOW_MS);
		return f.size() >= MAX_FAILS;
	}

	/**
	 * Checks the client's answer to {@link #challenge}. On success the name
	 * is verified for the login that follows from this address.
	 */
	synchronized boolean check(String name, String address, byte[] challenge, byte[] response) {
		Entry e = entries.get(key(name));
		if (e == null || !e.hasPassword() || challenge == null || challenge.length != 64 || response == null
				|| response.length != 32 || throttled(address)) {
			return false;
		}
		byte[] expected = sha256(e.hash, slice(challenge, 32, 32), SALT_BASE);
		if (!MessageDigest.isEqual(expected, response)) {
			fails.computeIfAbsent(address, k -> new ArrayList<>()).add(clock.getAsLong());
			return false;
		}
		fails.remove(address);
		verified.put(key(name), new Verified(address, clock.getAsLong()));
		return true;
	}

	/**
	 * Checks a typed password (the in-game /adminlogin command). Wrong tries
	 * count against the name AND the address, since on this network many
	 * Eaglercraft players share one address.
	 */
	synchronized boolean checkPlain(String name, String address, String password) {
		Entry e = entries.get(key(name));
		String nameKey = "name:" + key(name);
		if (e == null || !e.hasPassword() || password == null || throttled(nameKey) || address != null && throttled(address)) {
			return false;
		}
		if (!MessageDigest.isEqual(storedHash(password, e.salt), e.hash)) {
			fails.computeIfAbsent(nameKey, k -> new ArrayList<>()).add(clock.getAsLong());
			if (address != null) {
				fails.computeIfAbsent(address, k -> new ArrayList<>()).add(clock.getAsLong());
			}
			return false;
		}
		fails.remove(nameKey);
		return true;
	}

	/** Too many wrong passwords for this name lately (it stays locked for a while). */
	synchronized boolean nameThrottled(String name) {
		return throttled("name:" + key(name));
	}

	/**
	 * At the proxy's login: true (once) if this name just gave the right
	 * password from this address.
	 */
	synchronized boolean consumeVerified(String name, String address) {
		Verified v = verified.remove(key(name));
		if (v == null) {
			return false;
		}
		if (clock.getAsLong() - v.at > VERIFY_WINDOW_MS) {
			return false;
		}
		return v.address == null || address == null || v.address.equals(address);
	}

	/** Forgets old challenges and failures. */
	synchronized void cleanup() {
		long now = clock.getAsLong();
		verified.values().removeIf(v -> now - v.at > VERIFY_WINDOW_MS);
		for (Iterator<List<Long>> it = fails.values().iterator(); it.hasNext();) {
			List<Long> f = it.next();
			f.removeIf(t -> now - t > FAIL_WINDOW_MS);
			if (f.isEmpty()) {
				it.remove();
			}
		}
	}

	// ==================== hashing (same as EaglerXBungee / the client) ====================

	/** sha256(len, UTF-16 chars, SALT_SAVE): what the client computes from the typed password. */
	static byte[] passwordHash(String password) {
		MessageDigest d = digest();
		int len = password.length();
		d.update((byte) ((len >> 8) & 0xFF));
		d.update((byte) (len & 0xFF));
		for (int i = 0; i < len; ++i) {
			char c = password.charAt(i);
			d.update((byte) ((c >> 8) & 0xFF));
			d.update((byte) (c & 0xFF));
		}
		d.update(SALT_SAVE);
		return d.digest();
	}

	/** What is saved: sha256(passwordHash, salt, SALT_BASE). */
	static byte[] storedHash(String password, byte[] salt) {
		return sha256(passwordHash(password), salt, SALT_BASE);
	}

	/** The client's answer to a challenge (used by tests; the real client does this itself). */
	static byte[] answer(String password, byte[] challenge) {
		byte[] stored = sha256(passwordHash(password), slice(challenge, 0, 32), SALT_BASE);
		return sha256(stored, slice(challenge, 32, 32), SALT_BASE);
	}

	static UUID offlineUuid(String name) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
	}

	private static byte[] sha256(byte[]... parts) {
		MessageDigest d = digest();
		for (byte[] p : parts) {
			d.update(p);
		}
		return d.digest();
	}

	private static MessageDigest digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static byte[] slice(byte[] b, int from, int len) {
		byte[] out = new byte[len];
		System.arraycopy(b, from, out, 0, len);
		return out;
	}

	private static byte[] bytes(int... v) {
		byte[] out = new byte[v.length];
		for (int i = 0; i < v.length; i++) {
			out[i] = (byte) v[i];
		}
		return out;
	}

	// ==================== staff-lock.yml ====================

	private void load() {
		entries.clear();
		if (file == null || !file.exists()) {
			return;
		}
		try (InputStream in = new FileInputStream(file)) {
			Object o = new Yaml().load(new InputStreamReader(in, StandardCharsets.UTF_8));
			if (!(o instanceof Map) || !(((Map<?, ?>) o).get("names") instanceof Map)) {
				return;
			}
			for (Map.Entry<?, ?> e : ((Map<?, ?>) ((Map<?, ?>) o).get("names")).entrySet()) {
				String name = String.valueOf(e.getKey());
				byte[] hash = null;
				byte[] salt = null;
				if (e.getValue() instanceof Map) {
					Map<?, ?> v = (Map<?, ?>) e.getValue();
					hash = b64(v.get("hash"));
					salt = b64(v.get("salt"));
					if (hash != null && hash.length != 32 || salt != null && salt.length != 32) {
						log.warning("staff-lock.yml: bad password entry for " + name + ", it is locked until you set it again");
						hash = salt = null;
					}
				}
				entries.put(key(name), new Entry(name, hash, salt));
			}
		} catch (IOException | RuntimeException ex) {
			log.severe("Could not read " + file + ": " + ex + " (protected names keep whatever was loaded)");
		}
	}

	private void save() {
		if (file == null) {
			return;
		}
		Map<String, Object> names = new LinkedHashMap<>();
		for (Entry e : entries.values()) {
			Map<String, Object> v = new LinkedHashMap<>();
			if (e.hasPassword()) {
				v.put("hash", Base64.getEncoder().encodeToString(e.hash));
				v.put("salt", Base64.getEncoder().encodeToString(e.salt));
			}
			names.put(e.name, v);
		}
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("names", names);
		DumperOptions o = new DumperOptions();
		o.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		try {
			file.getParentFile().mkdirs();
			File tmp = new File(file.getPath() + ".tmp");
			try (Writer w = new OutputStreamWriter(Files.newOutputStream(tmp.toPath()), StandardCharsets.UTF_8)) {
				w.write("# Protected staff names (managed with /pleb staff in the proxy console).\n"
						+ "# Passwords are salted hashes; don't edit them by hand.\n");
				new Yaml(o).dump(root, w);
			}
			Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ex) {
			log.severe("Could not save " + file + ": " + ex);
		}
	}

	private static byte[] b64(Object o) {
		if (!(o instanceof String)) {
			return null;
		}
		try {
			return Base64.getDecoder().decode((String) o);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
