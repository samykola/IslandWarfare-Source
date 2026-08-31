package com.islandwarfare.config;

import com.islandwarfare.IslandWarfare;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Wraps config.yml and exposes typed, sensibly-defaulted getters so the rest
 * of the plugin never touches raw ConfigurationSection objects directly.
 */
public class ConfigManager {

    private final IslandWarfare plugin;
    private FileConfiguration config;

    public ConfigManager(IslandWarfare plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public FileConfiguration raw() {
        return config;
    }

    // ---------------- World / Spawn ----------------

    public String getWorldName() {
        return config.getString("world.name", "islandwarfare_world");
    }

    public double getSpawnX() { return config.getDouble("world.spawn.x", 0); }
    public double getSpawnY() { return config.getDouble("world.spawn.y", 100); }
    public double getSpawnZ() { return config.getDouble("world.spawn.z", 0); }
    public float getSpawnYaw() { return (float) config.getDouble("world.spawn.yaw", 0); }
    public float getSpawnPitch() { return (float) config.getDouble("world.spawn.pitch", 0); }

    // ---------------- Storage ----------------

    public String getStorageType() {
        return config.getString("storage.type", "YAML");
    }

    // ---------------- Island ----------------

    public int getGridSpacing() { return config.getInt("island.grid-spacing", 250); }
    public int getPasteY() { return config.getInt("island.paste-y", 64); }
    public int getMinGap() { return config.getInt("island.min-gap", 32); }
    public int getHomeCooldownSeconds() { return config.getInt("island.home.teleport-cooldown-seconds", 10); }

    public int getIslandSize(int level) {
        return config.getInt("island.levels." + level + ".size", 20);
    }

    public String getIslandSchematic(int level) {
        return config.getString("island.levels." + level + ".schematic", "island-level-" + level + ".schem");
    }

    public double getUpgradeCost(int level) {
        return config.getDouble("island.levels." + level + ".upgrade-cost", 0);
    }

    public int getMaxMembers(int level) {
        return config.getInt("island.levels." + level + ".max-members", 4);
    }

    public int getMaxTerritory(int level) {
        return config.getInt("island.levels." + level + ".max-territory", 1);
    }

    public int getResearchTier(int level) {
        return config.getInt("island.levels." + level + ".research-tier", 1);
    }

    public double getSellBonusPercent(int level) {
        return config.getDouble("island.levels." + level + ".sell-bonus-percent", 0);
    }

    public int getHomeWarps(int level) {
        return config.getInt("island.levels." + level + ".home-warps", 0);
    }

    public int getMaxIslandLevel() {
        ConfigurationSection section = config.getConfigurationSection("island.levels");
        if (section == null) return 1;
        int max = 1;
        for (String key : section.getKeys(false)) {
            try {
                max = Math.max(max, Integer.parseInt(key));
            } catch (NumberFormatException ignored) {
            }
        }
        return max;
    }

    // ---------------- Territory ----------------

    public boolean isTerritoryEnabled() { return config.getBoolean("territory.enabled", true); }
    public int getTerritoryDefaultRadius() { return config.getInt("territory.default-radius", 25); }
    public int getTerritoryCaptureSeconds() { return config.getInt("territory.capture-seconds", 180); }
    public boolean isTerritoryContestRequiresMajority() { return config.getBoolean("territory.contest-requires-majority", true); }
    public double getTerritoryDecayPerSecond() { return config.getDouble("territory.decay-per-second", 0.5); }

    // ---------------- Resource Islands ----------------

    public boolean isResourceIslandsEnabled() { return config.getBoolean("resource-islands.enabled", true); }

    public ConfigurationSection getResourceNodesSection() {
        return config.getConfigurationSection("resource-islands.nodes");
    }

    // ---------------- Bridge ----------------

    public boolean isBridgeDestructible() { return config.getBoolean("bridge.destructible", false); }
    public boolean isBridgeDestructibleDuringWarOnly() { return config.getBoolean("bridge.destructible-during-war-only", true); }
    public List<String> getBridgeAllowedClans() { return config.getStringList("bridge.allowed-clans"); }

    // ---------------- Clan ----------------

    public int getMinNameLength() { return config.getInt("clan.min-name-length", 3); }
    public int getMaxNameLength() { return config.getInt("clan.max-name-length", 16); }
    public int getMinTagLength() { return config.getInt("clan.min-tag-length", 2); }
    public int getMaxTagLength() { return config.getInt("clan.max-tag-length", 5); }
    public int getMaxOfficers() { return config.getInt("clan.max-officers", 3); }
    public double getStartingBalance() { return config.getDouble("clan.starting-balance", 500); }

    public List<String> getDefaultPermissions(String role) {
        return config.getStringList("clan.default-permissions." + role);
    }

    // ---------------- Protection ----------------

    public boolean isProtectionEnabled() { return config.getBoolean("protection.enabled", true); }
    public double getProtectionBuffer() { return config.getDouble("protection.protect-outside-island-radius-buffer", 0); }

    // ---------------- Raid ----------------

    public boolean isRaidEnabled() { return config.getBoolean("raid.enabled", true); }
    public long getPreparationHours() { return config.getLong("raid.preparation-hours", 72); }
    public long getWarHours() { return config.getLong("raid.war-hours", 12); }
    public long getCooldownHours() { return config.getLong("raid.cooldown-hours", 24); }
    public int getRaidZoneRadius() { return config.getInt("raid.raid-zone-radius", 999); }
    public int getAttackDurationMinutes() { return config.getInt("raid.attack-duration-minutes", 15); }
    public double getMaxPowerDifferencePercent() { return config.getDouble("raid.max-power-difference-percent", 0); }
    public boolean isRaidBreakAllowed() { return config.getBoolean("raid.rules.break-allowed", true); }
    public boolean isRaidLootAllowed() { return config.getBoolean("raid.rules.loot-allowed", true); }
    public boolean isRaidCombatEnabled() { return config.getBoolean("raid.rules.combat-enabled", true); }
    public boolean isLootItems() { return config.getBoolean("raid.loot.items", true); }
    public boolean isLootMoney() { return config.getBoolean("raid.loot.money", true); }
    public double getLootPercentage() { return config.getDouble("raid.loot.percentage", 50); }
    public boolean isFriendlyFire() { return config.getBoolean("raid.combat.friendly-fire", false); }
    public String getRespawnMode() { return config.getString("raid.respawn.mode", "ISLAND_HOME"); }

    // ---------------- Raid capture objective ----------------

    public int getCaptureRadius() { return config.getInt("raid.capture.radius", 12); }
    public int getCaptureSeconds() { return config.getInt("raid.capture.capture-seconds", 240); }
    public double getCaptureDecayPerSecond() { return config.getDouble("raid.capture.progress-decay-per-second", 1.0); }
    public String getDefenseMaterial() { return config.getString("raid.capture.defense.material", "OBSIDIAN"); }
    public double getDefenseSecondsPerBlock() { return config.getDouble("raid.capture.defense.seconds-added-per-block", 1.5); }
    public double getDefenseMaxAddedSeconds() { return config.getDouble("raid.capture.defense.max-added-seconds", 300); }

    // ---------------- Economy ----------------

    public ConfigurationSection getShopSection() {
        return config.getConfigurationSection("economy.shop");
    }

    public boolean isMarketEnabled() { return config.getBoolean("economy.market.enabled", true); }
    public double getMarketDropPercentPerUnit() { return config.getDouble("economy.market.price-drop-percent-per-sale-unit", 0.05); }
    public double getMarketRecoveryPercentPerMinute() { return config.getDouble("economy.market.recovery-percent-per-minute", 1.0); }
    public double getMarketFloorPercent() { return config.getDouble("economy.market.floor-percent-of-base", 40); }
    public double getMarketCeilingPercent() { return config.getDouble("economy.market.ceiling-percent-of-base", 150); }

    // ---------------- Power ----------------

    public double getPowerWeight(String key) {
        return config.getDouble("power.weights." + key, 0);
    }

    public int getActiveMemberWindowHours() { return config.getInt("power.active-member-window-hours", 168); }

    // ---------------- Research ----------------

    public boolean isResearchEnabled() { return config.getBoolean("research.enabled", true); }

    public ConfigurationSection getResearchTechsSection() {
        return config.getConfigurationSection("research.techs");
    }

    // ---------------- World Events ----------------

    public boolean isEventsEnabled() { return config.getBoolean("events.enabled", true); }
    public int getEventIntervalMinutes() { return config.getInt("events.interval-minutes", 45); }
    public int getEventWarningSeconds() { return config.getInt("events.warning-seconds", 60); }

    public ConfigurationSection getEventTypesSection() {
        return config.getConfigurationSection("events.types");
    }

    public List<String> getEventLocations() { return config.getStringList("events.locations"); }
    public int getEventClaimRadius() { return config.getInt("events.claim-radius", 20); }

    // ---------------- Scout ----------------

    public boolean isScoutEnabled() { return config.getBoolean("scout.enabled", true); }
    public double getScoutCost() { return config.getDouble("scout.cost-money", 250); }
    public int getScoutCooldownSeconds() { return config.getInt("scout.cooldown-seconds", 300); }

    // ---------------- Season ----------------

    public boolean isSeasonEnabled() { return config.getBoolean("season.enabled", true); }
    public int getSeasonLengthDays() { return config.getInt("season.length-days", 30); }
    public boolean isSeasonAutoReset() { return config.getBoolean("season.auto-reset", true); }
    public boolean isSeasonResetRaidStats() { return config.getBoolean("season.reset-raid-stats", true); }
    public boolean isSeasonResetTerritory() { return config.getBoolean("season.reset-territory", true); }

    // ---------------- GUI ----------------

    public String getGuiTitle(String key) {
        return config.getString("gui." + key, "&aMenu");
    }

    // ---------------- Scoreboard ----------------

    public boolean isScoreboardEnabled() { return config.getBoolean("scoreboard.enabled", true); }
    public int getScoreboardInterval() { return config.getInt("scoreboard.update-interval-ticks", 20); }
    public String getScoreboardTitle() { return config.getString("scoreboard.title", "&aIsland Warfare"); }
    public List<String> getScoreboardLines() { return config.getStringList("scoreboard.lines"); }

    // ---------------- Messages ----------------

    public String getPrefix() { return config.getString("messages.prefix", "&8[&aIslandWarfare&8]&r "); }

    public String getMessage(String key) {
        return config.getString("messages." + key, key);
    }
}
