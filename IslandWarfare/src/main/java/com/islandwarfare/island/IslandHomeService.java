package com.islandwarfare.island;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IslandHomeService {

    private final IslandWarfare plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public IslandHomeService(IslandWarfare plugin) {
        this.plugin = plugin;
    }

    public void teleportHome(Player player, Clan clan, Island island) {
        if (island == null) {
            MessageUtil.send(player, plugin.getConfigManager().getPrefix(), plugin.getConfigManager().getMessage("no-island"));
            return;
        }

        int cooldownSeconds = plugin.getConfigManager().getHomeCooldownSeconds();
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && !player.hasPermission("islandwarfare.bypass.protection")) {
            long remaining = (last + (cooldownSeconds * 1000L) - now) / 1000L;
            if (remaining > 0) {
                MessageUtil.send(player, plugin.getConfigManager().getPrefix(),
                        plugin.getConfigManager().getMessage("teleport-cooldown").replace("%seconds%", String.valueOf(remaining)));
                return;
            }
        }

        cooldowns.put(player.getUniqueId(), now);
        MessageUtil.send(player, plugin.getConfigManager().getPrefix(), plugin.getConfigManager().getMessage("teleporting"));

        Location initialLoc = player.getLocation().clone();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            // Cancel the teleport if the player moved significantly (basic anti-abuse, mirrors common home-tp UX).
            Location current = player.getLocation();
            if (current.getWorld() != null && current.getWorld().equals(initialLoc.getWorld())
                    && current.distanceSquared(initialLoc) > 1.0) {
                return;
            }
            Location home = island.getHomeLocation();
            if (home.getWorld() != null) {
                player.teleport(home);
            }
        }, 60L);
    }
}
