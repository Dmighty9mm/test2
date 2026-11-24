package net.plebmc.client.mods;

/** One teammate the server listed (Apollo teams), as the squad HUD shows them. */
public final class Mate {
        public final String name;
        public final int argb;
        /** Blocks away, or -1 when the server didn't say where they are. */
        public final int distance;
        /** Health in half hearts' units (20 = full), or -1 when they aren't loaded near you. */
        public final float health;
        public final float maxHealth;
        /** World bearing to them (vanilla yaw that faces them), or NaN. */
        public final float bearing;

        public Mate(String name, int argb, int distance, float health, float maxHealth, float bearing) {
                this.name = name == null ? "" : name;
                this.argb = argb;
                this.distance = distance;
                this.health = health;
                this.maxHealth = maxHealth;
                this.bearing = bearing;
        }
}
