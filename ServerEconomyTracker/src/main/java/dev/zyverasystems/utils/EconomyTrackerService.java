package dev.zyverasystems.utils;

import dev.zyverasystems.ServerEconomyTracker;
import dev.zyverasystems.utils.database.DatabaseManager;
import dev.zyverasystems.utils.database.EconomyTotalsFunc;
import dev.zyverasystems.utils.database.PlayerBalanceFunc;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

public class EconomyTrackerService {

    private final ServerEconomyTracker plugin;
    private final Economy economy;
    private final PlayerBalanceFunc playerBalanceFunc;
    private final EconomyTotalsFunc economyTotalsFunc;

    private EconomyTotals totals;

    public EconomyTrackerService(ServerEconomyTracker plugin, DatabaseManager databaseManager, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.playerBalanceFunc = new PlayerBalanceFunc(databaseManager);
        this.economyTotalsFunc = new EconomyTotalsFunc(databaseManager);
    }

    // Load or Create Totals
    public void loadOrCreateTotals() {
        this.totals = economyTotalsFunc.load().orElseGet(() -> {
            EconomyTotals newTotals = new EconomyTotals(
                    false,
                    bd(0),
                    bd(0),
                    bd(0),
                    bd(0),
                    bd(0),
                    System.currentTimeMillis()
            );
            economyTotalsFunc.save(newTotals);
            return newTotals;
        });
    }

    // Set a Beseline,
    // for running server with money
    // in the environment
    public void performInitialBaselineIfNeeded() {
        if (totals == null) {
            throw new IllegalStateException("Totals are not loaded yet.");
        }

        if (totals.isBaselineImportDone()) {
            plugin.getLogger().info("Baseline already imported.");
            return;
        }

        plugin.getLogger().info("Starte initialen Baseline-Import...");

        BigDecimal totalBalance = bd(0);
        long now = System.currentTimeMillis();

        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            if (offlinePlayer.getUniqueId() == null || offlinePlayer.getName() == null) {
                continue;
            }

            BigDecimal balance = bd(economy.getBalance(offlinePlayer));
            totalBalance = totalBalance.add(balance);

            playerBalanceFunc.upsert(
                    offlinePlayer.getUniqueId(),
                    offlinePlayer.getName(),
                    balance,
                    true,
                    now
            );
        }

        totals.setCurrentTotalBalance(scale(totalBalance));
        totals.setBaselineImportDone(true);
        totals.setUpdatedAt(now);

        economyTotalsFunc.save(totals);

        plugin.getLogger().info("Baseline-Import abgeschlossen. Gesamtvermögen: " + totalBalance);
    }

    // Player First Join
    public void handleFirstJoin(Player player) {
        UUID uuid = player.getUniqueId();
        Optional<PlayerBalanceData> existing = playerBalanceFunc.findByUuid(uuid);

        if (existing.isPresent()) {
            return;
        }

        int delaySeconds = plugin.getConfig().getInt("tracker.first-join-delay-seconds", 3);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            BigDecimal balance = bd(economy.getBalance(player));
            long now = System.currentTimeMillis();

            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                totals.setTotalSources(scale(totals.getTotalSources().add(balance)));
                totals.setTotalNetChange(scale(totals.getTotalSources().subtract(totals.getTotalSinks())));
                totals.setCurrentTotalBalance(scale(totals.getCurrentTotalBalance().add(balance)));
                totals.setUpdatedAt(now);

                economyTotalsFunc.save(totals);

                plugin.getLogger().info("Neuer Spieler mit Startgeld erkannt: " + player.getName() + " +" + balance);
            }

            playerBalanceFunc.upsert(
                    uuid,
                    player.getName(),
                    balance,
                    true,
                    now
            );
        }, delaySeconds * 20L);
    }

    // Scaning Online Players
    public void scanOnlinePlayers() {
        BigDecimal positiveSum = bd(0);
        BigDecimal negativeSum = bd(0);
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            String name = player.getName();
            BigDecimal newBalance = bd(economy.getBalance(player));

            Optional<PlayerBalanceData> optional = playerBalanceFunc.findByUuid(uuid);

            if (optional.isEmpty()) {
                playerBalanceFunc.upsert(uuid, name, newBalance, true, now);
                continue;
            }

            PlayerBalanceData oldData = optional.get();
            BigDecimal oldBalance = oldData.getBalance();
            BigDecimal delta = scale(newBalance.subtract(oldBalance));

            if (delta.compareTo(BigDecimal.ZERO) > 0) {
                positiveSum = positiveSum.add(delta);
            } else if (delta.compareTo(BigDecimal.ZERO) < 0) {
                negativeSum = negativeSum.add(delta.abs());
            }

            playerBalanceFunc.upsert(uuid, name, newBalance, true, now);
        }

        if (positiveSum.compareTo(BigDecimal.ZERO) == 0 && negativeSum.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        BigDecimal sourcePart = positiveSum.subtract(negativeSum).max(BigDecimal.ZERO);
        BigDecimal sinkPart = negativeSum.subtract(positiveSum).max(BigDecimal.ZERO);
        BigDecimal transferPart = positiveSum.min(negativeSum);

        totals.setTotalSources(scale(totals.getTotalSources().add(sourcePart)));
        totals.setTotalSinks(scale(totals.getTotalSinks().add(sinkPart)));
        totals.setTotalTransferVolume(scale(totals.getTotalTransferVolume().add(transferPart)));
        totals.setTotalNetChange(scale(totals.getTotalSources().subtract(totals.getTotalSinks())));

        BigDecimal currentTotalBalance = calculateCurrentTotalBalance();
        totals.setCurrentTotalBalance(currentTotalBalance);
        totals.setUpdatedAt(now);

        economyTotalsFunc.save(totals);

        plugin.getLogger().info(
                "Scan abgeschlossen | +" + positiveSum +
                        " | -" + negativeSum +
                        " | source=" + sourcePart +
                        " | sink=" + sinkPart +
                        " | transfer=" + transferPart +
                        " | total=" + currentTotalBalance
        );
    }

    public EconomyTotals getTotals() {
        return totals;
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal bd(int value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateCurrentTotalBalance() {
        BigDecimal total = bd(0);

        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            UUID uuid = offlinePlayer.getUniqueId();
            Optional<PlayerBalanceData> optional = playerBalanceFunc.findByUuid(uuid);

            if (optional.isPresent()) {
                total = total.add(optional.get().getBalance());
            }
        }

        return scale(total);
    }
}