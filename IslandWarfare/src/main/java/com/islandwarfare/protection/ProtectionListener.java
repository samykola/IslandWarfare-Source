package com.islandwarfare.protection;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.clan.ClanMember;
import com.islandwarfare.clan.IslandFlag;
import com.islandwarfare.config.ConfigManager;
import com.islandwarfare.island.Island;
import com.islandwarfare.raid.RaidManager;
import com.islandwarfare.utils.MessageUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;


/**
 * Central protection gate: every island-affecting event is routed through
 * {@link #isAllowed(Player, Island, IslandFlag)} so raid-phase overrides and
 * per-player permissions only need to be implemented once.
 */
public class ProtectionListener implements Listener {

    private final IslandWarfare plugin;
    private final ConfigManager cfg;

    public ProtectionListener(IslandWarfare plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
    }

    /**
     * Core permission check. Returns true if the action should be allowed.
     */
    public boolean isAllowed(Player player, Island island, IslandFlag flag) {
        if (!cfg.isProtectionEnabled()) return true;
        if (player.hasPermission("islandwarfare.bypass.protection")) return true;
        if (island == null) return true; // outside any island - no island protection applies

        Clan owningClan = plugin.getClanManager().getClanById(island.getOwnerClanId());
        Clan playerClan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());

        // Owning clan members: allowed based on role/permission flags.
        if (owningClan != null && playerClan != null && owningClan.getId().equals(playerClan.getId())) {
            ClanMember member = owningClan.getMember(player.getUniqueId());
            return member != null && member.hasFlag(flag);
        }

        // Not the owning clan: check if a raid is active and this action is permitted under raid rules.
        RaidManager raidManager = plugin.getRaidManager();
        if (raidManager.isIslandUnderActiveRaidBy(island, playerClan)) {
            return raidManager.isRaidActionAllowed(flag, player.getLocation(), island);
        }

        return false;
    }

    private void deny(Player player) {
        MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("island-protected"));
    }

    /**
     * Handles breaking a registered resource node: if the node's zone is
     * owned by a clan, only that clan may harvest it (this is what makes
     * territory control over resource zones actually matter). Unclaimed
     * zones are open to anyone. Returns true if this block was a resource
     * node (whether or not the break was allowed), so the caller stops
     * processing normal island/bridge rules for it.
     */
    private boolean handleResourceNode(BlockBreakEvent event) {
        var node = plugin.getResourceManager().getNodeAt(event.getBlock());
        if (node == null) return false;

        Player player = event.getPlayer();
        var zone = plugin.getTerritoryManager().getZoneAt(event.getBlock().getWorld().getName(),
                event.getBlock().getX(), event.getBlock().getZ());

        if (zone != null && !zone.isUnclaimed() && !player.hasPermission("islandwarfare.bypass.protection")) {
            Clan owningClan = plugin.getClanManager().getClanById(zone.getOwnerClanId());
            Clan playerClan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
            boolean sameClan = owningClan != null && playerClan != null && owningClan.getId().equals(playerClan.getId());
            if (!sameClan) {
                event.setCancelled(true);
                MessageUtil.send(player, cfg.getPrefix(), "&cThis resource zone is controlled by another clan.");
                return true;
            }
        }

        if (node.isDepleted()) {
            event.setCancelled(true);
            return true;
        }

        event.setDropItems(false);
        plugin.getResourceManager().handleBreak(player, event.getBlock(), node);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        // Resource nodes take priority: they live in neutral/resource zones,
        // completely separate from island protection.
        if (handleResourceNode(event)) return;

        Island island = plugin.getIslandManager().getIslandAt(event.getBlock().getWorld().getName(),
                event.getBlock().getX(), event.getBlock().getZ());
        if (island == null) {
            checkBridge(event);
            return;
        }
        if (!isAllowed(event.getPlayer(), island, IslandFlag.BREAK)) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Island island = plugin.getIslandManager().getIslandAt(event.getBlock().getWorld().getName(),
                event.getBlock().getX(), event.getBlock().getZ());
        if (island == null) return; // building on open terrain/bridges is allowed (only breaking bridges is restricted)
        if (!isAllowed(event.getPlayer(), island, IslandFlag.BUILD)) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    /**
     * Bridges are just the world terrain that connects islands - anything
     * broken outside every registered island bounding box is treated as
     * bridge/terrain and follows the bridge.* config rules.
     */
    private void checkBridge(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("islandwarfare.bypass.protection")) return;
        if (!event.getBlock().getWorld().getName().equals(cfg.getWorldName())) return;

        if (!cfg.isBridgeDestructible()) {
            event.setCancelled(true);
            deny(player);
            return;
        }
        if (cfg.isBridgeDestructibleDuringWarOnly()
                && plugin.getRaidManager().getCurrentPhase() != com.islandwarfare.raid.RaidPhase.WAR) {
            event.setCancelled(true);
            MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("bridge-protected"));
            return;
        }
        java.util.List<String> allowedClans = cfg.getBridgeAllowedClans();
        if (!allowedClans.isEmpty()) {
            Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
            if (clan == null || allowedClans.stream().noneMatch(c -> c.equalsIgnoreCase(clan.getTag()) || c.equalsIgnoreCase(clan.getName()))) {
                event.setCancelled(true);
                MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("bridge-protected"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Island island = plugin.getIslandManager().getIslandAt(block.getWorld().getName(), block.getX(), block.getZ());
        if (island == null) return;

        Material type = block.getType();
        IslandFlag flag = classifyBlock(type);
        if (flag == null) return;

        if (!isAllowed(event.getPlayer(), island, flag)) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    private IslandFlag classifyBlock(Material type) {
        String name = type.name();
        if (name.contains("CHEST") || name.contains("BARREL") || name.contains("SHULKER")
                || name.contains("FURNACE") || name.contains("HOPPER") || name.contains("DISPENSER")
                || name.contains("DROPPER") || name.contains("BREWING_STAND")) {
            return IslandFlag.CONTAINER;
        }
        if (name.contains("DOOR") || name.contains("TRAPDOOR") || name.contains("GATE")) {
            return IslandFlag.USE;
        }
        if (name.contains("BUTTON") || name.contains("LEVER") || name.contains("PRESSURE_PLATE")) {
            return IslandFlag.USE;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        InventoryHolder holder = inv.getHolder();
        if (holder == null) return;
        Block block = holder instanceof org.bukkit.block.Container container ? container.getBlock() : null;
        if (block == null) return;

        Island island = plugin.getIslandManager().getIslandAt(block.getWorld().getName(), block.getX(), block.getZ());
        if (island == null) return;

        if (!isAllowed(player, island, IslandFlag.CONTAINER)) {
            event.setCancelled(true);
            deny(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Island island = plugin.getIslandManager().getIslandAt(player.getWorld().getName(), player.getLocation().getX(), player.getLocation().getZ());
        if (island == null) return;
        if (!isAllowed(player, island, IslandFlag.PICKUP)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Island island = plugin.getIslandManager().getIslandAt(player.getWorld().getName(), player.getLocation().getX(), player.getLocation().getZ());
        if (island == null) return;
        if (!isAllowed(player, island, IslandFlag.PICKUP)) {
            event.setCancelled(true);
        }
    }
}
