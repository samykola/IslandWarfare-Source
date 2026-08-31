package com.islandwarfare.commands;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.gui.IslandGUI;
import com.islandwarfare.island.Island;
import com.islandwarfare.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class IslandCommand implements CommandExecutor, TabCompleter {

    private final IslandWarfare plugin;

    public IslandCommand(IslandWarfare plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, plugin.getConfigManager().getPrefix(), plugin.getConfigManager().getMessage("player-only"));
            return true;
        }

        if (args.length == 0) {
            IslandGUI.open(plugin, player);
            return true;
        }

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        String sub = args[0].toLowerCase();

        switch (sub) {
            case "info" -> {
                if (clan == null) {
                    MessageUtil.send(player, plugin.getConfigManager().getPrefix(), plugin.getConfigManager().getMessage("no-clan"));
                    return true;
                }
                Island island = plugin.getIslandManager().getIslandByClan(clan.getId());
                if (island == null) {
                    MessageUtil.send(player, plugin.getConfigManager().getPrefix(), plugin.getConfigManager().getMessage("no-island"));
                    return true;
                }
                player.sendMessage(MessageUtil.color("&2--- Island #" + island.getId() + " ---"));
                player.sendMessage(MessageUtil.color("&7Owner: &f" + clan.getName()));
                player.sendMessage(MessageUtil.color("&7Level: &f" + island.getLevel()));
                player.sendMessage(MessageUtil.color("&7Size: &f" + (island.getRadius() * 2) + "x" + (island.getRadius() * 2)));
                player.sendMessage(MessageUtil.color("&7Status: &f" + island.getStatus()));
                player.sendMessage(MessageUtil.color("&7Center: &f" + (int) island.getCenterX() + ", " + (int) island.getCenterY() + ", " + (int) island.getCenterZ()));
            }
            case "home" -> {
                if (clan == null) {
                    MessageUtil.send(player, plugin.getConfigManager().getPrefix(), plugin.getConfigManager().getMessage("no-clan"));
                    return true;
                }
                Island island = plugin.getIslandManager().getIslandByClan(clan.getId());
                plugin.getIslandHomeService().teleportHome(player, clan, island);
            }
            case "sethome" -> {
                if (clan == null) {
                    MessageUtil.send(player, plugin.getConfigManager().getPrefix(), plugin.getConfigManager().getMessage("no-clan"));
                    return true;
                }
                Island island = plugin.getIslandManager().getIslandByClan(clan.getId());
                if (island == null) {
                    MessageUtil.send(player, plugin.getConfigManager().getPrefix(), plugin.getConfigManager().getMessage("no-island"));
                    return true;
                }
                island.setHome(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ(),
                        player.getLocation().getYaw(), player.getLocation().getPitch());
                MessageUtil.send(player, plugin.getConfigManager().getPrefix(), "&aIsland home set to your current location.");
            }
            case "upgrade" -> {
                if (clan == null) {
                    MessageUtil.send(player, plugin.getConfigManager().getPrefix(), plugin.getConfigManager().getMessage("no-clan"));
                    return true;
                }
                Island island = plugin.getIslandManager().getIslandByClan(clan.getId());
                if (island == null) {
                    MessageUtil.send(player, plugin.getConfigManager().getPrefix(), plugin.getConfigManager().getMessage("no-island"));
                    return true;
                }
                plugin.getIslandUpgradeService().attemptUpgrade(player, clan, island);
            }
            default -> IslandGUI.open(plugin, player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("info", "home", "sethome", "upgrade");
        }
        return List.of();
    }
}
