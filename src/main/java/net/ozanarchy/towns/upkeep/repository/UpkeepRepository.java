package net.ozanarchy.towns.upkeep.repository;

import net.ozanarchy.towns.TownsPlugin;
import net.ozanarchy.towns.upkeep.UpkeepState;
import net.ozanarchy.towns.upkeep.UpkeepTownSnapshot;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

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
        String sql = "UPDATE town_bank SET upkeep_cost = CASE WHEN upkeep_cost - ? < 0 THEN 0 ELSE upkeep_cost - ? END WHERE town_id=?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setDouble(2, amount);
            stmt.setInt(3, townId);
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

    public void markPaymentSuccess(int townId) {
        try (PreparedStatement bankStmt = plugin.getConnection()
                .prepareStatement("UPDATE town_bank SET last_upkeep = CURRENT_TIMESTAMP WHERE town_id = ?");
             PreparedStatement townStmt = plugin.getConnection()
                     .prepareStatement("UPDATE towns SET upkeep_unpaid_cycles = 0, upkeep_last_payment = CURRENT_TIMESTAMP WHERE id = ?")) {
            bankStmt.setInt(1, townId);
            bankStmt.executeUpdate();
            townStmt.setInt(1, townId);
            townStmt.executeUpdate();
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
                SELECT town_id, upkeep_cost, last_upkeep, upkeep_interval
                FROM town_bank
                """;
        PreparedStatement stmt = plugin.getConnection().prepareStatement(sql);
        return stmt.executeQuery();
    }

    public List<UpkeepTownSnapshot> loadSnapshots() {
        String sql = """
                SELECT tb.town_id,
                       tb.balance,
                       tb.upkeep_cost,
                       tb.last_upkeep,
                       COALESCE(t.upkeep_unpaid_cycles, 0) AS upkeep_unpaid_cycles,
                       COALESCE(t.upkeep_state, 'ACTIVE') AS upkeep_state,
                       COALESCE(t.upkeep_decay_cycles, 0) AS upkeep_decay_cycles,
                       t.upkeep_last_activity
                FROM town_bank tb
                JOIN towns t ON t.id = tb.town_id
                """;
        List<UpkeepTownSnapshot> snapshots = new ArrayList<>();
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                snapshots.add(new UpkeepTownSnapshot(
                        rs.getInt("town_id"),
                        rs.getDouble("balance"),
                        rs.getDouble("upkeep_cost"),
                        rs.getTimestamp("last_upkeep"),
                        rs.getInt("upkeep_unpaid_cycles"),
                        UpkeepState.fromString(rs.getString("upkeep_state"), UpkeepState.ACTIVE),
                        rs.getInt("upkeep_decay_cycles"),
                        rs.getTimestamp("upkeep_last_activity")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return snapshots;
    }

    public int incrementUnpaidCycles(int townId) {
        String sql = """
                UPDATE towns
                SET upkeep_unpaid_cycles = COALESCE(upkeep_unpaid_cycles, 0) + 1
                WHERE id = ?
                """;
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return getUnpaidCycles(townId);
    }

    public int getUnpaidCycles(int townId) {
        String sql = "SELECT COALESCE(upkeep_unpaid_cycles, 0) AS upkeep_unpaid_cycles FROM towns WHERE id = ? LIMIT 1";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("upkeep_unpaid_cycles");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
    public UpkeepState getUpkeepState(int townId) {
        String sql = "SELECT COALESCE(upkeep_state, 'ACTIVE') AS upkeep_state FROM towns WHERE id = ? LIMIT 1";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return UpkeepState.fromString(rs.getString("upkeep_state"), UpkeepState.ACTIVE);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return UpkeepState.ACTIVE;
    }
    public void setUpkeepState(int townId, UpkeepState state) {
        String sql = "UPDATE towns SET upkeep_state = ? WHERE id = ?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setString(1, state.name());
            stmt.setInt(2, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int incrementDecayCycles(int townId) {
        String sql = "UPDATE towns SET upkeep_decay_cycles = COALESCE(upkeep_decay_cycles, 0) + 1 WHERE id = ?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return getDecayCycles(townId);
    }

    public int getDecayCycles(int townId) {
        String sql = "SELECT COALESCE(upkeep_decay_cycles, 0) AS upkeep_decay_cycles FROM towns WHERE id = ? LIMIT 1";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("upkeep_decay_cycles");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void setDecayCycles(int townId, int cycles) {
        String sql = "UPDATE towns SET upkeep_decay_cycles = ? WHERE id = ?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, Math.max(0, cycles));
            stmt.setInt(2, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setLastActivity(int townId, Timestamp timestamp) {
        String sql = "UPDATE towns SET upkeep_last_activity = ? WHERE id = ?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setTimestamp(1, timestamp);
            stmt.setInt(2, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}


