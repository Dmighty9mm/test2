package org.bukkit.entity;
public interface Player extends org.bukkit.OfflinePlayer, org.bukkit.command.CommandSender {
    void sendPluginMessage(org.bukkit.plugin.Plugin p, String channel, byte[] data);
    java.util.Set<String> getListeningPluginChannels();
    org.bukkit.World getWorld();
    boolean isOnline();
    Player getKiller();
    org.bukkit.GameMode getGameMode();
    void updateInventory();
}
