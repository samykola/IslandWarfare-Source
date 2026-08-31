package com.islandwarfare.gui;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.clan.ClanRole;
import com.islandwarfare.utils.ItemBuilder;
import com.islandwarfare.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class ClanGUI {

    public static void open(IslandWarfare plugin, Player player) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            MessageUtil.send(player, plugin.getConfigManager().getPrefix(), plugin.getConfigManager().getMessage("no-clan"));
            return;
        }

        IWGuiHolder holder = new IWGuiHolder(IWGuiHolder.Type.CLAN) {};
        Inventory inv = Bukkit.createInventory(holder, 27, MessageUtil.color(plugin.getConfigManager().getGuiTitle("clan-title")));
        holder.setInventory(inv);

        inv.setItem(11, new ItemBuilder(Material.PLAYER_HEAD)
                .name("&bClan Members")
                .lore("&7View and manage members.", "&7Members: &f" + clan.getMemberCount()).build());

        inv.setItem(13, new ItemBuilder(Material.BOOK)
                .name("&aClan Info")
                .lore(List.of(
                        "&7Name: &f" + clan.getName(),
                        "&7Tag: &f" + clan.getTag(),
                        "&7Balance: &f$" + String.format("%.2f", clan.getBalance()),
                        "&7Raid Wins: &f" + clan.getRaidWins(),
                        "&7Raid Losses: &f" + clan.getRaidLosses()
                )).build());

        inv.setItem(15, new ItemBuilder(Material.WRITABLE_BOOK)
                .name("&ePermissions")
                .lore("&7Manage member island permissions.").build());

        ClanRole role = clan.getMember(player.getUniqueId()).getRole();
        if (role == ClanRole.LEADER) {
            inv.setItem(22, new ItemBuilder(Material.TNT)
                    .name("&cDisband Clan")
                    .lore("&7Shift click to permanently disband your clan.").build());
        }

        player.openInventory(inv);
    }
}
