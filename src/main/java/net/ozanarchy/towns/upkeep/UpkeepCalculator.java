package net.ozanarchy.towns.upkeep;

public class UpkeepCalculator {
    private final UpkeepConfigManager config;
    private final net.ozanarchy.towns.town.tier.TownTierPerkService tierPerkService;

    public UpkeepCalculator(UpkeepConfigManager config, net.ozanarchy.towns.town.tier.TownTierPerkService tierPerkService) {
        this.config = config;
        this.tierPerkService = tierPerkService;
    }

    public double calculatePaymentDue(UpkeepTownSnapshot snapshot) {
        double variable = Math.max(0.0, snapshot.variableUpkeepCost());
        double due = Math.max(0.0, config.baseCost() + variable);
        if (tierPerkService == null) {
            return due;
        }
        return tierPerkService.applyUpkeepCostModifier(snapshot.townId(), due);
    }
}
