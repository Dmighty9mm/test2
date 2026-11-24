package org.bukkit.event.player;
public abstract class PlayerChannelEvent extends PlayerEvent {
    public String channel;
    public final String getChannel() { return channel; }
}
