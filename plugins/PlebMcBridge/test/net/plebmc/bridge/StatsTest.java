package net.plebmc.bridge;

import java.util.LinkedHashMap;
import java.util.Map;

/** java -cp build/classes:build/stubs:build/test net.plebmc.bridge.StatsTest */
public final class StatsTest {
	static int fails;

	static void eq(Object want, Object got, String what) {
		if (want == null ? got != null : !want.equals(got)) {
			fails++;
			System.out.println("FAIL " + what + ": want " + want + " got " + got);
		}
	}

	public static void main(String[] a) {
		eq(1234.0, Stats.number("1,234"), "commas");
		eq(7.0, Stats.number("§a7"), "colors");
		eq(12.5, Stats.number("12.5"), "decimal");
		eq(9910.0, Stats.number("0D-2H-45M-10S"), "brx time");
		eq(3720.0, Stats.number("1h 2m"), "short time");
		eq(null, Stats.number(""), "empty");
		eq(null, Stats.number("none"), "no number");
		eq(5.0, Stats.value(null, "100 / 20"), "divide");
		eq(null, Stats.value(null, "%not_here%"), "no papi");
		Map<String, Map<String, Double>> pl = new LinkedHashMap<>();
		Map<String, Double> s = new LinkedHashMap<>();
		s.put("kills", 10.0);
		s.put("kdr", 1.25);
		pl.put("Bob", s);
		Map<String, String> labels = new LinkedHashMap<>();
		labels.put("kills", "Kills \"all\"");
		eq("{\"game\":\"plebnite\",\"labels\":{\"kills\":\"Kills \\\"all\\\"\"},\"players\":[{\"name\":\"Bob\",\"stats\":{\"kills\":10,\"kdr\":1.25}}]}",
				Stats.json("plebnite", labels, pl), "json");
		System.out.println(fails == 0 ? "stats tests passed" : fails + " failed");
		System.exit(fails == 0 ? 0 : 1);
	}
}
