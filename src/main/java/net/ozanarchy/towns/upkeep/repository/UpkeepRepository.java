package net.ozanarchy.towns.upkeep.repository;

import net.ozanarchy.towns.TownsPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UpkeepRepository {
    private final TownsPlugin plugin;

    public UpkeepRepository(TownsPlugin plugin) {
        this.plugin = plugin;
    }

    public void increaseUpkeep(int townId, double amount) {
        String sql = "UPDATE town_bank SET upkeep_cost = upkeep_cost + ? WHERE town_id=?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setInt(2, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void decreaseUpkeep(int townId, double amount) {
        String sql = "UPDATE town_bank SET upkeep_cost = upkeep_cost - ? WHERE town_id=?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setInt(2, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateLastUpkeep(int townId) {
        String sql = "UPDATE town_bank SET last_upkeep = CURRENT_TIMESTAMP WHERE town_id = ?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public double getTownUpkeep(int townId) {
        String sql = "SELECT upkeep_cost FROM town_bank WHERE town_id=? LIMIT 1";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("upkeep_cost");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public ResultSet getTownsNeedingUpkeep() throws SQLException {
        String sql = """
        SELECT tb.town_id, tb.balance, tb.upkeep_cost, tb.last_upkeep, tb.upkeep_interval
        FROM town_bank tb
    """;
        return plugin.getConnection().prepareStatement(sql).executeQuery();
    }
}

