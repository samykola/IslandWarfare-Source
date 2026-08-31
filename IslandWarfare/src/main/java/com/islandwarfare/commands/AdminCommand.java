package com.islandwarfare.commands;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.config.ConfigManager;
import com.islandwarfare.island.Island;
import com.islandwarfare.raid.RaidPhase;
import com.islandwarfare.territory.TerritoryZone;
import com.islandwarfare.territory.ZoneType;
import com.islandwarfare.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final IslandWarfare plugin;
    private final ConfigManager cfg;

    public AdminCommand(IslandWarfare plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("islandwarfare.admin")) {
            MessageUtil.send(sender, cfg.getPrefix(), cfg.getMessage("no-permission"));
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("admin")) {
            sendUsage(sender);
            return true;
        }
        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String category = args[1].toLowerCase();
        String[] rest = shift(args, 2);

        switch (category) {
            case "island" -> handleIsland(sender, rest);
            case "reload" -> handleReload(sender);
            case "raid" -> handleRaid(sender, rest);
            case "clan" -> handleClan(sender, rest);
            case "territory" -> handleTerritory(sender, rest);
            case "resource" -> handleResource(sender, rest);
            case "season" -> handleSeason(sender, rest);
            case "debug" -> handleDebug(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private String[] shift(String[] array, int by) {
        if (array.length <= by) return new String[0];
        String[] result = new String[array.length - by];
        System.arraycopy(array, by, result, 0, result.length);
        return result;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(MessageUtil.color("&c--- IslandWarfare Admin ---"));
        sender.sendMessage(MessageUtil.color("&7/iw admin island create <clan> [level]"));
        sender.sendMessage(MessageUtil.color("&7/iw admin island delete <id>"));
        sender.sendMessage(MessageUtil.color("&7/iw admin island setlevel <id> <level>"));
        sender.sendMessage(MessageUtil.color("&7/iw admin island info <id>"));
        sender.sendMessage(MessageUtil.color("&7/iw admin reload"));
        sender.sendMessage(MessageUtil.color("&7/iw admin raid start|stop|status"));
        sender.sendMessage(MessageUtil.color("&7/iw admin clan info|setbalance <clan> [amount]"));
        sender.sendMessage(MessageUtil.color("&7/iw admin territory create <radius> [NEUTRAL|RESOURCE]"));
        sender.sendMessage(MessageUtil.color("&7/iw admin territory delete|list <id>"));
        sender.sendMessage(MessageUtil.color("&7/iw admin resource scan <zoneId> <minY> <maxY>"));
        sender.sendMessage(MessageUtil.color("&7/iw admin season status|end"));
        sender.sendMessage(MessageUtil.color("&7/iw admin debug"));
    }

    // ---------------- island ----------------

    private void handleIsland(CommandSender sender, String[] args) {
        if (args.length == 0) { sendUsage(sender); return; }
        String action = args[0].toLowerCase();

        switch (action) {
            case "create" -> {
                if (args.length < 2) {
                    sender.sendMessage(MessageUtil.color("&cUsage: /iw admin island create <clan> [level]"));
                    return;
                }
                Clan clan = plugin.getClanManager().getClanByName(args[1]);
                if (clan == null) clan = plugin.getClanManager().getClanByTag(args[1]);
                if (clan == null) {
                    sender.sendMessage(MessageUtil.color("&cClan not found."));
                    return;
                }
                if (plugin.getIslandManager().getIslandByClan(clan.getId()) != null) {
                    sender.sendMessage(MessageUtil.color("&cThat clan already has an island."));
                    return;
                }
                int level = args.length >= 3 ? parseIntOrDefault(args[2], 1) : 1;
                Island island = plugin.getIslandManager().createIslandForClan(clan.getId(), level);
                if (island == null) {
                    sender.sendMessage(MessageUtil.color("&cFailed to create island (world not loaded or WorldEdit missing)."));
                    return;
                }
                clan.setIslandId(island.getId());
                sender.sendMessage(MessageUtil.color("&aCreated island #" + island.getId() + " for clan " + clan.getName() + " at level " + level + "."));
            }
            case "delete" -> {
                if (args.length < 2) {
                    sender.sendMessage(MessageUtil.color("&cUsage: /iw admin island delete <id>"));
                    return;
                }
                int id = parseIntOrDefault(args[1], -1);
                Island island = plugin.getIslandManager().getIsland(id);
                if (island == null) {
                    sender.sendMessage(MessageUtil.color("&cIsland not found."));
                    return;
                }
                Clan owner = plugin.getClanManager().getClanById(island.getOwnerClanId());
                if (owner != null) owner.setIslandId(-1);
                plugin.getIslandManager().removeIsland(id);
                sender.sendMessage(MessageUtil.color("&aIsland #" + id + " removed from the registry."));
            }
            case "setlevel" -> {
                if (args.length < 3) {
                    sender.sendMessage(MessageUtil.color("&cUsage: /iw admin island setlevel <id> <level>"));
                    return;
                }
                int id = parseIntOrDefault(args[1], -1);
                Island island = plugin.getIslandManager().getIsland(id);
                if (island == null) {
                    sender.sendMessage(MessageUtil.color("&cIsland not found."));
                    return;
                }
                int level = parseIntOrDefault(args[2], island.getLevel());
                boolean success = plugin.getIslandManager().upgradeIsland(island, level);
                sender.sendMessage(MessageUtil.color(success
                        ? "&aIsland #" + id + " set to level " + level + "."
                        : "&cCould not set level - space is blocked by a neighboring island."));
            }
            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage(MessageUtil.color("&cUsage: /iw admin island info <id>"));
                    return;
                }
                int id = parseIntOrDefault(args[1], -1);
                Island island = plugin.getIslandManager().getIsland(id);
                if (island == null) {
                    sender.sendMessage(MessageUtil.color("&cIsland not found."));
                    return;
                }
                Clan owner = plugin.getClanManager().getClanById(island.getOwnerClanId());
                sender.sendMessage(MessageUtil.color("&2--- Island #" + island.getId() + " ---"));
                sender.sendMessage(MessageUtil.color("&7Owner: &f" + (owner != null ? owner.getName() : island.getOwnerClanId())));
                sender.sendMessage(MessageUtil.color("&7Level: &f" + island.getLevel()));
                sender.sendMessage(MessageUtil.color("&7Status: &f" + island.getStatus()));
                sender.sendMessage(MessageUtil.color("&7Center: &f" + (int) island.getCenterX() + ", " + (int) island.getCenterY() + ", " + (int) island.getCenterZ()));
                sender.sendMessage(MessageUtil.color("&7Radius: &f" + island.getRadius()));
            }
            default -> sendUsage(sender);
        }
    }

    private int parseIntOrDefault(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // ---------------- reload ----------------

    private void handleReload(CommandSender sender) {
        cfg.reload();
        sender.sendMessage(MessageUtil.color(cfg.getPrefix() + "&aConfiguration reloaded."));
    }

    // ---------------- raid ----------------

    private void handleRaid(CommandSender sender, String[] args) {
        if (args.length == 0) { sendUsage(sender); return; }
        switch (args[0].toLowerCase()) {
            case "start" -> {
                plugin.getRaidManager().forcePhase(RaidPhase.WAR);
                sender.sendMessage(MessageUtil.color("&cWar phase has been forced ON by an admin."));
            }
            case "stop" -> {
                plugin.getRaidManager().forcePhase(RaidPhase.COOLDOWN);
                sender.sendMessage(MessageUtil.color("&aWar phase has been stopped. Entering cooldown."));
            }
            case "status" -> sender.sendMessage(MessageUtil.color("&7Phase: &f" + plugin.getRaidManager().getCurrentPhase()
                    + " &7(" + plugin.getRaidManager().getRemainingFormatted() + " remaining)"));
            default -> sendUsage(sender);
        }
    }

    // ---------------- clan ----------------

    private void handleClan(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.color("&cUsage: /iw admin clan info|setbalance <clan> [amount]"));
            return;
        }
        String action = args[0].toLowerCase();
        Clan clan = plugin.getClanManager().getClanByName(args[1]);
        if (clan == null) clan = plugin.getClanManager().getClanByTag(args[1]);
        if (clan == null) {
            sender.sendMessage(MessageUtil.color("&cClan not found."));
            return;
        }

        switch (action) {
            case "info" -> {
                sender.sendMessage(MessageUtil.color("&3--- " + clan.getName() + " [" + clan.getTag() + "] ---"));
                sender.sendMessage(MessageUtil.color("&7Members: &f" + clan.getMemberCount()));
                sender.sendMessage(MessageUtil.color("&7Balance: &f$" + clan.getBalance()));
                sender.sendMessage(MessageUtil.color("&7Island ID: &f" + clan.getIslandId()));
            }
            case "setbalance" -> {
                if (args.length < 3) {
                    sender.sendMessage(MessageUtil.color("&cUsage: /iw admin clan setbalance <clan> <amount>"));
                    return;
                }
                try {
                    double amount = Double.parseDouble(args[2]);
                    clan.setBalance(amount);
                    sender.sendMessage(MessageUtil.color("&aSet " + clan.getName() + "'s balance to $" + amount));
                } catch (NumberFormatException e) {
                    sender.sendMessage(MessageUtil.color("&cInvalid amount."));
                }
            }
            default -> sender.sendMessage(MessageUtil.color("&cUsage: /iw admin clan info|setbalance <clan> [amount]"));
        }
    }

    // ---------------- territory ----------------

    private void handleTerritory(CommandSender sender, String[] args) {
        if (args.length == 0) { sendUsage(sender); return; }
        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(MessageUtil.color("&cThis command must be run in-game (zones are created at your location)."));
                    return;
                }
                if (args.length < 2) {
                    sender.sendMessage(MessageUtil.color("&cUsage: /iw admin territory create <radius> [NEUTRAL|RESOURCE]"));
                    return;
                }
                int radius = parseIntOrDefault(args[1], cfg.getTerritoryDefaultRadius());
                ZoneType type = ZoneType.NEUTRAL;
                if (args.length >= 3) {
                    try {
                        type = ZoneType.valueOf(args[2].toUpperCase());
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(MessageUtil.color("&cInvalid zone type. Use NEUTRAL or RESOURCE."));
                        return;
                    }
                }
                var loc = player.getLocation();
                TerritoryZone zone = plugin.getTerritoryManager().createZone(type, loc.getWorld().getName(),
                        loc.getX(), loc.getY(), loc.getZ(), radius);
                sender.sendMessage(MessageUtil.color("&aCreated " + type + " territory zone #" + zone.getId()
                        + " (radius " + radius + ") at your location."));
                if (type == ZoneType.RESOURCE) {
                    sender.sendMessage(MessageUtil.color("&7Now run /iw admin resource scan " + zone.getId()
                            + " <minY> <maxY> to register resource nodes inside it."));
                }
            }
            case "delete" -> {
                if (args.length < 2) {
                    sender.sendMessage(MessageUtil.color("&cUsage: /iw admin territory delete <id>"));
                    return;
                }
                int id = parseIntOrDefault(args[1], -1);
                if (plugin.getTerritoryManager().getZone(id) == null) {
                    sender.sendMessage(MessageUtil.color("&cZone not found."));
                    return;
                }
                plugin.getTerritoryManager().removeZone(id);
                sender.sendMessage(MessageUtil.color("&aZone #" + id + " removed."));
            }
            case "list" -> {
                sender.sendMessage(MessageUtil.color("&2--- Territory Zones (" + plugin.getTerritoryManager().getZones().size() + ") ---"));
                for (TerritoryZone zone : plugin.getTerritoryManager().getZones()) {
                    Clan owner = zone.getOwnerClanId() != null ? plugin.getClanManager().getClanById(zone.getOwnerClanId()) : null;
                    sender.sendMessage(MessageUtil.color("&f#" + zone.getId() + " &7[" + zone.getType() + "] &7r=" + zone.getRadius()
                            + " &7owner: &f" + (owner != null ? owner.getName() : "none")));
                }
            }
            default -> sendUsage(sender);
        }
    }

    // ---------------- resource ----------------

    private void handleResource(CommandSender sender, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("scan")) {
            sender.sendMessage(MessageUtil.color("&cUsage: /iw admin resource scan <zoneId> <minY> <maxY>"));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(MessageUtil.color("&cUsage: /iw admin resource scan <zoneId> <minY> <maxY>"));
            return;
        }
        int zoneId = parseIntOrDefault(args[1], -1);
        TerritoryZone zone = plugin.getTerritoryManager().getZone(zoneId);
        if (zone == null) {
            sender.sendMessage(MessageUtil.color("&cZone not found."));
            return;
        }
        if (zone.getType() != ZoneType.RESOURCE) {
            sender.sendMessage(MessageUtil.color("&cThat zone is not a RESOURCE zone."));
            return;
        }
        int minY = parseIntOrDefault(args[2], (int) zone.getCenterY() - 10);
        int maxY = parseIntOrDefault(args[3], (int) zone.getCenterY() + 10);
        if (maxY < minY) {
            sender.sendMessage(MessageUtil.color("&cmaxY must be >= minY."));
            return;
        }
        sender.sendMessage(MessageUtil.color("&7Scanning zone #" + zoneId + " (this may take a moment)..."));
        int found = plugin.getResourceManager().scanZone(zone, minY, maxY);
        sender.sendMessage(MessageUtil.color("&aRegistered " + found + " resource node(s) in zone #" + zoneId + "."));
    }

    // ---------------- season ----------------

    private void handleSeason(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(MessageUtil.color("&cUsage: /iw admin season status|end"));
            return;
        }
        switch (args[0].toLowerCase()) {
            case "status" -> {
                sender.sendMessage(MessageUtil.color("&6--- Season " + plugin.getSeasonManager().getSeasonNumber() + " ---"));
                long remaining = Math.max(0, plugin.getSeasonManager().getSeasonEndsAt() - System.currentTimeMillis());
                long days = remaining / (1000 * 60 * 60 * 24);
                sender.sendMessage(MessageUtil.color("&7Ends in: &f~" + days + " day(s)"));
                var rankings = plugin.getSeasonManager().computeRankings();
                int rank = 1;
                for (var result : rankings) {
                    if (rank > 10) break;
                    sender.sendMessage(MessageUtil.color("&e#" + rank + " &f" + result.getClanName() + " &7[" + result.getClanTag()
                            + "] &7- Power: &f" + String.format("%.0f", result.getPower())));
                    rank++;
                }
            }
            case "end" -> {
                plugin.getSeasonManager().endSeason();
                sender.sendMessage(MessageUtil.color("&aSeason ended manually. A new season has begun."));
            }
            default -> sender.sendMessage(MessageUtil.color("&cUsage: /iw admin season status|end"));
        }
    }

    // ---------------- debug ----------------

    private void handleDebug(CommandSender sender) {
        sender.sendMessage(MessageUtil.color("&e--- IslandWarfare Debug ---"));
        sender.sendMessage(MessageUtil.color("&7Clans: &f" + plugin.getClanManager().getClanCount()));
        sender.sendMessage(MessageUtil.color("&7Islands: &f" + plugin.getIslandManager().countIslands()));
        sender.sendMessage(MessageUtil.color("&7Territory zones: &f" + plugin.getTerritoryManager().getZones().size()));
        sender.sendMessage(MessageUtil.color("&7Resource nodes: &f" + plugin.getResourceManager().getNodes().size()));
        sender.sendMessage(MessageUtil.color("&7Research techs defined: &f" + plugin.getResearchManager().getAllTechs().size()));
        sender.sendMessage(MessageUtil.color("&7Season: &f#" + plugin.getSeasonManager().getSeasonNumber()));
        sender.sendMessage(MessageUtil.color("&7Raid Phase: &f" + plugin.getRaidManager().getCurrentPhase()
                + " (" + plugin.getRaidManager().getRemainingFormatted() + ")"));
        sender.sendMessage(MessageUtil.color("&7WorldEdit available: &f" + plugin.getSchematicService().isWorldEditAvailable()));
        sender.sendMessage(MessageUtil.color("&7World loaded: &f" + (org.bukkit.Bukkit.getWorld(cfg.getWorldName()) != null)));
        sender.sendMessage(MessageUtil.color("&7Storage type: &f" + cfg.getStorageType()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("admin");
        if (args.length == 2) return List.of("island", "reload", "raid", "clan", "territory", "resource", "season", "debug");
        if (args.length == 3) {
            return switch (args[1].toLowerCase()) {
                case "island" -> List.of("create", "delete", "setlevel", "info");
                case "raid" -> List.of("start", "stop", "status");
                case "clan" -> List.of("info", "setbalance");
                case "territory" -> List.of("create", "delete", "list");
                case "resource" -> List.of("scan");
                case "season" -> List.of("status", "end");
                default -> List.of();
            };
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("territory") && args[2].equalsIgnoreCase("create")) {
            return List.of("NEUTRAL", "RESOURCE");
        }
        return List.of();
    }
}
