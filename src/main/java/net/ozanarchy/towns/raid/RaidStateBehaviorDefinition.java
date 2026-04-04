package net.ozanarchy.towns.raid;

import net.ozanarchy.towns.upkeep.UpkeepState;

public record RaidStateBehaviorDefinition(
        UpkeepState upkeepState,
        RaidFlowType mode,
        int timerSeconds,
        boolean alertsEnabled,
        RaidPayoutMode payoutMode,
        double payoutPercent,
        boolean deleteTownOnSuccess
) {
}
