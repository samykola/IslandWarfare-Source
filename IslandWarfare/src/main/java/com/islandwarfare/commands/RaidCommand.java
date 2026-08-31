package com.islandwarfare.commands;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.config.ConfigManager;
import com.islandwarfare.gui.RaidGUI;
import com.islandwarfare.island.Island;
import com.islandwarfare.raid.ActiveRaid;
import com.islandwarfare.raid.RaidPhase;
import com.islandwarfare.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class RaidCommand implements CommandExecutor, TabCompleter {

    private final IslandWarfare plugin;
    private final ConfigManager cfg;

    public RaidCommand(IslandWarfare plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, cfg.getPrefix(), cfg.getMessage("player-only"));
            return true;
        }

        if (args.length == 0) {
            RaidGUI.open(plugin, player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "attack" -> attack(player, args);
            case "info" -> info(player);
            default -> RaidGUI.open(plugin, player);
        }
        return true;
    }

    private void attack(Player player, String[] args) {
        if (plugin.getRaidManager().getCurrentPhase() != RaidPhase.WAR) {
            MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("raid-not-active"));
            return;
        }
        if (args.length < 2) {
            MessageUtil.send(player, cfg.getPrefix(), "&cUsage: /raid attack <clan>");
            return;
        }
        Clan attacker = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (attacker == null) {
            MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("no-clan"));
            return;
        }
        Clan defender = plugin.getClanManager().getClanByName(args[1]);
        if (defender == null) defender = plugin.getClanManager().getClanByTag(args[1]);
        if (defender == null) {
            MessageUtil.send(player, cfg.getPrefix(), "&cClan not found.");
            return;
        }
        if (attacker.getId().equals(defender.getId())) {
            MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("raid-cannot-attack-own"));
            return;
        }
        if (!plugin.getRaidManager().canAttack(attacker, defender)) {
            MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("raid-power-too-different"));
            return;
        }
        Island targetIsland = plugin.getIslandManager().getIslandByClan(defender.getId());
        if (targetIsland == null) {
            MessageUtil.send(player, cfg.getPrefix(), "&cThat clan has no island to raid.");
            return;
        }

        ActiveRaid raid = plugin.getRaidManager().startAttack(attacker, targetIsland);
        MessageUtil.send(player, cfg.getPrefix(), "&aYou are now raiding &f" + defender.getName() + "&a!");
        for (UUID memberId : defender.getMembers().keySet()) {
            Player defenderPlayer = Bukkit.getPlayer(memberId);
            if (defenderPlayer != null) {
                MessageUtil.send(defenderPlayer, cfg.getPrefix(), cfg.getMessage("raid-attack-started").replace("%clan%", attacker.getName()));
            }
        }
    }

    private void info(Player player) {
        player.sendMessage(MessageUtil.color("&4--- Raid Status ---"));
        player.sendMessage(MessageUtil.color("&7Phase: &f" + plugin.getRaidManager().getCurrentPhase()));
        player.sendMessage(MessageUtil.color("&7Time remaining: &f" + plugin.getRaidManager().getRemainingFormatted()));

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan != null) {
            Island island = plugin.getIslandManager().getIslandByClan(clan.getId());
            if (island != null) {
                ActiveRaid raid = plugin.getRaidManager().getActiveRaid(island.getId());
                if (raid != null) {
                    Clan attackerClan = plugin.getClanManager().getClanById(raid.getAttackerClanId());
                    player.sendMessage(MessageUtil.color("&cYour island is currently being raided by &f"
                            + (attackerClan != null ? attackerClan.getName() : "unknown")));
                    player.sendMessage(MessageUtil.color("&7Stage: &f" + raid.getStage()
                            + " &7| Capture: &f" + String.format("%.0f", raid.getCapturePercent()) + "%"
                            + " &7| Time left: &f" + (raid.getRemainingMillis() / 1000) + "s"));
                    player.sendMessage(MessageUtil.color("&7Tip: stand near your island home to contest and freeze the capture!"));
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("attack", "info");
        return List.of();
    }
}
