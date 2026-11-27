package org.bukkit.inventory;
public class ItemStack implements Cloneable {
    public boolean hasItemMeta() { return false; }
    public org.bukkit.inventory.meta.ItemMeta getItemMeta() { return null; }
    public boolean setItemMeta(org.bukkit.inventory.meta.ItemMeta m) { return false; }
    public ItemStack clone() { return null; }
}
