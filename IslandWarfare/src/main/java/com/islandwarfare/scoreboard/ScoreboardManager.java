package com.islandwarfare.scoreboard;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.config.ConfigManager;
import com.islandwarfare.island.Island;
import com.islandwarfare.raid.RaidPhase;
import com.islandwarfare.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;

public class ScoreboardManager {

    private final IslandWarfare plugin;
    private final ConfigManager cfg;
    private BukkitTask task;

    public ScoreboardManager(IslandWarfare plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
    }

    public void start() {
        if (!cfg.isScoreboardEnabled()) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, Math.max(5, cfg.getScoreboardInterval()));
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    public void update(Player player) {
        if (!cfg.isScoreboardEnabled()) return;

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("iw", "dummy", MessageUtil.color(cfg.getScoreboardTitle()));
        objective.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        Island island = clan != null ? plugin.getIslandManager().getIslandByClan(clan.getId()) : null;
        RaidPhase phase = plugin.getRaidManager().getCurrentPhase();

        String phaseColor = switch (phase) {
            case PREPARATION -> "&e";
            case WAR -> "&c";
            case COOLDOWN -> "&7";
        };

        List<String> lines = cfg.getScoreboardLines();
        int score = lines.size();
        java.util.Set<String> usedLines = new java.util.HashSet<>();
        for (String rawLine : lines) {
            String line = rawLine
                    .replace("%clan_name%", clan != null ? clan.getName() : "None")
                    .replace("%island_level%", island != null ? String.valueOf(island.getLevel()) : "-")
                    .replace("%clan_balance%", clan != null ? String.format("%,.0f", clan.getBalance()) : "0")
                    .replace("%phase%", phase.name())
                    .replace("%phase_color%", phaseColor)
                    .replace("%phase_time%", plugin.getRaidManager().getRemainingFormatted())
                    .replace("%clan_power%", clan != null ? String.format("%.0f", plugin.getClanPowerService().calculate(clan)) : "0");

            line = MessageUtil.color(line);
            // Scoreboard lines must be unique - pad with invisible color codes if duplicated (e.g. blank lines).
            while (usedLines.contains(line)) {
                line = line + ChatColor.RESET;
            }
            usedLines.add(line);
            if (line.length() > 40) line = line.substring(0, 40);
            objective.getScore(line).setScore(score--);
        }

        player.setScoreboard(board);
    }

    public void clear(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
    }
}
