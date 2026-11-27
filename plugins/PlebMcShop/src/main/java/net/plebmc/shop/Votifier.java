package net.plebmc.shop;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.Gson;

/**
 * A small Votifier server, so server lists (eaglercraftservers.com and others)
 * can tell PlebioAPI when someone votes. Speaks both Votifier protocols:
 * v1 (an RSA-encrypted block, the server list needs the public key) and
 * NuVotifier v2 (a signed message, the server list needs the token). The keys
 * live in plugins/PlebioAPI/votifier/ in the same format as NuVotifier, so an
 * existing key pair can be dropped in.
 */
final class Votifier {

	private static final int V2_MAGIC = 0x733A;
	private static final int MAX_V2 = 16 * 1024;

	private final File dir;
	private final Logger log;
	/** Called with (username, service) for each vote, on the listener thread. */
	private final BiConsumer<String, String> onVote;
	private final Gson gson = new Gson();
	private final SecureRandom random = new SecureRandom();

	private KeyPair keys;
	private String token;
	private volatile ServerSocket server;

	Votifier(File dir, Logger log, BiConsumer<String, String> onVote) {
		this.dir = dir;
		this.log = log;
		this.onVote = onVote;
	}

	/** Loads (or makes) the key pair and token. */
	void loadKeys() throws IOException {
		if (!dir.isDirectory() && !dir.mkdirs()) {
			throw new IOException("can't make " + dir);
		}
		File pub = new File(dir, "public.key"), priv = new File(dir, "private.key"), tok = new File(dir, "token.txt");
		try {
			KeyFactory kf = KeyFactory.getInstance("RSA");
			if (pub.isFile() && priv.isFile()) {
				PublicKey pk = kf.generatePublic(new X509EncodedKeySpec(decode(pub)));
				PrivateKey sk = kf.generatePrivate(new PKCS8EncodedKeySpec(decode(priv)));
				keys = new KeyPair(pk, sk);
			} else {
				KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
				g.initialize(2048);
				keys = g.generateKeyPair();
				Files.write(pub.toPath(), Base64.getEncoder().encode(keys.getPublic().getEncoded()));
				Files.write(priv.toPath(), Base64.getEncoder().encode(keys.getPrivate().getEncoded()));
			}
		} catch (java.security.GeneralSecurityException e) {
			throw new IOException("bad Votifier keys in " + dir + ": " + e.getMessage());
		}
		if (tok.isFile()) {
			token = new String(Files.readAllBytes(tok.toPath()), StandardCharsets.UTF_8).trim();
		}
		if (token == null || token.isEmpty()) {
			byte[] b = new byte[20];
			random.nextBytes(b);
			StringBuilder sb = new StringBuilder();
			for (byte x : b) {
				sb.append(Character.forDigit((x >> 4) & 15, 36)).append(Character.forDigit(x & 15, 36));
			}
			token = sb.toString();
			Files.write(tok.toPath(), token.getBytes(StandardCharsets.UTF_8));
		}
	}

	private static byte[] decode(File f) throws IOException {
		String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).replaceAll("\\s", "");
		return Base64.getDecoder().decode(s);
	}

	/** The public key to paste into a server list (one line of base64). */
	String publicKey() {
		return keys == null ? "" : Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
	}

	String token() {
		return token == null ? "" : token;
	}

	int port() {
		ServerSocket s = server;
		return s == null ? -1 : s.getLocalPort();
	}

	/** Starts listening; throws if the port is taken (NuVotifier already running, say). */
	void start(String host, int port) throws IOException {
		stop();
		ServerSocket s = new ServerSocket();
		s.setReuseAddress(true);
		s.bind(new InetSocketAddress(host == null || host.isEmpty() ? "0.0.0.0" : host, port));
		server = s;
		Thread t = new Thread(() -> {
			while (!s.isClosed()) {
				try {
					Socket c = s.accept();
					Thread h = new Thread(() -> handle(c), "PlebioAPI-vote");
					h.setDaemon(true);
					h.start();
				} catch (IOException e) {
					if (!s.isClosed()) {
						log.warning("Votifier: " + e.getMessage());
					}
				}
			}
		}, "PlebioAPI-votifier");
		t.setDaemon(true);
		t.start();
	}

	void stop() {
		ServerSocket s = server;
		server = null;
		if (s != null) {
			try {
				s.close();
			} catch (IOException e) {
				// closing anyway
			}
		}
	}

	private void handle(Socket c) {
		try (Socket sock = c) {
			sock.setSoTimeout(5000);
			String challenge = Long.toString(random.nextLong() & Long.MAX_VALUE, 36);
			OutputStream out = sock.getOutputStream();
			out.write(("VOTIFIER 2 " + challenge + "\n").getBytes(StandardCharsets.UTF_8));
			out.flush();
			DataInputStream in = new DataInputStream(sock.getInputStream());
			byte[] head = new byte[2];
			in.readFully(head);
			if (((head[0] & 0xFF) << 8 | (head[1] & 0xFF)) == V2_MAGIC) {
				v2(in, out, challenge);
			} else {
				byte[] block = new byte[256];
				block[0] = head[0];
				block[1] = head[1];
				in.readFully(block, 2, 254);
				v1(block);
			}
		} catch (Exception e) {
			log.warning("Votifier: a vote from " + c.getInetAddress().getHostAddress() + " was refused: " + e.getMessage());
		}
	}

	/** v1: "VOTE\nservice\nusername\naddress\ntimestamp\n", RSA-encrypted with our public key. */
	void v1(byte[] block) throws Exception {
		Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		rsa.init(Cipher.DECRYPT_MODE, keys.getPrivate());
		String[] lines = new String(rsa.doFinal(block), StandardCharsets.UTF_8).split("\n");
		if (lines.length < 3 || !"VOTE".equals(lines[0].trim())) {
			throw new IOException("not a vote");
		}
		vote(lines[2].trim(), lines[1].trim());
	}

	/** v2: {"payload": "{...}", "signature": base64(HmacSHA256(payload, token))}. */
	private void v2(DataInputStream in, OutputStream out, String challenge) throws Exception {
		int len = in.readUnsignedShort();
		if (len <= 0 || len > MAX_V2) {
			throw new IOException("bad message length " + len);
		}
		byte[] body = new byte[len];
		in.readFully(body);
		Map<String, String> reply = new java.util.LinkedHashMap<>();
		try {
			v2Message(new String(body, StandardCharsets.UTF_8), challenge);
			reply.put("status", "ok");
		} catch (Exception e) {
			reply.put("status", "error");
			reply.put("cause", e.getClass().getSimpleName());
			reply.put("error", String.valueOf(e.getMessage()));
			throw e;
		} finally {
			out.write((gson.toJson(reply) + "\r\n").getBytes(StandardCharsets.UTF_8));
			out.flush();
		}
	}

	void v2Message(String json, String challenge) throws Exception {
		Map<?, ?> msg = gson.fromJson(json, Map.class);
		if (msg == null || !(msg.get("payload") instanceof String) || !(msg.get("signature") instanceof String)) {
			throw new IOException("not a vote");
		}
		String payload = (String) msg.get("payload");
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(token().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		byte[] want = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
		byte[] got = Base64.getDecoder().decode((String) msg.get("signature"));
		if (!MessageDigest.isEqual(want, got)) {
			throw new IOException("wrong signature (check the token)");
		}
		Map<?, ?> vote = gson.fromJson(payload, Map.class);
		if (vote == null || !(vote.get("username") instanceof String)) {
			throw new IOException("no username");
		}
		if (challenge != null && !challenge.equals(vote.get("challenge"))) {
			throw new IOException("wrong challenge");
		}
		vote(((String) vote.get("username")).trim(), String.valueOf(vote.get("serviceName")));
	}

	private void vote(String user, String service) {
		if (!user.matches("[A-Za-z0-9_]{1,16}")) {
			log.warning("Votifier: ignored a vote from " + service + " for an invalid name");
			return;
		}
		onVote.accept(user, service);
	}

	/** For tests: encrypts a v1 vote with this server's public key. */
	byte[] encryptV1(String service, String user) throws Exception {
		Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
		rsa.init(Cipher.ENCRYPT_MODE, keys.getPublic());
		return rsa.doFinal(("VOTE\n" + service + "\n" + user + "\n127.0.0.1\n" + System.currentTimeMillis() + "\n")
				.getBytes(StandardCharsets.UTF_8));
	}
}
