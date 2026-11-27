package net.plebmc.shop;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.logging.Logger;

import net.lax1dude.eaglercraft.v1_8.plugin.gateway_bungeecord.api.event.EaglercraftHandleAuthPasswordEvent;
import net.lax1dude.eaglercraft.v1_8.plugin.gateway_bungeecord.api.event.EaglercraftIsAuthRequiredEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

/**
 * Asks Eaglercraft players on a protected staff name for its password, with
 * the client's own password screen, before the proxy lets them in.
 * <p>
 * Needs EaglerXBungee's auth hooks on: in {@code plugins/EaglercraftXBungee/authservice.yml}
 * set {@code enable_authentication_system: true} and
 * {@code use_onboard_eaglerx_system: false}. Everyone else joins as before
 * (no password); only protected names get the password screen.
 * <p>
 * Only loaded when EaglerXBungee is installed (it references its classes).
 */
final class EaglerStaffAuth implements Listener {

	private final StaffLock lock;
	private final Logger log;
	private final SecureRandom random = new SecureRandom();

	EaglerStaffAuth(StaffLock lock, Logger log) {
		this.lock = lock;
		this.log = log;
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onAuthRequired(EaglercraftIsAuthRequiredEvent e) {
		String name = new String(e.getAuthUsername(), StandardCharsets.US_ASCII);
		StaffLock.Entry entry = lock.get(name);
		if (entry == null) {
			// not ours: leave it to any other auth plugin, else let them in without a password
			if (e.getAuthRequired() == null) {
				e.setAuthRequired(EaglercraftIsAuthRequiredEvent.AuthResponse.SKIP);
			}
			if (e.getUseAuthType() == null) {
				e.setUseAuthMethod(EaglercraftIsAuthRequiredEvent.AuthMethod.EAGLER_SHA256);
				byte[] salt = new byte[64];
				random.nextBytes(salt);
				e.setSaltingData(salt);
			}
			return;
		}
		String address = e.getRemoteAddress() == null ? null : e.getRemoteAddress().getHostAddress();
		if (!entry.hasPassword()) {
			e.kickUser("This staff name is locked until the server owner sets its password.");
			log.warning("Refused " + name + " from " + address + ": protected name without a password (proxy console: pleb staff set "
					+ entry.name + " <password>)");
			return;
		}
		if (address != null && lock.throttled(address)) {
			e.kickUser("Too many wrong passwords. Try again in a few minutes.");
			return;
		}
		e.setAuthAttachment(new Pending(entry.name));
		e.setAuthRequired(EaglercraftIsAuthRequiredEvent.AuthResponse.REQUIRE);
		e.setUseAuthMethod(EaglercraftIsAuthRequiredEvent.AuthMethod.EAGLER_SHA256);
		e.setAuthMessage("Staff account. Enter the password for " + entry.name + ":");
		e.setSaltingData(lock.challenge(entry.name));
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPassword(EaglercraftHandleAuthPasswordEvent e) {
		Object att = e.getAuthAttachment();
		String name = new String(e.getAuthUsername(), StandardCharsets.US_ASCII);
		if (!(att instanceof Pending)) {
			// a normal player whose client sent a saved password anyway
			if (!lock.isProtected(name) && e.getLoginAllowed() == null) {
				e.setLoginAllowed();
			} else if (lock.isProtected(name)) {
				e.setLoginDenied("This name needs its staff password.");
			}
			return;
		}
		String canonical = ((Pending) att).name;
		String address = e.getRemoteAddress() == null ? null : e.getRemoteAddress().getHostAddress();
		if (lock.check(canonical, address, e.getAuthSaltingData(), e.getAuthPasswordDataResponse())) {
			e.setLoginAllowed();
			// log in with the protected spelling, so "owner" becomes "Owner" with Owner's UUID
			e.setProfileUsername(canonical);
			e.setProfileUUID(StaffLock.offlineUuid(canonical));
			log.info("Staff login: " + canonical + " from " + address);
		} else {
			e.setLoginDenied(address != null && lock.throttled(address) ? "Too many wrong passwords. Try again in a few minutes."
					: "Wrong password.");
			log.warning("Wrong staff password for " + canonical + " from " + address);
		}
	}

	/** Marks a connection that was asked for a staff password. */
	private static final class Pending {
		final String name;

		Pending(String name) {
			this.name = name;
		}
	}
}
