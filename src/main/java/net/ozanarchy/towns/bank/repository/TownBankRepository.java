package net.ozanarchy.towns.bank.repository;

import net.ozanarchy.towns.TownsPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TownBankRepository {
    private final TownsPlugin plugin;

    public TownBankRepository(TownsPlugin plugin) {
        this.plugin = plugin;
    }

    public void createTownBank(int townId) {
        String sql = "INSERT INTO town_bank (town_id) VALUES (?)";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteTownBank(int townId) {
        String sql = "DELETE FROM town_bank WHERE town_id=?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void transferBankBalance(int fromTownId, int toTownId) {
        double balance = getTownBalance(fromTownId);
        if (balance > 0) {
            depositTownMoney(toTownId, balance);
            withdrawTownMoney(fromTownId, balance);
        }
    }

    public boolean depositTownMoney(int townId, double amount) {
        String sql = "UPDATE town_bank SET balance = balance + ? WHERE town_id=?";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setInt(2, townId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean withdrawTownMoney(int townId, double amount) {
        String sql = """
                UPDATE town_bank
                SET balance = balance - ?
                WHERE town_id=? AND balance >=?
                """;
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setInt(2, townId);
            stmt.setDouble(3, amount);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public double getTownBalance(int townId) {
        String sql = "SELECT balance FROM town_bank WHERE town_id=? LIMIT 1";
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, townId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("balance");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
}

