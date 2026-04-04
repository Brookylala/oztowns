package net.ozanarchy.towns.town.tier;

public record TownTierDefinition(
        TownTier tier,
        String displayName,
        double upgradeProgressRequired,
        double passiveDecayPerCycle,
        double claimCostDiscountPercent,
        double upkeepCostModifier,
        double bankCapBonus,
        int claimCapBonus
) {
}
