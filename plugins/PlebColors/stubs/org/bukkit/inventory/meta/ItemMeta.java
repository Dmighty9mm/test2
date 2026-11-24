package org.bukkit.inventory.meta;
public interface ItemMeta {
    boolean hasDisplayName();
    boolean hasLore();
    net.md_5.bungee.api.chat.BaseComponent[] getDisplayNameComponent();
    void setDisplayNameComponent(net.md_5.bungee.api.chat.BaseComponent[] c);
    java.util.List<net.md_5.bungee.api.chat.BaseComponent[]> getLoreComponents();
    void setLoreComponents(java.util.List<net.md_5.bungee.api.chat.BaseComponent[]> c);
}
