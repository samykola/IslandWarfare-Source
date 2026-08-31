package com.islandwarfare.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Base holder for all IslandWarfare GUIs. Using a typed holder lets a single
 * click listener route events without parsing inventory titles.
 */
public abstract class IWGuiHolder implements InventoryHolder {

    public enum Type { ISLAND, CLAN, CLAN_MEMBERS, CLAN_PERMISSIONS, SHOP, RAID }

    private final Type type;
    private Inventory inventory;
    private UUID contextPlayer; // e.g. target member being edited in the permissions GUI

    protected IWGuiHolder(Type type) {
        this.type = type;
    }

    public Type getType() { return type; }

    @Override
    public Inventory getInventory() { return inventory; }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    public UUID getContextPlayer() { return contextPlayer; }
    public void setContextPlayer(UUID contextPlayer) { this.contextPlayer = contextPlayer; }
}
