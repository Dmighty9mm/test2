package org.bukkit.configuration;
import java.util.*;
public interface ConfigurationSection {
    Set<String> getKeys(boolean deep);
    boolean contains(String path);
    String getString(String path);
    String getString(String path, String def);
    int getInt(String path, int def);
    long getLong(String path, long def);
    boolean getBoolean(String path, boolean def);
    List<String> getStringList(String path);
    ConfigurationSection getConfigurationSection(String path);
    void set(String path, Object value);
}
