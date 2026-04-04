package net.ozanarchy.towns.town.tier;

import org.bukkit.configuration.file.FileConfiguration;

import static net.ozanarchy.towns.TownsPlugin.tiersConfig;

public class TownTierPerkService {
    private final TownTierService townTierService;

    public TownTierPerkService(TownTierService townTierService) {
        this.townTierService = townTierService;
    }

    public double applyClaimCostDiscount(int townId, double baseCost) {
        TownTierDefinition definition = townTierService.getDefinition(townTierService.getTownTier(townId));
        double discountPercent = clamp(definition.claimCostDiscountPercent(), 0.0, 100.0);
        return Math.max(0.0, baseCost * (1.0 - (discountPercent / 100.0)));
    }

    public double applyUpkeepCostModifier(int townId, double baseDue) {
        TownTierDefinition definition = townTierService.getDefinition(townTierService.getTownTier(townId));
        double modifier = Math.max(0.0, definition.upkeepCostModifier());
        return Math.max(0.0, baseDue * modifier);
    }

    public double bankCapForTown(int townId) {
        double baseCap = configuredBaseBankCap();
        if (baseCap < 0.0) {
            return -1.0;
        }
        TownTierDefinition definition = townTierService.getDefinition(townTierService.getTownTier(townId));
        return Math.max(0.0, baseCap + Math.max(0.0, definition.bankCapBonus()));
    }

    public double allowedDepositAmount(int townId, double currentBalance, double requestedAmount) {
        if (requestedAmount <= 0.0) {
            return 0.0;
        }
        double cap = bankCapForTown(townId);
        if (cap < 0.0) {
            return requestedAmount;
        }
        double remaining = Math.max(0.0, cap - Math.max(0.0, currentBalance));
        return Math.max(0.0, Math.min(requestedAmount, remaining));
    }

    public int claimCapForTown(int townId) {
        int baseCap = configuredBaseClaimCap();
        if (baseCap < 0) {
            return -1;
        }
        TownTierDefinition definition = townTierService.getDefinition(townTierService.getTownTier(townId));
        int bonus = Math.max(0, definition.claimCapBonus());
        return Math.max(0, baseCap + bonus);
    }

    public boolean hasReachedClaimCap(int townId, int currentClaims) {
        int cap = claimCapForTown(townId);
        if (cap < 0) {
            return false;
        }
        return Math.max(0, currentClaims) >= cap;
    }

    private double configuredBaseBankCap() {
        FileConfiguration cfg = tiersConfig;
        if (cfg == null) {
            return -1.0;
        }
        return cfg.getDouble("tiers.perks.bank-cap.base-cap", -1.0);
    }

    private int configuredBaseClaimCap() {
        FileConfiguration cfg = tiersConfig;
        if (cfg == null) {
            return -1;
        }
        return cfg.getInt("tiers.perks.claim-cap.base-cap", -1);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
