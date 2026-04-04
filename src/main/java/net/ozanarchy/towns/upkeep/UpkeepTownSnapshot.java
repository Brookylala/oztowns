package net.ozanarchy.towns.upkeep;

import java.sql.Timestamp;

public record UpkeepTownSnapshot(
        int townId,
        double bankBalance,
        double variableUpkeepCost,
        Timestamp lastPaymentAt,
        int unpaidCycles,
        UpkeepState state,
        int decayCycles,
        Timestamp lastActivityAt
) {
}

