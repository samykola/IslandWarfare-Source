package com.islandwarfare.resource;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.config.ConfigManager;
import com.islandwarfare.territory.TerritoryZone;
import com.islandwarfare.territory.ZoneType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Resource nodes live inside RESOURCE-type {@link TerritoryZone}s. Nodes are
 * registered once (an admin-triggered, bounded scan of a single zone's
 * volume - never a whole-world scan) and from then on are tracked purely by
 * lookup key, so runtime break handling is O(1) and schedules a single
 * delayed task per depletion rather than polling.
 */
public class ResourceManager {

    private final IslandWarfare plugin;
    private final ConfigManager cfg;

    private final Map<String, ResourceNode> nodes = new HashMap<>();

    public ResourceManager(IslandWarfare plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
    }

    public Map<String, ResourceNode> getNodes() { return nodes; }

    public ResourceNode registerLoadedNode(ResourceNode node) {
        nodes.put(node.key(), node);
        return node;
    }

    /**
     * Scans the vertical column range within the given zone's horizontal
     * radius for blocks matching any configured resource node type, and
     * registers each match. This is a bounded, one-time admin operation -
     * not something run automatically or repeatedly.
     */
    public int scanZone(TerritoryZone zone, int minY, int maxY) {
        if (zone.getType() != ZoneType.RESOURCE) return 0;
        org.bukkit.World world = Bukkit.getWorld(zone.getWorldName());
        if (world == null) return 0;

        Map<Material, String> materialToType = new HashMap<>();
        ConfigurationSection section = cfg.getResourceNodesSection();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String matName = section.getString(key + ".block");
                Material mat = Material.matchMaterial(matName == null ? "" : matName);
                if (mat != null) materialToType.put(mat, key);
            }
        }
        if (materialToType.isEmpty()) return 0;

        int radius = zone.getRadius();
        int found = 0;
        int minX = (int) (zone.getCenterX() - radius);
        int maxX = (int) (zone.getCenterX() + radius);
        int minZ = (int) (zone.getCenterZ() - radius);
        int maxZ = (int) (zone.getCenterZ() + radius);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double dx = x - zone.getCenterX();
                double dz = z - zone.getCenterZ();
                if (dx * dx + dz * dz > (double) radius * radius) continue;
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    String type = materialToType.get(block.getType());
                    if (type == null) continue;
                    ResourceNode node = new ResourceNode(world.getName(), x, y, z, type, block.getType());
                    nodes.put(node.key(), node);
                    found++;
                }
            }
        }
        return found;
    }

    public ResourceNode getNodeAt(Block block) {
        return nodes.get(block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ());
    }

    /**
     * Handles a player breaking a registered node. Cancels the natural
     * drop (so yield is exactly config-controlled, preventing duplication
     * via Fortune/Silk Touch/etc.) and grants the configured reward once,
     * then schedules the node to respawn.
     *
     * @return true if this break was handled as a resource node (caller should cancel default drops).
     */
    public boolean handleBreak(Player player, Block block, ResourceNode node) {
        ConfigurationSection section = cfg.getResourceNodesSection();
        if (section == null) return false;
        ConfigurationSection typeSection = section.getConfigurationSection(node.getNodeType());
        if (typeSection == null) return false;

        if (node.isDepleted()) {
            return true; // already harvested, block break is cancelled by caller - nothing to give
        }

        double moneyYield = typeSection.getDouble("yield-money", 0);
        String itemName = typeSection.getString("yield-item");
        int itemAmount = typeSection.getInt("yield-amount", 1);
        int respawnSeconds = typeSection.getInt("respawn-seconds", 300);

        double multiplier = plugin.getEventManager() != null ? plugin.getEventManager().getResourceRushMultiplier() : 1.0;

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan != null) {
            double researchBonus = plugin.getResearchManager() != null
                    ? plugin.getResearchManager().getEffectBonus(clan.getId(), "MINING_BONUS") : 0;
            double finalMoney = moneyYield * multiplier * (1 + researchBonus / 100.0);
            if (finalMoney > 0) clan.deposit(finalMoney);
        }

        if (itemName != null) {
            Material itemMat = Material.matchMaterial(itemName);
            if (itemMat != null) {
                int finalAmount = (int) Math.max(1, Math.round(itemAmount * multiplier));
                java.util.Map<Integer, org.bukkit.inventory.ItemStack> overflow =
                        player.getInventory().addItem(new org.bukkit.inventory.ItemStack(itemMat, finalAmount));
                for (org.bukkit.inventory.ItemStack extra : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), extra);
                }
            }
        }

        block.setType(Material.AIR);
        long respawnAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(respawnSeconds);
        node.deplete(respawnAt);

        Bukkit.getScheduler().runTaskLater(plugin, () -> respawnNode(node), respawnSeconds * 20L);
        return true;
    }

    private void respawnNode(ResourceNode node) {
        org.bukkit.World world = Bukkit.getWorld(node.getWorld());
        if (world == null) return;
        Block block = world.getBlockAt(node.getX(), node.getY(), node.getZ());
        if (block.getType() == Material.AIR) {
            block.setType(node.getOriginalBlock());
        }
        node.restock();
    }

    /** Re-schedules respawn for nodes that were still depleted at server restart. */
    public void rescheduleAfterLoad() {
        long now = System.currentTimeMillis();
        for (ResourceNode node : nodes.values()) {
            if (!node.isDepleted()) continue;
            long delayMillis = Math.max(0, node.getRespawnAt() - now);
            Bukkit.getScheduler().runTaskLater(plugin, () -> respawnNode(node), delayMillis / 50L);
        }
    }
}
