import java.util.HashMap;
import java.util.Map;

import net.plebmc.client.mods.FpsLarp;
import net.plebmc.client.mods.SettingsStore;

/** Fps Larp: the shown number, jitter, caps, text styles, debug lines and saving. */
public class FpsLarpTest {
	static int checks, failures;

	static void check(boolean ok, String what) {
		checks++;
		if (!ok) {
			System.out.println("FAIL: " + what);
			failures++;
		}
	}

	public static void main(String[] a) {
		FpsLarp l = new FpsLarp();
		check(l.shown(60, 0) == 60 && "60 FPS".equals(l.hudText(60, 0)), "off: real number");
		check("60fps | C: 1/2".equals(l.debugLine("60fps | C: 1/2", 60, 0)), "off: debug untouched");
		l.enabled = true;
		l.jitter = 0;
		check(l.shown(60, 0) == 120, "2x by default: " + l.shown(60, 0));
		l.multiplier = 5; // 8x
		l.bonus = 2; // +60
		l.changed();
		check(l.shown(60, 1) == 540, "8x +60: " + l.shown(60, 1));
		l.cap = 3; // 500
		l.changed();
		check(l.shown(60, 2) == 500, "capped at 500");
		l.target = 4; // 360
		l.changed();
		check(l.shown(14, 3) == 360, "locked at 360 even at 14 real fps");
		l.target = 0;
		l.cap = 0;
		l.jitter = 3; // high, 20%
		l.changed();
		int min = Integer.MAX_VALUE, max = 0;
		for (long t = 0; t < 60_000; t += 1000) {
			int s = l.shown(60, t);
			min = Math.min(min, s);
			max = Math.max(max, s);
		}
		check(min >= 432 && max <= 648 && max - min > 40, "jitter wobbles within 20%: " + min + ".." + max);
		check(l.shown(60, 5_000) == l.shown(60, 5_900), "same number within a second (vanilla update speed)");
		check(l.shown(0, 1000) == 60 || l.shown(0, 1000) >= 48, "0 real fps still shows the bonus");
		l.jitter = 0;
		l.changed();
		check("540fps | C: 1/2".equals(l.debugLine("60fps | C: 1/2", 60, 9000)), "debug line: " + l.debugLine("60fps | C: 1/2", 60, 9000));
		check(l.debugLine("60 fps (3 chunk updates)", 60, 9000).startsWith("540 fps (3"), "F3 line");
		check("x: 1".equals(l.debugLine("x: 1", 60, 9000)), "lines without a number untouched");
		l.inDebug = false;
		check(l.debugLine("60fps", 60, 9000).equals("60fps") && l.forDebug(60, 9000) == 60, "debug can stay real");
		l.inHud = false;
		check("60 FPS".equals(l.hudText(60, 9000)), "HUD can stay real");
		l.inHud = true;
		l.format = 1;
		l.color = 1;
		check("§aFPS: 540".equals(l.hudText(60, 9000)), "text style + color: " + l.hudText(60, 9000));
		l.color = 6;
		check(l.hudText(60, 9000).replaceAll("§.", "").equals("FPS: 540") && l.hudText(60, 9000).length() > 16, "rainbow");
		check(l.summary().equals("8x +60, Off jitter"), "summary: " + l.summary());

		Map<String, String> kv = new HashMap<>();
		SettingsStore store = new SettingsStore() {
			public String get(String k) { return kv.get(k); }
			public void set(String k, String v) { kv.put(k, v); }
		};
		l.save(store);
		FpsLarp r = new FpsLarp();
		r.load(store);
		check(r.enabled && r.multiplier == 5 && r.bonus == 2 && r.format == 1 && r.color == 6 && !r.inDebug && r.inHud, "saved and loaded");
		kv.put(FpsLarp.STORE_KEY, "{\"on\":true,\"multiplier\":999,\"cap\":-5}");
		r.load(store);
		check(r.multiplier == FpsLarp.MULTIPLIERS.length - 1 && r.cap == 0, "bad values are clamped");
		kv.put(FpsLarp.STORE_KEY, "not json");
		r.load(store);
		r.reset();
		check(r.multiplier == 1 && r.jitter == 1 && r.inDebug, "reset");
		System.out.println("FpsLarpTest: " + checks + " checks, failures=" + failures);
		if (failures > 0) System.exit(1);
	}
}
