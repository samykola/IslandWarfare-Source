package com.islandwarfare.storage;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.clan.ClanMember;
import com.islandwarfare.clan.ClanRole;
import com.islandwarfare.clan.IslandFlag;
import com.islandwarfare.island.Island;
import com.islandwarfare.island.IslandStatus;
import com.islandwarfare.raid.RaidPhase;
import com.islandwarfare.resource.ResourceNode;
import com.islandwarfare.territory.TerritoryZone;
import com.islandwarfare.territory.ZoneType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Simple, dependency-free YAML persistence. Good enough for small/medium
 * servers; the {@link StorageService} interface keeps the door open for a
 * SQLite implementation later (e.g. for very large clan counts) without any
 * other code needing to change.
 *
 * Every load path below uses defensive defaults (getX(key, fallback)) so
 * that fields added by later versions (lastSeen, territory, research, ...)
 * never throw or corrupt data when reading a file saved by an older
 * version of the plugin that didn't write them yet.
 */
public class YamlStorageService implements StorageService {

    private final IslandWarfare plugin;
    private File clansFile;
    private File islandsFile;
    private File raidFile;
    private File territoryFile;
    private File resourceFile;
    private File researchFile;
    private File seasonFile;
    private File economyFile;
    private BukkitTask autosaveTask;

    public YamlStorageService(IslandWarfare plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        clansFile = new File(plugin.getDataFolder(), "clans.yml");
        islandsFile = new File(plugin.getDataFolder(), "islands.yml");
        raidFile = new File(plugin.getDataFolder(), "raid.yml");
        territoryFile = new File(plugin.getDataFolder(), "territory.yml");
        resourceFile = new File(plugin.getDataFolder(), "resource.yml");
        researchFile = new File(plugin.getDataFolder(), "research.yml");
        seasonFile = new File(plugin.getDataFolder(), "season.yml");
        economyFile = new File(plugin.getDataFolder(), "economy.yml");
        try {
            for (File f : new File[]{clansFile, islandsFile, raidFile, territoryFile, resourceFile, researchFile, seasonFile, economyFile}) {
                if (!f.exists()) f.createNewFile();
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create storage files", e);
        }
        // Autosave every 5 minutes so a crash doesn't lose much progress.
        autosaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveAll, 20L * 60L * 5, 20L * 60L * 5);
    }

    @Override
    public void shutdown() {
        if (autosaveTask != null) autosaveTask.cancel();
        saveAll();
    }

    @Override
    public void loadAll() {
        // Islands and clans first (other systems reference island level / clan ids),
        // then everything that depends on them.
        loadIslands();
        loadClans();
        loadRaidState();
        loadTerritory();
        loadResourceNodes();
        loadResearch();
        loadSeason();
        loadEconomy();
    }

    private void loadIslands() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(islandsFile);
        ConfigurationSection root = yaml.getConfigurationSection("islands");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            try {
                int id = Integer.parseInt(key);
                UUID owner = UUID.fromString(s.getString("owner"));
                String world = s.getString("world");
                double cx = s.getDouble("centerX");
                double cy = s.getDouble("centerY");
                double cz = s.getDouble("centerZ");
                int level = s.getInt("level");
                int radius = s.getInt("radius");

                Island island = new Island(id, owner, world, cx, cy, cz, level, radius);
                island.setStatus(safeEnum(IslandStatus.class, s.getString("status", "ACTIVE"), IslandStatus.ACTIVE));
                island.setHome(s.getDouble("homeX", cx), s.getDouble("homeY", cy + 2), s.getDouble("homeZ", cz),
                        (float) s.getDouble("homeYaw", 0), (float) s.getDouble("homePitch", 0));
                plugin.getIslandManager().registerLoadedIsland(island);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Skipped malformed island entry '" + key + "' in islands.yml", e);
            }
        }
    }

    private void loadClans() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(clansFile);
        ConfigurationSection root = yaml.getConfigurationSection("clans");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            try {
                UUID id = UUID.fromString(key);
                String name = s.getString("name");
                String tag = s.getString("tag");
                UUID leader = UUID.fromString(s.getString("leader"));

                Clan clan = new Clan(id, name, tag, leader);
                clan.setBalance(s.getDouble("balance"));
                clan.setIslandId(s.getInt("islandId", -1));
                for (int i = 0; i < s.getInt("raidWins", 0); i++) clan.incrementRaidWins();
                for (int i = 0; i < s.getInt("raidLosses", 0); i++) clan.incrementRaidLosses();

                ConfigurationSection membersSection = s.getConfigurationSection("members");
                if (membersSection != null) {
                    for (String memberKey : membersSection.getKeys(false)) {
                        ConfigurationSection ms = membersSection.getConfigurationSection(memberKey);
                        if (ms == null) continue;
                        try {
                            UUID memberUuid = UUID.fromString(memberKey);
                            String memberName = ms.getString("name", memberKey);
                            ClanRole role = safeEnum(ClanRole.class, ms.getString("role", "MEMBER"), ClanRole.MEMBER);
                            ClanMember member = new ClanMember(memberUuid, memberName, role);
                            for (String flag : ms.getStringList("permissions")) {
                                try {
                                    member.grant(IslandFlag.valueOf(flag));
                                } catch (IllegalArgumentException ignored) {
                                }
                            }
                            // lastSeen/joinedAt are new fields - default to "now" for older
                            // save files so pre-existing members aren't treated as inactive.
                            long now = System.currentTimeMillis();
                            member.setJoinedAt(ms.getLong("joinedAt", now));
                            member.setLastSeen(ms.getLong("lastSeen", now));
                            clan.addMember(member);
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING, "Skipped malformed member entry '" + memberKey + "' in clans.yml", e);
                        }
                    }
                }
                plugin.getClanManager().registerLoadedClan(clan);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Skipped malformed clan entry '" + key + "' in clans.yml", e);
            }
        }
    }

    private void loadRaidState() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(raidFile);
        String phaseStr = yaml.getString("phase");
        long endsAt = yaml.getLong("endsAt", 0);
        if (phaseStr != null) {
            RaidPhase phase = safeEnum(RaidPhase.class, phaseStr, null);
            if (phase != null) {
                plugin.getRaidManager().restorePhase(phase, endsAt);
            }
        }
        // Active in-progress raids (attacker, capture progress) are intentionally NOT
        // persisted: they're short-lived (minutes), tied to online presence, and safely
        // restart as "no active raid" after a server restart rather than risk a
        // stale/inconsistent capture state being resumed with no players around it.
    }

    private void loadTerritory() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(territoryFile);
        ConfigurationSection root = yaml.getConfigurationSection("zones");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            try {
                int id = Integer.parseInt(key);
                ZoneType type = safeEnum(ZoneType.class, s.getString("type", "NEUTRAL"), ZoneType.NEUTRAL);
                String world = s.getString("world");
                double x = s.getDouble("x");
                double y = s.getDouble("y");
                double z = s.getDouble("z");
                int radius = s.getInt("radius");

                TerritoryZone zone = new TerritoryZone(id, type, world, x, y, z, radius);
                zone.setName(s.getString("name", zone.getName()));
                String ownerStr = s.getString("owner");
                if (ownerStr != null && !ownerStr.isBlank()) {
                    zone.setOwnerClanId(UUID.fromString(ownerStr));
                }
                String capturingStr = s.getString("capturingClan");
                if (capturingStr != null && !capturingStr.isBlank()) {
                    zone.setCapturingClanId(UUID.fromString(capturingStr));
                }
                zone.setCaptureProgress(s.getDouble("captureProgress", 0));

                plugin.getTerritoryManager().registerLoadedZone(zone);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Skipped malformed territory zone '" + key + "' in territory.yml", e);
            }
        }
    }

    private void loadResourceNodes() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(resourceFile);
        ConfigurationSection root = yaml.getConfigurationSection("nodes");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            try {
                String world = s.getString("world");
                int x = s.getInt("x");
                int y = s.getInt("y");
                int z = s.getInt("z");
                String nodeType = s.getString("nodeType");
                Material original = Material.matchMaterial(s.getString("originalBlock", "STONE"));
                if (original == null) original = Material.STONE;

                ResourceNode node = new ResourceNode(world, x, y, z, nodeType, original);
                if (s.getBoolean("depleted", false)) {
                    node.deplete(s.getLong("respawnAt", System.currentTimeMillis()));
                }
                plugin.getResourceManager().registerLoadedNode(node);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Skipped malformed resource node '" + key + "' in resource.yml", e);
            }
        }
    }

    private void loadResearch() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(researchFile);
        ConfigurationSection root = yaml.getConfigurationSection("clans");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                UUID clanId = UUID.fromString(key);
                Set<String> unlocked = new HashSet<>(yaml.getStringList("clans." + key));
                plugin.getResearchManager().registerLoadedUnlocks(clanId, unlocked);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Skipped malformed research entry '" + key + "' in research.yml", e);
            }
        }
    }

    private void loadSeason() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(seasonFile);
        if (!yaml.contains("number")) return;
        int number = yaml.getInt("number", 1);
        long startedAt = yaml.getLong("startedAt", System.currentTimeMillis());
        long endsAt = yaml.getLong("endsAt", 0);
        plugin.getSeasonManager().restore(number, startedAt, endsAt);
    }

    private void loadEconomy() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(economyFile);
        ConfigurationSection root = yaml.getConfigurationSection("market");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) continue;
            plugin.getEconomyService().setMarketMultiplier(material, root.getDouble(key, 1.0));
        }
    }

    private <T extends Enum<T>> T safeEnum(Class<T> type, String value, T fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    @Override
    public void saveAll() {
        saveIslands();
        saveClans();
        saveRaidState();
        saveTerritory();
        saveResourceNodes();
        saveResearch();
        saveSeason();
        saveEconomy();
    }

    private void saveIslands() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Island island : plugin.getIslandManager().getIslands()) {
            String path = "islands." + island.getId();
            yaml.set(path + ".owner", island.getOwnerClanId().toString());
            yaml.set(path + ".world", island.getWorldName());
            yaml.set(path + ".centerX", island.getCenterX());
            yaml.set(path + ".centerY", island.getCenterY());
            yaml.set(path + ".centerZ", island.getCenterZ());
            yaml.set(path + ".level", island.getLevel());
            yaml.set(path + ".radius", island.getRadius());
            yaml.set(path + ".status", island.getStatus().name());
            yaml.set(path + ".homeX", island.getHomeX());
            yaml.set(path + ".homeY", island.getHomeY());
            yaml.set(path + ".homeZ", island.getHomeZ());
            yaml.set(path + ".homeYaw", island.getHomeYaw());
            yaml.set(path + ".homePitch", island.getHomePitch());
        }
        saveQuiet(yaml, islandsFile);
    }

    private void saveClans() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Clan clan : plugin.getClanManager().getClans()) {
            String path = "clans." + clan.getId();
            yaml.set(path + ".name", clan.getName());
            yaml.set(path + ".tag", clan.getTag());
            yaml.set(path + ".leader", clan.getLeader().toString());
            yaml.set(path + ".balance", clan.getBalance());
            yaml.set(path + ".islandId", clan.getIslandId());
            yaml.set(path + ".raidWins", clan.getRaidWins());
            yaml.set(path + ".raidLosses", clan.getRaidLosses());
            for (ClanMember member : clan.getMembers().values()) {
                String mp = path + ".members." + member.getUuid();
                yaml.set(mp + ".name", member.getName());
                yaml.set(mp + ".role", member.getRole().name());
                yaml.set(mp + ".joinedAt", member.getJoinedAt());
                yaml.set(mp + ".lastSeen", member.getLastSeen());
                java.util.List<String> perms = new java.util.ArrayList<>();
                for (IslandFlag flag : member.getPermissions()) perms.add(flag.name());
                yaml.set(mp + ".permissions", perms);
            }
        }
        saveQuiet(yaml, clansFile);
    }

    private void saveRaidState() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("phase", plugin.getRaidManager().getCurrentPhase().name());
        yaml.set("endsAt", plugin.getRaidManager().getPhaseEndsAt());
        saveQuiet(yaml, raidFile);
    }

    private void saveTerritory() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (TerritoryZone zone : plugin.getTerritoryManager().getZones()) {
            String path = "zones." + zone.getId();
            yaml.set(path + ".type", zone.getType().name());
            yaml.set(path + ".world", zone.getWorldName());
            yaml.set(path + ".x", zone.getCenterX());
            yaml.set(path + ".y", zone.getCenterY());
            yaml.set(path + ".z", zone.getCenterZ());
            yaml.set(path + ".radius", zone.getRadius());
            yaml.set(path + ".name", zone.getName());
            yaml.set(path + ".owner", zone.getOwnerClanId() != null ? zone.getOwnerClanId().toString() : "");
            yaml.set(path + ".capturingClan", zone.getCapturingClanId() != null ? zone.getCapturingClanId().toString() : "");
            yaml.set(path + ".captureProgress", zone.getCaptureProgress());
        }
        saveQuiet(yaml, territoryFile);
    }

    private void saveResourceNodes() {
        YamlConfiguration yaml = new YamlConfiguration();
        int i = 0;
        for (ResourceNode node : plugin.getResourceManager().getNodes().values()) {
            String path = "nodes.n" + (i++);
            yaml.set(path + ".world", node.getWorld());
            yaml.set(path + ".x", node.getX());
            yaml.set(path + ".y", node.getY());
            yaml.set(path + ".z", node.getZ());
            yaml.set(path + ".nodeType", node.getNodeType());
            yaml.set(path + ".originalBlock", node.getOriginalBlock().name());
            yaml.set(path + ".depleted", node.isDepleted());
            yaml.set(path + ".respawnAt", node.getRespawnAt());
        }
        saveQuiet(yaml, resourceFile);
    }

    private void saveResearch() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Clan clan : plugin.getClanManager().getClans()) {
            Set<String> unlocked = plugin.getResearchManager().getUnlocked(clan.getId());
            if (unlocked.isEmpty()) continue;
            yaml.set("clans." + clan.getId(), new java.util.ArrayList<>(unlocked));
        }
        saveQuiet(yaml, researchFile);
    }

    private void saveSeason() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("number", plugin.getSeasonManager().getSeasonNumber());
        yaml.set("startedAt", plugin.getSeasonManager().getSeasonStartedAt());
        yaml.set("endsAt", plugin.getSeasonManager().getSeasonEndsAt());
        saveQuiet(yaml, seasonFile);
    }

    private void saveEconomy() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (var entry : plugin.getEconomyService().getMarketMultipliers().entrySet()) {
            yaml.set("market." + entry.getKey().name(), entry.getValue());
        }
        saveQuiet(yaml, economyFile);
    }

    private void saveQuiet(YamlConfiguration yaml, File file) {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save " + file.getName(), e);
        }
    }
}
