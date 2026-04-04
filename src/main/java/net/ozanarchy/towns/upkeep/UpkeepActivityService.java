package net.ozanarchy.towns.upkeep;

import net.ozanarchy.towns.town.model.TownMember;
import net.ozanarchy.towns.util.db.DatabaseHandler;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class UpkeepActivityService {
    private final UpkeepConfigManager config;
    private final DatabaseHandler databaseHandler;

    public UpkeepActivityService(UpkeepConfigManager config, DatabaseHandler databaseHandler) {
        this.config = config;
        this.databaseHandler = databaseHandler;
    }

    public ActivityResult evaluateActivity(int townId) {
        long now = System.currentTimeMillis();
        if (!config.activityEnabled()) {
            return new ActivityResult(true, now);
        }

        long windowMs = config.activityWindowMinutes() * 60_000L;
        long cutoff = now - windowMs;
        long newestSeen = 0L;

        List<TownMember> members = databaseHandler.getTownMembers(townId);
        for (TownMember member : members) {
            UUID uuid = member.getUuid();
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                newestSeen = Math.max(newestSeen, now);
                if (config.requireAnyMemberOnlineWithinWindow()) {
                    return new ActivityResult(true, now);
                }
                continue;
            }

            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            long lastSeen = offlinePlayer.getLastPlayed();
            newestSeen = Math.max(newestSeen, lastSeen);
            if (lastSeen >= cutoff) {
                return new ActivityResult(true, lastSeen);
            }
        }

        return new ActivityResult(false, newestSeen);
    }

    public record ActivityResult(boolean active, long newestSeenTimestamp) {
    }
}

