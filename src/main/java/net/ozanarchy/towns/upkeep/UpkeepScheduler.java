package net.ozanarchy.towns.upkeep;

import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.town.TownNotifier;
import net.ozanarchy.towns.town.listener.TownEvents;
import net.ozanarchy.towns.upkeep.repository.UpkeepRepository;
import net.ozanarchy.towns.util.db.DatabaseHandler;
import org.bukkit.Bukkit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import static net.ozanarchy.towns.TownsPlugin.config;
import static net.ozanarchy.towns.TownsPlugin.messagesConfig;

public class UpkeepScheduler {
    private final TownsPlugin plugin;
    private final DatabaseHandler databaseHandler;
    private final UpkeepRepository upkeepRepository;
    private final TownEvents townEvents;
    private final TownNotifier townNotifier;

    public UpkeepScheduler(TownsPlugin plugin, DatabaseHandler db, TownEvents townEvents, TownNotifier townNotifier) {
        this.plugin = plugin;
        this.databaseHandler = db;
        this.upkeepRepository = new UpkeepRepository(plugin);
        this.townEvents = townEvents;
        this.townNotifier = townNotifier;
    }

    public void schedule() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long unpaidDeleteAfterMinutes = Math.max(0L, config.getLong("upkeep.unpaid-delete-after-minutes", 1440L));
            try (ResultSet rs = upkeepRepository.getTownsNeedingUpkeep()) {
                while (rs.next()) {
                    int townId = rs.getInt("town_id");
                    double cost = rs.getDouble("upkeep_cost");
                    long elapsedSeconds = 0L;
                    Timestamp lastUpkeep = rs.getTimestamp("last_upkeep");
                    if (lastUpkeep != null) {
                        elapsedSeconds = Math.max(0L, (System.currentTimeMillis() - lastUpkeep.getTime()) / 1000L);
                    }
                    long upkeepIntervalSeconds = Math.max(1L, rs.getLong("upkeep_interval"));
                    if (elapsedSeconds < upkeepIntervalSeconds) {
                        continue;
                    }
                    boolean paid = databaseHandler.withdrawTownMoney(townId, cost);
                    if (paid) {
                        upkeepRepository.updateLastUpkeep(townId);
                        townNotifier.notifyTown(townId, messagesConfig.getString("messages.upkeepsuccess").replace("{cost}", String.valueOf(cost)));
                    } else {
                        long overdueSeconds = Math.max(0L, elapsedSeconds - upkeepIntervalSeconds);

                        if (unpaidDeleteAfterMinutes == 0L || overdueSeconds >= unpaidDeleteAfterMinutes * 60L) {
                            String townName = databaseHandler.getTownName(townId);
                            townEvents.abandonTown(townId);
                            plugin.getLogger().info("Town " + (townName != null ? townName : ("#" + townId))
                                    + " deleted for unpaid upkeep after "
                                    + (overdueSeconds / 60L)
                                    + " overdue minutes (limit: "
                                    + unpaidDeleteAfterMinutes
                                    + ").");
                        } else {
                            townNotifier.notifyTown(townId, messagesConfig.getString("messages.upkeepoverdue"));
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, 0L, 20 * 60 * 15);
    }
}

