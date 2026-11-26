package net.plebmc.client.mods;

/**
 * This client's own identity, shown in its menus. Never another client's
 * name: Apollo channel ids are protocol identifiers only.
 */
public final class ClientInfo {

        public static final String NAME = "PlebMc";
        public static final String VERSION = net.plebmc.client.cosmetics.UpdateCheck.VERSION;

        private ClientInfo() {
        }
}
