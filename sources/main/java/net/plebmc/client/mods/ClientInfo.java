package net.plebmc.client.mods;

/**
 * This client's own identity, shown in its menus. Never another client's
 * name: Apollo channel ids are protocol identifiers only.
 */
public final class ClientInfo {

        public static final String NAME = "PlebClient";
        /** The version of this build. Raise it for each release and set "latest" on the panel to match. */
        public static final String VERSION = net.plebmc.client.cosmetics.UpdateCheck.VERSION;

        private ClientInfo() {
        }
}
