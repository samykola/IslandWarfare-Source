package com.islandwarfare.gui;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.island.Island;
import com.islandwarfare.raid.RaidPhase;
import com.islandwarfare.utils.ItemBuilder;
import com.islandwarfare.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class RaidGUI {

    public static void open(IslandWarfare plugin, Player player) {
        IWGuiHolder holder = new IWGuiHolder(IWGuiHolder.Type.RAID) {};
        Inventory inv = Bukkit.createInventory(holder, 54, MessageUtil.color(plugin.getConfigManager().getGuiTitle("raid-title")));
        holder.setInventory(inv);

        RaidPhase phase = plugin.getRaidManager().getCurrentPhase();
        inv.setItem(4, new ItemBuilder(Material.CLOCK)
                .name("&ePhase: &f" + phase)
                .lore("&7Time remaining: &f" + plugin.getRaidManager().getRemainingFormatted())
                .build());

        Clan myClan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        Map<Integer, UUID> slotToIsland = new LinkedHashMap<>();
        int slot = 9;
        for (Island island : plugin.getIslandManager().getIslands()) {
            if (slot >= 54) break;
            Clan owner = plugin.getClanManager().getClanById(island.getOwnerClanId());
            if (owner == null) continue;
            if (myClan != null && owner.getId().equals(myClan.getId())) continue;

            com.islandwarfare.raid.ActiveRaid activeRaid = plugin.getRaidManager().getActiveRaid(island.getId());
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("&7Island Level: &f" + island.getLevel());
            lore.add("&7Status: &f" + island.getStatus());
            if (activeRaid != null) {
                lore.add("&7Stage: &f" + activeRaid.getStage());
                lore.add("&7Capture: &f" + String.format("%.0f", activeRaid.getCapturePercent()) + "%");
                lore.add("&7Defense bonus: &f+" + activeRaid.getDefenseBonusSeconds() + "s");
            }
            lore.add("");
            lore.add(phase == RaidPhase.WAR ? "&cClick to attack!" : "&7Attacks only available during WAR phase.");

            inv.setItem(slot, new ItemBuilder(Material.RED_BANNER)
                    .name("&c" + owner.getName() + " &7[" + owner.getTag() + "]")
                    .lore(lore).build());
            slotToIsland.put(slot, owner.getId());
            slot++;
        }
        GuiListener.registerRaidSlotMap(inv, slotToIsland);

        player.openInventory(inv);
    }
}
