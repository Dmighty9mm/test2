package org.bukkit.scheduler;
public interface BukkitScheduler {
    BukkitTask runTaskLater(org.bukkit.plugin.Plugin p, Runnable r, long delay);
    BukkitTask runTaskTimer(org.bukkit.plugin.Plugin p, Runnable r, long delay, long period);
}
