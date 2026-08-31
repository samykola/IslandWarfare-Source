package com.islandwarfare.listeners;

import com.islandwarfare.IslandWarfare;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerListener implements Listener {

    private final IslandWarfare plugin;

    public PlayerListener(IslandWarfare plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getScoreboardManager().update(player);
        touchLastSeen(player);

        if (!player.hasPlayedBefore()) {
            World world = Bukkit.getWorld(plugin.getConfigManager().getWorldName());
            if (world != null) {
                Location spawn = new Location(world,
                        plugin.getConfigManager().getSpawnX(),
                        plugin.getConfigManager().getSpawnY(),
                        plugin.getConfigManager().getSpawnZ(),
                        plugin.getConfigManager().getSpawnYaw(),
                        plugin.getConfigManager().getSpawnPitch());
                player.teleport(spawn);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        touchLastSeen(event.getPlayer());
    }

    /**
     * Real, wired backing for ClanPowerService's "active member" count:
     * every join and quit stamps the member's lastSeen so the power
     * formula's active-member-window check reflects real play activity.
     */
    private void touchLastSeen(Player player) {
        com.islandwarfare.clan.Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) return;
        com.islandwarfare.clan.ClanMember member = clan.getMember(player.getUniqueId());
        if (member != null) {
            member.setLastSeen(System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        String mode = plugin.getConfigManager().getRespawnMode();

        if ("ISLAND_HOME".equalsIgnoreCase(mode)) {
            com.islandwarfare.clan.Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
            if (clan != null) {
                com.islandwarfare.island.Island island = plugin.getIslandManager().getIslandByClan(clan.getId());
                if (island != null && island.getHomeLocation().getWorld() != null) {
                    event.setRespawnLocation(island.getHomeLocation());
                    return;
                }
            }
        }

        World world = Bukkit.getWorld(plugin.getConfigManager().getWorldName());
        if (world != null) {
            event.setRespawnLocation(new Location(world,
                    plugin.getConfigManager().getSpawnX(),
                    plugin.getConfigManager().getSpawnY(),
                    plugin.getConfigManager().getSpawnZ(),
                    plugin.getConfigManager().getSpawnYaw(),
                    plugin.getConfigManager().getSpawnPitch()));
        }
    }
}
