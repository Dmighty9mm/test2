package net.plebmc.forge;

import java.util.concurrent.TimeUnit;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.NetworkManager;

/**
 * The Ping Equalizer's delay on Java Edition: a handler next to the game's
 * packet handler that holds every packet back, in both directions, for the
 * time the mod asks for. It is added once the connection is in play (the
 * login steps change the pipeline and must not be delayed) and keeps packets
 * in order when the delay changes.
 */
public final class PingDelay extends ChannelDuplexHandler {
	private static final String NAME = "plebmc_ping_delay";

	/** Delay each way in ms; written on the game thread every tick, read on the network thread. */
	public static volatile int delayMs;

	private long lastIn;
	private long lastOut;

	private PingDelay() {
	}

	/** Called when the game joins a server. */
	public static void install(NetworkManager nm) {
		try {
			if (nm == null || nm.isLocalChannel()) {
				return;
			}
			Channel ch = nm.channel();
			if (ch == null || ch.pipeline().get(NAME) != null || ch.pipeline().get("packet_handler") == null) {
				return;
			}
			ch.pipeline().addBefore("packet_handler", NAME, new PingDelay());
		} catch (Throwable t) {
			// the game works without it
		}
	}

	/** When the next packet may pass (nanoTime), never before the one ahead of it; 0 = right now. */
	private static long due(long last) {
		long now = System.nanoTime();
		int d = delayMs;
		long at = d <= 0 ? now : now + d * 1000000L;
		if (last != 0 && at - last < 0) {
			at = last;
		}
		return at - now <= 0 ? 0 : at;
	}

	@Override
	public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
		long at = due(lastIn);
		if (at == 0) {
			lastIn = 0;
			ctx.fireChannelRead(msg);
			return;
		}
		lastIn = at;
		ctx.executor().schedule(new Runnable() {
			@Override
			public void run() {
				ctx.fireChannelRead(msg);
			}
		}, at - System.nanoTime(), TimeUnit.NANOSECONDS);
	}

	@Override
	public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) throws Exception {
		long at = due(lastOut);
		if (at == 0) {
			lastOut = 0;
			ctx.write(msg, promise);
			return;
		}
		lastOut = at;
		ctx.executor().schedule(new Runnable() {
			@Override
			public void run() {
				ctx.writeAndFlush(msg, promise);
			}
		}, at - System.nanoTime(), TimeUnit.NANOSECONDS);
	}
}
