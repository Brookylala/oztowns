package net.ozanarchy.towns.raid.listener;

import net.ozanarchy.towns.raid.RaidService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class RaidEntryListener implements Listener {
    private final RaidService raidService;

    public RaidEntryListener(RaidService raidService) {
        this.raidService = raidService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.LODESTONE) {
            return;
        }

        Player player = event.getPlayer();
        raidService.handleRaidEntryAttempt(player, block, player.getInventory().getItemInMainHand());
    }
}

