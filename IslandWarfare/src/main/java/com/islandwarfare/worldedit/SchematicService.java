package com.islandwarfare.worldedit;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.island.Island;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Wraps all WorldEdit interaction: loading .schem files from the plugin's
 * schematics folder and pasting them at an island's location.
 *
 * Clipboards are cached in memory after first load so repeated pastes
 * (many islands sharing a level template) never re-read/re-parse the file
 * from disk, keeping upgrades and initial creation cheap.
 */
public class SchematicService {

    private final IslandWarfare plugin;
    private final File schematicsFolder;
    private final Map<String, Clipboard> cache = new HashMap<>();
    private boolean worldEditAvailable;

    public SchematicService(IslandWarfare plugin) {
        this.plugin = plugin;
        this.schematicsFolder = new File(plugin.getDataFolder(), "schematics");
        if (!schematicsFolder.exists()) schematicsFolder.mkdirs();
        this.worldEditAvailable = Bukkit.getPluginManager().getPlugin("WorldEdit") != null
                || Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null;
        if (!worldEditAvailable) {
            plugin.getLogger().warning("WorldEdit/FAWE was not found. Schematic pasting is disabled until it is installed.");
        }
    }

    public boolean isWorldEditAvailable() {
        return worldEditAvailable;
    }

    private Clipboard loadClipboard(String fileName) {
        if (cache.containsKey(fileName)) return cache.get(fileName);

        File file = new File(schematicsFolder, fileName);
        if (!file.exists()) {
            plugin.getLogger().warning("Schematic file not found: " + file.getPath());
            return null;
        }
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            plugin.getLogger().warning("Unknown schematic format for file: " + fileName);
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file); ClipboardReader reader = format.getReader(fis)) {
            Clipboard clipboard = reader.read();
            cache.put(fileName, clipboard);
            return clipboard;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load schematic " + fileName, e);
            return null;
        }
    }

    /**
     * Pastes the schematic configured for the given level, centered on the
     * island's paste location. Runs the WorldEdit edit session synchronously
     * (WorldEdit itself is not thread-safe for world edits), but the file
     * load is cached so this is just the paste operation cost.
     */
    public boolean pasteIslandSchematic(Island island, int level) {
        if (!worldEditAvailable) {
            plugin.getLogger().warning("Cannot paste island schematic: WorldEdit is not installed.");
            return false;
        }
        String fileName = plugin.getConfigManager().getIslandSchematic(level);
        Clipboard clipboard = loadClipboard(fileName);
        if (clipboard == null) return false;

        org.bukkit.World bukkitWorld = Bukkit.getWorld(island.getWorldName());
        if (bukkitWorld == null) return false;

        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(bukkitWorld);

        BlockVector3 origin = BlockVector3.at(island.getCenterX(), island.getCenterY(), island.getCenterZ());

        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(weWorld)
                .maxBlocks(-1)
                .build()) {
            ClipboardHolder holder = new ClipboardHolder(clipboard);
            Operation operation = holder
                    .createPaste(editSession)
                    .to(origin)
                    .ignoreAirBlocks(false)
                    .build();
            Operations.complete(operation);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to paste schematic for island #" + island.getId(), e);
            return false;
        }
    }

    public void clearCache() {
        cache.clear();
    }
}
