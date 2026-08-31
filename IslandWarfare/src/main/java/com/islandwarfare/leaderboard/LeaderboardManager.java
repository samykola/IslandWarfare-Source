package com.islandwarfare.leaderboard;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.island.Island;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Computed on demand rather than cached/looped continuously - leaderboards
 * are only requested occasionally (command/GUI usage), so there is no
 * benefit to maintaining a live-updating background task for them.
 */
public class LeaderboardManager {

    private final IslandWarfare plugin;

    public LeaderboardManager(IslandWarfare plugin) {
        this.plugin = plugin;
    }

    public List<Clan> richestClans(int limit) {
        return plugin.getClanManager().getClans().stream()
                .sorted(Comparator.comparingDouble(Clan::getBalance).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Clan> highestIslandLevel(int limit) {
        return plugin.getClanManager().getClans().stream()
                .sorted(Comparator.comparingInt(this::islandLevelOf).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Clan> mostRaidWins(int limit) {
        return plugin.getClanManager().getClans().stream()
                .sorted(Comparator.comparingInt(Clan::getRaidWins).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Clan> mostRaidLosses(int limit) {
        return plugin.getClanManager().getClans().stream()
                .sorted(Comparator.comparingInt(Clan::getRaidLosses).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Clan> highestPower(int limit) {
        return plugin.getClanManager().getClans().stream()
                .sorted(Comparator.comparingDouble((Clan c) -> plugin.getClanPowerService().calculate(c)).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<Clan> mostTerritory(int limit) {
        return plugin.getClanManager().getClans().stream()
                .sorted(Comparator.comparingInt((Clan c) -> plugin.getTerritoryManager().countOwnedBy(c.getId())).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private int islandLevelOf(Clan clan) {
        Island island = plugin.getIslandManager().getIslandByClan(clan.getId());
        return island != null ? island.getLevel() : 0;
    }
}
