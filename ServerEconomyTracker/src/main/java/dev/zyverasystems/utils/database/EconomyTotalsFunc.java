package dev.zyverasystems.utils.database;

import dev.zyverasystems.utils.EconomyTotals;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

public class EconomyTotalsFunc {

    private final DatabaseManager databaseManager;

    public EconomyTotalsFunc(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    // Load from Totals
    public Optional<EconomyTotals> load() {
        String sql = """
                SELECT baseline_import_done, current_total_balance, total_sources, total_sinks,
                       total_transfer_volume, total_net_change, updated_at
                FROM economy_totals
                WHERE id = 1
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (!rs.next()) {
                return Optional.empty();
            }

            EconomyTotals totals = new EconomyTotals(
                    rs.getBoolean("baseline_import_done"),
                    rs.getBigDecimal("current_total_balance"),
                    rs.getBigDecimal("total_sources"),
                    rs.getBigDecimal("total_sinks"),
                    rs.getBigDecimal("total_transfer_volume"),
                    rs.getBigDecimal("total_net_change"),
                    rs.getLong("updated_at")
            );

            return Optional.of(totals);

        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Laden von economy_totals", e);
        }
    }

    // Update or Insert
    public void save(EconomyTotals totals) {

        // Update
        String updateSql = """
                UPDATE economy_totals
                SET baseline_import_done = ?, current_total_balance = ?, total_sources = ?,
                    total_sinks = ?, total_transfer_volume = ?, total_net_change = ?, updated_at = ?
                WHERE id = 1
                """;

        // Insert
        String insertSql = """
                INSERT INTO economy_totals (
                    id, baseline_import_done, current_total_balance, total_sources,
                    total_sinks, total_transfer_volume, total_net_change, updated_at
                ) VALUES (1, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement updatePs = connection.prepareStatement(updateSql)) {

            updatePs.setBoolean(1, totals.isBaselineImportDone());
            updatePs.setBigDecimal(2, totals.getCurrentTotalBalance());
            updatePs.setBigDecimal(3, totals.getTotalSources());
            updatePs.setBigDecimal(4, totals.getTotalSinks());
            updatePs.setBigDecimal(5, totals.getTotalTransferVolume());
            updatePs.setBigDecimal(6, totals.getTotalNetChange());
            updatePs.setLong(7, totals.getUpdatedAt());

            int rows = updatePs.executeUpdate();

            if (rows == 0) {
                try (PreparedStatement insertPs = connection.prepareStatement(insertSql)) {
                    insertPs.setBoolean(1, totals.isBaselineImportDone());
                    insertPs.setBigDecimal(2, totals.getCurrentTotalBalance());
                    insertPs.setBigDecimal(3, totals.getTotalSources());
                    insertPs.setBigDecimal(4, totals.getTotalSinks());
                    insertPs.setBigDecimal(5, totals.getTotalTransferVolume());
                    insertPs.setBigDecimal(6, totals.getTotalNetChange());
                    insertPs.setLong(7, totals.getUpdatedAt());
                    insertPs.executeUpdate();
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Error occurred from saveing economy_totals", e);
        }
    }
}