package net.ozanarchy.towns.town;

import net.ozanarchy.towns.town.model.TownMember;
import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.util.Utils;
import net.ozanarchy.towns.util.db.DatabaseHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class TownNotifier {
    private final TownsPlugin plugin;
    private final DatabaseHandler db;

    public TownNotifier(TownsPlugin plugin, DatabaseHandler db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void notifyTown(int townId, String message) {
        for (TownMember member : db.getTownMembers(townId)) {
            UUID uuid = member.getUuid();
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(Utils.getColor(Utils.prefix() + message))
                );
            }
        }
    }
}



