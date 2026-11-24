package org.bukkit.configuration.file;
import java.util.*;
public class YamlConfiguration extends FileConfiguration {
    public static YamlConfiguration loadConfiguration(java.io.File f) { return null; }
    public Set<String> getKeys(boolean deep) { return null; }
    public boolean contains(String path) { return false; }
    public String getString(String path) { return null; }
    public String getString(String path, String def) { return null; }
    public int getInt(String path, int def) { return 0; }
    public long getLong(String path, long def) { return 0; }
    public boolean getBoolean(String path, boolean def) { return false; }
    public List<String> getStringList(String path) { return null; }
    public org.bukkit.configuration.ConfigurationSection getConfigurationSection(String path) { return null; }
    public void set(String path, Object value) {}
}
