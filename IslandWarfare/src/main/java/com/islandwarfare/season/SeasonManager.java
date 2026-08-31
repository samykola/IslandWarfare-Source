package com.islandwarfare.season;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.config.ConfigManager;
import com.islandwarfare.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SeasonManager {

    private final IslandWarfare plugin;
    private final ConfigManager cfg;

    private int seasonNumber = 1;
    private long seasonStartedAt;
    private long seasonEndsAt;
    private final List<SeasonResult> lastResults = new ArrayList<>();

    private BukkitTask checkTask;

    public SeasonManager(IslandWarfare plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
    }

    public void start() {
        if (!cfg.isSeasonEnabled()) return;
        if (seasonStartedAt <= 0) {
            beginSeason(1);
        }
        // Checking every 10 minutes is more than enough resolution for a multi-day season length.
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L * 60L * 10, 20L * 60L * 10);
    }

    public void stop() {
        if (checkTask != null) checkTask.cancel();
    }

    private void tick() {
        if (cfg.isSeasonAutoReset() && System.currentTimeMillis() >= seasonEndsAt) {
            endSeason();
        }
    }

    private void beginSeason(int number) {
        this.seasonNumber = number;
        this.seasonStartedAt = System.currentTimeMillis();
        this.seasonEndsAt = seasonStartedAt + TimeUnit.DAYS.toMillis(cfg.getSeasonLengthDays());
        Bukkit.broadcastMessage(com.islandwarfare.utils.MessageUtil.color(cfg.getPrefix() + cfg.getMessage("season-started")));
    }

    /** Snapshots rankings, broadcasts the results, optionally resets stats, then starts the next season. */
    public List<SeasonResult> endSeason() {
        List<SeasonResult> results = computeRankings();
        lastResults.clear();
        lastResults.addAll(results);

        Bukkit.broadcastMessage(com.islandwarfare.utils.MessageUtil.color(cfg.getPrefix() + cfg.getMessage("season-ended")));
        int rank = 1;
        for (SeasonResult result : results) {
            if (rank > 5) break;
            Bukkit.broadcastMessage(com.islandwarfare.utils.MessageUtil.color(
                    "&e#" + rank + " &f" + result.getClanName() + " &7[" + result.getClanTag() + "] &7- Power: &f"
                            + String.format("%.0f", result.getPower())));
            rank++;
        }

        if (cfg.isSeasonResetRaidStats()) {
            for (Clan clan : plugin.getClanManager().getClans()) {
                clan.resetRaidStats();
            }
        }
        if (cfg.isSeasonResetTerritory()) {
            plugin.getTerritoryManager().resetAllOwnership();
        }

        beginSeason(seasonNumber + 1);
        return results;
    }

    public List<SeasonResult> computeRankings() {
        List<SeasonResult> results = new ArrayList<>();
        for (Clan clan : plugin.getClanManager().getClans()) {
            Island island = plugin.getIslandManager().getIslandByClan(clan.getId());
            int territories = plugin.getTerritoryManager() != null ? plugin.getTerritoryManager().countOwnedBy(clan.getId()) : 0;
            double power = plugin.getClanPowerService().calculate(clan);
            results.add(new SeasonResult(clan.getName(), clan.getTag(), power, clan.getRaidWins(), clan.getRaidLosses(),
                    territories, island != null ? island.getLevel() : 0));
        }
        results.sort(Comparator.comparingDouble(SeasonResult::getPower).reversed());
        return results;
    }

    public int getSeasonNumber() { return seasonNumber; }
    public long getSeasonStartedAt() { return seasonStartedAt; }
    public long getSeasonEndsAt() { return seasonEndsAt; }
    public List<SeasonResult> getLastResults() { return lastResults; }

    public void restore(int number, long startedAt, long endsAt) {
        this.seasonNumber = number;
        this.seasonStartedAt = startedAt;
        this.seasonEndsAt = endsAt;
    }
}
