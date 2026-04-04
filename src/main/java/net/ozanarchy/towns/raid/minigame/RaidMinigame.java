package net.ozanarchy.towns.raid.minigame;

import org.bukkit.entity.Player;

public interface RaidMinigame {
    void start(RaidMinigameSession session);

    void handleClick(RaidMinigameSession session, Player player, int slot);

    void cancel(RaidMinigameSession session, MinigameResult result);
}
