package net.plebmc.shop;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.logging.Logger;

import net.lax1dude.eaglercraft.v1_8.plugin.gateway_bungeecord.auth.HashUtils;
import net.lax1dude.eaglercraft.v1_8.plugin.gateway_bungeecord.auth.SHA256Digest;

/** The staff password lock: hashing must match EaglerXBungee and the Eaglercraft client exactly. */
public class StaffLockTest {
    static int checks, failed;
    static void check(boolean ok, String what) { checks++; if (!ok) { failed++; System.out.println("FAIL " + what); } }

    /** The client's answer, computed exactly like ConnectionHandshake.java does (EaglerXBungee's SHA256Digest). */
    static byte[] clientAnswer(String password, byte[] salt) {
        SHA256Digest digest = new SHA256Digest();
        int passLen = password.length();
        digest.update((byte) ((passLen >> 8) & 0xFF));
        digest.update((byte) (passLen & 0xFF));
        for (int i = 0; i < passLen; ++i) {
            char c = password.charAt(i);
            digest.update((byte) ((c >> 8) & 0xFF));
            digest.update((byte) (c & 0xFF));
        }
        digest.update(HashUtils.EAGLER_SHA256_SALT_SAVE, 0, 32);
        byte[] hashed = new byte[32];
        digest.doFinal(hashed, 0);
        digest.reset();
        digest.update(hashed, 0, 32);
        digest.update(salt, 0, 32);
        digest.update(HashUtils.EAGLER_SHA256_SALT_BASE, 0, 32);
        digest.doFinal(hashed, 0);
        digest.reset();
        digest.update(hashed, 0, 32);
        digest.update(salt, 32, 32);
        digest.update(HashUtils.EAGLER_SHA256_SALT_BASE, 0, 32);
        digest.doFinal(hashed, 0);
        return hashed;
    }

    /** EaglerStaffAuth with EaglerXBungee's real event classes, the way its handshake fires them. */
    static void eaglerEvents(File f, Logger log) throws Exception {
        StaffLock lock = new StaffLock(f, log);
        lock.setPassword("Owner", "s3cret-pass");
        EaglerStaffAuth auth = new EaglerStaffAuth(lock, log);
        java.net.InetAddress ip = java.net.InetAddress.getByName("10.0.0.5");
        byte[] name = "Random".getBytes(StandardCharsets.US_ASCII);

        // a normal player: no password, nothing changes for them
        net.lax1dude.eaglercraft.v1_8.plugin.gateway_bungeecord.api.event.EaglercraftIsAuthRequiredEvent req =
                new net.lax1dude.eaglercraft.v1_8.plugin.gateway_bungeecord.api.event.EaglercraftIsAuthRequiredEvent(null, ip, "", false, name, e -> {});
        auth.onAuthRequired(req);
        check(req.getAuthRequired() == net.lax1dude.eaglercraft.v1_8.plugin.gateway_bungeecord.api.event.EaglercraftIsAuthRequiredEvent.AuthResponse.SKIP
                && req.getUseAuthType() != null && !req.shouldKickUser(), "normal players skip the password");

        // someone typing "owner": password screen
        byte[] owner = "owner".getBytes(StandardCharsets.US_ASCII);
        req = new net.lax1dude.eaglercraft.v1_8.plugin.gateway_bungeecord.api.event.EaglercraftIsAuthRequiredEvent(null, ip, "", true, owner, e -> {});
        auth.onAuthRequired(req);
        check(req.getAuthRequired() == net.lax1dude.eaglercraft.v1_8.plugin.gateway_bungeecord.api.event.EaglercraftIsAuthRequiredEvent.AuthResponse.REQUIRE
                && req.getUseAuthType() == net.lax1dude.eaglercraft.v1_8.plugin.gateway_bungeecord.api.event.EaglercraftIsAuthRequiredEvent.AuthMethod.EAGLER_SHA256,
                "staff name gets the password screen");
        for (String pw : new String[] { "letmein123", "s3cret-pass" }) {
            net.lax1dude.eaglercraft.v1_8.plugin.gateway_bungeecord.api.event.EaglercraftHandleAuthPasswordEvent pe =
                    new net.lax1dude.eaglercraft.v1_8.plugin.gateway_bungeecord.api.event.EaglercraftHandleAuthPasswordEvent(null, ip, "", owner,
                            req.getSaltingData(), "owner", java.util.UUID.randomUUID(), clientAnswer(pw, req.getSaltingData()), req.getUseAuthType(),
                            req.getAuthMessage(), req.getAuthAttachment(), "default", e -> {});
            auth.onPassword(pe);
            boolean ok = pe.getLoginAllowed() == net.lax1dude.eaglercraft.v1_8.plugin.gateway_bungeecord.api.event.EaglercraftHandleAuthPasswordEvent.AuthResponse.ALLOW;
            if (pw.startsWith("letmein")) {
                check(!ok && !lock.consumeVerified("Owner", "10.0.0.5"), "wrong password: refused at the handshake");
            } else {
                check(ok && "Owner".contentEquals(pe.getProfileUsername()) && StaffLock.offlineUuid("Owner").equals(pe.getProfileUUID()),
                        "right password: logged in as Owner with Owner's UUID");
                check(lock.consumeVerified("Owner", "10.0.0.5"), "the proxy login then goes through");
            }
        }

        // a protected name with no password yet: kicked at the handshake
        lock.protect("Admin");
        req = new net.lax1dude.eaglercraft.v1_8.plugin.gateway_bungeecord.api.event.EaglercraftIsAuthRequiredEvent(null, ip, "", false,
                "ADMIN".getBytes(StandardCharsets.US_ASCII), e -> {});
        auth.onAuthRequired(req);
        check(req.shouldKickUser(), "locked name kicked");
    }

    static int run(File dir, Logger log) throws Exception {
        check(Arrays.equals(StaffLock.SALT_BASE, HashUtils.EAGLER_SHA256_SALT_BASE)
                && Arrays.equals(StaffLock.SALT_SAVE, HashUtils.EAGLER_SHA256_SALT_SAVE), "salts match EaglerXBungee");

        File f = new File(dir, "staff-lock.yml");
        StaffLock lock = new StaffLock(f, log);
        long[] now = { 1_000_000 };
        lock.clock = () -> now[0];

        check(!lock.isProtected("Dmighty9mm"), "nothing protected at first");
        check(lock.setPassword("Dmighty9mm", "short") != null, "short password refused");
        check(lock.setPassword("bad name!", "longenough1") != null, "bad name refused");
        check(lock.setPassword("Dmighty9mm", "correct horse 9") == null, "password set");
        check(lock.isProtected("dmighty9mm") && lock.isProtected("DMIGHTY9MM"), "protected ignoring case");

        // the real handshake: challenge -> client answer -> check
        byte[] ch = lock.challenge("DMighty9mm");
        check(ch.length == 64, "64-byte challenge");
        byte[] ans = clientAnswer("correct horse 9", ch);
        check(Arrays.equals(ans, StaffLock.answer("correct horse 9", ch)), "our hash matches the client's");
        check(lock.check("Dmighty9mm", "1.2.3.4", ch, ans), "right password accepted");
        check(lock.consumeVerified("Dmighty9mm", "1.2.3.4"), "login after the right password goes through");
        check(!lock.consumeVerified("Dmighty9mm", "1.2.3.4"), "only once");

        // a replayed answer to an old challenge fails against a new one
        byte[] ch2 = lock.challenge("Dmighty9mm");
        check(!lock.check("Dmighty9mm", "6.6.6.6", ch2, ans), "replayed answer refused");
        check(!lock.check("Dmighty9mm", "6.6.6.6", ch2, clientAnswer("wrong password", ch2)), "wrong password refused");
        check(!lock.consumeVerified("Dmighty9mm", "6.6.6.6"), "impostor not let in");

        // verified from one address, someone else logs in from another
        ch = lock.challenge("Dmighty9mm");
        lock.check("Dmighty9mm", "1.2.3.4", ch, clientAnswer("correct horse 9", ch));
        check(!lock.consumeVerified("Dmighty9mm", "6.6.6.6"), "verification is tied to the address");
        // and it expires
        ch = lock.challenge("Dmighty9mm");
        lock.check("Dmighty9mm", "1.2.3.4", ch, clientAnswer("correct horse 9", ch));
        now[0] += StaffLock.VERIFY_WINDOW_MS + 1;
        check(!lock.consumeVerified("Dmighty9mm", "1.2.3.4"), "verification expires");

        // brute force: 5 wrong guesses, then even the right one is refused for a while
        for (int i = 0; i < StaffLock.MAX_FAILS; i++) {
            ch = lock.challenge("Dmighty9mm");
            lock.check("Dmighty9mm", "9.9.9.9", ch, clientAnswer("guess" + i, ch));
        }
        check(lock.throttled("9.9.9.9"), "throttled after " + StaffLock.MAX_FAILS + " wrong passwords");
        ch = lock.challenge("Dmighty9mm");
        check(!lock.check("Dmighty9mm", "9.9.9.9", ch, clientAnswer("correct horse 9", ch)), "throttled address can't log in");
        now[0] += StaffLock.FAIL_WINDOW_MS + 1;
        check(!lock.throttled("9.9.9.9"), "throttle wears off");

        // a protected name with no password can't log in at all
        check(lock.protect("Owner") && !lock.protect("owner"), "protect without a password");
        ch = lock.challenge("Owner");
        check(!lock.get("Owner").hasPassword() && !lock.check("Owner", "1.2.3.4", ch, clientAnswer("", ch)), "locked name refuses everything");

        // saved and read back; the file holds no password
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        check(!text.contains("correct horse"), "password not stored in the file");
        StaffLock again = new StaffLock(f, log);
        check(again.isProtected("dmighty9mm") && again.isProtected("Owner") && !again.get("Owner").hasPassword(), "survives restart");
        ch = again.challenge("Dmighty9mm");
        check(again.check("Dmighty9mm", "1.2.3.4", ch, clientAnswer("correct horse 9", ch)), "password works after restart");
        check(again.remove("OWNER") && !again.isProtected("Owner"), "remove");

        check(StaffLock.offlineUuid("Dmighty9mm").equals(java.util.UUID.nameUUIDFromBytes(
                "OfflinePlayer:Dmighty9mm".getBytes(StandardCharsets.US_ASCII))), "offline UUID matches EaglerXBungee");

        eaglerEvents(new File(dir, "staff-lock-2.yml"), log);

        System.out.println(checks - failed + "/" + checks + " staff lock checks passed");
        return failed;
    }
}
