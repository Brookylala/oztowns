package net.ozanarchy.towns.raid.listener;

import net.ozanarchy.towns.raid.RaidConfigManager;
import net.ozanarchy.towns.raid.RaidService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class RaidFailureListener implements Listener {
    private final RaidConfigManager raidConfig;
    private final RaidService raidService;

    public RaidFailureListener(RaidConfigManager raidConfig, RaidService raidService) {
        this.raidConfig = raidConfig;
        this.raidService = raidService;
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!raidConfig.failOnDamage()) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        raidService.failRaid(player, true, "messages.raidfaileddamage");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!raidConfig.failOnDeath()) {
            return;
        }
        raidService.failRaid(event.getEntity(), true, "messages.raidfailed");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!raidConfig.failOnDisconnect()) {
            return;
        }
        raidService.failRaid(event.getPlayer(), false);
    }
}

