package net.ozanarchy.towns.upkeep;

import org.bukkit.configuration.file.FileConfiguration;

import static net.ozanarchy.towns.TownsPlugin.upkeepConfig;

public class UpkeepConfigManager {
    private FileConfiguration cfg() {
        return upkeepConfig;
    }

    public boolean enabled() {
        return cfg().getBoolean("enabled", true);
    }

    public long paymentIntervalMinutes() {
        return Math.max(1L, cfg().getLong("payment.interval-minutes", 1440L));
    }

    public boolean autoPayFromBank() {
        return cfg().getBoolean("payment.auto-pay-from-bank", true);
    }

    public double baseCost() {
        return Math.max(0.0, cfg().getDouble("payment.costs.base", 0.0));
    }

    public double memberAddedCost() {
        return cfg().getDouble("payment.costs.member.added", 5.0);
    }

    public double memberRefundedCost() {
        return cfg().getDouble("payment.costs.member.refunded", 5.0);
    }

    public double claimAddedCost() {
        return cfg().getDouble("payment.costs.claim.added", 10.0);
    }

    public double claimRefundedCost() {
        return cfg().getDouble("payment.costs.claim.refunded", 10.0);
    }

    public boolean activityEnabled() {
        return cfg().getBoolean("activity.enabled", true);
    }

    public long activityWindowMinutes() {
        return Math.max(1L, cfg().getLong("activity.recent-login-within-minutes", 10080L));
    }

    public boolean requireAnyMemberOnlineWithinWindow() {
        return cfg().getBoolean("activity.require-any-member-online-within-window", true);
    }

    public int overdueUnpaidCycles() {
        return Math.max(1, cfg().getInt("states.overdue.unpaid-cycles", 1));
    }

    public int neglectedUnpaidCycles() {
        return Math.max(overdueUnpaidCycles(), cfg().getInt("states.neglected.unpaid-cycles", 2));
    }

    public long neglectedInactiveMinutes() {
        return Math.max(1L, cfg().getLong("states.neglected.inactive-minutes", 10080L));
    }

    public int abandonedUnpaidCycles() {
        return Math.max(neglectedUnpaidCycles(), cfg().getInt("states.abandoned.unpaid-cycles", 4));
    }

    public long abandonedInactiveMinutes() {
        return Math.max(neglectedInactiveMinutes(), cfg().getLong("states.abandoned.inactive-minutes", 20160L));
    }

    public int decayingUnpaidCycles() {
        return Math.max(abandonedUnpaidCycles(), cfg().getInt("states.decaying.unpaid-cycles", 6));
    }

    public long decayingInactiveMinutes() {
        return Math.max(abandonedInactiveMinutes(), cfg().getLong("states.decaying.inactive-minutes", 30240L));
    }

    public boolean tierIntegrationEnabled() {
        return cfg().getBoolean("tier-integration.enabled", true);
    }

    public double progressPerSuccessfulPayment() {
        return Math.max(0.0, cfg().getDouble("tier-integration.progression.progress-per-successful-payment", 25.0));
    }

    public boolean requireActivityForProgress() {
        return cfg().getBoolean("tier-integration.progression.require-activity-for-progress", true);
    }

    public boolean loseProgressWhenUnpaid() {
        return cfg().getBoolean("tier-integration.decay.lose-progress-when-unpaid", true);
    }

    public double progressLossPerUnpaidCycle() {
        return Math.max(0.0, cfg().getDouble("tier-integration.decay.progress-loss-per-unpaid-cycle", 15.0));
    }

    public boolean loseProgressWhenInactive() {
        return cfg().getBoolean("tier-integration.decay.lose-progress-when-inactive", true);
    }

    public double progressLossPerInactiveCycle() {
        return Math.max(0.0, cfg().getDouble("tier-integration.decay.progress-loss-per-inactive-cycle", 10.0));
    }

    public boolean downgradeEnabled() {
        return cfg().getBoolean("tier-integration.downgrade.enabled", true);
    }

    public boolean downgradeWhenProgressZero() {
        return cfg().getBoolean("tier-integration.downgrade.downgrade-when-progress-reaches-zero", true);
    }

    public boolean resetProgressOnDowngrade() {
        return cfg().getBoolean("tier-integration.downgrade.reset-progress-on-downgrade", false);
    }

    public boolean decayEnabled() {
        return cfg().getBoolean("decay.enabled", true);
    }

    public boolean claimDecayEnabled() {
        return cfg().getBoolean("decay.claim-decay.enabled", false);
    }

    public UpkeepState claimDecayStartState() {
        return UpkeepState.fromString(cfg().getString("decay.claim-decay.start-at-state", "DECAYING"), UpkeepState.DECAYING);
    }

    public boolean claimDecayOneChunkPerCycle() {
        return cfg().getBoolean("decay.claim-decay.unclaim-one-chunk-per-cycle", true);
    }

    public boolean preserveSpawnChunkUntilFinalRemoval() {
        return cfg().getBoolean("decay.claim-decay.preserve-spawn-chunk-until-final-removal", true);
    }

    public boolean townRemovalEnabled() {
        return cfg().getBoolean("decay.town-removal.enabled", true);
    }

    public UpkeepState removeAfterState() {
        return UpkeepState.fromString(cfg().getString("decay.town-removal.remove-after-state", "DECAYING"), UpkeepState.DECAYING);
    }

    public int removeAfterCycles() {
        return Math.max(1, cfg().getInt("decay.town-removal.remove-after-cycles", 10));
    }
}

