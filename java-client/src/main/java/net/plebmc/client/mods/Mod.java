package net.plebmc.client.mods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.plebmc.client.apollo.Apollo;
import net.plebmc.client.apollo.module.ModSettingModule;

/**
 * One client mod. {@link #id} doubles as the Apollo mod_setting key, so
 * a server can force it off with "&lt;id&gt;.enabled" = false (for example a
 * server blocking zoom) - that always wins over the player's toggle.
 */
public abstract class Mod {

        private final String id;
        private final String name;
        private final String description;
        private boolean enabled;
        /** Top-left as a fraction of the screen (HUD mods). */
        float fx;
        float fy;
        private final float defaultFx;
        private final float defaultFy;

        protected Mod(String id, String name, String description, boolean enabledByDefault, float fx, float fy) {
                this.id = id;
                this.name = name;
                this.description = description;
                this.enabled = enabledByDefault;
                this.fx = fx;
                this.fy = fy;
                this.defaultFx = fx;
                this.defaultFy = fy;
        }

        /** Back to where the mod puts its HUD element by default. */
        public final void resetPosition() {
                fx = defaultFx;
                fy = defaultFy;
        }

        public final String id() {
                return id;
        }

        public final String name() {
                return name;
        }

        public final String description() {
                return description;
        }

        /** The player's own toggle. */
        public final boolean isEnabled() {
                return enabled;
        }

        public final void setEnabled(boolean on) {
                enabled = on;
        }

        /** True while the connected server forbids this mod. */
        public boolean isBlockedByServer() {
                ModSettingModule ms = Apollo.module(ModSettingModule.class);
                return ms != null && ms.isModBlocked(id);
        }

        /** Enabled by the player and allowed by the server. */
        public final boolean isActive() {
                return enabled && !isBlockedByServer();
        }

        /** True when the saved x is the box's center (stays centered across screen sizes). */
        public boolean centerAnchored() {
                return false;
        }

        /** HUD mods draw a box; functional mods (zoom, fullbright) return false. */
        public boolean hasHud() {
                return false;
        }

        /** The HUD element, or null when there is nothing to show. */
        public HudBox layout(ClientView view, HudBox.Measure m) {
                return null;
        }

        /** Representative content for the HUD editor when the live view has none. */
        public HudBox preview(ClientView view, HudBox.Measure m) {
                return layout(view, m);
        }

        // ==================== categories and settings ====================

        public static final String HUD = "HUD";
        public static final String GAMEPLAY = "Gameplay";
        public static final String PLEBNITE = "PlebNite";
        public static final String PERFORMANCE = "Performance";

        private String category = GAMEPLAY;
        private final List<Setting> settings = new ArrayList<>();

        /** One option on a mod's settings page: a choice among labels, stored as an index. */
        public static final class Setting {
                public final String key;
                public final String name;
                public final String description;
                public final String[] labels;
                public final int def;
                private int value;

                Setting(String key, String name, String description, String[] labels, int def) {
                        this.key = key;
                        this.name = name;
                        this.description = description;
                        this.labels = labels;
                        this.def = def;
                        this.value = def;
                }

                public int get() {
                        return value;
                }

                public void set(int v) {
                        value = v < 0 || v >= labels.length ? def : v;
                }

                public void next() {
                        value = (value + 1) % labels.length;
                }

                public String label() {
                        return labels[value];
                }

                public boolean on() {
                        return value != 0;
                }

                /** Free-text settings (custom labels): the typed text, or the default. */
                private String defText;
                private String text;

                public boolean isText() {
                        return labels == TEXT;
                }

                public String text() {
                        return text == null ? "" : text;
                }

                public String defText() {
                        return defText == null ? "" : defText;
                }

                public void setText(String t) {
                        if (t == null) {
                                text = defText;
                                return;
                        }
                        // one line, no section signs, kept short
                        t = t.replace('\n', ' ').replace('\u00a7', ' ');
                        text = t.length() > 24 ? t.substring(0, 24) : t;
                }
        }

        public static final String[] OFF_ON = { "Off", "On" };
        /** Marks a free-text setting. */
        public static final String[] TEXT = { "" };

        protected final Setting textSetting(String key, String name, String description, String def) {
                Setting st = new Setting(key, name, description, TEXT, 0);
                st.defText = def;
                st.text = def;
                settings.add(st);
                return st;
        }

        protected final Setting setting(String key, String name, String description, String[] labels, int def) {
                Setting st = new Setting(key, name, description, labels, def);
                settings.add(st);
                return st;
        }

        public final List<Setting> settings() {
                return Collections.unmodifiableList(settings);
        }

        public final Setting setting(String key) {
                for (Setting st : settings) {
                        if (st.key.equals(key)) {
                                return st;
                        }
                }
                return null;
        }

        public final String category() {
                return category;
        }

        protected final void category(String c) {
                category = c;
        }

        /** Puts every option back to its default (not the on/off switch or the position). */
        public void resetSettings() {
                for (Setting st : settings) {
                        st.set(st.def);
                        if (st.isText()) {
                                st.setText(null);
                        }
                }
        }

        // ==================== HUD look (every HUD mod) ====================

        public static final float[] SCALES = { 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f };
        public static final String[] SCALE_NAMES = { "50%", "75%", "100%", "125%", "150%", "200%" };
        public static final int[] COLORS = { 0xFFFFFFFF, 0xFF4FE3F0, 0xFF6EE78A, 0xFFF5C542, 0xFFFF6B6B, 0xFFFF8AD8, 0xFFB8C4CC };
        public static final String[] COLOR_NAMES = { "White", "Cyan", "Green", "Gold", "Red", "Pink", "Gray" };
        public static final int[] BACKGROUNDS = { 0x00000000, 0x40000000, 0x6F000000, 0xA0000000 };
        public static final String[] BACKGROUND_NAMES = { "None", "Light", "Normal", "Dark" };

        private Setting hudScale;
        private Setting hudColor;
        private Setting hudBackground;

        /** Adds the scale / text color / background options; called by HUD mods. */
        protected final void hudLook(boolean colorable) {
                category(HUD);
                hudScale = setting("scale", "Size", "How big this element is drawn", SCALE_NAMES, 2);
                if (colorable) {
                        hudColor = setting("color", "Text color", "Color of the text", COLOR_NAMES, 0);
                }
                hudBackground = setting("background", "Background", "The box behind the element", BACKGROUND_NAMES, 2);
        }

        public float hudScale() {
                return hudScale == null ? 1f : SCALES[hudScale.get()];
        }

        /** The text color, or 0 to keep the element's own colors. */
        public int hudTextColor() {
                return hudColor == null || hudColor.get() == 0 ? 0 : COLORS[hudColor.get()];
        }

        /** The background color, or -1 to keep the element's own. */
        public int hudBackground() {
                return hudBackground == null || hudBackground.get() == 2 ? -1 : BACKGROUNDS[hudBackground.get()];
        }

        protected static List<String> one(String s) {
                return Collections.singletonList(s);
        }

        public final float fx() {
                return fx;
        }

        public final float fy() {
                return fy;
        }

        public final void setPosition(float x, float y) {
                fx = Math.max(0f, Math.min(1f, x));
                fy = Math.max(0f, Math.min(1f, y));
        }
}
