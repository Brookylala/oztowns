package net.ozanarchy.towns.raid;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public record RaidExecutionContext(
        Player raider,
        UUID raiderUuid,
        Block targetBlock,
        int targetTownId,
        String targetTownName,
        Integer raiderTownId,
        String raiderTownName,
        List<UUID> defenderUuids
) {
}
