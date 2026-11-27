package net.plebmc.client.mods;

/**
 * One item the player wears or carries, as the HUD mods see it. {@link #stack}
 * is the game's own ItemStack (null in tests and editor previews), which the
 * HUD renderer draws as the real item icon; {@link #id} ("diamond_helmet")
 * lets it draw a stand-in when there is no stack.
 */
public final class Gear {
        public final Object stack;
        public final String id;
        public final String name;
        public final int count;
        /** Uses left and the item's total; max 0 = the item doesn't wear out. */
        public final int durability;
        public final int maxDurability;

        public Gear(Object stack, String id, String name, int count, int durability, int maxDurability) {
                this.stack = stack;
                this.id = id == null ? "" : id;
                this.name = name == null ? "" : name;
                this.count = count;
                this.durability = durability;
                this.maxDurability = maxDurability;
        }

        public boolean damageable() {
                return maxDurability > 0;
        }

        /** 0..1 uses left (1 for items that don't wear out). */
        public float fraction() {
                return maxDurability <= 0 ? 1f : Math.max(0f, Math.min(1f, durability / (float) maxDurability));
        }

        /** Green when fresh, yellow at half, red when nearly broken (vanilla's bar colors). */
        public int color() {
                float f = fraction();
                if (f > 0.6f) {
                        return 0xFF55FF55;
                }
                if (f > 0.3f) {
                        return 0xFFFFFF55;
                }
                if (f > 0.12f) {
                        return 0xFFFFAA00;
                }
                return 0xFFFF5555;
        }
}
