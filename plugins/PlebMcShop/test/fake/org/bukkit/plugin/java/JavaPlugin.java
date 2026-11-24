package org.bukkit.plugin.java;
import org.bukkit.command.*;
import java.io.*;
import java.nio.file.*;
public abstract class JavaPlugin implements org.bukkit.plugin.Plugin {
    public static File folder;
    private org.bukkit.configuration.file.YamlConfiguration cfg;
    public org.bukkit.configuration.file.FileConfiguration getConfig() { if (cfg == null) reloadConfig(); return cfg; }
    public void saveDefaultConfig() {
        File f = new File(folder, "config.yml");
        if (f.exists()) return;
        folder.mkdirs();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) { Files.copy(in, f.toPath()); } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public void reloadConfig() { cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new File(folder, "config.yml")); }
    public File getDataFolder() { return folder; }
    public java.util.logging.Logger getLogger() { return java.util.logging.Logger.getLogger("PlebMcShop"); }
    public void onEnable() {}
    public void onDisable() {}
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) { return false; }
    public java.util.List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) { return null; }
}
