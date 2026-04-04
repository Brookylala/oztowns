package net.ozanarchy.towns.upkeep;

import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.town.listener.TownEvents;
import net.ozanarchy.towns.upkeep.repository.UpkeepRepository;
import net.ozanarchy.towns.util.DebugLogger;
import net.ozanarchy.towns.util.db.DatabaseHandler;

public class UpkeepDecayService {
    private final TownsPlugin plugin;
    private final UpkeepConfigManager config;
    private final UpkeepRepository upkeepRepository;
    private final DatabaseHandler databaseHandler;
    private final TownEvents townEvents;

    public UpkeepDecayService(
            TownsPlugin plugin,
            UpkeepConfigManager config,
            UpkeepRepository upkeepRepository,
            DatabaseHandler databaseHandler,
            TownEvents townEvents
    ) {
        this.plugin = plugin;
        this.config = config;
        this.upkeepRepository = upkeepRepository;
        this.databaseHandler = databaseHandler;
        this.townEvents = townEvents;
    }

    public void applyDecay(int townId, UpkeepState state) {
        if (!config.decayEnabled()) {
            upkeepRepository.setDecayCycles(townId, 0);
            return;
        }

        if (config.claimDecayEnabled()
                && state.atLeast(config.claimDecayStartState())
                && config.claimDecayOneChunkPerCycle()) {
            boolean decayed = databaseHandler.decayOneClaim(townId, config.preserveSpawnChunkUntilFinalRemoval());
            DebugLogger.debug(plugin, "Claim decay tick: townId=" + townId + ", state=" + state + ", removedClaim=" + decayed);
        }

        if (!config.townRemovalEnabled()) {
            upkeepRepository.setDecayCycles(townId, 0);
            return;
        }

        if (state.atLeast(config.removeAfterState())) {
            int cycles = upkeepRepository.incrementDecayCycles(townId);
            DebugLogger.debug(plugin, "Decay cycle increment: townId=" + townId + ", cycles=" + cycles + ", state=" + state);
            if (cycles >= config.removeAfterCycles()) {
                String townName = databaseHandler.getTownName(townId);
                townEvents.abandonTown(townId);
                plugin.getLogger().info("Town " + (townName == null ? ("#" + townId) : townName)
                        + " removed after upkeep decay cycle threshold (" + cycles + ").");
            }
        } else {
            upkeepRepository.setDecayCycles(townId, 0);
        }
    }
}
