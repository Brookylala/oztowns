package net.ozanarchy.towns.town.repository;

import net.ozanarchy.towns.TownsPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TownLifecycleRepository {
    private final TownsPlugin plugin;

    public TownLifecycleRepository(TownsPlugin plugin) {
        this.plugin = plugin;
    }

    public ResultSet getTownsWithoutSpawn() throws SQLException {
        String sql = "SELECT id, name FROM towns WHERE world IS NULL OR world = '' OR world = 'null'";
        return plugin.getConnection().prepareStatement(sql).executeQuery();
    }

    public long getTownAgeInSeconds(int townId) {
        String sql = "SELECT created_at FROM towns WHERE id = ?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
                    if (createdAt == null) {
                        return 0L;
                    }
                    return Math.max(0L, (System.currentTimeMillis() - createdAt.getTime()) / 1000L);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
}

