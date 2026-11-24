package org.bukkit.configuration.file;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.ConfigurationSection;
/** Test fake: Map-backed sections with dotted paths, YAML through SnakeYAML. */
public class YamlConfiguration extends FileConfiguration {
    final Map<String, Object> map;
    public YamlConfiguration() { this(new LinkedHashMap<>()); }
    YamlConfiguration(Map<String, Object> m) { map = m; }
    @SuppressWarnings("unchecked")
    public static YamlConfiguration loadConfiguration(File f) {
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            Object o = new org.yaml.snakeyaml.Yaml().load(r);
            return new YamlConfiguration(o instanceof Map ? stringKeys((Map<Object, Object>) o) : new LinkedHashMap<>());
        } catch (IOException e) { return new YamlConfiguration(); }
    }
    @SuppressWarnings("unchecked")
    static Map<String, Object> stringKeys(Map<Object, Object> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> e : in.entrySet()) {
            Object v = e.getValue();
            out.put(String.valueOf(e.getKey()), v instanceof Map ? stringKeys((Map<Object, Object>) v) : v);
        }
        return out;
    }
    public void save(File f) throws IOException {
        org.yaml.snakeyaml.DumperOptions d = new org.yaml.snakeyaml.DumperOptions();
        d.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) { new org.yaml.snakeyaml.Yaml(d).dump(map, w); }
    }
    @SuppressWarnings("unchecked")
    Object raw(String path) {
        Object cur = map;
        for (String part : path.split("\\.")) {
            if (!(cur instanceof Map)) return null;
            cur = ((Map<String, Object>) cur).get(part);
        }
        return cur;
    }
    public Set<String> getKeys(boolean deep) { return new LinkedHashSet<>(map.keySet()); }
    public boolean contains(String p) { return raw(p) != null; }
    public String getString(String p) { return getString(p, null); }
    public String getString(String p, String def) { Object o = raw(p); return o == null || o instanceof Map ? def : String.valueOf(o); }
    public int getInt(String p, int def) { Object o = raw(p); return o instanceof Number ? ((Number) o).intValue() : def; }
    public long getLong(String p, long def) { Object o = raw(p); return o instanceof Number ? ((Number) o).longValue() : def; }
    public boolean getBoolean(String p, boolean def) { Object o = raw(p); return o instanceof Boolean ? (Boolean) o : def; }
    public List<String> getStringList(String p) { Object o = raw(p); List<String> l = new ArrayList<>(); if (o instanceof List) for (Object x : (List<?>) o) l.add(String.valueOf(x)); return l; }
    @SuppressWarnings("unchecked")
    public ConfigurationSection getConfigurationSection(String p) { Object o = raw(p); return o instanceof Map ? new YamlConfiguration((Map<String, Object>) o) : null; }
    @SuppressWarnings("unchecked")
    public void set(String path, Object v) {
        String[] parts = path.split("\\.");
        Map<String, Object> cur = map;
        for (int i = 0; i < parts.length - 1; i++) cur = (Map<String, Object>) cur.computeIfAbsent(parts[i], k -> new LinkedHashMap<>());
        if (v == null) cur.remove(parts[parts.length - 1]); else cur.put(parts[parts.length - 1], v);
    }
}
