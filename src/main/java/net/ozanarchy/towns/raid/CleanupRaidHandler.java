package net.ozanarchy.towns.raid;

import net.ozanarchy.towns.TownsPlugin;
import org.bukkit.Bukkit;

public class CleanupRaidHandler {
    private final TownsPlugin plugin;
    private final RaidOutcomeHandler raidOutcomeHandler;

    public CleanupRaidHandler(TownsPlugin plugin, RaidOutcomeHandler raidOutcomeHandler) {
        this.plugin = plugin;
        this.raidOutcomeHandler = raidOutcomeHandler;
    }

    public void executeCleanupFlow(RaidExecutionContext context, RaidStateBehaviorDefinition behavior) {
        long delayTicks = Math.max(1L, behavior.timerSeconds()) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> raidOutcomeHandler.completeRaid(context, behavior), delayTicks);
    }
}
