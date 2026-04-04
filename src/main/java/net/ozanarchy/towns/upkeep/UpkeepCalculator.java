package net.ozanarchy.towns.upkeep;

public class UpkeepCalculator {
    private final UpkeepConfigManager config;

    public UpkeepCalculator(UpkeepConfigManager config) {
        this.config = config;
    }

    public double calculatePaymentDue(UpkeepTownSnapshot snapshot) {
        double variable = Math.max(0.0, snapshot.variableUpkeepCost());
        return Math.max(0.0, config.baseCost() + variable);
    }
}

