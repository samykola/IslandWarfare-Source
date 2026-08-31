package com.islandwarfare.island;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.utils.MessageUtil;
import org.bukkit.entity.Player;

public class IslandUpgradeService {

    private final IslandWarfare plugin;

    public IslandUpgradeService(IslandWarfare plugin) {
        this.plugin = plugin;
    }

    public boolean attemptUpgrade(Player player, Clan clan, Island island) {
        int nextLevel = island.getLevel() + 1;
        int maxLevel = plugin.getConfigManager().getMaxIslandLevel();
        String prefix = plugin.getConfigManager().getPrefix();

        if (island.getLevel() >= maxLevel) {
            MessageUtil.send(player, prefix, plugin.getConfigManager().getMessage("upgrade-max-level"));
            return false;
        }

        double cost = plugin.getConfigManager().getUpgradeCost(nextLevel);
        if (!clan.withdraw(cost)) {
            MessageUtil.send(player, prefix, plugin.getConfigManager().getMessage("upgrade-not-enough-money").replace("%cost%", String.valueOf(cost)));
            return false;
        }

        boolean success = plugin.getIslandManager().upgradeIsland(island, nextLevel);
        if (!success) {
            clan.deposit(cost); // refund, a neighboring island blocked the upgrade
            MessageUtil.send(player, prefix, "&cUpgrade failed: not enough free space around your island.");
            return false;
        }

        MessageUtil.send(player, prefix, plugin.getConfigManager().getMessage("upgrade-success").replace("%level%", String.valueOf(nextLevel)));
        return true;
    }
}
