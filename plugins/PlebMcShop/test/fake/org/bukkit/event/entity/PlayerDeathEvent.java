package org.bukkit.event.entity;
public class PlayerDeathEvent extends org.bukkit.event.Event {
    org.bukkit.entity.Player p;
    public PlayerDeathEvent(org.bukkit.entity.Player p) { this.p = p; }
    public org.bukkit.entity.Player getEntity() { return p; }
}
