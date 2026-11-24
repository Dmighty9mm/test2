package net.plebmc.client.mods;

/**
 * The world border as PlebNite uses it: the storm. Read from the vanilla
 * border packets every server already sends, so it works on any server
 * that moves the border.
 */
public final class Storm {
        /** Borders this big are the vanilla default (no storm in this world). */
        public static final double NO_STORM = 1_000_000;

        public final double centerX;
        public final double centerZ;
        /** Current width of the safe square. */
        public final double size;
        /** Where it is moving to (== size when still). */
        public final double target;
        /** Milliseconds until it reaches {@link #target}; 0 when still. */
        public final long msLeft;
        /** Blocks from the player to the nearest edge; negative when the player is outside. */
        public final double edge;

        public Storm(double centerX, double centerZ, double size, double target, long msLeft, double edge) {
                this.centerX = centerX;
                this.centerZ = centerZ;
                this.size = size;
                this.target = target;
                this.msLeft = msLeft;
                this.edge = edge;
        }

        public boolean shrinking() {
                return target < size - 0.01 && msLeft > 0;
        }

        public boolean growing() {
                return target > size + 0.01 && msLeft > 0;
        }

        public boolean inside() {
                return edge >= 0;
        }
}
