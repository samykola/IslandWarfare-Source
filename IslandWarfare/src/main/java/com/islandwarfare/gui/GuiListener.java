package com.islandwarfare.gui;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.clan.ClanMember;
import com.islandwarfare.clan.ClanRole;
import com.islandwarfare.clan.IslandFlag;
import com.islandwarfare.config.ConfigManager;
import com.islandwarfare.island.Island;
import com.islandwarfare.raid.RaidPhase;
import com.islandwarfare.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class GuiListener implements Listener {

    private static final Map<Inventory, Map<Integer, UUID>> MEMBER_SLOTS = new WeakHashMap<>();
    private static final Map<Inventory, Map<Integer, UUID>> RAID_SLOTS = new WeakHashMap<>();

    private final IslandWarfare plugin;
    private final ConfigManager cfg;

    public GuiListener(IslandWarfare plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
    }

    public static void registerMemberSlotMap(Inventory inv, Map<Integer, UUID> map) {
        MEMBER_SLOTS.put(inv, map);
    }

    public static void registerRaidSlotMap(Inventory inv, Map<Integer, UUID> map) {
        RAID_SLOTS.put(inv, map);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof IWGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        switch (holder.getType()) {
            case ISLAND -> handleIsland(player, slot, event.isShiftClick());
            case CLAN -> handleClan(player, slot, event.isShiftClick());
            case CLAN_MEMBERS -> handleMembers(player, event.getInventory(), slot, event.isRightClick(), event.isShiftClick());
            case CLAN_PERMISSIONS -> handlePermissions(player, holder, slot);
            case SHOP -> handleShop(player, event.getCurrentItem());
            case RAID -> handleRaid(player, event.getInventory(), slot);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        MEMBER_SLOTS.remove(event.getInventory());
        RAID_SLOTS.remove(event.getInventory());
    }

    // ---------------- Island ----------------

    private void handleIsland(Player player, int slot, boolean shift) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) return;
        Island island = plugin.getIslandManager().getIslandByClan(clan.getId());
        if (island == null) return;

        if (slot == 13) {
            plugin.getIslandHomeService().teleportHome(player, clan, island);
        } else if (slot == 15) {
            plugin.getIslandUpgradeService().attemptUpgrade(player, clan, island);
            player.closeInventory();
        } else if (slot == 22) {
            island.setHome(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ(),
                    player.getLocation().getYaw(), player.getLocation().getPitch());
            MessageUtil.send(player, cfg.getPrefix(), "&aIsland home set to your current location.");
        }
    }

    // ---------------- Clan ----------------

    private void handleClan(Player player, int slot, boolean shift) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) return;

        if (slot == 11) {
            ClanMembersGUI.open(plugin, player);
        } else if (slot == 15) {
            ClanMembersGUI.open(plugin, player); // pick a member first, then open their permissions
            MessageUtil.send(player, cfg.getPrefix(), "&7Left click a member to edit their permissions.");
        } else if (slot == 22 && shift) {
            ClanMember me = clan.getMember(player.getUniqueId());
            if (me != null && me.getRole() == ClanRole.LEADER) {
                plugin.getClanManager().disbandClan(clan.getId());
                MessageUtil.send(player, cfg.getPrefix(), "&cYour clan has been disbanded.");
                player.closeInventory();
            }
        }
    }

    // ---------------- Clan members ----------------

    private void handleMembers(Player player, Inventory inv, int slot, boolean rightClick, boolean shift) {
        Map<Integer, UUID> slotMap = MEMBER_SLOTS.get(inv);
        if (slotMap == null) return;
        UUID target = slotMap.get(slot);
        if (target == null) return;

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) return;

        if (rightClick && shift) {
            if (!clan.isOfficerOrLeader(player.getUniqueId())) {
                MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("not-clan-officer"));
                return;
            }
            if (clan.isLeader(target)) return;
            ClanMember targetMember = clan.getMember(target);
            String name = targetMember != null ? targetMember.getName() : "player";
            plugin.getClanManager().removeMember(clan, target);
            MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("player-kicked").replace("%player%", name));
            player.closeInventory();
        } else {
            ClanPermissionsGUI.open(plugin, player, target);
        }
    }

    private void handlePermissions(Player player, IWGuiHolder holder, int slot) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) return;
        if (!clan.isOfficerOrLeader(player.getUniqueId())) {
            MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("not-clan-officer"));
            return;
        }
        UUID target = holder.getContextPlayer();
        if (target == null) return;
        ClanMember member = clan.getMember(target);
        if (member == null) return;
        if (member.getRole() == ClanRole.LEADER) return; // leaders always have full access

        IslandFlag[] flags = IslandFlag.values();
        if (slot < 0 || slot >= flags.length) return;
        IslandFlag flag = flags[slot];
        if (member.getPermissions().contains(flag)) {
            member.revoke(flag);
        } else {
            member.grant(flag);
        }
        ClanPermissionsGUI.open(plugin, player, target);
    }

    // ---------------- Shop ----------------

    private void handleShop(Player player, ItemStack clicked) {
        if (clicked == null) return;
        Material material = clicked.getType();

        int amount = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                amount += stack.getAmount();
            }
        }
        if (amount <= 0) {
            MessageUtil.send(player, cfg.getPrefix(), "&cYou don't have any of that item to sell.");
            return;
        }

        ItemStack fullStack = new ItemStack(material, amount);
        double total = plugin.getEconomyService().sell(player, fullStack);
        if (total < 0) {
            MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("no-clan"));
            return;
        }
        player.getInventory().removeItem(new ItemStack(material, amount));
        MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("sold-items").replace("%amount%", String.format("%.2f", total)));
    }

    // ---------------- Raid ----------------

    private void handleRaid(Player player, Inventory inv, int slot) {
        Map<Integer, UUID> slotMap = RAID_SLOTS.get(inv);
        if (slotMap == null) return;
        UUID targetClanId = slotMap.get(slot);
        if (targetClanId == null) return;

        if (plugin.getRaidManager().getCurrentPhase() != RaidPhase.WAR) {
            MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("raid-not-active"));
            return;
        }

        Clan attacker = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        Clan defender = plugin.getClanManager().getClanById(targetClanId);
        if (attacker == null || defender == null) return;
        if (attacker.getId().equals(defender.getId())) {
            MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("raid-cannot-attack-own"));
            return;
        }
        if (!plugin.getRaidManager().canAttack(attacker, defender)) {
            MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("raid-power-too-different"));
            return;
        }

        Island targetIsland = plugin.getIslandManager().getIslandByClan(defender.getId());
        if (targetIsland == null) return;

        plugin.getRaidManager().startAttack(attacker, targetIsland);
        MessageUtil.send(player, cfg.getPrefix(), "&aYou are now raiding &f" + defender.getName() + "&a!");
        for (UUID memberId : defender.getMembers().keySet()) {
            Player defenderPlayer = Bukkit.getPlayer(memberId);
            if (defenderPlayer != null) {
                MessageUtil.send(defenderPlayer, cfg.getPrefix(), cfg.getMessage("raid-attack-started").replace("%clan%", attacker.getName()));
            }
        }
        player.closeInventory();
    }
}
