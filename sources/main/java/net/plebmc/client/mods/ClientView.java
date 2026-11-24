package net.plebmc.client.mods;

import java.util.List;

/**
 * What the HUD mods read from the running game. The browser glue
 * implements it over Minecraft/LocalPlayer; tests use a fake.
 */
public interface ClientView {

        int fps();

        double x();

        double y();

        double z();

        /** Player yaw in degrees (vanilla: 0 = +Z south, 90 = -X west). */
        float yaw();

        /** Round-trip latency in ms, or -1 when unknown (singleplayer). */
        int pingMs();

        /** "HH:mm" local wall-clock time. */
        String localTime();

        /** Movement / interaction key state by vanilla key name: "forward", "left", "back", "right", "jump". */
        boolean keyDown(String key);

        /** 0 = attack (left), 1 = use (right). */
        boolean mouseDown(int button);

        /** Clicks per second over the last second for a button. */
        int cps(int button);

        /** Worn armor, head first; null for an empty slot. */
        List<Gear> armor();

        /** The item in the player's hand, or null. */
        default Gear held() {
                return null;
        }

        /** Active effects as "Name II 0:45". */
        List<String> effects();

        /** Own health (20 = ten hearts), max health and absorption; -1 health when not in a world. */
        default float health() {
                return -1f;
        }

        default float maxHealth() {
                return 20f;
        }

        default float absorption() {
                return 0f;
        }

        /** How many of each supply group the inventory holds (see {@link Mods.Supplies#GROUPS}). */
        default int supply(String group) {
                return 0;
        }

        /** The storm (world border), or null when this world has none. */
        default Storm storm() {
                return null;
        }

        /** Teammates the server listed, without yourself. */
        default List<Mate> squad() {
                return java.util.Collections.emptyList();
        }
}
