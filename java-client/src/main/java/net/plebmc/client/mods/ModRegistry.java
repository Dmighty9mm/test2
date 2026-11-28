package net.plebmc.client.mods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.plebmc.client.apollo.proto.Json;
import net.plebmc.client.apollo.proto.ProtoValues;

/**
 * The mod list plus its persistence: one JSON document
 * {"fps":{"on":true,"x":0.005,"y":0.01}, ...} under {@link #STORE_KEY}.
 * Unknown ids and bad values in stored data are ignored.
 */
public final class ModRegistry {

        public static final String STORE_KEY = "mods";

        private final List<Mod> mods = new ArrayList<>();
        private final Map<String, Mod> byId = new LinkedHashMap<>();

        public final Mods.Fps fps = add(new Mods.Fps());
        public final Mods.Cps cps = add(new Mods.Cps());
        public final Mods.Keystrokes keystrokes = add(new Mods.Keystrokes());
        public final Mods.Coordinates coordinates = add(new Mods.Coordinates());
        public final Mods.Armor armor = add(new Mods.Armor());
        public final Mods.Potions potions = add(new Mods.Potions());
        public final Mods.Ping ping = add(new Mods.Ping());
        public final Mods.Clock clock = add(new Mods.Clock());
        public final Mods.ToggleSprint toggleSprint = add(new Mods.ToggleSprint());
        public final Mods.Zoom zoom = add(new Mods.Zoom());
        public final Mods.Fullbright fullbright = add(new Mods.Fullbright());
        public final Mods.Compass compass = add(new Mods.Compass());
        public final Mods.SquadPing squadPing = add(new Mods.SquadPing());
        public final Mods.StormTracker storm = add(new Mods.StormTracker());
        public final Mods.Squad squad = add(new Mods.Squad());
        public final Mods.Supplies supplies = add(new Mods.Supplies());
        public final Mods.LowHealth lowHealth = add(new Mods.LowHealth());
        public final Mods.FpsBoost fpsBoost = add(new Mods.FpsBoost());
        public final MoreMods.Speed speed = add(new MoreMods.Speed());
        public final MoreMods.DayCounter dayCounter = add(new MoreMods.DayCounter());
        public final MoreMods.Biome biome = add(new MoreMods.Biome());
        public final MoreMods.Server serverAddress = add(new MoreMods.Server());
        public final MoreMods.Arrows arrows = add(new MoreMods.Arrows());
        public final MoreMods.LightLevel lightLevel = add(new MoreMods.LightLevel());
        public final MoreMods.SessionTimer sessionTimer = add(new MoreMods.SessionTimer());
        public final MoreMods.Crosshair crosshair = add(new MoreMods.Crosshair());
        public final MoreMods.Scoreboard scoreboard = add(new MoreMods.Scoreboard());
        public final MoreMods.Chat chat = add(new MoreMods.Chat());
        public final MoreMods.HurtCam hurtCam = add(new MoreMods.HurtCam());
        public final MoreMods.BlockOutline blockOutline = add(new MoreMods.BlockOutline());
        public final MoreMods.ClearWeather clearWeather = add(new MoreMods.ClearWeather());
        public final MoreMods.Overlays overlays = add(new MoreMods.Overlays());
        public final MoreMods.DeathCoords deathCoords = add(new MoreMods.DeathCoords());
        public final MoreMods.OwnNametag ownNametag = add(new MoreMods.OwnNametag());
        public final MoreMods.Waypoints waypoints = add(new MoreMods.Waypoints());

        private <T extends Mod> T add(T m) {
                mods.add(m);
                byId.put(m.id(), m);
                return m;
        }

        public List<Mod> all() {
                return Collections.unmodifiableList(mods);
        }

        public Mod byId(String id) {
                return byId.get(id);
        }

        public void load(SettingsStore store) {
                String raw = store == null ? null : store.get(STORE_KEY);
                if (raw == null || raw.isEmpty()) {
                        return;
                }
                Object root;
                try {
                        root = Json.parse(raw);
                } catch (RuntimeException e) {
                        return; // corrupt settings: keep defaults
                }
                if (!(root instanceof Map)) {
                        return;
                }
                for (Map.Entry<?, ?> e : ((Map<?, ?>) root).entrySet()) {
                        Mod m = byId.get(String.valueOf(e.getKey()));
                        if (m == null || !(e.getValue() instanceof Map)) {
                                continue;
                        }
                        Map<?, ?> s = (Map<?, ?>) e.getValue();
                        m.setEnabled(ProtoValues.asBool(s.get("on"), m.isEnabled()));
                        double x = ProtoValues.asNumber(s.get("x"), m.fx());
                        double y = ProtoValues.asNumber(s.get("y"), m.fy());
                        if (!Double.isNaN(x) && !Double.isNaN(y)) {
                                m.setPosition((float) x, (float) y);
                        }
                        if (s.get("opts") instanceof Map) {
                                Map<?, ?> o = (Map<?, ?>) s.get("opts");
                                for (Mod.Setting st : m.settings()) {
                                        if (!o.containsKey(st.key)) {
                                                continue; // not in this save (or import): keep what it is
                                        }
                                        if (st.isText()) {
                                                st.setText(o.get(st.key) instanceof String ? (String) o.get(st.key) : null);
                                                continue;
                                        }
                                        double v = ProtoValues.asNumber(o.get(st.key), st.def);
                                        st.set(Double.isNaN(v) ? st.def : (int) v);
                                }
                        }
                }
        }

        public String serialize() {
                Map<String, Object> root = new LinkedHashMap<>();
                for (Mod m : mods) {
                        Map<String, Object> s = new LinkedHashMap<>();
                        s.put("on", m.isEnabled());
                        if (m.hasHud()) {
                                s.put("x", (double) m.fx());
                                s.put("y", (double) m.fy());
                        }
                        if (!m.settings().isEmpty()) {
                                Map<String, Object> o = new LinkedHashMap<>();
                                for (Mod.Setting st : m.settings()) {
                                        o.put(st.key, st.isText() ? (Object) st.text() : (Object) (double) st.get());
                                }
                                s.put("opts", o);
                        }
                        root.put(m.id(), s);
                }
                return Json.write(root);
        }

        public void save(SettingsStore store) {
                if (store != null) {
                        store.set(STORE_KEY, serialize());
                }
        }

        /** Top-left pixel of a HUD box (boxW/boxH already scaled), clamped fully on screen. */
        public static int[] place(Mod m, int boxW, int boxH, int screenW, int screenH) {
                int x = Math.round(m.fx() * screenW) - (m.centerAnchored() ? boxW / 2 : 0);
                int y = Math.round(m.fy() * screenH);
                x = Math.max(0, Math.min(x, screenW - boxW));
                y = Math.max(0, Math.min(y, screenH - boxH));
                return new int[] { x, y };
        }
}
