package net.plebmc.client.mods;

import java.util.ArrayList;
import java.util.List;

import net.plebmc.client.apollo.Apollo;
import net.plebmc.client.apollo.module.ModSettingModule;
import net.plebmc.client.apollo.module.ServerRuleModule;
import net.plebmc.client.apollo.proto.ProtoValues;

/**
 * The built-in mods. Ids match the option keys servers use in Apollo
 * mod_setting overrides ("zoom", "fps", "toggle-sneak", "lighting", ...),
 * which is what lets a server block one. QoL only: nothing here reads
 * hidden information or automates combat.
 */
public final class Mods {

        private Mods() {
        }

        static ModSettingModule modSettings() {
                return Apollo.module(ModSettingModule.class);
        }

        /** Server override of one option, or {@code def}. */
        static boolean serverOption(String id, String option, boolean def) {
                ModSettingModule ms = modSettings();
                return ms == null ? def : ms.optionBool(id, option, def);
        }

        // ==================== HUD text mods ====================

        /** A mod whose HUD is a plain text panel. */
        public abstract static class TextMod extends Mod {
                protected TextMod(String id, String name, String desc, boolean on, float fx, float fy) {
                        super(id, name, desc, on, fx, fy);
                }

                @Override
                public boolean hasHud() {
                        return true;
                }

                protected abstract List<String> lines(ClientView v);

                @Override
                public HudBox layout(ClientView v, HudBox.Measure m) {
                        List<String> l = lines(v);
                        if (l.isEmpty()) {
                                return null;
                        }
                        ClientPrefs pr = ClientPrefs.get();
                        if (pr.hudBrackets != 0 || pr.hudTextStyle != 0) {
                                List<String> d = new java.util.ArrayList<>(l.size());
                                for (String s : l) {
                                        d.add(pr.decorateHud(s));
                                }
                                l = d;
                        }
                        return HudBox.lines(l, m);
                }
        }

        public static final class Fps extends TextMod {
                public Fps() {
                        super("fps", "FPS", "Frames per second", true, 0.005f, 0.10f);
                        hudLook(true);
                }

                @Override
                protected List<String> lines(ClientView v) {
                        return one(FpsLarp.INSTANCE.hudText(v.fps(), System.currentTimeMillis()));
                }
        }

        public static final class Cps extends TextMod {
                public Cps() {
                        super("cps", "CPS", "Clicks per second (left | right)", true, 0.005f, 0.14f);
                        hudLook(true);
                        right = setting("right", "Right clicks", "Also count right clicks", OFF_ON, 1);
                }

                private final Setting right;

                @Override
                protected List<String> lines(ClientView v) {
                        return one(right.on() ? v.cps(0) + " | " + v.cps(1) + " CPS" : v.cps(0) + " CPS");
                }
        }

        public static final class Coordinates extends TextMod {
                public Coordinates() {
                        super("coordinates", "Coordinates", "Position and facing", true, 0.005f, 0.18f);
                        hudLook(true);
                        facing = setting("facing", "Facing", "Show which way you're looking", OFF_ON, 1);
                        decimals = setting("decimals", "Decimals", "Show one decimal place", OFF_ON, 1);
                        compact = setting("compact", "Layout", "One line or one line per axis", new String[] { "Stacked", "One line" }, 0);
                        labelX = textSetting("label_x", "X label", "Text before the X number (empty for none)", "X: ");
                        labelY = textSetting("label_y", "Y label", "Text before the Y number", "Y: ");
                        labelZ = textSetting("label_z", "Z label", "Text before the Z number", "Z: ");
                        labelFacing = textSetting("label_facing", "Facing label", "Text before the direction", "Facing: ");
                }

                private final Setting facing;
                private final Setting decimals;
                private final Setting compact;
                private final Setting labelX, labelY, labelZ, labelFacing;

                private String n(double d) {
                        return decimals.on() ? fmt(d) : Long.toString((long) Math.floor(d));
                }

                @Override
                protected List<String> lines(ClientView v) {
                        List<String> l = new ArrayList<>();
                        String x = labelX.text() + n(v.x()), y = labelY.text() + n(v.y()), z = labelZ.text() + n(v.z());
                        String f = facing.on() ? labelFacing.text() + facing(v.yaw()) : null;
                        if (compact.on()) {
                                // everything on one line, facing included
                                l.add(x + "  " + y + "  " + z + (f == null ? "" : "  " + f));
                        } else {
                                l.add(x);
                                l.add(y);
                                l.add(z);
                                if (f != null) {
                                        l.add(f);
                                }
                        }
                        return l;
                }

                static String fmt(double d) {
                        long tenths = Math.round(d * 10.0);
                        String sign = tenths < 0 ? "-" : "";
                        tenths = Math.abs(tenths);
                        return sign + (tenths / 10) + "." + (tenths % 10);
                }

                /** Vanilla compass: yaw 0 = south (+Z), 90 = west (-X), 180 = north, 270 = east. */
                public static String facing(float yaw) {
                        float y = ((yaw % 360f) + 360f) % 360f;
                        int q = (int) Math.floor((y + 45f) / 90f) & 3;
                        switch (q) {
                        case 0:
                                return "South (+Z)";
                        case 1:
                                return "West (-X)";
                        case 2:
                                return "North (-Z)";
                        default:
                                return "East (+X)";
                        }
                }
        }

        public static final class Ping extends TextMod {
                public Ping() {
                        super("ping", "Ping", "Connection latency", false, 0.90f, 0.05f);
                        hudLook(true);
                }

                @Override
                protected List<String> lines(ClientView v) {
                        int p = v.pingMs();
                        return one(p < 0 ? "Ping: n/a" : "Ping: " + p + " ms");
                }
        }

        public static final class Clock extends TextMod {
                public Clock() {
                        super("clock", "Clock", "Local time", false, 0.90f, 0.01f);
                        hudLook(true);
                        format = setting("format", "Format", "24-hour or 12-hour time", new String[] { "24-hour", "12-hour" }, 0);
                }

                private final Setting format;

                @Override
                protected List<String> lines(ClientView v) {
                        return one(format.on() ? twelve(v.localTime()) : v.localTime());
                }

                /** "13:05" -> "1:05 PM". */
                public static String twelve(String hhmm) {
                        int c = hhmm == null ? -1 : hhmm.indexOf(':');
                        if (c < 1) {
                                return hhmm;
                        }
                        try {
                                int h = Integer.parseInt(hhmm.substring(0, c));
                                String ap = h >= 12 ? " PM" : " AM";
                                h = h % 12 == 0 ? 12 : h % 12;
                                return h + hhmm.substring(c) + ap;
                        } catch (NumberFormatException e) {
                                return hhmm;
                        }
                }
        }

        /**
         * Armor: the pieces you're wearing drawn as their real item icons, each
         * with a durability bar and uses left colored green to red, plus the
         * item in your hand if you want it.
         */
        public static final class Armor extends Mod {
                public static final int ROW = 17;

                public Armor() {
                        super("armorstatus", "Armor", "Your armor as item icons with durability", false, 0.005f, 0.68f);
                        hudLook(false);
                        durability = setting("durability", "Durability", "How uses left are written next to each piece",
                                        new String[] { "Numbers", "Percent", "Hidden" }, 0);
                        bars = setting("bars", "Durability bars", "A colored bar under each icon", OFF_ON, 1);
                        layout = setting("layout", "Layout", "A column or a row", new String[] { "Vertical", "Horizontal" }, 0);
                        held = setting("held", "Held item", "Also show what's in your hand", OFF_ON, 1);
                }

                private final Setting durability;
                private final Setting bars;
                private final Setting layout;
                private final Setting held;

                @Override
                public boolean hasHud() {
                        return true;
                }

                /** The words next to a piece: "363", "100%" or nothing; a held stack shows its count. */
                String label(Gear g) {
                        if (!g.damageable()) {
                                return g.count > 1 ? Integer.toString(g.count) : "";
                        }
                        switch (durability.get()) {
                        case 0:
                                return Integer.toString(g.durability);
                        case 1:
                                return Math.round(g.fraction() * 100f) + "%";
                        default:
                                return "";
                        }
                }

                private List<Gear> pieces(ClientView v) {
                        List<Gear> out = new ArrayList<>();
                        for (Gear g : v.armor()) {
                                if (g != null) {
                                        out.add(g);
                                }
                        }
                        Gear h = held.on() ? v.held() : null;
                        if (h != null) {
                                out.add(h);
                        }
                        return out;
                }

                @Override
                public HudBox layout(ClientView v, HudBox.Measure m) {
                        List<Gear> l = pieces(v);
                        return l.isEmpty() ? null : draw(l, m);
                }

                HudBox draw(List<Gear> l, HudBox.Measure m) {
                        HudBox b = new HudBox();
                        boolean row = layout.on();
                        int p = HudBox.PAD;
                        if (row) {
                                int x = p;
                                List<int[]> cols = new ArrayList<>();
                                boolean anyText = false;
                                for (Gear g : l) {
                                        String t = label(g);
                                        int cw = Math.max(HudBox.ITEM, t.isEmpty() ? 0 : m.width(t));
                                        cols.add(new int[] { x, cw });
                                        anyText |= !t.isEmpty();
                                        x += cw + 4;
                                }
                                int h = HudBox.ITEM + p * 2 + (anyText ? 10 : 0) + (bars.on() ? 2 : 0);
                                b.rect(0, 0, x - 4 + p, h, HudBox.BG);
                                for (int i = 0; i < l.size(); i++) {
                                        Gear g = l.get(i);
                                        int ix = cols.get(i)[0] + (cols.get(i)[1] - HudBox.ITEM) / 2;
                                        b.item(g.stack, g.id, ix, p);
                                        int ty = p + HudBox.ITEM + 1;
                                        if (bars.on() && g.damageable()) {
                                                bar(b, g, ix + 1, p + HudBox.ITEM + 1);
                                        }
                                        if (bars.on()) {
                                                ty += 2;
                                        }
                                        String t = label(g);
                                        if (!t.isEmpty()) {
                                                b.text(t, cols.get(i)[0] + cols.get(i)[1] / 2, ty + 1, g.damageable() ? g.color() : HudBox.TEXT, true);
                                        }
                                }
                        } else {
                                int tw = 0;
                                for (Gear g : l) {
                                        tw = Math.max(tw, m.width(label(g)));
                                }
                                int w = p + HudBox.ITEM + (tw > 0 ? 3 + tw : 0) + p;
                                b.rect(0, 0, w, p + l.size() * ROW + p - 1, HudBox.BG);
                                for (int i = 0; i < l.size(); i++) {
                                        Gear g = l.get(i);
                                        int y = p + i * ROW;
                                        b.item(g.stack, g.id, p, y);
                                        if (bars.on() && g.damageable()) {
                                                bar(b, g, p + 1, y + 14);
                                        }
                                        String t = label(g);
                                        if (!t.isEmpty()) {
                                                b.text(t, p + HudBox.ITEM + 3, y + 4, g.damageable() ? g.color() : HudBox.TEXT, false);
                                        }
                                }
                        }
                        return b;
                }

                /** Vanilla-style durability bar: 14 px, black under the colored part. */
                private static void bar(HudBox b, Gear g, int x, int y) {
                        b.rect(x, y, 14, 2, 0xFF000000);
                        int w = Math.round(13 * g.fraction());
                        if (w > 0) {
                                b.rect(x, y, w, 1, g.color());
                        }
                }

                @Override
                public HudBox preview(ClientView v, HudBox.Measure m) {
                        HudBox b = layout(v, m);
                        if (b != null) {
                                return b;
                        }
                        List<Gear> l = new ArrayList<>();
                        l.add(new Gear(null, "diamond_helmet", "Diamond Helmet", 1, 363, 363));
                        l.add(new Gear(null, "diamond_chestplate", "Diamond Chestplate", 1, 401, 528));
                        l.add(new Gear(null, "iron_leggings", "Iron Leggings", 1, 120, 225));
                        l.add(new Gear(null, "diamond_boots", "Diamond Boots", 1, 40, 429));
                        if (held.on()) {
                                l.add(new Gear(null, "diamond_sword", "Diamond Sword", 1, 1561, 1561));
                        }
                        return draw(l, m);
                }
        }

        public static final class Potions extends TextMod {
                public Potions() {
                        super("potion-effects", "Potion effects", "Active effects and time left", false, 0.80f, 0.30f);
                        hudLook(true);
                }

                @Override
                protected List<String> lines(ClientView v) {
                        return new ArrayList<>(v.effects());
                }

                @Override
                public HudBox preview(ClientView v, HudBox.Measure m) {
                        HudBox b = layout(v, m);
                        if (b != null) {
                                return b;
                        }
                        List<String> l = new ArrayList<>();
                        l.add("Speed II 1:30");
                        l.add("Strength 0:45");
                        return HudBox.lines(l, m);
                }
        }

        // ==================== keystrokes ====================

        public static final class Keystrokes extends Mod {
                static final int KEY = 20;
                static final int GAP = 2;

                public Keystrokes() {
                        super("keystrokes", "Keystrokes", "WASD, mouse buttons and jump", true, 0.005f, 0.37f);
                        hudLook(false);
                        mouse = setting("mouse", "Mouse buttons", "Show the LMB / RMB keys", OFF_ON, 1);
                        cps = setting("cps", "Clicks per second", "Show CPS on the mouse keys", OFF_ON, 1);
                        jump = setting("jump", "Space bar", "Show the jump key", OFF_ON, 1);
                }

                private final Setting mouse;
                private final Setting cps;
                private final Setting jump;

                @Override
                public boolean hasHud() {
                        return true;
                }

                @Override
                public HudBox layout(ClientView v, HudBox.Measure m) {
                        HudBox b = new HudBox();
                        int col = KEY + GAP;
                        key(b, "W", col, 0, KEY, v.keyDown("forward"));
                        key(b, "A", 0, col, KEY, v.keyDown("left"));
                        key(b, "S", col, col, KEY, v.keyDown("back"));
                        key(b, "D", col * 2, col, KEY, v.keyDown("right"));
                        int mw = (col * 3 - GAP - GAP) / 2;
                        int y = col * 2;
                        if (mouse.on()) {
                                if (cps.on()) {
                                        // the label on top and the CPS under it, both centered in the key
                                        mouseKey(b, "LMB", v.cps(0), 0, y, mw, v.mouseDown(0));
                                        mouseKey(b, "RMB", v.cps(1), mw + GAP, y, mw, v.mouseDown(1));
                                        y += MOUSE_KEY + GAP;
                                } else {
                                        key(b, "LMB", 0, y, mw, v.mouseDown(0));
                                        key(b, "RMB", mw + GAP, y, mw, v.mouseDown(1));
                                        y += col;
                                }
                        }
                        if (jump.on()) {
                                b.rect(0, y, col * 3 - GAP, 8, v.keyDown("jump") ? HudBox.BG_PRESSED : HudBox.BG);
                                b.rect(col * 3 / 2 - 10, y + 3, 20, 1, v.keyDown("jump") ? HudBox.TEXT_PRESSED : HudBox.TEXT);
                        }
                        return b;
                }

                static final int MOUSE_KEY = 24;

                private static void mouseKey(HudBox b, String label, int cps, int x, int y, int w, boolean down) {
                        b.rect(x, y, w, MOUSE_KEY, down ? HudBox.BG_PRESSED : HudBox.BG);
                        int fg = down ? HudBox.TEXT_PRESSED : HudBox.TEXT;
                        b.text(label, x + w / 2, y + 3, fg, true);
                        // just the number, so even 10+ CPS stays inside the key
                        b.text(Integer.toString(cps), x + w / 2, y + 13, fg, true);
                }

                private static void key(HudBox b, String label, int x, int y, int w, boolean down) {
                        b.rect(x, y, w, KEY, down ? HudBox.BG_PRESSED : HudBox.BG);
                        b.text(label, x + w / 2, y + (KEY - 8) / 2, down ? HudBox.TEXT_PRESSED : HudBox.TEXT, true);
                }
        }

        // ==================== functional mods ====================

        /** Toggle sprint: tap the sprint key to keep sprinting (Apollo id "toggle-sneak", option "toggle-sprint"). */
        public static final class ToggleSprint extends TextMod {
                private boolean toggled;

                public ToggleSprint() {
                        super("toggle-sneak", "Toggle Sprint", "Tap sprint to keep sprinting", false, 0.005f, 0.95f);
                        hudLook(true);
                        category(GAMEPLAY);
                        indicator = setting("indicator", "On-screen label", "Show [Sprinting (Toggled)] while it's on", OFF_ON, 1);
                }

                private final Setting indicator;

                @Override
                public boolean isBlockedByServer() {
                        return super.isBlockedByServer() || !serverOption(id(), "toggle-sprint", true);
                }

                /** Sprint key pressed (not held): flips the toggle. */
                public void onSprintKey() {
                        if (isActive()) {
                                toggled = !toggled;
                        }
                }

                public boolean sprintHeld() {
                        return isActive() && toggled;
                }

                public void reset() {
                        toggled = false;
                }

                @Override
                protected List<String> lines(ClientView v) {
                        return sprintHeld() && indicator.on() ? one("[Sprinting (Toggled)]") : new ArrayList<String>();
                }

                @Override
                public HudBox preview(ClientView v, HudBox.Measure m) {
                        return HudBox.lines(one("[Sprinting (Toggled)]"), m);
                }
        }

        /** Zoom: hold the zoom key (C) to divide the field of view. */
        public static final class Zoom extends Mod {
                public static final float DEFAULT_DIVISOR = 4f;

                public Zoom() {
                        super("zoom", "Zoom", "Hold C to zoom", true, 0f, 0f);
                        level = setting("level", "Zoom level", "How far you zoom in when you press the key", LEVEL_NAMES, 1);
                        scroll = setting("scroll", "Scroll to adjust", "Mouse wheel zooms further in or out while zooming", OFF_ON, 1);
                        smooth = setting("smooth", "Smooth camera", "Cinematic camera while zoomed", OFF_ON, 0);
                }

                /** Field of view while zoomed (Eaglercraft's zoom works in FOV degrees). */
                public static final float[] LEVELS = { 30f, 18f, 10f, 5f };
                public static final String[] LEVEL_NAMES = { "Light", "Normal", "Strong", "Max" };
                private final Setting level;
                private final Setting scroll;
                private final Setting smooth;

                public float startFov() {
                        return LEVELS[level.get()];
                }

                /** The zoomed FOV after one mouse wheel step (Eaglercraft: 4 degrees a notch, 5..32). */
                public float scrolled(float current, int wheel) {
                        if (!scroll.on()) {
                                return current;
                        }
                        return Math.max(5f, Math.min(32f, current - wheel * 4f));
                }

                public boolean smoothCamera() {
                        return smooth.on();
                }

                public float fov(float baseFov, boolean keyDown) {
                        if (!keyDown || !isActive()) {
                                return baseFov;
                        }
                        ModSettingModule ms = modSettings();
                        float div = DEFAULT_DIVISOR;
                        if (ms != null) {
                                double d = ProtoValues.asNumber(ms.option(id(), "zoom-divisor"), DEFAULT_DIVISOR);
                                if (d >= 1.0 && d <= 32.0) {
                                        div = (float) d;
                                }
                        }
                        return baseFov / div;
                }
        }

        /** Fullbright (Apollo id "lighting", option "full-bright"); a server brightness rule wins. */
        public static final class Fullbright extends Mod {
                public Fullbright() {
                        super("lighting", "Fullbright", "See in the dark", false, 0f, 0f);
                        category(GAMEPLAY);
                }

                @Override
                public boolean isBlockedByServer() {
                        return super.isBlockedByServer() || !serverOption(id(), "full-bright", true);
                }

                /** Minimum light level 0..1 for the world shader. */
                public float minLight() {
                        ServerRuleModule rules = Apollo.module(ServerRuleModule.class);
                        float forced = rules == null ? -1f : rules.brightnessOverride();
                        if (forced >= 0f) {
                                return forced;
                        }
                        return isActive() ? 1f : 0f;
                }
        }

        // ==================== PlebNite / performance ====================

        /**
         * Compass: a heading strip (top center by default) with a colored pip
         * for every teammate and waypoint the server sent, so you can see where
         * your squad is even when they're behind you.
         */
        public static final class Compass extends Mod {
                public static final int WIDTH = 181;
                public static final int HEIGHT = 18;
                /** Degrees shown either side of straight ahead. */
                public static final float SPAN = 90f;

                /** One pip: world bearing (vanilla yaw that faces it), color, short label, distance. */
                public static final class Marker {
                        public final float bearing;
                        public final int argb;
                        public final String label;
                        public final int distance;
                        public final boolean waypoint;

                        public Marker(float bearing, int argb, String label, int distance, boolean waypoint) {
                                this.bearing = bearing;
                                this.argb = argb;
                                this.label = label;
                                this.distance = distance;
                                this.waypoint = waypoint;
                        }
                }

                private List<Marker> markers = new ArrayList<>();

                public Compass() {
                        super("compass", "Compass", "Heading strip with your squad, waypoints and the storm", true, 0.5f, 0.09f);
                        hudLook(false);
                        category(PLEBNITE);
                        pips = setting("markers", "Squad markers", "Show teammates and waypoints on the strip", OFF_ON, 1);
                        safe = setting("safezone", "Safe zone", "A white square points to the middle of the storm", OFF_ON, 1);
                }

                private final Setting pips;
                private final Setting safe;

                public void setMarkers(List<Marker> m) {
                        markers = m == null ? new ArrayList<Marker>() : m;
                }

                @Override
                public boolean hasHud() {
                        return true;
                }

                @Override
                public boolean centerAnchored() {
                        return true;
                }

                /** Signed angle from {@code yaw} to {@code bearing}, -180..180. */
                public static float relative(float bearing, float yaw) {
                        float d = (bearing - yaw) % 360f;
                        if (d > 180f) {
                                d -= 360f;
                        } else if (d < -180f) {
                                d += 360f;
                        }
                        return d;
                }

                private static final String[] NAMES = { "S", "SW", "W", "NW", "N", "NE", "E", "SE" };

                @Override
                public HudBox layout(ClientView v, HudBox.Measure m) {
                        HudBox b = new HudBox();
                        int mid = WIDTH / 2;
                        float yaw = v.yaw();
                        b.rect(0, 0, WIDTH, HEIGHT, HudBox.BG_LIGHT);
                        // ticks every 15 degrees, letters every 45
                        for (int deg = 0; deg < 360; deg += 15) {
                                float rel = relative(deg, yaw);
                                if (Math.abs(rel) > SPAN) {
                                        continue;
                                }
                                int x = mid + Math.round(rel * (mid - 4) / SPAN);
                                if (deg % 45 == 0) {
                                        String n = NAMES[deg / 45];
                                        boolean card = n.length() == 1;
                                        b.text(n, x, 5, card ? (n.equals("N") ? 0xFFFF5555 : 0xFFFFFFFF) : 0xFFAAAAAA, true);
                                } else {
                                        b.rect(x, HEIGHT - 5, 1, 3, 0x90FFFFFF);
                                }
                        }
                        // squad + waypoint pips
                        for (int i = 0; pips.on() && i < markers.size(); i++) {
                                Marker mk = markers.get(i);
                                float rel = relative(mk.bearing, yaw);
                                boolean edge = Math.abs(rel) > SPAN;
                                float clamped = Math.max(-SPAN, Math.min(SPAN, rel));
                                int x = mid + Math.round(clamped * (mid - 4) / SPAN);
                                int c = mk.argb | 0xFF000000;
                                if (mk.waypoint) {
                                        // diamond
                                        b.rect(x - 1, 0, 3, 1, c);
                                        b.rect(x - 2, 1, 5, 2, c);
                                        b.rect(x - 1, 3, 3, 1, c);
                                } else {
                                        b.rect(x - 2, 0, 5, 4, edge ? (c & 0x00FFFFFF) | 0x90000000 : c);
                                        b.rect(x - 1, 4, 3, 1, c);
                                }
                        }
                        // the middle of the safe zone (world border center)
                Storm st = safe.on() ? v.storm() : null;
                if (st != null && st.size < Storm.NO_STORM) {
                        double dx = st.centerX - v.x(), dz = st.centerZ - v.z();
                        if (dx * dx + dz * dz > 4.0) {
                                float rel = relative((float) Math.toDegrees(Math.atan2(-dx, dz)), yaw);
                                float clamped = Math.max(-SPAN, Math.min(SPAN, rel));
                                int x = mid + Math.round(clamped * (mid - 4) / SPAN);
                                int c = st.inside() ? 0xFFFFFFFF : 0xFFFF5555;
                                b.rect(x - 2, 0, 5, 1, c);
                                b.rect(x - 2, 4, 5, 1, c);
                                b.rect(x - 2, 1, 1, 3, c);
                                b.rect(x + 2, 1, 1, 3, c);
                        }
                }
                // center notch
                        b.rect(mid, HEIGHT - 2, 1, 2, 0xFFFFFF55);
                        b.rect(mid - 1, HEIGHT - 1, 3, 1, 0xFFFFFF55);
                        return b;
                }
        }

        /**
         * Squad ping: Sneak + F sends the "swap hands" action newer servers
         * use for pings (1.8 has no off hand, so the key does nothing else).
         */
        public static final class SquadPing extends Mod {
                public SquadPing() {
                        super("squad-ping", "Squad Ping", "Sneak + a key (F) marks a spot for your squad", true, 0f, 0f);
                        category(PLEBNITE);
                        key = setting("key", "Key", "Hold Sneak and press this key to ping", KEY_NAMES, 0);
                }

                private final Setting key;

                /** The key that pings while sneaking (an LWJGL key code). */
                public int keyCode() {
                        return KEY_CODES[key.get()];
                }
        }

        /**
         * Storm tracker: how far the storm (world border) is, whether it is
         * closing and how long until it stops, and a red warning when you are
         * caught outside it.
         */
        public static final class StormTracker extends Mod {
                public StormTracker() {
                        super("storm", "Storm Tracker", "Distance to the storm and when it closes", false, 0.5f, 0.25f);
                        hudLook(false);
                        category(PLEBNITE);
                        timer = setting("timer", "Closing timer", "Show when the storm stops moving", OFF_ON, 1);
                        size = setting("size", "Safe zone size", "Show how wide the safe zone is", OFF_ON, 0);
                }

                private final Setting timer;
                private final Setting size;

                @Override
                public boolean hasHud() {
                        return true;
                }

                @Override
                public boolean centerAnchored() {
                        return true;
                }

                public static String clock(long ms) {
                        long s = Math.max(0, (ms + 999) / 1000);
                        long m = s / 60;
                        s %= 60;
                        return m + ":" + (s < 10 ? "0" : "") + s;
                }

                @Override
                public HudBox layout(ClientView v, HudBox.Measure m) {
                        Storm st = v.storm();
                        if (st == null || st.size >= Storm.NO_STORM) {
                                return null;
                        }
                        return draw(st, m);
                }

                HudBox draw(Storm st, HudBox.Measure m) {
                        String head;
                        int hc;
                        if (!st.inside()) {
                                head = "IN THE STORM  " + (int) Math.ceil(-st.edge) + "m to safety";
                                hc = 0xFFFF5555;
                        } else {
                                head = "Storm " + (int) Math.floor(st.edge) + "m away";
                                hc = st.edge < 15 ? 0xFFFFAA00 : HudBox.TEXT;
                        }
                        List<String> rest = new ArrayList<>();
                        if (timer.on()) {
                                if (st.shrinking()) {
                                        rest.add("Closing  " + clock(st.msLeft));
                                } else if (st.growing()) {
                                        rest.add("Opening  " + clock(st.msLeft));
                                } else {
                                        rest.add("Holding");
                                }
                        }
                        if (size.on()) {
                                rest.add("Safe zone " + (int) Math.round(st.size)
                                                + (st.shrinking() || st.growing() ? " to " + (int) Math.round(st.target) : ""));
                        }
                        int w = m.width(head);
                        for (String r : rest) {
                                w = Math.max(w, m.width(r));
                        }
                        int p = HudBox.PAD;
                        int iw = 9 + 3;
                        HudBox b = new HudBox();
                        b.rect(0, 0, p + iw + w + p, p + (1 + rest.size()) * HudBox.LINE_H + p - 1, HudBox.BG);
                        // storm icon: a ring, red when you're outside it
                        int ic = st.inside() ? 0xFFB28CFF : 0xFFFF5555;
                        b.rect(p + 2, p, 5, 1, ic);
                        b.rect(p + 2, p + 6, 5, 1, ic);
                        b.rect(p + 1, p + 1, 1, 5, ic);
                        b.rect(p + 7, p + 1, 1, 5, ic);
                        b.rect(p + 3, p + 3, 3, 1, ic);
                        b.text(head, p + iw, p, hc, false);
                        for (int i = 0; i < rest.size(); i++) {
                                b.text(rest.get(i), p + iw, p + (i + 1) * HudBox.LINE_H, st.shrinking() && i == 0 ? 0xFFD6C2FF : HudBox.TEXT, false);
                        }
                        return b;
                }

                @Override
                public HudBox preview(ClientView v, HudBox.Measure m) {
                        HudBox b = layout(v, m);
                        return b != null ? b : draw(new Storm(0, 0, 240, 120, 35_000, 42), m);
                }
        }

        /**
         * Squad: everyone on your team with their color, health (when they are
         * close enough for the game to know it) and how far away they are.
         */
        public static final class Squad extends Mod {
                public Squad() {
                        super("squad-hud", "Squad", "Your teammates' health and distance", true, 0.99f, 0.40f);
                        hudLook(false);
                        category(PLEBNITE);
                        health = setting("health", "Health bars", "Show each teammate's health", OFF_ON, 1);
                        distance = setting("distance", "Distance", "Show how far away they are", OFF_ON, 1);
                }

                private final Setting health;
                private final Setting distance;

                @Override
                public boolean hasHud() {
                        return true;
                }

                @Override
                public HudBox layout(ClientView v, HudBox.Measure m) {
                        List<Mate> l = v.squad();
                        return l.isEmpty() ? null : draw(l, m);
                }

                static final int BAR = 28;

                HudBox draw(List<Mate> l, HudBox.Measure m) {
                        int p = HudBox.PAD;
                        int nw = 0, dw = 0;
                        for (Mate mt : l) {
                                nw = Math.max(nw, m.width(mt.name));
                                if (distance.on() && mt.distance >= 0) {
                                        dw = Math.max(dw, m.width(mt.distance + "m"));
                                }
                        }
                        int x0 = p + 8;
                        int barX = x0 + nw + 4;
                        int distX = barX + (health.on() ? BAR + 4 : 0);
                        int w = distX + dw + p - (dw == 0 ? 4 : 0);
                        HudBox b = new HudBox();
                        b.rect(0, 0, w, p + l.size() * 11 + p - 1, HudBox.BG);
                        for (int i = 0; i < l.size(); i++) {
                                Mate mt = l.get(i);
                                int y = p + i * 11;
                                b.rect(p, y + 1, 5, 7, mt.argb | 0xFF000000);
                                boolean down = mt.health == 0f;
                                b.text(mt.name, x0, y, down ? 0xFF888888 : HudBox.TEXT, false);
                                if (health.on()) {
                                        b.rect(barX, y + 3, BAR, 3, 0xFF000000);
                                        if (mt.health > 0) {
                                                float f = Math.min(1f, mt.health / Math.max(1f, mt.maxHealth));
                                                int c = f > 0.5f ? 0xFF55FF55 : f > 0.25f ? 0xFFFFFF55 : 0xFFFF5555;
                                                b.rect(barX, y + 3, Math.max(1, Math.round(BAR * f)), 3, c);
                                        } else if (mt.health < 0) {
                                                b.rect(barX, y + 4, BAR, 1, 0xFF555555); // too far to see
                                        }
                                }
                                if (distance.on() && mt.distance >= 0) {
                                        b.text(mt.distance + "m", distX, y, 0xFFAAAAAA, false);
                                }
                        }
                        return b;
                }

                @Override
                public HudBox preview(ClientView v, HudBox.Measure m) {
                        HudBox b = layout(v, m);
                        if (b != null) {
                                return b;
                        }
                        List<Mate> l = new ArrayList<>();
                        l.add(new Mate("Teammate1", 0xFF55FFFF, 12, 18f, 20f, 0f));
                        l.add(new Mate("Teammate2", 0xFFFFAA00, 87, 6f, 20f, 0f));
                        l.add(new Mate("Teammate3", 0xFFFF55FF, 240, -1f, 20f, 0f));
                        return draw(l, m);
                }
        }

        /**
         * Supplies: the building blocks, arrows, golden apples, pearls, potions
         * and TNT in your inventory, as item icons with counts.
         */
        public static final class Supplies extends Mod {
                /** Group key and the item drawn for it. */
                public static final String[][] GROUPS = { { "blocks", "planks" }, { "arrows", "arrow" },
                                { "gapples", "golden_apple" }, { "pearls", "ender_pearl" }, { "potions", "potion" },
                                { "tnt", "tnt" } };

                public Supplies() {
                        super("supplies", "Supplies", "Blocks, arrows, gapples and pearls you're carrying", false, 0.99f, 0.60f);
                        hudLook(false);
                        category(PLEBNITE);
                        layout = setting("layout", "Layout", "A column or a row", new String[] { "Vertical", "Horizontal" }, 0);
                        empty = setting("empty", "Show empty", "Keep showing supplies you have none of", OFF_ON, 0);
                }

                private final Setting layout;
                private final Setting empty;

                @Override
                public boolean hasHud() {
                        return true;
                }

                @Override
                public HudBox layout(ClientView v, HudBox.Measure m) {
                        int[] n = new int[GROUPS.length];
                        for (int i = 0; i < GROUPS.length; i++) {
                                n[i] = Math.max(0, v.supply(GROUPS[i][0]));
                        }
                        return draw(n, m, false);
                }

                HudBox draw(int[] n, HudBox.Measure m, boolean all) {
                        List<Integer> shown = new ArrayList<>();
                        for (int i = 0; i < n.length; i++) {
                                if (n[i] > 0 || empty.on() || all) {
                                        shown.add(i);
                                }
                        }
                        if (shown.isEmpty()) {
                                return null;
                        }
                        int p = HudBox.PAD;
                        HudBox b = new HudBox();
                        if (layout.on()) {
                                int step = HudBox.ITEM + 4;
                                b.rect(0, 0, p + shown.size() * step - 4 + p, p + HudBox.ITEM + p, HudBox.BG);
                                for (int k = 0; k < shown.size(); k++) {
                                        int i = shown.get(k);
                                        int x = p + k * step;
                                        b.item(null, GROUPS[i][1], x, p);
                                        String t = Integer.toString(n[i]);
                                        b.text(t, x + HudBox.ITEM + 1 - m.width(t), p + 9, n[i] == 0 ? 0xFFFF5555 : HudBox.TEXT, false);
                                }
                        } else {
                                int tw = 0;
                                for (int i : shown) {
                                        tw = Math.max(tw, m.width(Integer.toString(n[i])));
                                }
                                b.rect(0, 0, p + HudBox.ITEM + 3 + tw + p, p + shown.size() * Armor.ROW + p - 1, HudBox.BG);
                                for (int k = 0; k < shown.size(); k++) {
                                        int i = shown.get(k);
                                        int y = p + k * Armor.ROW;
                                        b.item(null, GROUPS[i][1], p, y);
                                        b.text(Integer.toString(n[i]), p + HudBox.ITEM + 3, y + 4, n[i] == 0 ? 0xFFFF5555 : HudBox.TEXT, false);
                                }
                        }
                        return b;
                }

                @Override
                public HudBox preview(ClientView v, HudBox.Measure m) {
                        HudBox b = layout(v, m);
                        return b != null ? b : draw(new int[] { 128, 32, 3, 2, 0, 0 }, m, false);
                }
        }

        /**
         * Low health warning: the screen edges glow red (and pulse) when your
         * health drops to the chosen number of hearts.
         */
        public static final class LowHealth extends Mod {
                public static final int[] AT = { 4, 6, 8, 10 };

                public LowHealth() {
                        super("low-health", "Low Health Warning", "Red screen edges when you're about to die", true, 0f, 0f);
                        category(PLEBNITE);
                        at = setting("at", "Warn at", "How low your health must be",
                                        new String[] { "2 hearts", "3 hearts", "4 hearts", "5 hearts" }, 2);
                        pulse = setting("pulse", "Pulse", "Throb like a heartbeat", OFF_ON, 1);
                }

                private final Setting at;
                private final Setting pulse;

                /** 0..1 strength of the red edges right now. */
                public float intensity(float health, long nowMs) {
                        if (!isActive() || health <= 0f) {
                                return 0f;
                        }
                        float limit = AT[at.get()];
                        if (health > limit) {
                                return 0f;
                        }
                        float base = 0.45f + 0.55f * (1f - health / limit);
                        if (!pulse.on()) {
                                return base;
                        }
                        // faster heartbeat the lower you are
                        float period = 500f + 700f * (health / limit);
                        float ph = (nowMs % (long) period) / period;
                        return base * (0.6f + 0.4f * (float) Math.abs(Math.cos(ph * Math.PI)));
                }
        }

        /**
         * FPS Boost: switches the heavy video settings to their fastest
         * values while on (clouds, particles, fancy graphics, smooth lighting,
         * entity shadows, FXAA, frame cap) and puts your own values back when
         * turned off.
         */
        public static final class FpsBoost extends Mod {
                public FpsBoost() {
                        super("fps-boost", "FPS Boost", "75% resolution, no vsync, fast settings (undone when off)", false, 0f, 0f);
                        category(PERFORMANCE);
                }
        }
}
