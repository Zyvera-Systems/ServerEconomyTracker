package dev.zyverasystems.utils.database;

import dev.zyverasystems.utils.PlayerBalanceData;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;

public class PlayerBalanceFunc {

    private final DatabaseManager databaseManager;

    public PlayerBalanceFunc(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    // Find PlayerBalance through UUID
    public Optional<PlayerBalanceData> findByUuid(UUID uuid) {
        String sql = """
                SELECT uuid, name, balance, known_player, last_updated
                FROM player_balances
                WHERE uuid = ?
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                PlayerBalanceData data = new PlayerBalanceData(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("name"),
                        rs.getBigDecimal("balance"),
                        rs.getBoolean("known_player"),
                        rs.getLong("last_updated")
                );

                return Optional.of(data);
            }
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Laden von player_balances für UUID " + uuid, e);
        }
    }

    // Update when Player exists or Insert when not
    public void upsert(UUID uuid, String name, BigDecimal balance, boolean knownPlayer, long lastUpdated) {

        // Update
        String updateSql = """
                UPDATE player_balances
                SET name = ?, balance = ?, known_player = ?, last_updated = ?
                WHERE uuid = ?
                """;

        // Insert
        String insertSql = """
                INSERT INTO player_balances (uuid, name, balance, known_player, last_updated)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement updatePs = connection.prepareStatement(updateSql)) {

            updatePs.setString(1, name);
            updatePs.setBigDecimal(2, balance);
            updatePs.setBoolean(3, knownPlayer);
            updatePs.setLong(4, lastUpdated);
            updatePs.setString(5, uuid.toString());

            int rows = updatePs.executeUpdate();

            if (rows == 0) {
                try (PreparedStatement insertPs = connection.prepareStatement(insertSql)) {
                    insertPs.setString(1, uuid.toString());
                    insertPs.setString(2, name);
                    insertPs.setBigDecimal(3, balance);
                    insertPs.setBoolean(4, knownPlayer);
                    insertPs.setLong(5, lastUpdated);
                    insertPs.executeUpdate();
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Error occurred from saving player_balance from UUID: " + uuid, e);
        }
    }
}