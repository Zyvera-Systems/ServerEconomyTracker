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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// How the numbers fit together:
// - current total balance follows every real balance change (noteBalanceChange)
// - sources / sinks / transfers only classify those changes (recordSource / recordSink / recordTransfer)
// - baseline = current total balance - (sources - sinks)
public class EconomyTrackerService {

    // Key used for all non-player accounts (banks, towns, NPC shops) in noteBalanceChange.
    public static final String ACCOUNTS_KEY = "#accounts";

    private record PlayerSnapshot(String name, BigDecimal balance) {}

    // State of a running full scan (initial import or recalculate).
    private static final class ScanState {
        // Every account that is part of this scan
        final Set<String> allKeys = ConcurrentHashMap.newKeySet();
        // Accounts whose balance has not been read yet. Changes to them will show up in the scan anyway.
        final Set<String> remaining = ConcurrentHashMap.newKeySet();
        // Changes the scan cannot see because the account was already read (or is not part of the scan).
        BigDecimal adjustment = BigDecimal.ZERO;
    }

    private final ServerEconomyTracker plugin;
    private final Economy economy;
    private final PlayerBalanceFunc playerBalanceFunc;
    private final EconomyTotalsFunc economyTotalsFunc;
    private final Object saveLock = new Object();

    private EconomyTotals totals;
    private boolean dirty;
    private volatile ScanState activeScan;
    // Balance changes reported by hooks since a player's first join, until the starting balance is read.
    private final Map<String, BigDecimal> pendingFirstJoins = new HashMap<>();
    // Last known sum of non-player accounts, used by the heuristic scan to see bank changes.
    private BigDecimal lastAccountWealth;

    public EconomyTrackerService(ServerEconomyTracker plugin, DatabaseManager databaseManager, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.playerBalanceFunc = new PlayerBalanceFunc(databaseManager);
        this.economyTotalsFunc = new EconomyTotalsFunc(databaseManager);
    }

    public void loadOrCreateTotals() {
        this.totals = economyTotalsFunc.load().orElseGet(() -> {
            EconomyTotals newTotals = new EconomyTotals(
                    false, bd(0), bd(0), bd(0), bd(0), bd(0), bd(0),
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
        BigDecimal totalBalance = runFullScan();
        plugin.getLogger().info("Baseline import complete. Total balance: " + totalBalance);
    }

    // Rescans all players and sets the current total balance to the real sum.
    // Sources/sinks stay untouched, so only the baseline is corrected. Returns the new total.
    public BigDecimal recalculateBaseline() {
        if (totals == null) throw new IllegalStateException("Totals are not loaded yet.");

        BigDecimal totalBalance = runFullScan();
        plugin.getLogger().info("Baseline recalculated. Total balance: " + totalBalance);
        return totalBalance;
    }

    public boolean isScanning() {
        return activeScan != null;
    }

    // The baseline is the starting wealth: current total minus everything tracked since (net change).
    // It consists of what the scan found plus the manual correction.
    public synchronized BigDecimal getBaseline() {
        if (totals == null) return null;
        return scale(totals.getCurrentTotalBalance().subtract(totals.getTotalNetChange()));
    }

    public synchronized BigDecimal getManualCorrection() {
        if (totals == null) return null;
        return scale(totals.getManualCorrection());
    }

    // Part of the baseline that comes from scanning the balances (baseline without manual correction).
    public synchronized BigDecimal getScannedBaseline() {
        if (totals == null) return null;
        return scale(getBaseline().subtract(totals.getManualCorrection()));
    }

    // Sets the manual correction so the baseline becomes exactly this value.
    public void setBaseline(BigDecimal baseline) {
        BigDecimal change;
        synchronized (this) {
            if (totals == null) throw new IllegalStateException("Totals are not loaded yet.");
            change = baseline.subtract(getBaseline());
        }
        adjustManualCorrection(change);
    }

    // Money the scan cannot see. Stays when the baseline is recalculated.
    public void adjustManualCorrection(BigDecimal amount) {
        synchronized (this) {
            if (totals == null) throw new IllegalStateException("Totals are not loaded yet.");
            totals.setManualCorrection(scale(totals.getManualCorrection().add(amount)));
            totals.setCurrentTotalBalance(scale(totals.getCurrentTotalBalance().add(amount)));
            totals.setUpdatedAt(System.currentTimeMillis());
            dirty = true;
        }
        persist();
        plugin.getLogger().info("Manual correction changed by " + scale(amount) + ". Correction: "
                + totals.getManualCorrection() + " | Baseline: " + getBaseline());
    }

    public void resetManualCorrection() {
        BigDecimal correction;
        synchronized (this) {
            if (totals == null) throw new IllegalStateException("Totals are not loaded yet.");
            correction = totals.getManualCorrection();
        }
        adjustManualCorrection(correction.negate());
    }

    // Sums all player wallets plus all non-player accounts (banks, towns, NPC shops) and sets the
    // current total balance to that. Balance changes during the scan are taken into account.
    private BigDecimal runFullScan() {
        ScanState state = new ScanState();
        synchronized (this) {
            if (activeScan != null) throw new IllegalStateException("A scan is already running.");
            activeScan = state;
        }

        try {
            OfflinePlayer[] players = Bukkit.getOfflinePlayers();
            for (OfflinePlayer offlinePlayer : players) {
                if (offlinePlayer.getUniqueId() == null || offlinePlayer.getName() == null) continue;
                String key = offlinePlayer.getUniqueId().toString();
                state.allKeys.add(key);
                state.remaining.add(key);
            }
            state.allKeys.add(ACCOUNTS_KEY);
            state.remaining.add(ACCOUNTS_KEY);

            long now = System.currentTimeMillis();
            BigDecimal playerWealth = bd(0);
            int failed = 0;

            for (OfflinePlayer offlinePlayer : players) {
                if (offlinePlayer.getUniqueId() == null || offlinePlayer.getName() == null) continue;
                String key = offlinePlayer.getUniqueId().toString();

                // One broken player must not abort the whole import.
                try {
                    BigDecimal balance = getTotalWealth(offlinePlayer);
                    playerWealth = playerWealth.add(balance);
                    playerBalanceFunc.upsert(offlinePlayer.getUniqueId(), offlinePlayer.getName(), balance, true, now);
                } catch (Exception e) {
                    failed++;
                    plugin.getLogger().warning("Could not read balance of " + offlinePlayer.getName() + ": " + e.getMessage());
                } finally {
                    state.remaining.remove(key);
                }
            }

            BigDecimal accountWealth;
            try {
                accountWealth = scale(plugin.getHookManager().getTotalAccountWealth());
            } finally {
                state.remaining.remove(ACCOUNTS_KEY);
            }

            BigDecimal total;
            synchronized (this) {
                // The manual correction is money the scan cannot see, so it is kept on top.
                total = scale(playerWealth.add(accountWealth).add(state.adjustment).add(totals.getManualCorrection()));
                totals.setCurrentTotalBalance(total);
                totals.setBaselineImportDone(true);
                totals.setUpdatedAt(System.currentTimeMillis());
                lastAccountWealth = accountWealth;
                dirty = true;
            }
            persist();

            plugin.getLogger().info("Player wealth: " + scale(playerWealth) + " | Bank/account wealth: " + accountWealth
                    + " | Changes during scan: " + scale(state.adjustment)
                    + " | Manual correction: " + totals.getManualCorrection());
            if (failed > 0) {
                plugin.getLogger().warning(failed + " player balance(s) could not be read and are missing in the total. "
                        + "Use /et baseline to correct it.");
            }

            return total;
        } finally {
            synchronized (this) {
                activeScan = null;
            }
        }
    }

    // Called for every real balance change of a player (key = UUID) or non-player account (ACCOUNTS_KEY).
    // Keeps the current total balance exact, no matter how the change gets classified.
    public synchronized void noteBalanceChange(String key, BigDecimal delta) {
        if (totals == null || delta.signum() == 0) return;

        totals.setCurrentTotalBalance(scale(totals.getCurrentTotalBalance().add(delta)));
        totals.setUpdatedAt(System.currentTimeMillis());
        dirty = true;

        ScanState scan = activeScan;
        if (scan != null && !scan.remaining.contains(key)) {
            scan.adjustment = scan.adjustment.add(delta);
        }

        pendingFirstJoins.computeIfPresent(key, (k, tracked) -> tracked.add(delta));
    }

    public void handleFirstJoin(Player player) {
        UUID uuid = player.getUniqueId();
        String key = uuid.toString();
        String name = player.getName();
        int delaySeconds = plugin.getConfig().getInt("tracker.first-join-delay-seconds", 3);

        // Start collecting hook-reported changes right at the join, before anything can happen to the balance.
        synchronized (this) {
            pendingFirstJoins.putIfAbsent(key, BigDecimal.ZERO);
        }

        // DB lookup off the main thread, so joins never lag on a slow database.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<PlayerBalanceData> existing = playerBalanceFunc.findByUuid(uuid);
            if (existing.isPresent() || !plugin.isEnabled()) {
                synchronized (EconomyTrackerService.this) {
                    pendingFirstJoins.remove(key);
                }
                return;
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                BigDecimal balance;
                try {
                    balance = getTotalWealth(player);
                } catch (Exception e) {
                    synchronized (EconomyTrackerService.this) {
                        pendingFirstJoins.remove(key);
                    }
                    plugin.getLogger().warning("Could not read starting balance of " + name + ": " + e.getMessage());
                    return;
                }
                long now = System.currentTimeMillis();
                BigDecimal startingBalance;

                synchronized (EconomyTrackerService.this) {
                    // Money given or taken by other plugins since the join was already booked by the hooks
                    // (e.g. a join reward). Only what is left is the real starting balance.
                    BigDecimal tracked = pendingFirstJoins.remove(key);
                    startingBalance = scale(tracked != null ? balance.subtract(tracked) : balance);

                    if (startingBalance.signum() != 0) {
                        if (startingBalance.signum() > 0) {
                            totals.setTotalSources(scale(totals.getTotalSources().add(startingBalance)));
                        } else {
                            totals.setTotalSinks(scale(totals.getTotalSinks().add(startingBalance.abs())));
                        }
                        totals.setTotalNetChange(scale(totals.getTotalSources().subtract(totals.getTotalSinks())));
                        totals.setCurrentTotalBalance(scale(totals.getCurrentTotalBalance().add(startingBalance)));
                        totals.setUpdatedAt(now);
                        dirty = true;

                        // A running scan only sees this money if the player is part of it.
                        ScanState scan = activeScan;
                        if (scan != null && !scan.allKeys.contains(key)) {
                            scan.adjustment = scan.adjustment.add(startingBalance);
                        }
                    }
                }

                if (startingBalance.signum() != 0) {
                    plugin.getLogger().info("New player with starting balance: " + name + " " + startingBalance
                            + " (current balance: " + balance + ")");
                }

                Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                        playerBalanceFunc.upsert(uuid, name, balance, true, now));
            }, delaySeconds * 20L);
        });
    }

    // Must be called from the main thread — reads Vault balances safely, then processes DB async.
    public void scanAndSchedule() {
        // A full scan overwrites stored balances and the total, so don't interfere with it.
        if (isScanning()) return;

        Map<UUID, PlayerSnapshot> snapshots = new LinkedHashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            snapshots.put(player.getUniqueId(), new PlayerSnapshot(player.getName(), getTotalWealth(player)));
        }

        // When transaction hooks are active they handle source/sink/transfer booking in real-time.
        // The scan then only updates stored balances to keep player_balances table current.
        boolean useHeuristic = !plugin.getHookManager().hasTransactionHooks();

        // Without transaction hooks, bank changes must be detected by the scan as well,
        // otherwise a deposit into a bank would look like money leaving the server.
        BigDecimal accountWealth = useHeuristic && !plugin.getHookManager().getWealthHooks().isEmpty()
                ? scale(plugin.getHookManager().getTotalAccountWealth())
                : null;

        if (snapshots.isEmpty() && accountWealth == null) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                processSnapshots(snapshots, useHeuristic, accountWealth);
            } catch (Exception e) {
                plugin.getLogger().severe("Error during economy scan: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void processSnapshots(Map<UUID, PlayerSnapshot> snapshots, boolean useHeuristic, BigDecimal accountWealth) {
        if (isScanning()) return;

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

        if (accountWealth != null) {
            synchronized (this) {
                if (lastAccountWealth != null) {
                    BigDecimal delta = accountWealth.subtract(lastAccountWealth);
                    if (delta.compareTo(BigDecimal.ZERO) > 0) positiveSum = positiveSum.add(delta);
                    else if (delta.compareTo(BigDecimal.ZERO) < 0) negativeSum = negativeSum.add(delta.abs());
                }
                lastAccountWealth = accountWealth;
            }
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
            dirty = true;
        }

        plugin.getLogger().info(
                "Scan | +" + positiveSum + " | -" + negativeSum +
                " | source=" + sourcePart + " | sink=" + sinkPart +
                " | transfer=" + transferPart + " | total=" + totals.getCurrentTotalBalance()
        );
    }

    // Classification only — the balance itself is tracked by noteBalanceChange.
    public synchronized void recordSource(BigDecimal amount) {
        if (totals == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        totals.setTotalSources(scale(totals.getTotalSources().add(amount)));
        totals.setTotalNetChange(scale(totals.getTotalSources().subtract(totals.getTotalSinks())));
        totals.setUpdatedAt(System.currentTimeMillis());
        dirty = true;
    }

    public synchronized void recordSink(BigDecimal amount) {
        if (totals == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        totals.setTotalSinks(scale(totals.getTotalSinks().add(amount)));
        totals.setTotalNetChange(scale(totals.getTotalSources().subtract(totals.getTotalSinks())));
        totals.setUpdatedAt(System.currentTimeMillis());
        dirty = true;
    }

    public synchronized void recordTransfer(BigDecimal amount) {
        if (totals == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;
        totals.setTotalTransferVolume(scale(totals.getTotalTransferVolume().add(amount)));
        totals.setUpdatedAt(System.currentTimeMillis());
        dirty = true;
    }

    // Changes are kept in memory and written regularly by the flush task (and on shutdown),
    // so a busy economy doesn't cause a DB write per transaction.
    public void flushIfDirty() {
        synchronized (saveLock) {
            EconomyTotals snapshot;
            synchronized (this) {
                if (!dirty || totals == null) return;
                snapshot = totals.copy();
                dirty = false;
            }
            saveSnapshot(snapshot);
        }
    }

    private void persist() {
        synchronized (saveLock) {
            EconomyTotals snapshot;
            synchronized (this) {
                if (totals == null) return;
                snapshot = totals.copy();
                dirty = false;
            }
            saveSnapshot(snapshot);
        }
    }

    private void saveSnapshot(EconomyTotals snapshot) {
        try {
            economyTotalsFunc.save(snapshot);
        } catch (RuntimeException e) {
            synchronized (this) {
                dirty = true;
            }
            throw e;
        }
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
        return bd(economy.getBalance(player));
    }
}
