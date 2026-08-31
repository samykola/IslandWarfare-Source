package com.islandwarfare.territory;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.config.ConfigManager;
import com.islandwarfare.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Territory capture is resolved by a single scheduled tick (once per
 * second) that makes one pass over currently online players (already a
 * bounded, necessary cost for any live PvP game) and buckets them into
 * zones - never a world/chunk scan. Zone count is expected to stay small
 * (tens, not thousands), so the players x zones check stays cheap.
 */
public class TerritoryManager {

    private final IslandWarfare plugin;
    private final ConfigManager cfg;

    private final Map<Integer, TerritoryZone> zones = new LinkedHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);
    private BukkitTask tickTask;

    public TerritoryManager(IslandWarfare plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
    }

    public void start() {
        if (!cfg.isTerritoryEnabled()) return;
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (tickTask != null) tickTask.cancel();
    }

    public Collection<TerritoryZone> getZones() { return zones.values(); }

    public TerritoryZone getZone(int id) { return zones.get(id); }

    public TerritoryZone registerLoadedZone(TerritoryZone zone) {
        zones.put(zone.getId(), zone);
        idCounter.set(Math.max(idCounter.get(), zone.getId() + 1));
        return zone;
    }

    public TerritoryZone createZone(ZoneType type, String world, double x, double y, double z, int radius) {
        int id = idCounter.getAndIncrement();
        TerritoryZone zone = new TerritoryZone(id, type, world, x, y, z, radius);
        zones.put(id, zone);
        return zone;
    }

    public void removeZone(int id) {
        zones.remove(id);
    }

    public TerritoryZone getZoneAt(String world, double x, double z) {
        for (TerritoryZone zone : zones.values()) {
            if (zone.contains(world, x, z)) return zone;
        }
        return null;
    }

    public int countOwnedBy(UUID clanId) {
        int count = 0;
        for (TerritoryZone zone : zones.values()) {
            if (clanId.equals(zone.getOwnerClanId())) count++;
        }
        return count;
    }

    private void tick() {
        if (zones.isEmpty()) return;

        // zoneId -> (clanId -> playerCount)
        Map<Integer, Map<UUID, Integer>> presence = new HashMap<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
            if (clan == null) continue;
            TerritoryZone zone = getZoneAt(player.getWorld().getName(), player.getLocation().getX(), player.getLocation().getZ());
            if (zone == null) continue;
            presence.computeIfAbsent(zone.getId(), k -> new HashMap<>())
                    .merge(clan.getId(), 1, Integer::sum);
        }

        for (TerritoryZone zone : zones.values()) {
            Map<UUID, Integer> clanCounts = presence.getOrDefault(zone.getId(), Map.of());
            UUID dominant = findDominantClan(clanCounts);

            if (dominant == null || dominant.equals(zone.getOwnerClanId())) {
                // Secure (or contested to a tie / nobody present): decay any in-progress capture.
                decayProgress(zone);
                continue;
            }

            if (!dominant.equals(zone.getCapturingClanId())) {
                zone.setCapturingClanId(dominant);
                zone.setCaptureProgress(0);
            }
            zone.setCaptureProgress(zone.getCaptureProgress() + 1.0);

            if (zone.getCaptureProgress() >= cfg.getTerritoryCaptureSeconds()) {
                completeCapture(zone, dominant);
            }
        }
    }

    private UUID findDominantClan(Map<UUID, Integer> counts) {
        if (counts.isEmpty()) return null;
        UUID best = null;
        int bestCount = 0;
        boolean tie = false;
        for (Map.Entry<UUID, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
                tie = false;
            } else if (entry.getValue() == bestCount) {
                tie = true;
            }
        }
        if (cfg.isTerritoryContestRequiresMajority() && tie) return null;
        return best;
    }

    private void decayProgress(TerritoryZone zone) {
        if (zone.getCapturingClanId() == null) return;
        double newProgress = zone.getCaptureProgress() - cfg.getTerritoryDecayPerSecond();
        if (newProgress <= 0) {
            zone.setCaptureProgress(0);
            zone.setCapturingClanId(null);
        } else {
            zone.setCaptureProgress(newProgress);
        }
    }

    private void completeCapture(TerritoryZone zone, UUID newOwner) {
        UUID previousOwner = zone.getOwnerClanId();
        Clan owningClan = plugin.getClanManager().getClanById(newOwner);
        if (owningClan == null) return;

        int maxLevel = plugin.getIslandManager().getIslandByClan(newOwner) != null
                ? plugin.getIslandManager().getIslandByClan(newOwner).getLevel() : 1;
        int limit = cfg.getMaxTerritory(maxLevel);
        if (countOwnedBy(newOwner) >= limit) {
            // Capped out - freeze progress just below the threshold instead of losing it entirely.
            zone.setCaptureProgress(cfg.getTerritoryCaptureSeconds() - 1);
            notifyClan(newOwner, cfg.getMessage("territory-max-reached"));
            return;
        }

        zone.setOwnerClanId(newOwner);
        zone.setCaptureProgress(0);
        zone.setCapturingClanId(null);

        notifyClan(newOwner, cfg.getMessage("territory-claimed").replace("%territory%", zone.getName()));
        if (previousOwner != null) {
            notifyClan(previousOwner, cfg.getMessage("territory-lost").replace("%territory%", zone.getName()));
        }
    }

    private void notifyClan(UUID clanId, String message) {
        Clan clan = plugin.getClanManager().getClanById(clanId);
        if (clan == null) return;
        for (UUID memberId : clan.getMembers().keySet()) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null) {
                MessageUtil.send(player, cfg.getPrefix(), message);
            }
        }
    }

    /** Used by SeasonManager when a season resets territory control. */
    public void resetAllOwnership() {
        for (TerritoryZone zone : zones.values()) {
            zone.setOwnerClanId(null);
            zone.setCapturingClanId(null);
            zone.setCaptureProgress(0);
        }
    }
}
