package com.islandwarfare.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public final class ItemBuilder {

    private final ItemStack stack;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.stack = new ItemStack(material);
        this.meta = stack.getItemMeta();
    }

    public ItemBuilder(Material material, int amount) {
        this.stack = new ItemStack(material, Math.max(1, amount));
        this.meta = stack.getItemMeta();
    }

    public ItemBuilder name(String name) {
        if (meta != null) meta.setDisplayName(MessageUtil.color(name));
        return this;
    }

    public ItemBuilder lore(String... lines) {
        List<String> lore = new ArrayList<>();
        for (String line : lines) lore.add(MessageUtil.color(line));
        if (meta != null) meta.setLore(lore);
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        List<String> colored = new ArrayList<>();
        for (String line : lines) colored.add(MessageUtil.color(line));
        if (meta != null) meta.setLore(colored);
        return this;
    }

    public ItemBuilder skullOwner(String ownerName) {
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(ownerName));
        }
        return this;
    }

    public ItemStack build() {
        stack.setItemMeta(meta);
        return stack;
    }
}
