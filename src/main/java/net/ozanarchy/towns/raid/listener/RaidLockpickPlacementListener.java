package net.ozanarchy.towns.raid.listener;

import net.ozanarchy.towns.raid.RaidService;
import net.ozanarchy.towns.util.Utils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

public class RaidLockpickPlacementListener implements Listener {
    private final RaidService raidService;

    public RaidLockpickPlacementListener(RaidService raidService) {
        this.raidService = raidService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!raidService.isRaidLockpick(item)) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(Utils.getColor(
                Utils.prefix() + raidService.raidConfig().lockpickCannotPlaceMessage()
        ));
    }
}
