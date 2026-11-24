package me.clip.placeholderapi;
import java.util.*;
public class PlaceholderAPI {
    public static final Map<String, String> values = new HashMap<>();
    public static String setPlaceholders(org.bukkit.OfflinePlayer p, String text) {
        return text.replace("%battleroyalex_coins%", values.getOrDefault(p.getName(), ""));
    }
}
