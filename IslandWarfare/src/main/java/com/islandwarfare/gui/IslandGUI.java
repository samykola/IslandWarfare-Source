package com.islandwarfare.gui;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.island.Island;
import com.islandwarfare.utils.ItemBuilder;
import com.islandwarfare.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class IslandGUI {

    public static final String SLOT_INFO = "info";

    public static void open(IslandWarfare plugin, Player player) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            MessageUtil.send(player, plugin.getConfigManager().getPrefix(), plugin.getConfigManager().getMessage("no-clan"));
            return;
        }
        Island island = plugin.getIslandManager().getIslandByClan(clan.getId());

        IWGuiHolder holder = new IWGuiHolder(IWGuiHolder.Type.ISLAND) {};
        Inventory inv = Bukkit.createInventory(holder, 27, MessageUtil.color(plugin.getConfigManager().getGuiTitle("island-title")));
        holder.setInventory(inv);

        if (island == null) {
            inv.setItem(13, new ItemBuilder(Material.BARRIER).name("&cNo island assigned").build());
        } else {
            int nextLevel = island.getLevel() + 1;
            double cost = plugin.getConfigManager().getUpgradeCost(nextLevel);
            boolean maxed = island.getLevel() >= plugin.getConfigManager().getMaxIslandLevel();

            inv.setItem(11, new ItemBuilder(Material.GRASS_BLOCK)
                    .name("&aIsland Info")
                    .lore(
                            "&7Level: &f" + island.getLevel(),
                            "&7Size: &f" + (island.getRadius() * 2) + "x" + (island.getRadius() * 2),
                            "&7Status: &f" + island.getStatus(),
                            "&7Members: &f" + clan.getMemberCount() + "/" + plugin.getConfigManager().getMaxMembers(island.getLevel())
                    ).build());

            inv.setItem(13, new ItemBuilder(Material.ENDER_PEARL)
                    .name("&bTeleport Home")
                    .lore("&7Click to teleport to your island home.").build());

            inv.setItem(15, new ItemBuilder(maxed ? Material.GRAY_DYE : Material.EMERALD)
                    .name(maxed ? "&7Max Level Reached" : "&aUpgrade Island")
                    .lore(maxed ? java.util.List.of("&7Your island is already at the max level.") :
                            java.util.List.of("&7Upgrade to level " + nextLevel, "&7Cost: &f$" + cost, "", "&eClick to upgrade")).build());

            inv.setItem(22, new ItemBuilder(Material.COMPASS)
                    .name("&eSet Home")
                    .lore("&7Click to set your island home to your current location.").build());
        }

        player.openInventory(inv);
    }
}
