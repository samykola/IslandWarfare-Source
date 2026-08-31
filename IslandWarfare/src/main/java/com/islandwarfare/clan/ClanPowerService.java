package com.islandwarfare.clan;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.config.ConfigManager;
import com.islandwarfare.island.Island;

import java.util.concurrent.TimeUnit;

/**
 * The single source of truth for clan power - used by the scoreboard,
 * leaderboards, season rankings, and the raid "max power difference"
 * guard rail. Every input is real, tracked state (not a placeholder):
 * island level, members who were online within the configured window,
 * raid win/loss record, territory zones currently held, and the clan's
 * highest-tier unlocked research.
 */
public class ClanPowerService {

    private final IslandWarfare plugin;
    private final ConfigManager cfg;

    public ClanPowerService(IslandWarfare plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
    }

    public double calculate(Clan clan) {
        Island island = plugin.getIslandManager().getIslandByClan(clan.getId());
        int islandLevel = island != null ? island.getLevel() : 0;

        double power = 0;
        power += islandLevel * cfg.getPowerWeight("island-level");
        power += countActiveMembers(clan) * cfg.getPowerWeight("active-member");
        power += clan.getRaidWins() * cfg.getPowerWeight("raid-win");
        power += clan.getRaidLosses() * cfg.getPowerWeight("raid-loss");

        if (plugin.getTerritoryManager() != null) {
            power += plugin.getTerritoryManager().countOwnedBy(clan.getId()) * cfg.getPowerWeight("territory");
        }

        if (plugin.getResearchManager() != null) {
            int researchCount = plugin.getResearchManager().getUnlocked(clan.getId()).size();
            power += researchCount * cfg.getPowerWeight("research-tier");
        }

        double divisor = cfg.getPowerWeight("balance-divisor");
        if (divisor > 0) {
            power += clan.getBalance() / divisor;
        }

        return power;
    }

    private int countActiveMembers(Clan clan) {
        long windowMillis = TimeUnit.HOURS.toMillis(cfg.getActiveMemberWindowHours());
        long now = System.currentTimeMillis();
        int count = 0;
        for (ClanMember member : clan.getMembers().values()) {
            if (org.bukkit.Bukkit.getPlayer(member.getUuid()) != null) {
                count++;
            } else if (now - member.getLastSeen() <= windowMillis) {
                count++;
            }
        }
        return count;
    }
}
