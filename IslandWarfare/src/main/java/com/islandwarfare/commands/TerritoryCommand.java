package com.islandwarfare.commands;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.config.ConfigManager;
import com.islandwarfare.territory.TerritoryZone;
import com.islandwarfare.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class TerritoryCommand implements CommandExecutor, TabCompleter {

    private final IslandWarfare plugin;
    private final ConfigManager cfg;

    public TerritoryCommand(IslandWarfare plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!cfg.isTerritoryEnabled()) {
            sender.sendMessage(MessageUtil.color(cfg.getPrefix() + "&cTerritory control is disabled on this server."));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("info")) {
            info(sender, args);
            return true;
        }

        list(sender);
        return true;
    }

    private void list(CommandSender sender) {
        sender.sendMessage(MessageUtil.color("&2--- Territory Zones (" + plugin.getTerritoryManager().getZones().size() + ") ---"));
        if (plugin.getTerritoryManager().getZones().isEmpty()) {
            sender.sendMessage(MessageUtil.color("&7No territory zones have been created yet. Ask an admin to run /iw admin territory create."));
            return;
        }
        for (TerritoryZone zone : plugin.getTerritoryManager().getZones()) {
            Clan owner = zone.getOwnerClanId() != null ? plugin.getClanManager().getClanById(zone.getOwnerClanId()) : null;
            String ownerText = owner != null ? owner.getName() + " [" + owner.getTag() + "]" : "&7Unclaimed";
            String progressText = zone.getCapturingClanId() != null
                    ? " &e(capturing: " + String.format("%.0f", (zone.getCaptureProgress() / cfg.getTerritoryCaptureSeconds()) * 100.0) + "%)"
                    : "";
            sender.sendMessage(MessageUtil.color("&f#" + zone.getId() + " &7[" + zone.getType() + "] &f" + zone.getName()
                    + " &7- Owner: &f" + ownerText + progressText));
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.color("&cUsage: /territory info <id>"));
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.color("&cInvalid zone id."));
            return;
        }
        TerritoryZone zone = plugin.getTerritoryManager().getZone(id);
        if (zone == null) {
            sender.sendMessage(MessageUtil.color("&cZone not found."));
            return;
        }
        Clan owner = zone.getOwnerClanId() != null ? plugin.getClanManager().getClanById(zone.getOwnerClanId()) : null;
        sender.sendMessage(MessageUtil.color("&2--- " + zone.getName() + " (#" + zone.getId() + ") ---"));
        sender.sendMessage(MessageUtil.color("&7Type: &f" + zone.getType()));
        sender.sendMessage(MessageUtil.color("&7Owner: &f" + (owner != null ? owner.getName() : "Unclaimed")));
        sender.sendMessage(MessageUtil.color("&7Radius: &f" + zone.getRadius()));
        sender.sendMessage(MessageUtil.color("&7Center: &f" + (int) zone.getCenterX() + ", " + (int) zone.getCenterY() + ", " + (int) zone.getCenterZ()));
        if (zone.getCapturingClanId() != null) {
            Clan capturing = plugin.getClanManager().getClanById(zone.getCapturingClanId());
            sender.sendMessage(MessageUtil.color("&7Being captured by: &f" + (capturing != null ? capturing.getName() : "?")
                    + " &7(" + String.format("%.0f", (zone.getCaptureProgress() / cfg.getTerritoryCaptureSeconds()) * 100.0) + "%)"));
        }
        if (sender instanceof Player player) {
            Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
            if (clan != null) {
                sender.sendMessage(MessageUtil.color("&7Your clan controls &f" + plugin.getTerritoryManager().countOwnedBy(clan.getId())
                        + " &7zone(s)."));
            }
        }
        sender.sendMessage(MessageUtil.color("&7Tip: hold a numeric presence advantage inside the zone with your clanmates to capture it."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("list", "info");
        return List.of();
    }
}
