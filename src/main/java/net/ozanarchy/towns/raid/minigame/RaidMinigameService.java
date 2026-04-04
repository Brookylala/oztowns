package net.ozanarchy.towns.raid.minigame;

import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.raid.RaidExecutionContext;
import net.ozanarchy.towns.raid.RaidConfigManager;
import net.ozanarchy.towns.raid.RaidMinigameSettings;
import net.ozanarchy.towns.raid.RaidStateBehaviorDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class RaidMinigameService implements Listener {
    private final Map<UUID, RaidMinigameSession> sessions = new ConcurrentHashMap<>();
    private final ReactionTimingMinigame minigame;

    public RaidMinigameService(TownsPlugin plugin, RaidConfigManager raidConfig) {
        this.minigame = new ReactionTimingMinigame(plugin, this);
    }

    public boolean hasSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public void startSession(Player player,
                             RaidExecutionContext context,
                             RaidStateBehaviorDefinition behavior,
                             RaidMinigameSettings settings,
                             Consumer<MinigameResult> completion) {
        UUID playerId = player.getUniqueId();
        if (sessions.containsKey(playerId)) {
            completion.accept(MinigameResult.CANCELLED);
            return;
        }

        RaidMinigameSession session = new RaidMinigameSession(context, behavior, settings, completion);
        sessions.put(playerId, session);
        minigame.start(session);
    }

    public void completeSession(RaidMinigameSession session, MinigameResult result) {
        sessions.remove(session.player().getUniqueId(), session);
        session.completion().accept(result);
    }

    public void cancelSession(UUID playerId, MinigameResult reason) {
        RaidMinigameSession session = sessions.get(playerId);
        if (session == null) {
            return;
        }
        minigame.cancel(session, reason);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        RaidMinigameSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (event.getInventory().getHolder() != session) {
            return;
        }
        event.setCancelled(true);
        minigame.handleClick(session, player, event.getRawSlot());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        RaidMinigameSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (event.getInventory().getHolder() != session) {
            return;
        }
        cancelSession(player.getUniqueId(), MinigameResult.CANCELLED);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelSession(event.getPlayer().getUniqueId(), MinigameResult.CANCELLED);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        cancelSession(event.getEntity().getUniqueId(), MinigameResult.CANCELLED);
    }
}
