package net.plebmc.shop;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.Yaml;

/**
 * A read-only view of a YAML map with dotted paths ("quests.reset-hour").
 * Works the same on BungeeCord and in tests (SnakeYAML ships with BungeeCord).
 */
final class Cfg {

	private final Map<String, Object> map;

	Cfg(Map<String, Object> map) {
		this.map = map == null ? new LinkedHashMap<String, Object>() : map;
	}

	static Cfg load(File f) throws IOException {
		try (InputStream in = new FileInputStream(f)) {
			return load(in);
		}
	}

	@SuppressWarnings("unchecked")
	static Cfg load(InputStream in) throws IOException {
		try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			Object o = new Yaml().load(r);
			return new Cfg(o instanceof Map ? stringKeys((Map<Object, Object>) o) : null);
		}
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

	@SuppressWarnings("unchecked")
	private Object raw(String path) {
		Object cur = map;
		for (String part : path.split("\\.")) {
			if (!(cur instanceof Map)) {
				return null;
			}
			cur = ((Map<String, Object>) cur).get(part);
		}
		return cur;
	}

	Set<String> keys() {
		return Collections.unmodifiableSet(map.keySet());
	}

	boolean has(String path) {
		return raw(path) != null;
	}

	String str(String path, String def) {
		Object o = raw(path);
		return o == null || o instanceof Map || o instanceof List ? def : String.valueOf(o);
	}

	long num(String path, long def) {
		Object o = raw(path);
		if (o instanceof Number) {
			return ((Number) o).longValue();
		}
		if (o instanceof String) {
			try {
				return Long.parseLong(((String) o).trim());
			} catch (NumberFormatException e) {
				return def;
			}
		}
		return def;
	}

	double dbl(String path, double def) {
		Object o = raw(path);
		if (o instanceof Number) {
			return ((Number) o).doubleValue();
		}
		if (o instanceof String) {
			try {
				return Double.parseDouble(((String) o).trim());
			} catch (NumberFormatException e) {
				return def;
			}
		}
		return def;
	}

	int integer(String path, int def) {
		return (int) num(path, def);
	}

	boolean bool(String path, boolean def) {
		Object o = raw(path);
		return o instanceof Boolean ? (Boolean) o : def;
	}

	List<String> list(String path) {
		Object o = raw(path);
		List<String> out = new ArrayList<>();
		if (o instanceof List) {
			for (Object x : (List<?>) o) {
				if (x != null) {
					out.add(String.valueOf(x));
				}
			}
		}
		return out;
	}

	@SuppressWarnings("unchecked")
	Cfg section(String path) {
		Object o = raw(path);
		return o instanceof Map ? new Cfg((Map<String, Object>) o) : null;
	}
}
