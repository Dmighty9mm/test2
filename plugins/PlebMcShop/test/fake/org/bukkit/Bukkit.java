package org.bukkit;
import java.util.*;
import java.util.function.*;
import org.bukkit.entity.Player;
/** Test fake: a tiny in-memory server. */
public final class Bukkit {
    public static final List<Player> online = new ArrayList<>();
    public static final List<String> commands = new ArrayList<>();
    public static Consumer<String> commandHandler = c -> {};
    public static final List<Runnable> later = new ArrayList<>();
    public static org.bukkit.plugin.messaging.PluginMessageListener incoming;
    public static Object listener;
    public static Server getServer() { return null; }
    public static Collection<? extends Player> getOnlinePlayers() { return new ArrayList<>(online); }
    public static Player getPlayer(UUID id) { for (Player p : online) if (p.getUniqueId().equals(id)) return p; return null; }
    public static Player getPlayerExact(String n) { for (Player p : online) if (p.getName().equalsIgnoreCase(n)) return p; return null; }
    public static OfflinePlayer getOfflinePlayer(String n) {
        UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + n).getBytes());
        return new OfflinePlayer() { public UUID getUniqueId() { return id; } public String getName() { return n; } };
    }
    public static boolean dispatchCommand(org.bukkit.command.CommandSender s, String c) { commands.add(c); commandHandler.accept(c); return true; }
    public static org.bukkit.command.ConsoleCommandSender getConsoleSender() {
        return new org.bukkit.command.ConsoleCommandSender() {
            public void sendMessage(String m) { System.out.println("[console] " + m); }
            public String getName() { return "CONSOLE"; }
            public boolean hasPermission(String p) { return true; }
        };
    }
    public static org.bukkit.plugin.PluginManager getPluginManager() {
        return new org.bukkit.plugin.PluginManager() {
            public void registerEvents(org.bukkit.event.Listener l, org.bukkit.plugin.Plugin p) { listener = l; }
            public org.bukkit.plugin.Plugin getPlugin(String n) { return null; }
        };
    }
    public static org.bukkit.plugin.messaging.Messenger getMessenger() {
        return new org.bukkit.plugin.messaging.Messenger() {
            public void registerOutgoingPluginChannel(org.bukkit.plugin.Plugin p, String c) {}
            public org.bukkit.plugin.messaging.PluginMessageListenerRegistration registerIncomingPluginChannel(org.bukkit.plugin.Plugin p, String c, org.bukkit.plugin.messaging.PluginMessageListener l) { incoming = l; return null; }
            public void unregisterOutgoingPluginChannel(org.bukkit.plugin.Plugin p) {}
            public void unregisterIncomingPluginChannel(org.bukkit.plugin.Plugin p) {}
        };
    }
    public static org.bukkit.scheduler.BukkitScheduler getScheduler() {
        return new org.bukkit.scheduler.BukkitScheduler() {
            public org.bukkit.scheduler.BukkitTask runTaskLater(org.bukkit.plugin.Plugin p, Runnable r, long d) { later.add(r); return () -> {}; }
            public org.bukkit.scheduler.BukkitTask runTaskTimer(org.bukkit.plugin.Plugin p, Runnable r, long d, long q) { return () -> {}; }
        };
    }
}
