package com.islandwarfare.gui;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.utils.ItemBuilder;
import com.islandwarfare.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;

public class ShopGUI {

    public static void open(IslandWarfare plugin, Player player) {
        Map<Material, Double> prices = plugin.getEconomyService().getShopPrices();

        IWGuiHolder holder = new IWGuiHolder(IWGuiHolder.Type.SHOP) {};
        int size = Math.min(54, Math.max(9, ((prices.size() / 9) + 1) * 9));
        Inventory inv = Bukkit.createInventory(holder, size, MessageUtil.color(plugin.getConfigManager().getGuiTitle("shop-title")));
        holder.setInventory(inv);

        int slot = 0;
        for (Map.Entry<Material, Double> entry : prices.entrySet()) {
            if (slot >= inv.getSize()) break;
            inv.setItem(slot, new ItemBuilder(entry.getKey())
                    .name("&f" + prettify(entry.getKey().name()))
                    .lore(
                            "&7Price: &a$" + entry.getValue() + " &7each",
                            "",
                            "&eClick to sell all of this item",
                            "&7from your inventory."
                    ).build());
            slot++;
        }

        player.openInventory(inv);
    }

    private static String prettify(String name) {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            sb.append(part.charAt(0)).append(part.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }
}
