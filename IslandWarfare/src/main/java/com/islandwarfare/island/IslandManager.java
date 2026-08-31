package com.islandwarfare.island;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns the island registry. Placement uses a simple expanding spiral over a
 * fixed grid (island.grid-spacing) so we never have to scan the whole world:
 * each candidate slot is checked only against the bounding boxes already in
 * the registry (an O(n) in-memory check, not a world scan).
 */
public class IslandManager {

    private final IslandWarfare plugin;
    private final ConfigManager cfg;

    private final Map<Integer, Island> islandsById = new HashMap<>();
    private final Map<UUID, Integer> clanToIsland = new HashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);

    public IslandManager(IslandWarfare plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
    }

    public Collection<Island> getIslands() {
        return islandsById.values();
    }

    public Island getIsland(int id) {
        return islandsById.get(id);
    }

    public Island getIslandByClan(UUID clanId) {
        Integer id = clanToIsland.get(clanId);
        return id == null ? null : islandsById.get(id);
    }

    /**
     * Real, wired effect of protection.protect-outside-island-radius-buffer:
     * extends the protected area slightly beyond the island's exact
     * schematic bounds, so players can't stand just outside the boundary
     * line and grief the edge blocks. Only used by protection checks -
     * placement/collision logic uses island.min-gap separately.
     */
    public Island getIslandAt(String world, double x, double z) {
        double buffer = plugin.getConfigManager().getProtectionBuffer();
        for (Island island : islandsById.values()) {
            if (island.getWorldName().equals(world) && island.containsWithBuffer(x, z, buffer)) {
                return island;
            }
        }
        return null;
    }

    public void registerLoadedIsland(Island island) {
        islandsById.put(island.getId(), island);
        clanToIsland.put(island.getOwnerClanId(), island.getId());
        idCounter.set(Math.max(idCounter.get(), island.getId() + 1));
    }

    /**
     * Finds a free grid slot, creates the Island record and pastes the
     * level-1 schematic there. Returns the created Island (paste happens
     * synchronously on the calling thread's next tick via SchematicService).
     */
    public Island createIslandForClan(UUID clanId, int level) {
        World world = Bukkit.getWorld(cfg.getWorldName());
        if (world == null) {
            plugin.getLogger().severe("Island world '" + cfg.getWorldName() + "' is not loaded!");
            return null;
        }

        int size = cfg.getIslandSize(level);
        int radius = size / 2;
        int[] slot = findFreeSlot(radius);

        int id = idCounter.getAndIncrement();
        double centerX = slot[0];
        double centerZ = slot[1];
        double centerY = cfg.getPasteY();

        Island island = new Island(id, clanId, world.getName(), centerX, centerY, centerZ, level, radius);
        islandsById.put(id, island);
        clanToIsland.put(clanId, id);

        plugin.getSchematicService().pasteIslandSchematic(island, level);
        return island;
    }

    /**
     * Expanding spiral search over the fixed grid. Only checks against
     * islands already registered in memory - no world scanning involved.
     */
    private int[] findFreeSlot(int radius) {
        int spacing = cfg.getGridSpacing();
        int gap = cfg.getMinGap();

        int x = 0, z = 0;
        int dx = 0, dz = -1;
        int maxSteps = 100000;

        for (int i = 0; i < maxSteps; i++) {
            if (isSlotFree(x * spacing, z * spacing, radius, gap)) {
                return new int[]{x * spacing, z * spacing};
            }
            // spiral step
            if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                int temp = dx;
                dx = -dz;
                dz = temp;
            }
            x += dx;
            z += dz;
        }
        // Fallback: far away deterministic slot based on registry size
        int fallback = islandsById.size() + 1;
        return new int[]{fallback * spacing * 4, fallback * spacing * 4};
    }

    private boolean isSlotFree(int centerX, int centerZ, int radius, int gap) {
        double minX = centerX - radius - gap;
        double maxX = centerX + radius + gap;
        double minZ = centerZ - radius - gap;
        double maxZ = centerZ + radius + gap;
        for (Island island : islandsById.values()) {
            if (island.getMinX() < maxX && island.getMaxX() > minX
                    && island.getMinZ() < maxZ && island.getMaxZ() > minZ) {
                return false;
            }
        }
        return true;
    }

    /**
     * Upgrades an island to the next level: resizes the bounding box and
     * pastes the new schematic on top, centered on the same point.
     */
    public boolean upgradeIsland(Island island, int newLevel) {
        int newSize = cfg.getIslandSize(newLevel);
        int newRadius = newSize / 2;

        // Ensure the bigger footprint still doesn't collide with a neighbor.
        double minX = island.getCenterX() - newRadius - cfg.getMinGap();
        double maxX = island.getCenterX() + newRadius + cfg.getMinGap();
        double minZ = island.getCenterZ() - newRadius - cfg.getMinGap();
        double maxZ = island.getCenterZ() + newRadius + cfg.getMinGap();
        for (Island other : islandsById.values()) {
            if (other.getId() == island.getId()) continue;
            if (other.getMinX() < maxX && other.getMaxX() > minX
                    && other.getMinZ() < maxZ && other.getMaxZ() > minZ) {
                return false; // would collide with a neighboring island
            }
        }

        island.setLevel(newLevel);
        island.setRadius(newRadius);
        plugin.getSchematicService().pasteIslandSchematic(island, newLevel);
        return true;
    }

    public int countIslands() {
        return islandsById.size();
    }

    /** Removes an island from the registry entirely (admin use). */
    public void removeIsland(int id) {
        Island island = islandsById.remove(id);
        if (island != null) {
            clanToIsland.remove(island.getOwnerClanId());
        }
    }
}
