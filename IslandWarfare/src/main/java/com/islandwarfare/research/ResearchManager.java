package com.islandwarfare.research;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.config.ConfigManager;
import com.islandwarfare.island.Island;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Effects are simple additive percentage bonuses looked up by other systems
 * (mining yield in {@link com.islandwarfare.resource.ResourceManager},
 * shop sell price in {@link com.islandwarfare.economy.EconomyService},
 * clan power, and raid defense timers) via {@link #getEffectBonus}, so a
 * research unlock is never "just a flag" - every effect type is wired to
 * something that actually changes gameplay.
 */
public class ResearchManager {

    private final IslandWarfare plugin;
    private final ConfigManager cfg;

    private final Map<String, TechDef> techDefs = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> unlocked = new HashMap<>();

    public ResearchManager(IslandWarfare plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
        loadDefs();
    }

    private void loadDefs() {
        techDefs.clear();
        ConfigurationSection section = cfg.getResearchTechsSection();
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(id);
            if (s == null) continue;
            Map<String, Integer> items = new LinkedHashMap<>();
            ConfigurationSection itemsSection = s.getConfigurationSection("cost-items");
            if (itemsSection != null) {
                for (String matKey : itemsSection.getKeys(false)) {
                    items.put(matKey, itemsSection.getInt(matKey));
                }
            }
            TechDef def = new TechDef(
                    id,
                    s.getString("name", id),
                    s.getInt("tier", 1),
                    s.getDouble("cost-money", 0),
                    items,
                    s.getStringList("requires"),
                    s.getString("effect-type", ""),
                    s.getDouble("effect-value", 0)
            );
            techDefs.put(id, def);
        }
    }

    public Collection<TechDef> getAllTechs() { return techDefs.values(); }

    public TechDef getTech(String id) { return techDefs.get(id); }

    public Set<String> getUnlocked(UUID clanId) {
        return unlocked.computeIfAbsent(clanId, k -> new HashSet<>());
    }

    public boolean isUnlocked(UUID clanId, String techId) {
        return getUnlocked(clanId).contains(techId);
    }

    public void registerLoadedUnlocks(UUID clanId, Set<String> techIds) {
        unlocked.put(clanId, new HashSet<>(techIds));
    }

    /** Sum of effect-value across every unlocked tech of the given effect type for a clan. */
    public double getEffectBonus(UUID clanId, String effectType) {
        double total = 0;
        for (String techId : getUnlocked(clanId)) {
            TechDef def = techDefs.get(techId);
            if (def != null && def.getEffectType().equalsIgnoreCase(effectType)) {
                total += def.getEffectValue();
            }
        }
        return total;
    }

    public enum UnlockResult {
        SUCCESS, ALREADY_UNLOCKED, TIER_LOCKED, MISSING_REQUIREMENT, NOT_ENOUGH_MONEY, MISSING_ITEMS, UNKNOWN_TECH
    }

    public static class AttemptResult {
        public final UnlockResult result;
        public final String missingItemsDescription;

        AttemptResult(UnlockResult result, String missingItemsDescription) {
            this.result = result;
            this.missingItemsDescription = missingItemsDescription;
        }
    }

    public AttemptResult attemptUnlock(Player player, Clan clan, Island island, String techId) {
        TechDef def = techDefs.get(techId);
        if (def == null) return new AttemptResult(UnlockResult.UNKNOWN_TECH, null);

        if (isUnlocked(clan.getId(), techId)) {
            return new AttemptResult(UnlockResult.ALREADY_UNLOCKED, null);
        }

        int islandLevel = island != null ? island.getLevel() : 1;
        int maxTier = cfg.getResearchTier(islandLevel);
        if (def.getTier() > maxTier) {
            return new AttemptResult(UnlockResult.TIER_LOCKED, null);
        }

        for (String req : def.getRequires()) {
            if (!isUnlocked(clan.getId(), req)) {
                return new AttemptResult(UnlockResult.MISSING_REQUIREMENT, null);
            }
        }

        // Validate item costs against the player's inventory BEFORE taking anything (atomic check-then-act).
        StringBuilder missing = new StringBuilder();
        for (Map.Entry<String, Integer> entry : def.getCostItems().entrySet()) {
            Material material = Material.matchMaterial(entry.getKey());
            if (material == null) continue;
            int required = entry.getValue();
            int have = countItem(player, material);
            if (have < required) {
                if (missing.length() > 0) missing.append(", ");
                missing.append(required - have).append("x ").append(material.name());
            }
        }
        if (missing.length() > 0) {
            return new AttemptResult(UnlockResult.MISSING_ITEMS, missing.toString());
        }

        if (clan.getBalance() < def.getCostMoney()) {
            return new AttemptResult(UnlockResult.NOT_ENOUGH_MONEY, null);
        }

        // All checks passed - now actually deduct, atomically from the caller's point of view.
        clan.withdraw(def.getCostMoney());
        for (Map.Entry<String, Integer> entry : def.getCostItems().entrySet()) {
            Material material = Material.matchMaterial(entry.getKey());
            if (material == null) continue;
            removeItem(player, material, entry.getValue());
        }
        getUnlocked(clan.getId()).add(techId);
        return new AttemptResult(UnlockResult.SUCCESS, null);
    }

    private int countItem(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) total += stack.getAmount();
        }
        return total;
    }

    private void removeItem(Player player, Material material, int amount) {
        player.getInventory().removeItem(new ItemStack(material, amount));
    }
}
