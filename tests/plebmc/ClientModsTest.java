import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.plebmc.client.apollo.Apollo;
import net.plebmc.client.mods.ClickCounter;
import net.plebmc.client.mods.ClientView;
import net.plebmc.client.mods.Gear;
import net.plebmc.client.mods.Mate;
import net.plebmc.client.mods.Storm;
import net.plebmc.client.mods.HudBox;
import net.plebmc.client.mods.Mod;
import net.plebmc.client.mods.ModRegistry;
import net.plebmc.client.mods.Mods;
import net.plebmc.client.mods.Passwords;
import net.plebmc.client.mods.SettingsStore;
import net.plebmc.client.network.PluginChannels;

/**
 * JVM test for the client's own mods: HUD layouts, saved layout/toggle
 * persistence (the HUD editor's storage layer), and - the key rule - that
 * a server's Apollo mod_setting / server_rule overrides force-disable or
 * override the player's mods (zoom, toggle sprint, fullbright).
 *
 * Exit 0 = holds; 1 = violation.
 */
public class ClientModsTest {

        static int failures = 0;
        static int checks = 0;

        static void check(boolean ok, String what) {
                checks++;
                if (!ok) {
                        System.out.println("FAIL: " + what);
                        failures++;
                }
        }

        static final class FakeView implements ClientView {
                boolean w;
                boolean lmb;

                @Override
                public int fps() {
                        return 144;
                }

                @Override
                public double x() {
                        return -12.34;
                }

                @Override
                public double y() {
                        return 64;
                }

                @Override
                public double z() {
                        return 1000.06;
                }

                @Override
                public float yaw() {
                        return 180f;
                }

                @Override
                public int pingMs() {
                        return -1;
                }

                @Override
                public String localTime() {
                        return "13:37";
                }

                @Override
                public boolean keyDown(String key) {
                        return "forward".equals(key) && w;
                }

                @Override
                public boolean mouseDown(int button) {
                        return button == 0 && lmb;
                }

                @Override
                public int cps(int button) {
                        return button == 0 ? 9 : 2;
                }

                List<Gear> armor = new ArrayList<>(Arrays.asList(null, null, null, null));
                Gear held;
                Storm storm;
                List<Mate> squad = new ArrayList<>();
                float health = 20f;
                Map<String, Integer> supplies = new HashMap<>();

                @Override
                public List<Gear> armor() {
                        return armor;
                }

                @Override
                public Gear held() {
                        return held;
                }

                @Override
                public Storm storm() {
                        return storm;
                }

                @Override
                public List<Mate> squad() {
                        return squad;
                }

                @Override
                public float health() {
                        return health;
                }

                @Override
                public int supply(String g) {
                        Integer n = supplies.get(g);
                        return n == null ? 0 : n;
                }

                @Override
                public List<String> effects() {
                        return Collections.emptyList();
                }
        }

        static final HudBox.Measure M = s -> s.length() * 6;

        static void json(String json) {
                PluginChannels.dispatch(Apollo.CHANNEL_JSON, json.getBytes(StandardCharsets.UTF_8));
        }

        static String modSetting(String props) {
                return "{\"@type\":\"type.googleapis.com/lunarclient.apollo.configurable.v1.OverrideConfigurableSettingsMessage\","
                                + "\"configurable_settings\":[{\"apollo_module\":\"mod_setting\",\"enable\":true,\"properties\":" + props
                                + "}]}";
        }

        public static void main(String[] args) {
                PluginChannels.resetAllForTests();
                Apollo.resetAllForTests();
                PluginChannels.Log quiet = new PluginChannels.Log() {
                        @Override
                        public void info(String msg) {
                        }

                        @Override
                        public void warn(String msg) {
                        }
                };
                PluginChannels.setLog(quiet);
                Apollo.setLog(quiet);
                Apollo.init();
                PluginChannels.attach((ch, d) -> true);
                PluginChannels.onJoin();

                ModRegistry reg = new ModRegistry();
                FakeView v = new FakeView();

                // HUD layouts
                HudBox fps = reg.fps.layout(v, M);
                check(fps != null && fps.texts.get(0).text.equals("144 FPS"), "FPS text");
                HudBox coords = reg.coordinates.layout(v, M);
                check(coords.texts.get(0).text.equals("X: -12.3") && coords.texts.get(2).text.equals("Z: 1000.1")
                                && coords.texts.get(3).text.equals("Facing: North (-Z)"), "coordinates + facing");
                reg.coordinates.setting("compact").set(1);
                reg.coordinates.setting("label_x").setText("x ");
                HudBox one = reg.coordinates.layout(v, M);
                check(one.texts.size() == 1 && one.texts.get(0).text.startsWith("x -12.3") && one.texts.get(0).text.contains("Facing: North"),
                                "one line keeps facing on it, with a custom label");
                String saved = reg.serialize();
                reg.coordinates.resetSettings();
                check(reg.coordinates.setting("label_x").text().equals("X: "), "label resets to default");
                MapStore ms = new MapStore();
                ms.set(ModRegistry.STORE_KEY, saved);
                reg.load(ms);
                check(reg.coordinates.setting("label_x").text().equals("x "), "custom label saved and loaded");
                String ex = net.plebmc.client.mods.SettingsTransfer.export(ms);
                MapStore other = new MapStore();
                check(net.plebmc.client.mods.SettingsTransfer.importInto(other, ex) == 1 && other.get(ModRegistry.STORE_KEY) != null,
                                "settings export imports into another client");
                check(net.plebmc.client.mods.SettingsTransfer.importInto(other, "hello") == -1, "random text is not an import");
                // a PlebClient Studio code: only what it names changes
                MapStore st3 = new MapStore();
                st3.set(ModRegistry.STORE_KEY, reg.serialize());
                String studio = "{\"plebclient-settings\":1,\"mods\":{\"crosshair\":{\"on\":true,\"opts\":{\"style\":3,\"color\":4}},"
                                + "\"coordinates\":{\"on\":true,\"opts\":{\"label_x\":\"x \"}}},\"prefs\":{\"menuBackground\":2}}";
                check(net.plebmc.client.mods.SettingsTransfer.importInto(st3, studio) == 2, "studio code imports");
                reg.coordinates.setting("decimals").set(0);
                reg.load(st3);
                check(reg.crosshair.isEnabled() && reg.crosshair.style.get() == 3 && reg.crosshair.color.get() == 4
                                && reg.coordinates.setting("label_x").text().equals("x ") && reg.coordinates.setting("decimals").get() == 0
                                && reg.fps.isEnabled(), "studio code changes only what it names");
                reg.crosshair.setEnabled(false);
                reg.coordinates.resetSettings();
                check(net.plebmc.client.mods.MoreMods.DayCounter.text(0, true).equals("Day 1  6:00")
                                && net.plebmc.client.mods.MoreMods.DayCounter.text(24000L * 2 + 18000, true).equals("Day 3  0:00"),
                                "day counter");
                check(net.plebmc.client.mods.MoreMods.Server.strip("wss://eagler.plebmc.top/").equals("eagler.plebmc.top"), "server address");
                check(net.plebmc.client.mods.MoreMods.SessionTimer.duration(3725).equals("1:02:05")
                                && net.plebmc.client.mods.MoreMods.SessionTimer.duration(65).equals("1:05"), "session timer");
                int[] p1 = reg.chat.place("hi"), p2 = reg.chat.place("hi"), p3 = reg.chat.place("yo");
                check(p1[0] == p2[0] && p2[1] == 2 && p3[0] != p2[0] && p3[1] == 1, "chat stacks repeats on one line");
                check(net.plebmc.client.mods.MoreMods.Chat.stamp(13, 5, 2).equals("1:05 PM")
                                && net.plebmc.client.mods.MoreMods.Chat.stamp(9, 30, 1).equals("09:30"), "chat timestamps");
                reg.speed.sample(0, 64, 0, 1000);
                check(Math.abs(reg.speed.sample(2, 64, 0, 1500) - 4.0) < 1e-6, "speed in blocks per second");
                check(reg.byId("crosshair") != null && reg.byId("scoreboard") != null && reg.byId("hurt-cam") != null, "visual mods registered");
                // ---- speed follows every game tick (20 a second), not a quarter-second window
                reg.speed.sample(0, 64, 0, 0, 100);
                check(Math.abs(reg.speed.sample(0.28, 64, 0, 50, 101) - 5.6) < 1e-6, "speed is the last tick's move");
                check(Math.abs(reg.speed.sample(0.28, 64, 0, 60, 101) - 5.6) < 1e-6, "speed is steady within a tick");
                check(reg.speed.sample(0.28, 64, 0, 100, 102) == 0.0, "speed drops to zero the tick you stop");
                check(Math.abs(reg.speed.sample(0.5, 64, 0, 200, 104) - 2.2) < 1e-6, "skipped ticks are averaged");

                // ---- the server's timer is a HUD mod now: off switch, position, look
                net.plebmc.client.mods.MoreMods.ServerTimer timer = reg.serverTimer;
                check(timer.hasHud() && timer.isEnabled() && timer.centerAnchored() && "stopwatch".equals(timer.id()), "playtime counter is a movable HUD mod");
                check(timer.layout(v, M) == null, "no timer from the server: nothing drawn");
                check(timer.preview(v, M) != null && timer.preview(v, M).texts.get(0).text.equals("12:34.32"), "timer preview for the HUD editor");
                timer.setting("hundredths").set(0);
                check(timer.text(754320).equals("12:34"), "timer without hundredths");
                timer.resetSettings();
                Apollo.preview("{\"@type\":\"type.googleapis.com/lunarclient.apollo.stopwatch.v1.StartStopwatchMessage\"}");
                check(timer.layout(v, M) != null, "a running server timer is drawn");
                timer.setEnabled(false);
                check(!timer.isActive(), "the timer can be turned off");
                timer.setEnabled(true);
                Apollo.preview("{\"@type\":\"type.googleapis.com/lunarclient.apollo.stopwatch.v1.ResetStopwatchMessage\"}");

                // ---- password hider
                check(Passwords.mask("/login hunter2").equals("/login *******"), "login password is starred");
                check(Passwords.mask("/l hunter2").equals("/l *******") && Passwords.mask("/LOGIN Hunter2").equals("/LOGIN *******"), "/l and upper case too");
                check(Passwords.mask("/register abc abc").equals("/register *** ***") && Passwords.mask("/reg abc abc").equals("/reg *** ***"), "register passwords are starred");
                check(Passwords.mask("/login").equals("/login") && Passwords.mask("/login ").equals("/login "), "nothing to hide yet");
                check(Passwords.mask("/loginx secret").equals("/loginx secret") && Passwords.mask("/msg Bob /login x").equals("/msg Bob /login x")
                                && Passwords.mask("hello /login x").equals("hello /login x") && Passwords.mask("/list all").equals("/list all"), "other text is left alone");
                check(Passwords.mask("/login a b").length() == "/login a b".length(), "length is kept (the cursor stays put)");
                check(Passwords.isSecret("/l pw") && !Passwords.isSecret("/l") && !Passwords.isSecret("/l  ") && !Passwords.isSecret("/help pw"), "what counts as a secret");
                check(reg.passwordHider.isEnabled() && reg.passwordHider.skipHistory(), "password hider is on by default");

                // ---- password manager
                Passwords.forgetAll();
                check(Passwords.complete("/login", "play.plebmc.top") == null, "nothing saved: nothing to fill in");
                check(!Passwords.sent("hello", "play.plebmc.top", 1000) && !Passwords.sent("/login", "play.plebmc.top", 1000), "no offer for other messages");
                check(Passwords.sent("/login hunter2", "Play.PlebMc.top", 1000) && "play.plebmc.top".equals(Passwords.pendingServer(2000)), "offers to save after /login");
                check(Passwords.saved("play.plebmc.top") == null, "not saved until you say so");
                check("play.plebmc.top".equals(Passwords.savePending(3000)) && "hunter2".equals(Passwords.saved("PLAY.plebmc.top")), "saved per server");
                check(!Passwords.sent("/login hunter2", "play.plebmc.top", 4000), "no offer for the password already saved");
                check("/login hunter2".equals(Passwords.complete("/login", "play.plebmc.top")) && "/l hunter2".equals(Passwords.complete("/l ", "play.plebmc.top")),
                                "Tab completes /login and /l");
                check("/register hunter2 hunter2".equals(Passwords.complete("/register", "play.plebmc.top"))
                                && "/reg hunter2 hunter2".equals(Passwords.complete("/reg", "play.plebmc.top")), "Tab completes /register and /reg");
                check(Passwords.complete("/login hun", "play.plebmc.top") == null && Passwords.complete("/log", "play.plebmc.top") == null
                                && Passwords.complete("/login", "other.server") == null, "only the bare command on the right server");
                check(Passwords.sent("/register newpass newpass", "other.server", 5000) && Passwords.pendingServer(5000 + 121000) == null, "an unanswered offer runs out");
                check(!Passwords.sent("/register one two", "other.server", 5000), "mismatched register passwords aren't offered");
                MapStore pws = new MapStore();
                Passwords.save(pws);
                check(!pws.get(Passwords.STORE_KEY).contains("hunter2"), "not stored as readable text");
                Passwords.forgetAll();
                Passwords.load(pws);
                check("hunter2".equals(Passwords.saved("play.plebmc.top")) && Passwords.count() == 1, "passwords survive a restart");
                check(Passwords.forget("play.plebmc.top") && Passwords.saved("play.plebmc.top") == null, "forget");
                MapStore exp = new MapStore();
                reg.save(exp);
                exp.set(Passwords.STORE_KEY, "{\"a\":\"b\"}");
                check(!net.plebmc.client.mods.SettingsTransfer.export(exp).contains(Passwords.STORE_KEY), "passwords are never in a settings export");

                // ---- ping equalizer
                net.plebmc.client.mods.MoreMods.PingEqualizer eq = reg.pingEqualizer;
                check(!eq.isEnabled() && eq.delayEachWay() == 0, "ping equalizer is off by default and adds nothing");
                eq.setEnabled(true);
                eq.setting("target").set(2); // 100 ms
                // a server whose reported ping follows the real one slowly (like the tab list)
                double real = 30, shown = 30;
                long t = 100000;
                for (int i = 0; i < 240; i++) {
                        eq.update((int) Math.round(shown), t);
                        shown += (real + eq.addedMs() - shown) * 0.12;
                        t += 1000;
                }
                check(Math.abs(shown - 100) <= 3 && Math.abs(eq.addedMs() - 70) <= 3, "30 ms is brought up to 100: " + shown + " / " + eq.addedMs());
                check(eq.delayEachWay() * 2 >= eq.addedMs() - 1 && eq.delayEachWay() * 2 <= eq.addedMs() + 1, "half the delay each way");
                real = 180;
                for (int i = 0; i < 240; i++) {
                        eq.update((int) Math.round(shown), t);
                        shown += (real + eq.addedMs() - shown) * 0.12;
                        t += 1000;
                }
                check(eq.addedMs() == 0, "a ping above the target is never lowered or raised: " + eq.addedMs());
                eq.reset();
                eq.update(0, t);
                check(eq.addedMs() == 0, "a ping of 0 right after joining is not trusted");
                eq.update(-1, t + 60000);
                check(eq.addedMs() == 0, "unknown ping: no delay");
                eq.setEnabled(false);
                eq.update(20, t + 120000);
                check(eq.delayEachWay() == 0, "off: no delay");
                reg.coordinates.resetSettings();
                check(Mods.Coordinates.facing(0).startsWith("South") && Mods.Coordinates.facing(90).startsWith("West")
                                && Mods.Coordinates.facing(-90).startsWith("East") && Mods.Coordinates.facing(44).startsWith("South"),
                                "vanilla compass directions");
                check(reg.cps.layout(v, M).texts.get(0).text.equals("9 | 2 CPS"), "CPS text");
                check(reg.ping.layout(v, M).texts.get(0).text.equals("Ping: n/a"), "unknown ping shows n/a");
                v.w = true;
                v.lmb = true;
                HudBox keys = reg.keystrokes.layout(v, M);
                check(keys.rects.get(0).argb == HudBox.BG_PRESSED && keys.rects.get(1).argb == HudBox.BG,
                                "keystrokes: W pressed, A not");
                check(keys.texts.get(4).text.equals("LMB") && keys.texts.get(5).text.equals("9")
                                && keys.texts.get(5).argb == HudBox.TEXT_PRESSED && keys.texts.get(5).y > keys.texts.get(4).y,
                                "LMB label above its CPS");
                check(reg.armor.layout(v, M) == null && reg.armor.preview(v, M) != null,
                                "armor hides with no armor but previews in the editor");
                check(reg.armor.preview(v, M).items.size() == 5 && reg.armor.preview(v, M).items.get(0).id.equals("diamond_helmet"),
                                "armor preview draws item icons, held item included");
                reg.armor.setEnabled(true);
                v.armor.set(0, new Gear(null, "iron_helmet", "Iron Helmet", 1, 30, 165));
                v.armor.set(3, new Gear(null, "diamond_boots", "Diamond Boots", 1, 429, 429));
                HudBox ab = reg.armor.layout(v, M);
                check(ab.items.size() == 2 && ab.items.get(0).id.equals("iron_helmet") && ab.items.get(1).id.equals("diamond_boots"),
                                "armor shows worn pieces as icons, head first, empty slots skipped");
                check(ab.texts.get(0).text.equals("30") && ab.texts.get(0).argb == 0xFFFFAA00
                                && ab.texts.get(1).argb == 0xFF55FF55, "durability colored by wear");
                reg.armor.setting("durability").set(1);
                check(reg.armor.layout(v, M).texts.get(0).text.equals("18%"), "durability as percent");
                reg.armor.setting("durability").set(2);
                check(reg.armor.layout(v, M).texts.isEmpty(), "durability text hidden");
                reg.armor.setting("layout").set(1);
                HudBox row = reg.armor.layout(v, M);
                check(row.items.get(1).x > row.items.get(0).x && row.items.get(1).y == row.items.get(0).y, "horizontal armor row");
                v.held = new Gear(null, "arrow", "Arrow", 32, 0, 0);
                check(reg.armor.layout(v, M).items.size() == 3, "held item added");
                reg.armor.resetSettings();
                check(reg.armor.layout(v, M).texts.get(2).text.equals("32"), "held stack shows its count");

                // PlebNite mods
                check(Mod.PLEBNITE.equals(reg.compass.category()) && Mod.PLEBNITE.equals(reg.squadPing.category())
                                && Mod.PLEBNITE.equals(reg.storm.category()) && Mod.PLEBNITE.equals(reg.squad.category())
                                && Mod.PLEBNITE.equals(reg.supplies.category()) && Mod.PLEBNITE.equals(reg.lowHealth.category()),
                                "PlebNite category holds the PlebNite mods");
                check(reg.storm.layout(v, M) == null && reg.storm.preview(v, M) != null, "storm hidden without a border");
                v.storm = new Storm(0, 0, 200, 100, 65_000, 42.7);
                HudBox sb = reg.storm.layout(v, M);
                check(sb.texts.get(0).text.equals("Storm 42m away") && sb.texts.get(1).text.equals("Closing  1:05"),
                                "storm distance and closing timer: " + sb.texts.get(0).text);
                v.storm = new Storm(0, 0, 200, 200, 0, -7.2);
                sb = reg.storm.layout(v, M);
                check(sb.texts.get(0).text.startsWith("IN THE STORM  8m") && sb.texts.get(0).argb == 0xFFFF5555
                                && sb.texts.get(1).text.equals("Holding"), "outside the storm warns in red");
                v.storm = new Storm(0, 0, Storm.NO_STORM * 30, Storm.NO_STORM * 30, 0, 1e6);
                check(reg.storm.layout(v, M) == null, "vanilla default border is not a storm");
                // compass points to the safe zone center
                v.storm = new Storm(-12.34, 900, 200, 200, 0, 20); // due north of the player, who faces north
                HudBox cb = reg.compass.layout(v, M);
                boolean centerSquare = false;
                for (HudBox.Rect r : cb.rects) {
                        centerSquare |= r.argb == 0xFFFFFFFF && r.w == 5 && r.h == 1 && Math.abs(r.x + 2 - Mods.Compass.WIDTH / 2) <= 1;
                }
                check(centerSquare, "compass safe-zone square straight ahead");
                v.storm = null;

                check(reg.squad.layout(v, M) == null && reg.squad.preview(v, M) != null, "squad hidden without a team");
                v.squad.add(new Mate("Bob", 0xFF55FFFF, 12, 10f, 20f, 0f));
                v.squad.add(new Mate("Alice", 0xFFFF5555, -1, -1f, 20f, Float.NaN));
                HudBox sq = reg.squad.layout(v, M);
                check(sq.texts.size() == 3 && sq.texts.get(0).text.equals("Bob") && sq.texts.get(1).text.equals("12m")
                                && sq.texts.get(2).text.equals("Alice"), "squad names and distance");
                boolean halfBar = false;
                for (HudBox.Rect r : sq.rects) {
                        halfBar |= r.w == 14 && r.argb == 0xFFFFFF55;
                }
                check(halfBar, "squad health bar at half health is yellow");

                reg.supplies.setEnabled(true);
                check(reg.supplies.layout(v, M) == null, "supplies hidden when you carry none");
                v.supplies.put("blocks", 64);
                v.supplies.put("pearls", 2);
                HudBox su = reg.supplies.layout(v, M);
                check(su.items.size() == 2 && su.items.get(0).id.equals("planks") && su.texts.get(0).text.equals("64")
                                && su.items.get(1).id.equals("ender_pearl"), "supplies count blocks and pearls");
                reg.supplies.setting("empty").set(1);
                check(reg.supplies.layout(v, M).items.size() == Mods.Supplies.GROUPS.length, "show empty supplies");

                check(reg.lowHealth.intensity(20f, 0) == 0f && reg.lowHealth.intensity(4f, 0) > 0f
                                && reg.lowHealth.intensity(0f, 0) == 0f, "low health warning only when low (and alive)");
                reg.lowHealth.setting("pulse").set(0);
                check(reg.lowHealth.intensity(1f, 0) > reg.lowHealth.intensity(7f, 0), "stronger the lower you are");
                reg.lowHealth.resetSettings();
                reg.armor.setEnabled(false);
                reg.supplies.setEnabled(false);
                reg.supplies.resetSettings();

                // persistence (HUD editor storage layer)
                Map<String, String> mem = new HashMap<>();
                SettingsStore store = new SettingsStore() {
                        @Override
                        public String get(String key) {
                                return mem.get(key);
                        }

                        @Override
                        public void set(String key, String value) {
                                mem.put(key, value);
                        }
                };
                reg.fps.setPosition(0.5f, 0.25f);
                reg.zoom.setEnabled(false);
                reg.clock.setEnabled(true);
                reg.save(store);
                ModRegistry again = new ModRegistry();
                again.load(store);
                check(again.fps.fx() == 0.5f && again.fps.fy() == 0.25f, "HUD position persists: " + mem.get("mods"));
                check(!again.zoom.isEnabled() && again.clock.isEnabled(), "toggles persist");
                again.fps.setPosition(4f, -2f);
                check(again.fps.fx() == 1f && again.fps.fy() == 0f, "positions clamp to the screen");
                mem.put("mods", "{\"fps\":{\"on\":\"yes\",\"x\":\"bad\"},\"nope\":1,\"cps\":[1]");
                ModRegistry corrupt = new ModRegistry();
                corrupt.load(store);
                check(corrupt.fps.isEnabled() && corrupt.cps.isEnabled(), "corrupt settings keep defaults");
                mem.put("mods", "{\"fps\":{\"on\":false,\"x\":\"bad\",\"y\":0.5}}");
                corrupt.load(store);
                check(!corrupt.fps.isEnabled() && corrupt.fps.fy() == 0.5f, "partially bad entry applies the good fields");
                int[] p = ModRegistry.place(reg.fps, 60, 12, 320, 240);
                check(p[0] == 160 && p[1] == 60, "place() maps fractions to pixels");
                reg.fps.setPosition(1f, 1f);
                p = ModRegistry.place(reg.fps, 60, 12, 320, 240);
                check(p[0] == 260 && p[1] == 228, "place() keeps the box on screen");

                // server overrides
                reg.zoom.setEnabled(true);
                check(reg.zoom.fov(70f, true) == 17.5f && reg.zoom.fov(70f, false) == 70f, "zoom divides FOV by 4 while held");
                json(modSetting("{\"zoom.enabled\":false}"));
                check(reg.zoom.isBlockedByServer() && !reg.zoom.isActive() && reg.zoom.fov(70f, true) == 70f,
                                "server blocks zoom: no FOV change even with the key held");
                json(modSetting("{\"zoom.enabled\":null,\"zoom.zoom-divisor\":2}"));
                check(reg.zoom.isActive() && reg.zoom.fov(70f, true) == 35f, "override rolled back; server divisor honored");

                reg.toggleSprint.setEnabled(true);
                reg.toggleSprint.onSprintKey();
                check(reg.toggleSprint.sprintHeld(), "toggle sprint latches");
                json(modSetting("{\"toggle-sneak.toggle-sprint\":false}"));
                check(!reg.toggleSprint.sprintHeld(), "server option toggle-sprint=false stops toggle sprint");
                json(modSetting("{\"toggle-sneak.toggle-sprint\":null}"));
                check(reg.toggleSprint.sprintHeld(), "and it resumes once the override is rolled back");

                reg.fullbright.setEnabled(true);
                check(reg.fullbright.minLight() == 1f, "fullbright raises the light floor");
                json(modSetting("{\"lighting.full-bright\":false}"));
                check(reg.fullbright.minLight() == 0f, "server can turn fullbright off");
                json("{\"@type\":\"type.googleapis.com/lunarclient.apollo.configurable.v1.OverrideConfigurableSettingsMessage\","
                                + "\"configurable_settings\":[{\"apollo_module\":\"server_rule\",\"enable\":true,\"properties\":"
                                + "{\"override-brightness\":true,\"brightness\":25}}]}");
                check(Math.abs(reg.fullbright.minLight() - 0.25f) < 1e-6, "server brightness rule wins over the mod");

                // every HUD mod can be blocked by "<id>.enabled" = false
                List<String> ids = new ArrayList<>();
                for (Mod m : reg.all()) {
                        ids.add(m.id());
                }
                StringBuilder props = new StringBuilder("{");
                for (int i = 0; i < ids.size(); i++) {
                        props.append(i == 0 ? "" : ",").append('"').append(ids.get(i)).append(".enabled\":false");
                }
                json(modSetting(props.append('}').toString()));
                boolean allBlocked = true;
                for (Mod m : reg.all()) {
                        allBlocked &= m.isBlockedByServer() && !m.isActive();
                }
                check(allBlocked, "every mod honours a server block: " + ids);
                check(ids.containsAll(Arrays.asList("fps", "cps", "keystrokes", "coordinates", "armorstatus", "potion-effects",
                                "ping", "toggle-sneak", "zoom", "lighting", "clock")), "mod ids match the server option keys");

                // disconnect drops the overrides
                PluginChannels.reset();
                check(reg.zoom.isActive() && reg.fullbright.minLight() == 1f, "overrides end with the connection");

                ClickCounter cc = new ClickCounter();
                for (int i = 0; i < 12; i++) {
                        cc.click(0, 1000 + i * 50);
                }
                cc.click(1, 1500);
                check(cc.cps(0, 1599) == 12 && cc.cps(0, 2400) == 3 && cc.cps(1, 2400) == 1 && cc.cps(1, 2600) == 0,
                                "click counter window: " + cc.cps(0, 1599) + "/" + cc.cps(0, 2400));

                // HUD editor: reset position, prefs round trip
                ModRegistry rr = new ModRegistry();
                float dx = rr.fps.fx(), dy = rr.fps.fy();
                rr.fps.setPosition(0.6f, 0.7f);
                rr.fps.resetPosition();
                check(rr.fps.fx() == dx && rr.fps.fy() == dy, "reset position");
                net.plebmc.client.mods.ClientPrefs pr = net.plebmc.client.mods.ClientPrefs.get();
                pr.shiftOpens = 2;
                pr.accent = 3;
                Map<String, String> kv = new HashMap<>();
                SettingsStore st2 = new SettingsStore() {
                        public String get(String k) { return kv.get(k); }
                        public void set(String k, String v) { kv.put(k, v); }
                };
                pr.save(st2);
                pr.reset();
                check(pr.shiftOpens == 0 && pr.accent == 0, "prefs reset");
                pr.load(st2);
                check(pr.shiftOpens == 2 && pr.accent == 3 && pr.accentColor() == net.plebmc.client.mods.ClientPrefs.ACCENTS[3], "prefs saved and loaded");
                kv.put("prefs", "{\"shiftOpens\":99,\"accent\":-4}");
                pr.load(st2);
                check(pr.shiftOpens == 3 && pr.accent == 0, "out-of-range prefs are clamped");
                pr.reset();

                // HUD colors, brackets and text style
                check(net.plebmc.client.mods.Mod.colorAt(7) == pr.accentColor(), "Accent text color follows the accent");
                check(net.plebmc.client.mods.Mod.colorAt(8) == net.plebmc.client.mods.Mod.CHROMA, "Chroma is its own marker");
                int c0 = net.plebmc.client.mods.Mod.chroma(0, 0), c1 = net.plebmc.client.mods.Mod.chroma(0, 1000);
                check((c0 >>> 24) == 0xFF && c0 != c1, "Chroma is opaque and moves over time");
                check(net.plebmc.client.mods.Mod.hsv(0f, 1f, 1f) == 0xFFFF0000 && net.plebmc.client.mods.Mod.hsv(1f / 3f, 1f, 1f) == 0xFF00FF00,
                                "hsv: red and green");
                check("FPS".equals(pr.decorateHud("FPS")), "no brackets by default");
                pr.hudBrackets = 1;
                check("[FPS]".equals(pr.decorateHud("FPS")), "square brackets");
                pr.hudTextStyle = 1;
                check("\u00a7l[FPS\u00a7l]".equals(pr.decorateHud("FPS")), "bold brackets");
                pr.reset();
                check(pr.hudBrackets == 0 && pr.hudTextStyle == 0, "bracket and style reset");

                // block outline, overlays, death coords
                ModRegistry rx = new ModRegistry();
                check(rx.blockOutline.lineArgb(0) == 0xC04FE3F0, "outline: cyan at 75%");
                check(rx.blockOutline.fillArgb(0) == 0, "outline: no fill by default");
                rx.blockOutline.color.set(0);
                rx.blockOutline.fill.set(2);
                check(rx.blockOutline.fillArgb(0) == 0x44000000, "outline: black fill");
                check(rx.overlays.fireDrop() == 0.3f, "fire low by default");
                rx.overlays.fire.set(2);
                check(rx.overlays.fireDrop() < 0, "fire hidden");
                check("You died at X -13, Y 64, Z 7 in the Nether".equals(rx.deathCoords.message(-12.5, 64.9, 7.2, -1)), "death message");
                rx.deathCoords.dimension.set(0);
                check("You died at X 0, Y 1, Z 2".equals(rx.deathCoords.message(0.1, 1.5, 2.9, 0)), "death message without dimension");

                System.out.println("ClientModsTest: " + checks + " checks, failures=" + failures);
                System.exit(failures > 0 ? 1 : 0);
        }

        static final class MapStore implements SettingsStore {
                final Map<String, String> m = new HashMap<>();

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
