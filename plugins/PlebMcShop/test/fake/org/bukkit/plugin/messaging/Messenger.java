package org.bukkit.plugin.messaging;
public interface Messenger {
    void registerOutgoingPluginChannel(org.bukkit.plugin.Plugin p, String c);
    PluginMessageListenerRegistration registerIncomingPluginChannel(org.bukkit.plugin.Plugin p, String c, PluginMessageListener l);
    void unregisterOutgoingPluginChannel(org.bukkit.plugin.Plugin p);
    void unregisterIncomingPluginChannel(org.bukkit.plugin.Plugin p);
}
