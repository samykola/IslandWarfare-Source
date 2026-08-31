package com.islandwarfare;

import com.islandwarfare.clan.ClanManager;
import com.islandwarfare.clan.ClanPowerService;
import com.islandwarfare.commands.AdminCommand;
import com.islandwarfare.commands.ClanCommand;
import com.islandwarfare.commands.IslandCommand;
import com.islandwarfare.commands.RaidCommand;
import com.islandwarfare.commands.ResearchCommand;
import com.islandwarfare.commands.ShopCommand;
import com.islandwarfare.commands.TerritoryCommand;
import com.islandwarfare.config.ConfigManager;
import com.islandwarfare.economy.EconomyService;
import com.islandwarfare.events.EventManager;
import com.islandwarfare.gui.GuiListener;
import com.islandwarfare.island.IslandHomeService;
import com.islandwarfare.island.IslandManager;
import com.islandwarfare.island.IslandUpgradeService;
import com.islandwarfare.leaderboard.LeaderboardManager;
import com.islandwarfare.listeners.CombatListener;
import com.islandwarfare.listeners.PlayerListener;
import com.islandwarfare.protection.ProtectionListener;
import com.islandwarfare.raid.RaidManager;
import com.islandwarfare.research.ResearchManager;
import com.islandwarfare.resource.ResourceManager;
import com.islandwarfare.scoreboard.ScoreboardManager;
import com.islandwarfare.season.SeasonManager;
import com.islandwarfare.storage.StorageService;
import com.islandwarfare.storage.YamlStorageService;
import com.islandwarfare.territory.TerritoryManager;
import com.islandwarfare.worldedit.SchematicService;
import org.bukkit.Bukkit;
import org.bukkit.WorldCreator;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Level;

public final class IslandWarfare extends JavaPlugin {

    private static IslandWarfare instance;

    private ConfigManager configManager;
    private ClanManager clanManager;
    private IslandManager islandManager;
    private RaidManager raidManager;
    private EconomyService economyService;
    private SchematicService schematicService;
    private StorageService storageService;
    private ScoreboardManager scoreboardManager;
    private LeaderboardManager leaderboardManager;
    private IslandHomeService islandHomeService;
    private IslandUpgradeService islandUpgradeService;
    private TerritoryManager territoryManager;
    private ResourceManager resourceManager;
    private ResearchManager researchManager;
    private EventManager eventManager;
    private SeasonManager seasonManager;
    private ClanPowerService clanPowerService;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        copyDefaultSchematics();

        // Order matters: config first, then registries, then services that depend on them.
        this.configManager = new ConfigManager(this);
        this.clanManager = new ClanManager(this);
        this.islandManager = new IslandManager(this);
        this.schematicService = new SchematicService(this);
        this.territoryManager = new TerritoryManager(this);
        this.resourceManager = new ResourceManager(this);
        this.researchManager = new ResearchManager(this);
        this.eventManager = new EventManager(this);
        this.raidManager = new RaidManager(this);
        this.economyService = new EconomyService(this);
        this.scoreboardManager = new ScoreboardManager(this);
        this.leaderboardManager = new LeaderboardManager(this);
        this.islandHomeService = new IslandHomeService(this);
        this.islandUpgradeService = new IslandUpgradeService(this);
        this.seasonManager = new SeasonManager(this);
        this.clanPowerService = new ClanPowerService(this);

        ensureWorldLoaded();

        this.storageService = new YamlStorageService(this);
        storageService.init();
        storageService.loadAll();
        resourceManager.rescheduleAfterLoad();

        registerCommands();
        registerListeners();

        raidManager.start();
        scoreboardManager.start();
        territoryManager.start();
        eventManager.start();
        seasonManager.start();
        economyService.start();

        getLogger().info("IslandWarfare enabled - " + clanManager.getClanCount() + " clans, "
                + islandManager.countIslands() + " islands, " + territoryManager.getZones().size() + " territory zones loaded.");
    }

    @Override
    public void onDisable() {
        if (raidManager != null) raidManager.stop();
        if (scoreboardManager != null) scoreboardManager.stop();
        if (territoryManager != null) territoryManager.stop();
        if (eventManager != null) eventManager.stop();
        if (seasonManager != null) seasonManager.stop();
        if (economyService != null) economyService.stop();
        if (storageService != null) storageService.shutdown();
        getLogger().info("IslandWarfare disabled - data saved.");
    }

    private void ensureWorldLoaded() {
        String worldName = configManager.getWorldName();
        if (Bukkit.getWorld(worldName) == null) {
            getLogger().info("World '" + worldName + "' not found, creating it now...");
            WorldCreator creator = new WorldCreator(worldName);
            Bukkit.createWorld(creator);
        }
    }

    private void copyDefaultSchematics() {
        File schematicsFolder = new File(getDataFolder(), "schematics");
        if (!schematicsFolder.exists()) schematicsFolder.mkdirs();

        // Ship a README inside schematics/ so builders know exactly what to name their files.
        File readme = new File(schematicsFolder, "README.txt");
        if (!readme.exists()) {
            try (InputStream in = getResource("schematics/README.txt")) {
                if (in != null) {
                    Files.copy(in, readme.toPath());
                }
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "Could not copy schematics README", e);
            }
        }
    }

    private void registerCommands() {
        setExecutor("island", new IslandCommand(this));
        setExecutor("clan", new ClanCommand(this));
        setExecutor("raid", new RaidCommand(this));
        setExecutor("shop", new ShopCommand(this));
        setExecutor("territory", new TerritoryCommand(this));
        setExecutor("research", new ResearchCommand(this));
        setExecutor("iw", new AdminCommand(this));
    }

    private void setExecutor(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml!");
            return;
        }
        command.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CombatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);
        Bukkit.getPluginManager().registerEvents(eventManager, this);
    }

    public static IslandWarfare getInstance() { return instance; }

    public ConfigManager getConfigManager() { return configManager; }
    public ClanManager getClanManager() { return clanManager; }
    public IslandManager getIslandManager() { return islandManager; }
    public RaidManager getRaidManager() { return raidManager; }
    public EconomyService getEconomyService() { return economyService; }
    public SchematicService getSchematicService() { return schematicService; }
    public StorageService getStorageService() { return storageService; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public LeaderboardManager getLeaderboardManager() { return leaderboardManager; }
    public IslandHomeService getIslandHomeService() { return islandHomeService; }
    public IslandUpgradeService getIslandUpgradeService() { return islandUpgradeService; }
    public TerritoryManager getTerritoryManager() { return territoryManager; }
    public ResourceManager getResourceManager() { return resourceManager; }
    public ResearchManager getResearchManager() { return researchManager; }
    public EventManager getEventManager() { return eventManager; }
    public SeasonManager getSeasonManager() { return seasonManager; }
    public ClanPowerService getClanPowerService() { return clanPowerService; }
}
