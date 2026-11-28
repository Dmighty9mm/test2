/*
 * Copyright (c) 2022-2025 lax1dude. All Rights Reserved.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */

package net.lax1dude.eaglercraft.v1_8.socket;

import java.io.IOException;
import java.util.List;

import net.lax1dude.eaglercraft.v1_8.internal.EnumEaglerConnectionState;
import net.lax1dude.eaglercraft.v1_8.internal.IWebSocketClient;
import net.lax1dude.eaglercraft.v1_8.internal.IWebSocketFrame;
import net.lax1dude.eaglercraft.v1_8.netty.ByteBuf;
import net.lax1dude.eaglercraft.v1_8.netty.Unpooled;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

public class WebSocketNetworkManager extends EaglercraftNetworkManager {

	protected final IWebSocketClient webSocketClient;

	public WebSocketNetworkManager(IWebSocketClient webSocketClient) {
		super(webSocketClient.getCurrentURI());
		this.webSocketClient = webSocketClient;
	}

	public void connect() {
	}

	public EnumEaglerConnectionState getConnectStatus() {
		return webSocketClient.getState();
	}

	public void closeChannel(IChatComponent reason) {
		plebDrain = true;
		flushDelayed(); // PlebClient: send what the Ping Equalizer still holds
		webSocketClient.close();
		if(nethandler != null) {
			nethandler.onDisconnect(reason);
		}
		clientDisconnected = true;
	}

	public void processReceivedPackets() throws IOException {
		if(nethandler == null) return;
		if(webSocketClient.availableStringFrames() > 0) {
			logger.warn("discarding {} string frames recieved on a binary connection", webSocketClient.availableStringFrames());
			webSocketClient.clearStringFrames();
		}
		List<IWebSocketFrame> pkts = webSocketClient.getNextBinaryFrames();

		// PlebClient (Ping Equalizer): frames can be held back a little before they
		// are handled, always in the order they came in
		long plebNow = net.lax1dude.eaglercraft.v1_8.EagRuntime.steadyTimeMillis();
		int plebDelay = plebDrain ? 0 : net.plebmc.client.hud.PlebClient.netDelay();
		flushDelayed();
		if(plebDelay <= 0 && plebIn.isEmpty()) {
			if(pkts == null) {
				return;
			}
			for(int i = 0, l = pkts.size(); i < l; ++i) {
				handleFrame(pkts.get(i));
			}
			return;
		}
		if(pkts != null) {
			for(int i = 0, l = pkts.size(); i < l; ++i) {
				plebInDue = Math.max(plebInDue, plebNow + plebDelay);
				plebIn.add(pkts.get(i));
				plebInAt.add(plebInDue);
			}
		}
		while(!plebIn.isEmpty() && (plebDrain || plebInAt.get(0) <= plebNow)) {
			plebInAt.remove(0);
			handleFrame(plebIn.remove(0));
		}
	}

	private final java.util.ArrayList<IWebSocketFrame> plebIn = new java.util.ArrayList<>();
	private final java.util.ArrayList<Long> plebInAt = new java.util.ArrayList<>();
	private long plebInDue;
	private final java.util.ArrayList<byte[]> plebOut = new java.util.ArrayList<>();
	private final java.util.ArrayList<Long> plebOutAt = new java.util.ArrayList<>();
	private long plebOutDue;
	private boolean plebDrain;

	/** PlebClient (Ping Equalizer): sends now, or a little later when a delay is on (order is kept). */
	private void plebSend(byte[] bytes) {
		int delay = net.plebmc.client.hud.PlebClient.netDelay();
		if(delay <= 0 && plebOut.isEmpty()) {
			webSocketClient.send(bytes);
			return;
		}
		long now = net.lax1dude.eaglercraft.v1_8.EagRuntime.steadyTimeMillis();
		plebOutDue = Math.max(plebOutDue, now + delay);
		plebOut.add(bytes);
		plebOutAt.add(plebOutDue);
	}

	/** PlebClient (Ping Equalizer): sends what has waited long enough. Called every frame and every tick. */
	public void flushDelayed() {
		if(plebOut.isEmpty()) {
			return;
		}
		long now = net.lax1dude.eaglercraft.v1_8.EagRuntime.steadyTimeMillis();
		while(!plebOut.isEmpty() && (plebDrain || plebOutAt.get(0) <= now)) {
			plebOutAt.remove(0);
			byte[] b = plebOut.remove(0);
			if(!webSocketClient.isClosed()) {
				webSocketClient.send(b);
			}
		}
	}

	private void handleFrame(IWebSocketFrame next) {
			++debugPacketCounter;
			try {
				byte[] asByteArray = next.getByteArray();
				
				if(injectedController != null && injectedController.handlePacket(asByteArray, 0)) {
					return;
				}
				
				ByteBuf nettyBuffer = Unpooled.buffer(asByteArray, asByteArray.length);
				nettyBuffer.writerIndex(asByteArray.length);
				PacketBuffer input = new PacketBuffer(nettyBuffer);
				int pktId = input.readVarIntFromBuffer();
				
				Packet pkt;
				try {
					pkt = packetState.getPacket(EnumPacketDirection.CLIENTBOUND, pktId);
				}catch(IllegalAccessException | InstantiationException ex) {
					throw new IOException("Recieved a packet with type " + pktId + " which is invalid!");
				}
				
				if(pkt == null) {
					throw new IOException("Recieved packet type " + pktId + " which is undefined in state " + packetState);
				}
				
				try {
					pkt.readPacketData(input);
				}catch(Throwable t) {
					throw new IOException("Failed to read packet type '" + pkt.getClass().getSimpleName() + "'", t);
				}
				
				try {
					pkt.processPacket(nethandler);
				}catch(Throwable t) {
					logger.error("Failed to process {}! It'll be skipped for debug purposes.", pkt.getClass().getSimpleName());
					logger.error(t);
				}
				
			}catch(Throwable t) {
				logger.error("Failed to process websocket frame {}! It'll be skipped for debug purposes.", debugPacketCounter);
				logger.error(t);
			}
	}

	public void sendPacket(Packet pkt) {
		if(!isChannelOpen()) {
			logger.error("Packet was sent on a closed connection: {}", pkt.getClass().getSimpleName());
			return;
		}
		
		int i;
		try {
			i = packetState.getPacketId(EnumPacketDirection.SERVERBOUND, pkt);
		}catch(Throwable t) {
			logger.error("Incorrect packet for state: {}", pkt.getClass().getSimpleName());
			return;
		}
		
		temporaryBuffer.clear();
		temporaryBuffer.writeVarIntToBuffer(i);
		try {
			pkt.writePacketData(temporaryBuffer);
		}catch(IOException ex) {
			logger.error("Failed to write packet {}!", pkt.getClass().getSimpleName());
			return;
		}
		
		int len = temporaryBuffer.writerIndex();
		byte[] bytes = new byte[len];
		temporaryBuffer.getBytes(0, bytes);
		
		plebSend(bytes);
	}

	@Override
	protected boolean sendRawBytes(byte[] bytes) {
		plebSend(bytes);
		return true;
	}

	public boolean checkDisconnected() {
		if(webSocketClient.isClosed()) {
			try {
				plebDrain = true; // PlebClient: nothing held back is lost
				processReceivedPackets(); // catch kick message
			} catch (IOException e) {
			}
			doClientDisconnect(new ChatComponentTranslation("disconnect.endOfStream"));
			return true;
		}else {
			return false;
		}
	}

	@Override
	public void injectRawFrame(byte[] data) {
		if(!isChannelOpen()) {
			logger.error("Frame was injected on a closed connection");
			return;
		}
		plebSend(data);
	}

}