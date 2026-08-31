package com.islandwarfare.commands;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.clan.ClanMember;
import com.islandwarfare.clan.ClanRole;
import com.islandwarfare.config.ConfigManager;
import com.islandwarfare.gui.ClanGUI;
import com.islandwarfare.gui.ClanMembersGUI;
import com.islandwarfare.gui.ClanPermissionsGUI;
import com.islandwarfare.island.Island;
import com.islandwarfare.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class ClanCommand implements CommandExecutor, TabCompleter {

    private final IslandWarfare plugin;
    private final ConfigManager cfg;

    public ClanCommand(IslandWarfare plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
    }

    private void msg(CommandSender s, String key) {
        MessageUtil.send(s, cfg.getPrefix(), cfg.getMessage(key));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            msg(sender, "player-only");
            return true;
        }
        if (args.length == 0) {
            ClanGUI.open(plugin, player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> create(player, args);
            case "invite" -> invite(player, args);
            case "join" -> join(player, args);
            case "leave" -> leave(player);
            case "kick" -> kick(player, args);
            case "promote" -> promote(player, args);
            case "demote" -> demote(player, args);
            case "info" -> info(player);
            case "members" -> ClanMembersGUI.open(plugin, player);
            case "permissions" -> permissions(player, args);
            case "top" -> top(player, args);
            default -> ClanGUI.open(plugin, player);
        }
        return true;
    }

    private void create(Player player, String[] args) {
        if (plugin.getClanManager().getClanByPlayer(player.getUniqueId()) != null) {
            msg(player, "already-in-clan");
            return;
        }
        if (args.length < 3) {
            MessageUtil.send(player, cfg.getPrefix(), "&cUsage: /clan create <name> <tag>");
            return;
        }
        String name = args[1];
        String tag = args[2];

        if (name.length() < cfg.getMinNameLength() || name.length() > cfg.getMaxNameLength()) {
            msg(player, "clan-invalid-name");
            return;
        }
        if (plugin.getClanManager().isNameTaken(name)) {
            msg(player, "clan-name-taken");
            return;
        }
        if (plugin.getClanManager().isTagTaken(tag)) {
            msg(player, "clan-tag-taken");
            return;
        }

        UUID clanId = UUID.randomUUID();
        Clan clan = plugin.getClanManager().createClan(clanId, name, tag, player.getUniqueId(), player.getName());

        Island island = plugin.getIslandManager().createIslandForClan(clanId, 1);
        if (island != null) {
            clan.setIslandId(island.getId());
        }

        MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("clan-created").replace("%clan%", name));
        if (island != null) {
            plugin.getIslandHomeService().teleportHome(player, clan, island);
        }
    }

    private void invite(Player player, String[] args) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) { msg(player, "no-clan"); return; }
        if (!clan.isOfficerOrLeader(player.getUniqueId())) { msg(player, "not-clan-officer"); return; }
        if (args.length < 2) {
            MessageUtil.send(player, cfg.getPrefix(), "&cUsage: /clan invite <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            MessageUtil.send(player, cfg.getPrefix(), "&cPlayer not found or offline.");
            return;
        }
        if (plugin.getClanManager().getClanByPlayer(target.getUniqueId()) != null) {
            MessageUtil.send(player, cfg.getPrefix(), "&cThat player is already in a clan.");
            return;
        }
        plugin.getClanManager().invite(clan, target.getUniqueId());
        MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("invite-sent").replace("%player%", target.getName()));
        MessageUtil.send(target, cfg.getPrefix(), cfg.getMessage("invite-received").replace("%clan%", clan.getName()));
    }

    private void join(Player player, String[] args) {
        if (plugin.getClanManager().getClanByPlayer(player.getUniqueId()) != null) {
            msg(player, "already-in-clan");
            return;
        }
        if (args.length < 2) {
            MessageUtil.send(player, cfg.getPrefix(), "&cUsage: /clan join <name>");
            return;
        }
        Clan clan = plugin.getClanManager().getClanByName(args[1]);
        if (clan == null) {
            MessageUtil.send(player, cfg.getPrefix(), "&cClan not found.");
            return;
        }
        if (!plugin.getClanManager().hasInvite(player.getUniqueId(), clan.getId())) {
            msg(player, "no-pending-invite");
            return;
        }
        int maxMembers = effectiveMaxMembers(clan);
        if (clan.getMemberCount() >= maxMembers) {
            MessageUtil.send(player, cfg.getPrefix(), "&cThat clan's island is full. They need to upgrade first.");
            return;
        }
        plugin.getClanManager().addMember(clan, player.getUniqueId(), player.getName());
        plugin.getClanManager().clearInvite(player.getUniqueId(), clan.getId());
        MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("joined-clan").replace("%clan%", clan.getName()));
    }

    private void leave(Player player) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) { msg(player, "no-clan"); return; }
        if (clan.isLeader(player.getUniqueId())) {
            MessageUtil.send(player, cfg.getPrefix(), "&cLeaders cannot leave. Promote a new leader first or disband the clan.");
            return;
        }
        plugin.getClanManager().removeMember(clan, player.getUniqueId());
        msg(player, "left-clan");
    }

    private void kick(Player player, String[] args) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) { msg(player, "no-clan"); return; }
        if (!clan.isOfficerOrLeader(player.getUniqueId())) { msg(player, "not-clan-officer"); return; }
        if (args.length < 2) {
            MessageUtil.send(player, cfg.getPrefix(), "&cUsage: /clan kick <player>");
            return;
        }
        ClanMember target = findMemberByName(clan, args[1]);
        if (target == null) {
            MessageUtil.send(player, cfg.getPrefix(), "&cThat player isn't in your clan.");
            return;
        }
        if (clan.isLeader(target.getUuid())) {
            MessageUtil.send(player, cfg.getPrefix(), "&cYou cannot kick the leader.");
            return;
        }
        plugin.getClanManager().removeMember(clan, target.getUuid());
        MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("player-kicked").replace("%player%", target.getName()));
        Player targetPlayer = Bukkit.getPlayer(target.getUuid());
        if (targetPlayer != null) msg(targetPlayer, "kicked-from-clan");
    }

    private void promote(Player player, String[] args) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) { msg(player, "no-clan"); return; }
        if (!clan.isLeader(player.getUniqueId())) { msg(player, "not-clan-leader"); return; }
        if (args.length < 2) {
            MessageUtil.send(player, cfg.getPrefix(), "&cUsage: /clan promote <player>");
            return;
        }
        ClanMember target = findMemberByName(clan, args[1]);
        if (target == null || target.getRole() != ClanRole.MEMBER) {
            MessageUtil.send(player, cfg.getPrefix(), "&cThat player cannot be promoted.");
            return;
        }
        long officerCount = clan.getMembers().values().stream().filter(m -> m.getRole() == ClanRole.OFFICER).count();
        if (officerCount >= cfg.getMaxOfficers()) {
            MessageUtil.send(player, cfg.getPrefix(), "&cYour clan already has the maximum number of officers.");
            return;
        }
        plugin.getClanManager().setRole(target, ClanRole.OFFICER);
        MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("promoted").replace("%player%", target.getName()));
    }

    private void demote(Player player, String[] args) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) { msg(player, "no-clan"); return; }
        if (!clan.isLeader(player.getUniqueId())) { msg(player, "not-clan-leader"); return; }
        if (args.length < 2) {
            MessageUtil.send(player, cfg.getPrefix(), "&cUsage: /clan demote <player>");
            return;
        }
        ClanMember target = findMemberByName(clan, args[1]);
        if (target == null || target.getRole() != ClanRole.OFFICER) {
            MessageUtil.send(player, cfg.getPrefix(), "&cThat player cannot be demoted.");
            return;
        }
        plugin.getClanManager().setRole(target, ClanRole.MEMBER);
        MessageUtil.send(player, cfg.getPrefix(), cfg.getMessage("demoted").replace("%player%", target.getName()));
    }

    private void info(Player player) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) { msg(player, "no-clan"); return; }
        Island island = plugin.getIslandManager().getIslandByClan(clan.getId());
        player.sendMessage(MessageUtil.color("&3--- " + clan.getName() + " [" + clan.getTag() + "] ---"));
        player.sendMessage(MessageUtil.color("&7Leader: &f" + resolveName(clan.getLeader())));
        player.sendMessage(MessageUtil.color("&7Members: &f" + clan.getMemberCount()));
        player.sendMessage(MessageUtil.color("&7Balance: &f$" + String.format("%.2f", clan.getBalance())));
        player.sendMessage(MessageUtil.color("&7Island Level: &f" + (island != null ? island.getLevel() : "-")));
        player.sendMessage(MessageUtil.color("&7Raid Wins/Losses: &f" + clan.getRaidWins() + "/" + clan.getRaidLosses()));
        player.sendMessage(MessageUtil.color("&7Power: &f" + String.format("%.0f", plugin.getClanPowerService().calculate(clan))));
    }

    private void permissions(Player player, String[] args) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) { msg(player, "no-clan"); return; }
        if (!clan.isOfficerOrLeader(player.getUniqueId())) { msg(player, "not-clan-officer"); return; }
        if (args.length < 2) {
            ClanMembersGUI.open(plugin, player);
            return;
        }
        ClanMember target = findMemberByName(clan, args[1]);
        if (target == null) {
            MessageUtil.send(player, cfg.getPrefix(), "&cThat player isn't in your clan.");
            return;
        }
        ClanPermissionsGUI.open(plugin, player, target.getUuid());
    }

    /**
     * Surfaces LeaderboardManager in-game - without this the leaderboard
     * system (richest/highest level/raid wins/territory/power) would be
     * built but completely unreachable by players.
     */
    private void top(Player player, String[] args) {
        String category = args.length >= 2 ? args[1].toLowerCase() : "power";

        java.util.List<Clan> results;
        String title;
        switch (category) {
            case "money", "balance", "richest" -> {
                results = plugin.getLeaderboardManager().richestClans(10);
                title = "&6--- Richest Clans ---";
            }
            case "level", "island" -> {
                results = plugin.getLeaderboardManager().highestIslandLevel(10);
                title = "&6--- Highest Island Level ---";
            }
            case "wins", "raidwins" -> {
                results = plugin.getLeaderboardManager().mostRaidWins(10);
                title = "&6--- Most Raid Wins ---";
            }
            case "losses", "raidlosses" -> {
                results = plugin.getLeaderboardManager().mostRaidLosses(10);
                title = "&6--- Most Raid Losses ---";
            }
            case "territory" -> {
                results = plugin.getLeaderboardManager().mostTerritory(10);
                title = "&6--- Most Territory Held ---";
            }
            default -> {
                results = plugin.getLeaderboardManager().highestPower(10);
                title = "&6--- Highest Clan Power ---";
            }
        }

        player.sendMessage(MessageUtil.color(title));
        if (results.isEmpty()) {
            player.sendMessage(MessageUtil.color("&7No clans yet."));
            return;
        }
        int rank = 1;
        for (Clan clan : results) {
            String valueText = switch (category) {
                case "money", "balance", "richest" -> "$" + String.format("%.0f", clan.getBalance());
                case "level", "island" -> "Level " + islandLevelOf(clan);
                case "wins", "raidwins" -> clan.getRaidWins() + " wins";
                case "losses", "raidlosses" -> clan.getRaidLosses() + " losses";
                case "territory" -> plugin.getTerritoryManager().countOwnedBy(clan.getId()) + " zone(s)";
                default -> String.format("%.0f", plugin.getClanPowerService().calculate(clan)) + " power";
            };
            player.sendMessage(MessageUtil.color("&e#" + rank + " &f" + clan.getName() + " &7[" + clan.getTag() + "] &7- &f" + valueText));
            rank++;
        }
    }

    private int islandLevelOf(Clan clan) {
        Island island = plugin.getIslandManager().getIslandByClan(clan.getId());
        return island != null ? island.getLevel() : 0;
    }

    /**
     * Real, wired effect of the STORAGE_BONUS research: extends the
     * island-level member cap by the unlocked percentage, rounded down.
     */
    private int effectiveMaxMembers(Clan clan) {
        Island island = plugin.getIslandManager().getIslandByClan(clan.getId());
        int base = cfg.getMaxMembers(island != null ? island.getLevel() : 1);
        double bonus = plugin.getResearchManager() != null
                ? plugin.getResearchManager().getEffectBonus(clan.getId(), "STORAGE_BONUS") : 0;
        return base + (int) Math.floor(base * (bonus / 100.0));
    }

    private ClanMember findMemberByName(Clan clan, String name) {
        for (ClanMember member : clan.getMembers().values()) {
            if (member.getName().equalsIgnoreCase(name)) return member;
        }
        return null;
    }

    private String resolveName(UUID uuid) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(uuid);
        return p.getName() != null ? p.getName() : uuid.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("create", "invite", "join", "leave", "kick", "promote", "demote", "info", "members", "permissions", "top");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            return List.of("power", "money", "level", "wins", "losses", "territory");
        }
        return List.of();
    }
}
