package org.bukkit.plugin.java;
import org.bukkit.command.*;
public abstract class JavaPlugin implements org.bukkit.plugin.Plugin {
    public org.bukkit.configuration.file.FileConfiguration getConfig() { return null; }
    public void saveDefaultConfig() {}
    public void reloadConfig() {}
    public java.io.File getDataFolder() { return null; }
    public java.util.logging.Logger getLogger() { return null; }
    public void onEnable() {}
    public void onDisable() {}
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) { return false; }
    public java.util.List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) { return null; }
}
