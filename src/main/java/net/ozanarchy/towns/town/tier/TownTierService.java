package net.ozanarchy.towns.town.tier;

import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.util.db.DatabaseHandler;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;

import static net.ozanarchy.towns.TownsPlugin.tiersConfig;

public class TownTierService {
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
            definitions.put(tier, new TownTierDefinition(tier, displayName, upgradeProgress, decay));
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
        return new TownTierDefinition(tier, tier.name(), 100.0, 0.0);
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
}

