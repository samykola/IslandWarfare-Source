package com.islandwarfare.gui;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.clan.ClanMember;
import com.islandwarfare.clan.IslandFlag;
import com.islandwarfare.utils.ItemBuilder;
import com.islandwarfare.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

public class ClanPermissionsGUI {

    private static final IslandFlag[] FLAGS = IslandFlag.values();

    public static void open(IslandWarfare plugin, Player viewer, UUID target) {
        Clan clan = plugin.getClanManager().getClanByPlayer(viewer.getUniqueId());
        if (clan == null) return;
        ClanMember member = clan.getMember(target);
        if (member == null) return;

        IWGuiHolder holder = new IWGuiHolder(IWGuiHolder.Type.CLAN_PERMISSIONS) {};
        Inventory inv = Bukkit.createInventory(holder, 9, MessageUtil.color(plugin.getConfigManager().getGuiTitle("clan-permissions-title")) + " - " + member.getName());
        holder.setInventory(inv);
        holder.setContextPlayer(target);

        for (int i = 0; i < FLAGS.length; i++) {
            IslandFlag flag = FLAGS[i];
            boolean granted = member.getRole() == com.islandwarfare.clan.ClanRole.LEADER || member.getPermissions().contains(flag);
            Material mat = granted ? Material.LIME_DYE : Material.GRAY_DYE;
            inv.setItem(i, new ItemBuilder(mat)
                    .name((granted ? "&a" : "&7") + flag.name())
                    .lore("&7Click to toggle", "&7Currently: " + (granted ? "&aGRANTED" : "&cDENIED"))
                    .build());
        }

        viewer.openInventory(inv);
    }
}
