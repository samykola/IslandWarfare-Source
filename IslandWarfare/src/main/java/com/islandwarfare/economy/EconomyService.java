package com.islandwarfare.economy;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.clan.Clan;
import com.islandwarfare.config.ConfigManager;
import com.islandwarfare.island.Island;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fully self-contained economy: money lives on the {@link Clan} balance
 * field. No Vault dependency is required for the plugin to function -
 * Vault is only relevant if a server wants to bridge this into a global
 * player economy later (see README).
 *
 * Three real, gameplay-affecting price modifiers stack on the base shop
 * price at sale time:
 *  - island level's sell-bonus-percent (island.levels.<n>.sell-bonus-percent)
 *  - the selling clan's unlocked ECONOMY_BONUS research (research effect-type)
 *  - the server-wide dynamic market multiplier for that material, which
 *    drops as it's sold in bulk and recovers over time (economy.market.*)
 */
public class EconomyService {

    private final IslandWarfare plugin;
    private final ConfigManager cfg;

    // material -> current price multiplier (1.0 = base price, clamped between floor/ceiling).
    private final Map<Material, Double> marketMultiplier = new HashMap<>();
    private BukkitTask recoveryTask;

    public EconomyService(IslandWarfare plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
    }

    public void start() {
        if (!cfg.isMarketEnabled()) return;
        // Recover prices once a minute - a single pass over a small, bounded map, not per-tick work.
        recoveryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::recoverPrices, 20L * 60L, 20L * 60L);
    }

    public void stop() {
        if (recoveryTask != null) recoveryTask.cancel();
    }

    public Map<Material, Double> getShopPrices() {
        Map<Material, Double> prices = new LinkedHashMap<>();
        ConfigurationSection section = cfg.getShopSection();
        if (section == null) return prices;
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) continue;
            prices.put(material, getCurrentPrice(material));
        }
        return prices;
    }

    public double getBasePrice(Material material) {
        ConfigurationSection section = cfg.getShopSection();
        if (section == null) return 0;
        return section.getDouble(material.name(), 0);
    }

    /** Base price adjusted only by the live server-wide market multiplier (used for GUI display). */
    public double getCurrentPrice(Material material) {
        double base = getBasePrice(material);
        if (base <= 0) return 0;
        return base * marketMultiplier.getOrDefault(material, 1.0);
    }

    public double getPrice(Material material) {
        return getCurrentPrice(material);
    }

    /**
     * Sells the full stack of {@code item} from the player's inventory,
     * depositing the proceeds (after island-level and research bonuses) into
     * the player's clan balance, and pushes the market price down for
     * that material. Returns the amount earned, or -1 if the sale could
     * not be completed.
     */
    public double sell(Player player, ItemStack item) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) return -1;

        double unitPrice = getCurrentPrice(item.getType());
        if (unitPrice <= 0) return -1;

        Island island = plugin.getIslandManager().getIslandByClan(clan.getId());
        double islandBonus = island != null ? cfg.getSellBonusPercent(island.getLevel()) : 0;
        double researchBonus = plugin.getResearchManager() != null
                ? plugin.getResearchManager().getEffectBonus(clan.getId(), "ECONOMY_BONUS") : 0;

        double totalBonusPercent = islandBonus + researchBonus;
        double total = unitPrice * item.getAmount() * (1 + totalBonusPercent / 100.0);
        clan.deposit(total);

        applyMarketPressure(item.getType(), item.getAmount());
        return total;
    }

    private void applyMarketPressure(Material material, int amountSold) {
        if (!cfg.isMarketEnabled()) return;
        double current = marketMultiplier.getOrDefault(material, 1.0);
        double drop = (cfg.getMarketDropPercentPerUnit() / 100.0) * amountSold;
        double floor = cfg.getMarketFloorPercent() / 100.0;
        double ceiling = cfg.getMarketCeilingPercent() / 100.0;
        double updated = Math.max(floor, Math.min(ceiling, current - drop));
        marketMultiplier.put(material, updated);
    }

    private void recoverPrices() {
        double recoveryStep = cfg.getMarketRecoveryPercentPerMinute() / 100.0;
        double ceiling = cfg.getMarketCeilingPercent() / 100.0;
        for (Map.Entry<Material, Double> entry : marketMultiplier.entrySet()) {
            double current = entry.getValue();
            if (current < 1.0) {
                entry.setValue(Math.min(1.0, current + recoveryStep));
            } else if (current > 1.0) {
                entry.setValue(Math.max(1.0, Math.min(ceiling, current - recoveryStep)));
            }
        }
    }

    /** Used by storage load so market pressure survives a restart. */
    public void setMarketMultiplier(Material material, double multiplier) {
        marketMultiplier.put(material, multiplier);
    }

    public Map<Material, Double> getMarketMultipliers() {
        return marketMultiplier;
    }
}
