package net.ozanarchy.towns.town.tier;

import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.upkeep.UpkeepState;
import net.ozanarchy.towns.util.db.DatabaseHandler;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;

import static net.ozanarchy.towns.TownsPlugin.tiersConfig;

public class TownTierService {
    public record TierUpgradeResult(
            boolean upgraded,
            TownTier previousTier,
            TownTier newTier,
            int upgradesApplied,
            double remainingProgress
    ) {
    }

    private final TownsPlugin plugin;
    private final DatabaseHandler databaseHandler;
    private final Map<TownTier, TownTierDefinition> definitions = new EnumMap<>(TownTier.class);

    public TownTierService(TownsPlugin plugin, DatabaseHandler databaseHandler) {
        this.plugin = plugin;
        this.databaseHandler = databaseHandler;
        reloadDefinitions();
    }

    public void reloadDefinitions() {
        definitions.clear();
        FileConfiguration cfg = tiersConfig;
        if (cfg == null) {
            return;
        }

        ConfigurationSection section = cfg.getConfigurationSection("tiers.definitions");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            TownTier tier = TownTier.fromKey(key, null);
            if (tier == null) {
                continue;
            }
            String path = "tiers.definitions." + key + ".";
            String displayName = cfg.getString(path + "display-name", key);
            double upgradeProgress = cfg.getDouble(path + "upgrade-progress-required", 100.0);
            double decay = cfg.getDouble(path + "passive-decay-per-cycle", 0.0);
            double claimDiscount = cfg.getDouble(path + "perks.claim-cost-discount-percent", 0.0);
            double upkeepModifier = cfg.getDouble(path + "perks.upkeep-cost-modifier", 1.0);
            double bankCapBonus = cfg.getDouble(path + "perks.bank-cap-bonus", 0.0);
            int claimCapBonus = cfg.getInt(path + "perks.claim-cap-bonus", 0);
            definitions.put(tier, new TownTierDefinition(tier, displayName, upgradeProgress, decay, claimDiscount, upkeepModifier, bankCapBonus, claimCapBonus));
        }
    }

    public boolean isEnabled() {
        return tiersConfig != null && tiersConfig.getBoolean("tiers.enabled", true);
    }

    public boolean useProgress() {
        return tiersConfig != null && tiersConfig.getBoolean("tiers.use-progress", true);
    }

    public TownTier getDefaultTier() {
        String configured = tiersConfig == null ? "TIER_1" : tiersConfig.getString("tiers.default-tier", "TIER_1");
        return TownTier.fromKey(configured, TownTier.TIER_1);
    }

    public TownTier getMaxTier() {
        String configured = tiersConfig == null ? "TIER_5" : tiersConfig.getString("tiers.max-tier", "TIER_5");
        return TownTier.fromKey(configured, TownTier.TIER_5);
    }

    public TownTierDefinition getDefinition(TownTier tier) {
        TownTierDefinition definition = definitions.get(tier);
        if (definition != null) {
            return definition;
        }
        return new TownTierDefinition(tier, tier.name(), 100.0, 0.0, 0.0, 1.0, 0.0, 0);
    }

    public TownTier getTownTier(int townId) {
        String tierKey = databaseHandler.getTownTierKey(townId);
        TownTier fallback = getDefaultTier();
        return TownTier.fromKey(tierKey, fallback);
    }

    public void setTownTier(int townId, TownTier tier) {
        TownTier safeTier = tier == null ? getDefaultTier() : tier;
        databaseHandler.setTownTierKey(townId, safeTier.name());
    }

    public double getTierProgress(int townId) {
        return databaseHandler.getTownTierProgress(townId);
    }

    public void setTierProgress(int townId, double progress) {
        databaseHandler.setTownTierProgress(townId, Math.max(0.0, progress));
    }

    public void increaseTierProgress(int townId, double amount) {
        if (amount <= 0) {
            return;
        }
        databaseHandler.incrementTownTierProgress(townId, amount);
    }

    public void decreaseTierProgress(int townId, double amount) {
        if (amount <= 0) {
            return;
        }
        double current = getTierProgress(townId);
        setTierProgress(townId, Math.max(0.0, current - amount));
    }

    public int getTierStreak(int townId) {
        return databaseHandler.getTownTierStreak(townId);
    }

    public void setTierStreak(int townId, int streak) {
        databaseHandler.setTownTierStreak(townId, Math.max(0, streak));
    }

    public boolean canUpgradeTier(int townId) {
        if (!isEnabled()) {
            return false;
        }
        TownTier current = getTownTier(townId);
        TownTier max = getMaxTier();
        if (current.level() >= max.level()) {
            return false;
        }
        if (!useProgress()) {
            return true;
        }
        TownTierDefinition def = getDefinition(current);
        return getTierProgress(townId) >= Math.max(0.0, def.upgradeProgressRequired());
    }

    public boolean canDowngradeTier(int townId) {
        if (!isEnabled()) {
            return false;
        }
        TownTier current = getTownTier(townId);
        TownTier min = getDefaultTier();
        return current.level() > min.level();
    }

    public void initializeTownTier(int townId) {
        TownTier defaultTier = getDefaultTier();
        databaseHandler.initializeTownTierData(townId, defaultTier.name());
    }

    public TownTier getNextTier(TownTier tier) {
        if (tier == null) {
            return getDefaultTier();
        }
        TownTier next = TownTier.fromLevel(tier.level() + 1, tier);
        TownTier max = getMaxTier();
        if (next.level() > max.level()) {
            return max;
        }
        return next;
    }

    public TierUpgradeResult tryUpgradeTownTier(int townId, UpkeepState upkeepState) {
        if (!isEnabled() || !useProgress()) {
            return noUpgrade(townId);
        }
        if (!databaseHandler.townExists(townId)) {
            return noUpgrade(townId);
        }
        if (upkeepState != UpkeepState.ACTIVE) {
            return noUpgrade(townId);
        }

        TownTier currentTier = getTownTier(townId);
        TownTier maxTier = getMaxTier();
        if (currentTier.level() >= maxTier.level()) {
            return noUpgrade(townId);
        }

        double progress = Math.max(0.0, getTierProgress(townId));
        TownTier startingTier = currentTier;
        int upgradesApplied = 0;

        while (currentTier.level() < maxTier.level()) {
            TownTierDefinition currentDefinition = getDefinition(currentTier);
            double required = Math.max(0.0, currentDefinition.upgradeProgressRequired());
            if (required <= 0.0 || progress < required) {
                break;
            }

            progress = Math.max(0.0, progress - required);
            TownTier nextTier = getNextTier(currentTier);
            if (nextTier == currentTier) {
                break;
            }
            currentTier = nextTier;
            upgradesApplied++;
        }

        if (upgradesApplied <= 0) {
            return new TierUpgradeResult(false, startingTier, startingTier, 0, progress);
        }

        setTownTier(townId, currentTier);
        setTierProgress(townId, progress);
        return new TierUpgradeResult(true, startingTier, currentTier, upgradesApplied, progress);
    }

    private TierUpgradeResult noUpgrade(int townId) {
        TownTier tier = getTownTier(townId);
        return new TierUpgradeResult(false, tier, tier, 0, Math.max(0.0, getTierProgress(townId)));
    }
}
