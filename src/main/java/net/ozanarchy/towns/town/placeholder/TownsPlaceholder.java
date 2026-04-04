package net.ozanarchy.towns.town.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.util.db.DatabaseHandler;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TownsPlaceholder extends PlaceholderExpansion {

    private final TownsPlugin plugin;
    private final DatabaseHandler db;

    public TownsPlaceholder(TownsPlugin plugin, DatabaseHandler db) {
        this.plugin = plugin;
        this.db = db;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "oztowns";
    }

    @Override
    public @NotNull String getAuthor() {
        return "OzAnarchy";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (offlinePlayer == null) return null;

        // Only handle %oztowns_town%
        if (params.equalsIgnoreCase("town")) {
            java.util.UUID uuid = offlinePlayer.getUniqueId();
            Integer townId = db.getPlayerTownId(uuid);
        
            if (townId == null) return "";
        
            String townName = db.getTownName(townId);
            return (townName != null) ? townName : "";
        }

        return "";
    }
}




