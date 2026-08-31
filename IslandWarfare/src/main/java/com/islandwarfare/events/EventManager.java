package com.islandwarfare.events;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.config.ConfigManager;
import com.islandwarfare.utils.MessageUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Every reward path here is claimed exactly once, server-side:
 *  - Supply drop money is granted on the *first* InventoryOpenEvent for the
 *    tracked chest location, then the location is unmarked.
 *  - Meteor money goes to whichever clan has a member within claim-radius
 *    at strike time - resolved once, synchronously, not repeatable.
 *  - Boss money is granted on the tracked entity's EntityDeathEvent to the
 *    killer's clan, then the tracked UUID is cleared so a re-fired death
 *    event (or a copy-cat mob) can never pay out twice.
 *  - Resource Rush is just a time-boxed multiplier read by ResourceManager;
 *    it grants nothing directly, so it cannot be "claimed" at all.
 */
public class EventManager implements Listener {

    private final IslandWarfare plugin;
    private final ConfigManager cfg;
    private final Random random = new Random();

    private BukkitTask scheduleTask;

    private Location activeSupplyDropLocation;
    private boolean supplyDropClaimed = true;

    private UUID activeBossId;
    private boolean bossClaimed = true;

    private long resourceRushExpiresAt = 0;
    private double resourceRushMultiplier = 1.0;

    public EventManager(IslandWarfare plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
    }

    public void start() {
        if (!cfg.isEventsEnabled()) return;
        long intervalTicks = 20L * 60L * Math.max(1, cfg.getEventIntervalMinutes());
        scheduleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runRandomEvent, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (scheduleTask != null) scheduleTask.cancel();
    }

    public double getResourceRushMultiplier() {
        if (System.currentTimeMillis() >= resourceRushExpiresAt) return 1.0;
        return resourceRushMultiplier;
    }

    /** Admin-triggerable, also used by the scheduler. */
    public void runRandomEvent() {
        ConfigurationSection section = cfg.getEventTypesSection();
        if (section == null) return;

        List<EventType> pool = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            try {
                EventType type = EventType.valueOf(key);
                pool.add(type);
                weights.add(Math.max(1, section.getInt(key + ".weight", 10)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (pool.isEmpty()) return;

        int total = weights.stream().mapToInt(Integer::intValue).sum();
        int roll = random.nextInt(total);
        int cursor = 0;
        EventType chosen = pool.get(0);
        for (int i = 0; i < pool.size(); i++) {
            cursor += weights.get(i);
            if (roll < cursor) { chosen = pool.get(i); break; }
        }
        runEvent(chosen);
    }

    public void runEvent(EventType type) {
        ConfigurationSection section = cfg.getEventTypesSection();
        if (section == null) return;
        ConfigurationSection typeSection = section.getConfigurationSection(type.name());
        if (typeSection == null) return;

        switch (type) {
            case SUPPLY_DROP -> runSupplyDrop(typeSection);
            case METEOR -> runMeteor(typeSection);
            case RESOURCE_RUSH -> runResourceRush(typeSection);
            case BOSS -> runBoss(typeSection);
        }
    }

    private Location pickLocation() {
        List<String> locations = cfg.getEventLocations();
        World world = Bukkit.getWorld(cfg.getWorldName());
        if (world == null) return null;

        if (!locations.isEmpty()) {
            String chosen = locations.get(random.nextInt(locations.size()));
            String[] parts = chosen.split(",");
            if (parts.length == 2) {
                try {
                    double x = Double.parseDouble(parts[0].trim());
                    double z = Double.parseDouble(parts[1].trim());
                    return new Location(world, x, cfg.getPasteY() + 5, z);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return new Location(world, cfg.getSpawnX(), cfg.getSpawnY(), cfg.getSpawnZ());
    }

    private void broadcast(String key, Map<String, String> placeholders) {
        String message = cfg.getMessage(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }
        Bukkit.broadcastMessage(MessageUtil.color(cfg.getPrefix() + message));
    }

    // ---------------- Supply Drop ----------------

    private void runSupplyDrop(ConfigurationSection section) {
        Location loc = pickLocation();
        if (loc == null || loc.getWorld() == null) return;

        broadcast("event-supply-drop", Map.of());

        Block block = loc.getBlock();
        block.setType(Material.CHEST);
        if (!(block.getState() instanceof Chest chest)) return;

        Map<?, ?> itemsMap = section.getConfigurationSection("loot-items") != null
                ? section.getConfigurationSection("loot-items").getValues(false) : Map.of();
        for (Map.Entry<?, ?> entry : itemsMap.entrySet()) {
            Material mat = Material.matchMaterial(String.valueOf(entry.getKey()));
            if (mat == null) continue;
            int amount = ((Number) entry.getValue()).intValue();
            chest.getInventory().addItem(new ItemStack(mat, amount));
        }
        chest.update();

        activeSupplyDropLocation = loc;
        supplyDropClaimed = false;

        // Auto-clear after 30 minutes so an unclaimed drop doesn't linger forever.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (loc.getBlock().getType() == Material.CHEST) loc.getBlock().setType(Material.AIR);
            if (loc.equals(activeSupplyDropLocation)) supplyDropClaimed = true;
        }, 20L * 60L * 30L);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (supplyDropClaimed || activeSupplyDropLocation == null) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        InventoryHolder holder = inv.getHolder();
        if (!(holder instanceof Chest chest)) return;
        if (!chest.getLocation().equals(activeSupplyDropLocation)) return;

        supplyDropClaimed = true; // claim immediately - no window for a second opener to also get paid
        double money = cfg.getEventTypesSection() != null
                ? cfg.getEventTypesSection().getDouble("SUPPLY_DROP.loot-money", 0) : 0;
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan != null && money > 0) {
            clan.deposit(money);
            MessageUtil.send(player, cfg.getPrefix(), "&aYour clan received $" + money + " from the supply drop!");
        }
    }

    // ---------------- Meteor ----------------

    private void runMeteor(ConfigurationSection section) {
        Location loc = pickLocation();
        if (loc == null || loc.getWorld() == null) return;

        broadcast("event-meteor", Map.of());

        int warning = cfg.getEventWarningSeconds();
        Bukkit.getScheduler().runTaskLater(plugin, () -> strikeMeteor(loc, section), warning * 20L);
    }

    private void strikeMeteor(Location loc, ConfigurationSection section) {
        loc.getWorld().createExplosion(loc, 0f, false, false);

        double money = section.getDouble("loot-money", 0);
        Map<?, ?> itemsMap = section.getConfigurationSection("loot-items") != null
                ? section.getConfigurationSection("loot-items").getValues(false) : Map.of();

        Player claimant = findNearestPlayer(loc, cfg.getEventClaimRadius());
        for (Map.Entry<?, ?> entry : itemsMap.entrySet()) {
            Material mat = Material.matchMaterial(String.valueOf(entry.getKey()));
            if (mat == null) continue;
            int amount = ((Number) entry.getValue()).intValue();
            loc.getWorld().dropItemNaturally(loc, new ItemStack(mat, amount));
        }

        if (claimant != null && money > 0) {
            Clan clan = plugin.getClanManager().getClanByPlayer(claimant.getUniqueId());
            if (clan != null) {
                clan.deposit(money);
                MessageUtil.send(claimant, cfg.getPrefix(), "&aYour clan received $" + money + " from the meteor strike!");
            }
        }
    }

    private Player findNearestPlayer(Location loc, double radius) {
        Player nearest = null;
        double best = radius * radius;
        for (Player player : loc.getWorld().getPlayers()) {
            double distSq = player.getLocation().distanceSquared(loc);
            if (distSq <= best) {
                nearest = player;
                best = distSq;
            }
        }
        return nearest;
    }

    // ---------------- Resource Rush ----------------

    private void runResourceRush(ConfigurationSection section) {
        int minutes = section.getInt("duration-minutes", 10);
        double multiplier = section.getDouble("yield-multiplier", 3);
        resourceRushExpiresAt = System.currentTimeMillis() + (minutes * 60_000L);
        resourceRushMultiplier = multiplier;
        broadcast("event-resource-rush", Map.of("%multiplier%", String.valueOf((int) multiplier), "%minutes%", String.valueOf(minutes)));
    }

    // ---------------- Boss ----------------

    private void runBoss(ConfigurationSection section) {
        Location loc = pickLocation();
        if (loc == null || loc.getWorld() == null) return;

        broadcast("event-boss", Map.of());

        String typeName = section.getString("boss-type", "WITHER_SKELETON");
        EntityType type;
        try {
            type = EntityType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            type = EntityType.WITHER_SKELETON;
        }
        double health = section.getDouble("boss-health", 200);

        org.bukkit.entity.Entity spawned = loc.getWorld().spawnEntity(loc, type);
        if (!(spawned instanceof LivingEntity entity)) {
            // Misconfigured boss-type (e.g. an item/projectile entity) - clean up and bail out
            // rather than crashing the event scheduler with a ClassCastException.
            spawned.remove();
            plugin.getLogger().warning("events.types.BOSS.boss-type '" + typeName + "' is not a living entity - boss event skipped.");
            return;
        }
        entity.setMaxHealth(health);
        entity.setHealth(health);
        entity.setCustomName(MessageUtil.color("&4&lWorld Boss"));
        entity.setCustomNameVisible(true);

        activeBossId = entity.getUniqueId();
        bossClaimed = false;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (bossClaimed || activeBossId == null) return;
        if (!event.getEntity().getUniqueId().equals(activeBossId)) return;

        bossClaimed = true;
        activeBossId = null;

        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        ConfigurationSection section = cfg.getEventTypesSection();
        double money = section != null ? section.getDouble("BOSS.loot-money", 0) : 0;

        Clan clan = plugin.getClanManager().getClanByPlayer(killer.getUniqueId());
        if (clan != null && money > 0) {
            clan.deposit(money);
            MessageUtil.send(killer, cfg.getPrefix(), "&aYour clan received $" + money + " for defeating the World Boss!");
        }

        if (section != null) {
            ConfigurationSection itemsSection = section.getConfigurationSection("BOSS.loot-items");
            if (itemsSection != null) {
                for (Map.Entry<String, Object> entry : itemsSection.getValues(false).entrySet()) {
                    Material mat = Material.matchMaterial(entry.getKey());
                    if (mat == null) continue;
                    int amount = ((Number) entry.getValue()).intValue();
                    event.getEntity().getWorld().dropItemNaturally(event.getEntity().getLocation(), new ItemStack(mat, amount));
                }
            }
        }
    }
}
