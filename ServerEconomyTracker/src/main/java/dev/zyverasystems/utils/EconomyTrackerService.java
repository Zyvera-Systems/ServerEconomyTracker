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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class EconomyTrackerService {

    private record PlayerSnapshot(String name, BigDecimal balance) {}

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

    public void loadOrCreateTotals() {
        this.totals = economyTotalsFunc.load().orElseGet(() -> {
            EconomyTotals newTotals = new EconomyTotals(
                    false, bd(0), bd(0), bd(0), bd(0), bd(0),
                    System.currentTimeMillis()
            );
            economyTotalsFunc.save(newTotals);
            return newTotals;
        });
    }

    // Scans all known offline players once on first startup to establish a baseline total balance.
    public void performInitialBaselineIfNeeded() {
        if (totals == null) throw new IllegalStateException("Totals are not loaded yet.");
        if (totals.isBaselineImportDone()) {
            plugin.getLogger().info("Baseline already imported.");
            return;
        }

        plugin.getLogger().info("Starting initial baseline import...");

        BigDecimal totalBalance = bd(0);
        long now = System.currentTimeMillis();

        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            if (offlinePlayer.getUniqueId() == null || offlinePlayer.getName() == null) continue;

            BigDecimal balance = getTotalWealth(offlinePlayer);
            totalBalance = totalBalance.add(balance);
            playerBalanceFunc.upsert(offlinePlayer.getUniqueId(), offlinePlayer.getName(), balance, true, now);
        }

        synchronized (this) {
            totals.setCurrentTotalBalance(scale(totalBalance));
            totals.setBaselineImportDone(true);
            totals.setUpdatedAt(now);
            economyTotalsFunc.save(totals);
        }

        plugin.getLogger().info("Baseline import complete. Total balance: " + totalBalance);
    }

    public void handleFirstJoin(Player player) {
        UUID uuid = player.getUniqueId();
        Optional<PlayerBalanceData> existing = playerBalanceFunc.findByUuid(uuid);

        if (existing.isPresent()) return;

        int delaySeconds = plugin.getConfig().getInt("tracker.first-join-delay-seconds", 3);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            BigDecimal balance = getTotalWealth(player);
            long now = System.currentTimeMillis();

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                synchronized (EconomyTrackerService.this) {
                    if (balance.compareTo(BigDecimal.ZERO) > 0) {
                        totals.setTotalSources(scale(totals.getTotalSources().add(balance)));
                        totals.setTotalNetChange(scale(totals.getTotalSources().subtract(totals.getTotalSinks())));
                        totals.setCurrentTotalBalance(scale(totals.getCurrentTotalBalance().add(balance)));
                        totals.setUpdatedAt(now);
                        economyTotalsFunc.save(totals);
                        plugin.getLogger().info("New player with starting balance: " + player.getName() + " +" + balance);
                    }
                    playerBalanceFunc.upsert(uuid, player.getName(), balance, true, now);
                }
            });
        }, delaySeconds * 20L);
    }

    // Must be called from the main thread — reads Vault balances safely, then processes DB async.
    public void scanAndSchedule() {
        Map<UUID, PlayerSnapshot> snapshots = new LinkedHashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            snapshots.put(player.getUniqueId(), new PlayerSnapshot(player.getName(), getTotalWealth(player)));
        }
        if (snapshots.isEmpty()) return;

        // When transaction hooks are active they handle source/sink/transfer booking in real-time.
        // The scan then only updates stored balances to keep player_balances table current.
        boolean useHeuristic = !plugin.getHookManager().hasTransactionHooks();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                processSnapshots(snapshots, useHeuristic);
            } catch (Exception e) {
                plugin.getLogger().severe("Error during economy scan: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void processSnapshots(Map<UUID, PlayerSnapshot> snapshots, boolean useHeuristic) {
        BigDecimal positiveSum = bd(0);
        BigDecimal negativeSum = bd(0);
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, PlayerSnapshot> entry : snapshots.entrySet()) {
            UUID uuid = entry.getKey();
            PlayerSnapshot snap = entry.getValue();

            Optional<PlayerBalanceData> optional = playerBalanceFunc.findByUuid(uuid);

            if (optional.isEmpty()) {
                playerBalanceFunc.upsert(uuid, snap.name(), snap.balance(), true, now);
                continue;
            }

            BigDecimal delta = scale(snap.balance().subtract(optional.get().getBalance()));

            if (useHeuristic) {
                if (delta.compareTo(BigDecimal.ZERO) > 0) positiveSum = positiveSum.add(delta);
                else if (delta.compareTo(BigDecimal.ZERO) < 0) negativeSum = negativeSum.add(delta.abs());
            }

            playerBalanceFunc.upsert(uuid, snap.name(), snap.balance(), true, now);
        }

        if (!useHeuristic) {
            return;
        }

        if (positiveSum.compareTo(BigDecimal.ZERO) == 0 && negativeSum.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        BigDecimal sourcePart = positiveSum.subtract(negativeSum).max(BigDecimal.ZERO);
        BigDecimal sinkPart = negativeSum.subtract(positiveSum).max(BigDecimal.ZERO);
        BigDecimal transferPart = positiveSum.min(negativeSum);

        synchronized (this) {
            totals.setTotalSources(scale(totals.getTotalSources().add(sourcePart)));
            totals.setTotalSinks(scale(totals.getTotalSinks().add(sinkPart)));
            totals.setTotalTransferVolume(scale(totals.getTotalTransferVolume().add(transferPart)));
            totals.setTotalNetChange(scale(totals.getTotalSources().subtract(totals.getTotalSinks())));
            totals.setCurrentTotalBalance(scale(totals.getCurrentTotalBalance().add(positiveSum).subtract(negativeSum)));
            totals.setUpdatedAt(now);
            economyTotalsFunc.save(totals);
        }

        plugin.getLogger().info(
                "Scan | +" + positiveSum + " | -" + negativeSum +
                " | source=" + sourcePart + " | sink=" + sinkPart +
                " | transfer=" + transferPart + " | total=" + totals.getCurrentTotalBalance()
        );
    }

    public synchronized void recordSource(BigDecimal amount) {
        if (totals == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        totals.setTotalSources(scale(totals.getTotalSources().add(amount)));
        totals.setTotalNetChange(scale(totals.getTotalSources().subtract(totals.getTotalSinks())));
        totals.setCurrentTotalBalance(scale(totals.getCurrentTotalBalance().add(amount)));
        totals.setUpdatedAt(System.currentTimeMillis());
        economyTotalsFunc.save(totals);
    }

    public synchronized void recordSink(BigDecimal amount) {
        if (totals == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        totals.setTotalSinks(scale(totals.getTotalSinks().add(amount)));
        totals.setTotalNetChange(scale(totals.getTotalSources().subtract(totals.getTotalSinks())));
        totals.setCurrentTotalBalance(scale(totals.getCurrentTotalBalance().subtract(amount)));
        totals.setUpdatedAt(System.currentTimeMillis());
        economyTotalsFunc.save(totals);
    }

    public synchronized void recordTransfer(BigDecimal amount) {
        if (totals == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        totals.setTotalTransferVolume(scale(totals.getTotalTransferVolume().add(amount)));
        totals.setUpdatedAt(System.currentTimeMillis());
        economyTotalsFunc.save(totals);
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

    private BigDecimal getTotalWealth(OfflinePlayer player) {
        BigDecimal walletBalance = bd(economy.getBalance(player));
        BigDecimal extraWealth = plugin.getHookManager().getTotalExtraWealth(player.getUniqueId());
        return scale(walletBalance.add(extraWealth));
    }
}