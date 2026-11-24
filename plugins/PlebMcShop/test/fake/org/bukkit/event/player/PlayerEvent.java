package org.bukkit.event.player;
public abstract class PlayerEvent extends org.bukkit.event.Event {
    org.bukkit.entity.Player player;
    public final org.bukkit.entity.Player getPlayer() { return player; }
    public PlayerEvent with(org.bukkit.entity.Player p) { player = p; return this; }
}
