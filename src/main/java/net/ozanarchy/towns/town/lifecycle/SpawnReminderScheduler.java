package net.ozanarchy.towns.town.lifecycle;

import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.town.TownNotifier;
import net.ozanarchy.towns.town.listener.TownEvents;
import net.ozanarchy.towns.town.repository.TownLifecycleRepository;
import org.bukkit.Bukkit;

import java.sql.ResultSet;
import java.sql.SQLException;

import static net.ozanarchy.towns.TownsPlugin.townConfig;

public class SpawnReminderScheduler {
    private final TownsPlugin plugin;
    private final TownLifecycleRepository townLifecycleRepository;
    private final TownEvents townEvents;
    private final TownNotifier townNotifier;

    public SpawnReminderScheduler(TownsPlugin plugin, TownEvents townEvents, TownNotifier townNotifier) {
        this.plugin = plugin;
        this.townLifecycleRepository = new TownLifecycleRepository(plugin);
        this.townEvents = townEvents;
        this.townNotifier = townNotifier;
    }

    public void schedule() {
        boolean remindersEnabled = townConfig.getBoolean("lifecycle.spawn-reminder.enabled", true);
        if (!remindersEnabled) {
            return;
        }
        long maxAgeMinutes = townConfig.getLong("lifecycle.spawn-reminder.max-age-minutes", 60);
        long intervalMinutes = Math.max(1L, townConfig.getLong("lifecycle.spawn-reminder.reminder-interval-minutes", 5));
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try (ResultSet rs = townLifecycleRepository.getTownsWithoutSpawn()) {
                while (rs.next()) {
                    int townId = rs.getInt("id");
                    String townName = rs.getString("name");
                    long ageSeconds = townLifecycleRepository.getTownAgeInSeconds(townId);
                    long ageMinutes = ageSeconds / 60;

                    if (ageMinutes >= maxAgeMinutes) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            townEvents.abandonTown(townId);
                            plugin.getLogger().info("Town " + townName + " deleted for not setting spawn within " + maxAgeMinutes + " minutes.");
                        });
                    } else if (ageMinutes > 0 && ageMinutes % intervalMinutes == 0 && ageSeconds % 60 < 20) {
                        String msg = plugin.getMessageService()
                                .format("town.spawn.reminder", java.util.Map.of("minutes", String.valueOf(maxAgeMinutes - ageMinutes)));
                        townNotifier.notifyTown(townId, msg);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, 20 * 30L, 20 * 60L);
    }
}

