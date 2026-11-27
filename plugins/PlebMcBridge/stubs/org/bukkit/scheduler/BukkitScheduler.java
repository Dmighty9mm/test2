package org.bukkit.scheduler;
public interface BukkitScheduler {
    BukkitTask runTaskLater(org.bukkit.plugin.Plugin p, Runnable r, long delay);
    BukkitTask runTaskAsynchronously(org.bukkit.plugin.Plugin p, Runnable r);
    BukkitTask runTaskTimer(org.bukkit.plugin.Plugin p, Runnable r, long delay, long period);
}
