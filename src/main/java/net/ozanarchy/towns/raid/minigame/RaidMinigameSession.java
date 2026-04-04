package net.ozanarchy.towns.raid.minigame;

import net.ozanarchy.towns.raid.RaidExecutionContext;
import net.ozanarchy.towns.raid.RaidMinigameSettings;
import net.ozanarchy.towns.raid.RaidStateBehaviorDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Consumer;

public final class RaidMinigameSession implements InventoryHolder {
    private final RaidExecutionContext context;
    private final RaidStateBehaviorDefinition behavior;
    private final RaidMinigameSettings settings;
    private final Consumer<MinigameResult> completion;
    private final Inventory inventory;

    private BukkitTask task;
    private int elapsedTicks;
    private int indicatorIndex;
    private int direction;
    private boolean completed;

    public RaidMinigameSession(RaidExecutionContext context,
                               RaidStateBehaviorDefinition behavior,
                               RaidMinigameSettings settings,
                               Consumer<MinigameResult> completion) {
        this.context = context;
        this.behavior = behavior;
        this.settings = settings;
        this.completion = completion;
        this.inventory = Bukkit.createInventory(this, 27, settings.title());
        this.indicatorIndex = 0;
        this.direction = 1;
    }

    public RaidExecutionContext context() { return context; }

    public RaidStateBehaviorDefinition behavior() { return behavior; }

    public RaidMinigameSettings settings() { return settings; }

    public Consumer<MinigameResult> completion() { return completion; }

    @Override
    public Inventory getInventory() { return inventory; }

    public BukkitTask task() { return task; }

    public void setTask(BukkitTask task) { this.task = task; }

    public int elapsedTicks() { return elapsedTicks; }

    public void addElapsedTicks(int ticks) { this.elapsedTicks += ticks; }

    public int indicatorIndex() { return indicatorIndex; }

    public void setIndicatorIndex(int indicatorIndex) { this.indicatorIndex = indicatorIndex; }

    public int direction() { return direction; }

    public void setDirection(int direction) { this.direction = direction; }

    public boolean completed() { return completed; }

    public void markCompleted() { this.completed = true; }

    public Player player() { return context.raider(); }
}
