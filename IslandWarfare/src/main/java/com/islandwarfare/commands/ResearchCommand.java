package com.islandwarfare.commands;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.config.ConfigManager;
import com.islandwarfare.island.Island;
import com.islandwarfare.research.ResearchManager;
import com.islandwarfare.research.TechDef;
import com.islandwarfare.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class ResearchCommand implements CommandExecutor, TabCompleter {

    private final IslandWarfare plugin;
    private final ConfigManager cfg;

    public ResearchCommand(IslandWarfare plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!cfg.isResearchEnabled()) {
            sender.sendMessage(MessageUtil.color(cfg.getPrefix() + "&cResearch is disabled on this server."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, cfg.getPrefix(), cfg.getMessage("player-only"));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("unlock")) {
            unlock(player, args);
            return true;
        }

        list(player);
        return true;
    }

    private void list(Player player) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("no-clan"));
            return;
        }
        Island island = plugin.getIslandManager().getIslandByClan(clan.getId());
        int maxTier = cfg.getResearchTier(island != null ? island.getLevel() : 1);
        ResearchManager research = plugin.getResearchManager();

        player.sendMessage(MessageUtil.color("&5--- Research Tree (island unlocks up to tier " + maxTier + ") ---"));
        for (TechDef def : research.getAllTechs()) {
            boolean unlocked = research.isUnlocked(clan.getId(), def.getId());
            String status = unlocked ? "&a[UNLOCKED]" : (def.getTier() > maxTier ? "&7[LOCKED - tier " + def.getTier() + "]" : "&e[AVAILABLE]");
            String reqs = def.getRequires().isEmpty() ? "" : " &7(requires: " + String.join(", ", def.getRequires()) + ")";
            player.sendMessage(MessageUtil.color("&f" + def.getId() + " &7- " + def.getName() + " " + status
                    + " &7- $" + def.getCostMoney() + reqs));
        }
        player.sendMessage(MessageUtil.color("&7Use /research unlock <id> to unlock one."));
    }

    private void unlock(Player player, String[] args) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("no-clan"));
            return;
        }
        if (!clan.isOfficerOrLeader(player.getUniqueId())) {
            MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("not-clan-officer"));
            return;
        }
        if (args.length < 2) {
            MessageUtil.send(player, cfg.getPrefix(), "&cUsage: /research unlock <id>");
            return;
        }
        Island island = plugin.getIslandManager().getIslandByClan(clan.getId());
        ResearchManager.AttemptResult result = plugin.getResearchManager().attemptUnlock(player, clan, island, args[1]);

        switch (result.result) {
            case SUCCESS -> {
                TechDef def = plugin.getResearchManager().getTech(args[1]);
                MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("research-unlocked").replace("%research%", def != null ? def.getName() : args[1]));
            }
            case ALREADY_UNLOCKED -> MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("research-already-unlocked"));
            case TIER_LOCKED -> MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("research-tier-locked"));
            case MISSING_REQUIREMENT -> MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("research-missing-requirement"));
            case NOT_ENOUGH_MONEY -> MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("research-not-enough-money"));
            case MISSING_ITEMS -> MessageUtil.send(player, cfg.getPrefix(),
                    cfg.getMessage("research-not-enough-items").replace("%items%", result.missingItemsDescription));
            case UNKNOWN_TECH -> MessageUtil.send(player, cfg.getPrefix(), "&cUnknown research id. Use /research to see the list.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("unlock", "list");
        if (args.length == 2 && args[0].equalsIgnoreCase("unlock")) {
            return plugin.getResearchManager().getAllTechs().stream().map(TechDef::getId).collect(Collectors.toList());
        }
        return List.of();
    }
}
