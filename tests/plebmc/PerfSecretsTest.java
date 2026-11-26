import net.plebmc.client.mods.ClientPrefs;
import net.plebmc.client.mods.Secrets;

/** The Video Settings performance options and the easter egg matchers. */
public final class PerfSecretsTest {

	private static int passed, failed;

	private static void check(String name, boolean ok) {
		if (ok) {
			passed++;
		} else {
			failed++;
			System.out.println("FAIL " + name);
		}
	}

	public static void main(String[] args) {
		ClientPrefs d = new ClientPrefs();
		check("video menu on by default", d.videoMenu);
		check("entity distance unlimited by default", d.entityBlocks() == 0);
		check("block entity distance unlimited by default", d.blockEntityBlocks() == 0);
		check("vanilla particle cap by default", d.particleCap() == 4000);
		check("menus at 30 fps by default", d.menuFpsCap(260) == 30);
		check("background cap 30 by default", d.idleFpsCap() == 30);

		ClientPrefs p = new ClientPrefs();
		p.videoMenu = false;
		p.entityDistance = 4;
		p.blockEntityDistance = 2;
		p.maxParticles = 3;
		p.menuFps = 1;
		p.idleFps = 3;
		p.rainbowAccent = true;
		check("first secret is new", p.addSecret("konami"));
		check("second time isn't", !p.addSecret("konami"));
		check("bad id refused", !p.addSecret("a,b"));
		p.addSecret("rain");
		String raw = p.serialize();
		ClientPrefs q = new ClientPrefs();
		q.load(new MemStore(ClientPrefs.STORE_KEY, raw));
		check("video menu round trip", !q.videoMenu);
		check("entity distance round trip", q.entityBlocks() == 32);
		check("block entity round trip", q.blockEntityBlocks() == 32);
		check("particle cap round trip", q.particleCap() == 400);
		check("menu fps 60 caps at the game limit", q.menuFpsCap(260) == 60 && q.menuFpsCap(40) == 40);
		check("idle fps round trip", q.idleFpsCap() == 5);
		check("rainbow round trip", q.rainbowAccent);
		check("secrets round trip", q.hasSecret("konami") && q.hasSecret("rain") && q.secretCount() == 2);
		check("no partial secret match", !q.hasSecret("rai"));
		q.menuFps = 2;
		check("menu fps same as game", q.menuFpsCap(144) == 144);
		q.reset();
		check("reset clears performance options", q.entityBlocks() == 0 && q.particleCap() == 4000 && q.videoMenu);
		check("reset keeps found secrets", q.secretCount() == 2);

		ClientPrefs bad = new ClientPrefs();
		bad.load(new MemStore(ClientPrefs.STORE_KEY,
				"{\"entityDistance\":99,\"maxParticles\":-4,\"secrets\":\"<script>\",\"idleFps\":\"x\"}"));
		check("out of range entity distance clamps", bad.entityBlocks() == 32);
		check("negative particle cap clamps", bad.particleCap() == 4000);
		check("junk secrets ignored", bad.secretCount() == 0);
		check("junk idle fps keeps default", bad.idleFpsCap() == 30);

		Secrets s = new Secrets();
		boolean done = false;
		for (int k : Secrets.KONAMI) {
			done = s.konami(k);
		}
		check("konami completes", done);
		int[] extraUp = { 200, 200, 200, 208, 208, 203, 205, 203, 205, 48, 30 };
		done = false;
		for (int k : extraUp) {
			done = s.konami(k);
		}
		check("konami with a third up", done);
		done = false;
		for (int k : new int[] { 200, 200, 208, 208, 203, 205, 203, 205, 30, 48 }) {
			done = s.konami(k);
		}
		check("konami wrong order fails", !done);
		String got = null;
		for (char c : "hello pleb".toCharArray()) {
			String id = s.letter(c);
			if (id != null) {
				got = id;
			}
		}
		check("typed pleb", "rain".equals(got));
		got = null;
		for (char c : "Do a Barrel Roll".toCharArray()) {
			String id = s.letter(c);
			if (id != null) {
				got = id;
			}
		}
		check("typed barrel roll with spaces and capitals", "roll".equals(got));
		got = null;
		for (char c : "Dinnerbone".toCharArray()) {
			String id = s.letter(c);
			if (id != null) {
				got = id;
			}
		}
		check("typed dinnerbone", "dinnerbone".equals(got));
		check("ids and hints line up", Secrets.IDS.length == Secrets.HINTS.length);
		check("16 secrets, no golden", Secrets.IDS.length == 16 && !java.util.Arrays.asList(Secrets.IDS).contains("golden"));
		got = null;
		for (char c : "hello".toCharArray()) {
			String id = s.letter(c);
			if (id != null) {
				got = id;
			}
		}
		check("typed hello", "hello".equals(got));
		check("mod words", "snow".equals(Secrets.modWord(" Let It Snow ")) && "buddy".equals(Secrets.modWord("bestfriend"))
				&& "bighead".equals(Secrets.modWord("BIGHEAD")) && Secrets.modWord("fps") == null);
		check("night owl", Secrets.nightOwl(3) && !Secrets.nightOwl(5) && !Secrets.nightOwl(23));
		boolean every = true;
		for (String[] w : Secrets.MOD_WORDS) {
			every &= java.util.Arrays.asList(Secrets.IDS).contains(w[1]);
		}
		for (String[] w : Secrets.WORDS) {
			every &= java.util.Arrays.asList(Secrets.IDS).contains(w[1]);
		}
		check("every word finds a listed secret", every);

		check("thanksgiving 2026", "Happy Thanksgiving!".equals(Secrets.holiday(11, 26, 5)[0]));
		check("not thanksgiving on the third thursday", Secrets.holiday(11, 19, 5) == null);
		check("christmas", Secrets.holiday(12, 25, 6) != null);
		check("halloween", Secrets.holiday(10, 31, 7) != null);
		check("ordinary day", Secrets.holiday(3, 10, 3) == null);

		check("a 1.8 bow matches crossbow gun rules",
				net.plebmc.client.items.VanillaNames.modernNames("bow").contains("crossbow"));
		check("small caps are kept for their own glyphs",
				"\u0280\u1D00\u0274\u1D0B: Owner".equals(net.plebmc.client.text.ModernText.fold("\u0280\u1D00\u0274\u1D0B: Owner")));
		check("glyphs", net.plebmc.client.text.ModernText.glyph('\u0280') == "ABCDEFGHIJKLMNOPQRSTUVWYZ".indexOf('R')
				&& net.plebmc.client.text.ModernText.glyph('\uA731') == "ABCDEFGHIJKLMNOPQRSTUVWYZ".indexOf('S')
				&& net.plebmc.client.text.ModernText.glyph('\u0455') == "ABCDEFGHIJKLMNOPQRSTUVWYZ".indexOf('S')
				&& net.plebmc.client.text.ModernText.glyph('a') == -1 && net.plebmc.client.text.ModernText.glyph('\u00e9') == -1);
		boolean shapes = true;
		for (int g = 0; g < net.plebmc.client.text.ModernText.glyphCount(); g++) {
			int w = net.plebmc.client.text.ModernText.glyphWidth(g);
			boolean any = false;
			for (int y = 0; y < 5; y++) {
				for (int x = 0; x < w; x++) {
					any |= net.plebmc.client.text.ModernText.glyphPixel(g, x, y);
				}
			}
			shapes &= any && w >= 3 && w <= 5;
		}
		check("every glyph has pixels", shapes);
		String plain = "plain text \u00a7aok";
		check("plain text is the same string", net.plebmc.client.text.ModernText.fold(plain) == plain);
		String rgb = "\u00a7\uE123hi\u2019";
		check("rgb codes pass through", ("\u00a7\uE123hi'").equals(net.plebmc.client.text.ModernText.fold(rgb)));
		check("char fold", net.plebmc.client.text.ModernText.fold('\u2014') == '-' && net.plebmc.client.text.ModernText.fold('a') == 'a');

		System.out.println(passed + " passed, " + failed + " failed");
		if (failed > 0) {
			System.exit(1);
		}
	}

	private static final class MemStore implements net.plebmc.client.mods.SettingsStore {
		private final java.util.Map<String, String> m = new java.util.HashMap<>();

		MemStore(String k, String v) {
			m.put(k, v);
		}

		@Override
		public String get(String key) {
			return m.get(key);
		}

		@Override
		public void set(String key, String value) {
			m.put(key, value);
		}
	}
}
