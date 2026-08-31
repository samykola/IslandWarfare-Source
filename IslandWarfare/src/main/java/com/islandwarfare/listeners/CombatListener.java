package com.islandwarfare.listeners;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.UUID;

public class CombatListener implements Listener {

    private final IslandWarfare plugin;

    public CombatListener(IslandWarfare plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(event);
        if (attacker == null) return;
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return;

        if (attacker.hasPermission("islandwarfare.bypass.protection")) return;

        Clan attackerClan = plugin.getClanManager().getClanByPlayer(attacker.getUniqueId());
        Clan victimClan = plugin.getClanManager().getClanByPlayer(victim.getUniqueId());

        UUID a = attackerClan != null ? attackerClan.getId() : null;
        UUID v = victimClan != null ? victimClan.getId() : null;

        if (!plugin.getRaidManager().isCombatAllowedBetween(a, v)) {
            event.setCancelled(true);
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return player;
        if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
