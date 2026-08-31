package com.islandwarfare.raid;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.clan.IslandFlag;
import com.islandwarfare.config.ConfigManager;
import com.islandwarfare.island.Island;
import com.islandwarfare.island.IslandStatus;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Drives the server-wide PREPARATION -> WAR -> COOLDOWN cycle AND the real
 * per-raid objective flow:
 *
 *   Attack -> Infiltration -> Objective -> Capture Timer -> Defense -> Win/Loss -> Loot
 *
 * The "objective" is the defender's island home location. Once
 * {@code /raid attack} opens a window, a lightweight per-second tick
 * (only runs while at least one raid is active, and only makes a single
 * pass over online players - the same bounded-cost pattern used by
 * TerritoryManager) checks who is standing within capture radius:
 *   - only attacker clan present  -> progress accumulates (CAPTURING)
 *   - any defender clan member present -> progress freezes/decays (CONTESTED)
 *   - nobody present              -> progress decays (INFILTRATION)
 * Reaching 100% pays out loot exactly once (CAPTURED). Running out of time
 * first means the defenders win and nothing is paid out (DEFENDED).
 *
 * Base defense is real and skill-based, not pay-to-win: at raid start the
 * plugin does a single bounded scan of the configured defense material
 * within the capture radius (built by the defending clan) and adds time
 * to the capture timer accordingly, capped by config.
 */
public class RaidManager {

    private final IslandWarfare plugin;
    private final ConfigManager cfg;

    private RaidPhase currentPhase = RaidPhase.PREPARATION;
    private long phaseEndsAt;

    private final Map<Integer, ActiveRaid> activeRaids = new HashMap<>();
    private BukkitTask phaseTickTask;
    private BukkitTask captureTickTask;

    public RaidManager(IslandWarfare plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
    }

    public void start() {
        if (phaseEndsAt <= 0) {
            beginPhase(RaidPhase.PREPARATION);
        }
        // Phase advancement is cheap timestamp comparison - checked every 20 seconds.
        phaseTickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::phaseTick, 20L * 20L, 20L * 20L);
        // Capture progress needs 1-second resolution, but only runs real work when raids exist.
        captureTickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::captureTick, 20L, 20L);
    }

    public void stop() {
        if (phaseTickTask != null) phaseTickTask.cancel();
        if (captureTickTask != null) captureTickTask.cancel();
    }

    private void phaseTick() {
        if (System.currentTimeMillis() >= phaseEndsAt) {
            advancePhase();
        }
        activeRaids.values().removeIf(raid -> {
            if (raid.isExpired() && raid.getStage() != RaidStage.CAPTURED) {
                resolveDefended(raid);
                return true;
            }
            return false;
        });
        for (Island island : plugin.getIslandManager().getIslands()) {
            if (island.getStatus() == IslandStatus.UNDER_RAID && !activeRaids.containsKey(island.getId())) {
                island.setStatus(IslandStatus.ACTIVE);
            }
        }
    }

    private void advancePhase() {
        switch (currentPhase) {
            case PREPARATION -> beginPhase(RaidPhase.WAR);
            case WAR -> {
                for (ActiveRaid raid : activeRaids.values()) {
                    if (raid.getStage() != RaidStage.CAPTURED) resolveDefended(raid);
                }
                activeRaids.clear();
                for (Island island : plugin.getIslandManager().getIslands()) {
                    island.setStatus(IslandStatus.ACTIVE);
                }
                beginPhase(RaidPhase.COOLDOWN);
            }
            case COOLDOWN -> beginPhase(RaidPhase.PREPARATION);
        }
    }

    private void beginPhase(RaidPhase phase) {
        this.currentPhase = phase;
        long hours = switch (phase) {
            case PREPARATION -> cfg.getPreparationHours();
            case WAR -> cfg.getWarHours();
            case COOLDOWN -> cfg.getCooldownHours();
        };
        this.phaseEndsAt = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(hours);

        if (phase == RaidPhase.WAR) {
            Bukkit.broadcastMessage(com.islandwarfare.utils.MessageUtil.color(cfg.getPrefix() + cfg.getMessage("raid-started")));
        } else if (phase == RaidPhase.PREPARATION) {
            Bukkit.broadcastMessage(com.islandwarfare.utils.MessageUtil.color(cfg.getPrefix() + cfg.getMessage("raid-ended")));
        }
    }

    public RaidPhase getCurrentPhase() { return currentPhase; }

    public long getPhaseEndsAt() { return phaseEndsAt; }

    public long getRemainingMillis() { return Math.max(0, phaseEndsAt - System.currentTimeMillis()); }

    public String getRemainingFormatted() {
        long millis = getRemainingMillis();
        long h = TimeUnit.MILLISECONDS.toHours(millis);
        long m = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long s = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    /** Restores a persisted phase/end time on startup (used by storage loader). */
    public void restorePhase(RaidPhase phase, long phaseEndsAt) {
        this.currentPhase = phase;
        this.phaseEndsAt = phaseEndsAt;
        if (this.phaseEndsAt <= System.currentTimeMillis()) {
            beginPhase(phase); // expired while offline, restart this phase fresh
        }
    }

    /** Admin override: immediately force the server into a given phase. */
    public void forcePhase(RaidPhase phase) {
        if (phase == RaidPhase.PREPARATION) {
            activeRaids.clear();
            for (Island island : plugin.getIslandManager().getIslands()) {
                island.setStatus(IslandStatus.ACTIVE);
            }
        }
        beginPhase(phase);
    }

    // ---------------- Attacks ----------------

    public boolean canAttack(Clan attacker, Clan defender) {
        if (currentPhase != RaidPhase.WAR) return false;
        if (attacker.getId().equals(defender.getId())) return false;

        // Real, wired fairness guard rail: when configured (>0), a clan
        // significantly more powerful than its target cannot attack it -
        // prevents strong clans from endlessly farming weak ones.
        double maxDiffPercent = cfg.getMaxPowerDifferencePercent();
        if (maxDiffPercent > 0) {
            double attackerPower = plugin.getClanPowerService().calculate(attacker);
            double defenderPower = plugin.getClanPowerService().calculate(defender);
            double allowedCeiling = defenderPower * (1 + maxDiffPercent / 100.0);
            if (attackerPower > allowedCeiling) return false;
        }
        return true;
    }

    public ActiveRaid startAttack(Clan attacker, Island targetIsland) {
        long duration = TimeUnit.MINUTES.toMillis(cfg.getAttackDurationMinutes());
        int defenseBonusSeconds = scanDefenseBonus(targetIsland);

        // Real, wired effect of the defender's DEFENSE_BONUS research: adds
        // a percentage on top of the base+block-scan capture requirement.
        Clan defender = plugin.getClanManager().getClanById(targetIsland.getOwnerClanId());
        double researchDefensePercent = defender != null && plugin.getResearchManager() != null
                ? plugin.getResearchManager().getEffectBonus(defender.getId(), "DEFENSE_BONUS") : 0;

        double required = (cfg.getCaptureSeconds() + defenseBonusSeconds) * (1 + researchDefensePercent / 100.0);

        ActiveRaid raid = new ActiveRaid(targetIsland.getId(), attacker.getId(), targetIsland.getOwnerClanId(),
                duration, required, defenseBonusSeconds);
        activeRaids.put(targetIsland.getId(), raid);
        targetIsland.setStatus(IslandStatus.UNDER_RAID);
        return raid;
    }

    /**
     * One-time, bounded scan (capture radius only, +/-6 blocks vertically
     * around the island home) counting the configured defense material.
     * Real, build-based defense - not purchasable.
     */
    private int scanDefenseBonus(Island island) {
        Material defenseMaterial = Material.matchMaterial(cfg.getDefenseMaterial());
        if (defenseMaterial == null) return 0;

        Location home = island.getHomeLocation();
        if (home.getWorld() == null) return 0;

        int radius = cfg.getCaptureRadius();
        int blockCount = 0;
        int baseX = home.getBlockX();
        int baseY = home.getBlockY();
        int baseZ = home.getBlockZ();

        for (int x = baseX - radius; x <= baseX + radius; x++) {
            for (int z = baseZ - radius; z <= baseZ + radius; z++) {
                double dx = x - home.getX();
                double dz = z - home.getZ();
                if (dx * dx + dz * dz > (double) radius * radius) continue;
                for (int y = baseY - 6; y <= baseY + 6; y++) {
                    Block block = home.getWorld().getBlockAt(x, y, z);
                    if (block.getType() == defenseMaterial) blockCount++;
                }
            }
        }

        double bonusSeconds = Math.min(blockCount * cfg.getDefenseSecondsPerBlock(), cfg.getDefenseMaxAddedSeconds());
        return (int) Math.round(bonusSeconds);
    }

    /**
     * Runs every second. Skips all work instantly if there are no active
     * raids (the overwhelmingly common case), and otherwise makes exactly
     * one pass over online players, bucketing presence per active raid's
     * island - never a world/chunk scan.
     */
    private void captureTick() {
        if (activeRaids.isEmpty()) return;

        // Resolve any raid whose attack window ran out as DEFENDED right
        // away (every second) instead of waiting up to 20s for the slower
        // phase-tick sweep - and stops it from progressing any further.
        activeRaids.values().removeIf(raid -> {
            if (raid.isExpired() && raid.getStage() != RaidStage.CAPTURED) {
                resolveDefended(raid);
                return true;
            }
            return false;
        });
        if (activeRaids.isEmpty()) return;

        Map<Integer, Integer> attackerPresence = new HashMap<>();
        Map<Integer, Integer> defenderPresence = new HashMap<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
            if (clan == null) continue;

            for (ActiveRaid raid : activeRaids.values()) {
                Island island = plugin.getIslandManager().getIsland(raid.getIslandId());
                if (island == null) continue;
                Location home = island.getHomeLocation();
                if (home.getWorld() == null || !home.getWorld().equals(player.getWorld())) continue;
                if (player.getLocation().distanceSquared(home) > (double) cfg.getCaptureRadius() * cfg.getCaptureRadius()) continue;

                if (clan.getId().equals(raid.getAttackerClanId())) {
                    attackerPresence.merge(raid.getIslandId(), 1, Integer::sum);
                } else if (clan.getId().equals(raid.getDefenderClanId())) {
                    defenderPresence.merge(raid.getIslandId(), 1, Integer::sum);
                }
            }
        }

        for (ActiveRaid raid : activeRaids.values()) {
            if (raid.getStage() == RaidStage.CAPTURED) continue;

            int attackers = attackerPresence.getOrDefault(raid.getIslandId(), 0);
            int defenders = defenderPresence.getOrDefault(raid.getIslandId(), 0);

            RaidStage previousStage = raid.getStage();

            if (attackers > 0 && defenders == 0) {
                raid.setStage(RaidStage.CAPTURING);
                raid.setCaptureProgressSeconds(raid.getCaptureProgressSeconds() + 1.0);
            } else if (defenders > 0) {
                raid.setStage(RaidStage.CONTESTED);
                raid.setCaptureProgressSeconds(raid.getCaptureProgressSeconds() - cfg.getCaptureDecayPerSecond());
            } else {
                raid.setStage(RaidStage.INFILTRATION);
                raid.setCaptureProgressSeconds(raid.getCaptureProgressSeconds() - cfg.getCaptureDecayPerSecond());
            }

            if (previousStage != RaidStage.CONTESTED && raid.getStage() == RaidStage.CONTESTED) {
                notifyDefenseInterrupt(raid);
            }
        }

        // Resolve completions in a second pass, after the iteration above has
        // fully finished - resolveCaptured() removes from activeRaids, and
        // mutating the map mid-iteration would throw ConcurrentModificationException.
        java.util.List<ActiveRaid> justCaptured = new java.util.ArrayList<>();
        for (ActiveRaid raid : activeRaids.values()) {
            if (raid.getStage() != RaidStage.CAPTURED && raid.isComplete()) {
                justCaptured.add(raid);
            }
        }
        for (ActiveRaid raid : justCaptured) {
            resolveCaptured(raid);
        }
    }

    private void resolveCaptured(ActiveRaid raid) {
        raid.setStage(RaidStage.CAPTURED);
        Island island = plugin.getIslandManager().getIsland(raid.getIslandId());
        Clan attacker = plugin.getClanManager().getClanById(raid.getAttackerClanId());
        Clan defender = plugin.getClanManager().getClanById(raid.getDefenderClanId());
        if (attacker == null || defender == null) return;

        if (cfg.isRaidLootAllowed()) {
            processLoot(raid, attacker, defender);
        }
        attacker.incrementRaidWins();
        defender.incrementRaidLosses();

        notifyClan(defender.getId(), cfg.getMessage("raid-capture-complete"));
        notifyClan(attacker.getId(), "&a&lYou captured " + defender.getName() + "'s base!");

        if (island != null) island.setStatus(IslandStatus.ACTIVE);
        activeRaids.remove(raid.getIslandId());
    }

    private void resolveDefended(ActiveRaid raid) {
        Island island = plugin.getIslandManager().getIsland(raid.getIslandId());
        Clan defender = plugin.getClanManager().getClanById(raid.getDefenderClanId());
        if (defender != null) {
            notifyClan(defender.getId(), cfg.getMessage("raid-defended"));
        }
        if (island != null) island.setStatus(IslandStatus.ACTIVE);
    }

    private void notifyDefenseInterrupt(ActiveRaid raid) {
        notifyClan(raid.getAttackerClanId(), cfg.getMessage("raid-capture-interrupted"));
        notifyClan(raid.getDefenderClanId(), cfg.getMessage("raid-capture-interrupted"));
    }

    private void notifyClan(UUID clanId, String message) {
        Clan clan = plugin.getClanManager().getClanById(clanId);
        if (clan == null) return;
        for (UUID memberId : clan.getMembers().keySet()) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null) {
                com.islandwarfare.utils.MessageUtil.send(player, cfg.getPrefix(), message);
            }
        }
    }

    private void processLoot(ActiveRaid raid, Clan attacker, Clan defender) {
        if (raid.isLootClaimed()) return;

        if (cfg.isLootMoney()) {
            double percent = cfg.getLootPercentage() / 100.0;
            double amount = defender.getBalance() * percent;
            if (defender.withdraw(amount)) {
                attacker.deposit(amount);
            }
        }
        raid.setLootClaimed(true);
    }

    public ActiveRaid getActiveRaid(int islandId) {
        ActiveRaid raid = activeRaids.get(islandId);
        if (raid != null && raid.isExpired() && raid.getStage() != RaidStage.CAPTURED) return null;
        return raid;
    }

    public boolean isIslandUnderActiveRaidBy(Island island, Clan attackerClan) {
        if (attackerClan == null) return false;
        ActiveRaid raid = getActiveRaid(island.getId());
        return raid != null && raid.getAttackerClanId().equals(attackerClan.getId());
    }

    public boolean isRaidActionAllowed(IslandFlag flag, Location location, Island island) {
        if (currentPhase != RaidPhase.WAR) return false;
        double dist = island.distanceToCenter(location.getX(), location.getZ());
        if (dist > cfg.getRaidZoneRadius()) return false;

        return switch (flag) {
            case BUILD, BREAK -> cfg.isRaidBreakAllowed();
            case CONTAINER, PICKUP -> cfg.isRaidLootAllowed();
            case USE, INTERACT -> true;
        };
    }

    public boolean isCombatAllowedBetween(UUID clanA, UUID clanB) {
        if (clanA == null || clanB == null) return false;
        if (clanA.equals(clanB)) return cfg.isFriendlyFire();
        if (currentPhase != RaidPhase.WAR) return false;
        return cfg.isRaidCombatEnabled();
    }
}
