package com.islandwarfare.gui;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.clan.ClanMember;
import com.islandwarfare.utils.ItemBuilder;
import com.islandwarfare.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.LinkedHashMap;
import java.util.Map;

public class ClanMembersGUI {

    public static void open(IslandWarfare plugin, Player player) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) return;

        IWGuiHolder holder = new IWGuiHolder(IWGuiHolder.Type.CLAN_MEMBERS) {};
        int size = Math.max(9, ((clan.getMemberCount() / 9) + 1) * 9);
        Inventory inv = Bukkit.createInventory(holder, Math.min(54, size), MessageUtil.color(plugin.getConfigManager().getGuiTitle("clan-members-title")));
        holder.setInventory(inv);

        // Keep a stable slot -> uuid map for click handling.
        Map<Integer, java.util.UUID> slotMap = new LinkedHashMap<>();
        int slot = 0;
        for (ClanMember member : clan.getMembers().values()) {
            if (slot >= inv.getSize()) break;
            ItemStack head = new ItemBuilder(Material.PLAYER_HEAD)
                    .name("&f" + member.getName())
                    .lore(
                            "&7Role: &f" + member.getRole(),
                            "&7Permissions: &f" + member.getPermissions(),
                            "",
                            "&eLeft click: open permissions",
                            "&cShift-right click: kick"
                    ).skullOwner(member.getName()).build();
            inv.setItem(slot, head);
            slotMap.put(slot, member.getUuid());
            slot++;
        }

        GuiListener.registerMemberSlotMap(inv, slotMap);
        player.openInventory(inv);
    }
}
