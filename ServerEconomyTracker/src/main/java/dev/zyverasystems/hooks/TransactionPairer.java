package dev.zyverasystems.hooks;

import dev.zyverasystems.utils.EconomyTrackerService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;

// Pairs a withdrawal with a deposit that happens shortly before or after it.
// - pair found  -> transfer (money moved between accounts); a difference (e.g. /pay tax) is a sink/source
// - no pair     -> after a few ticks: deposit = source, withdrawal = sink
public class TransactionPairer {

    private static final long PAIR_WINDOW_TICKS = 5L;

    private final Plugin plugin;
    private final EconomyTrackerService trackerService;
    // true: pair with any amount (e.g. /pay where the receiver can get less because of tax)
    // false: only pair equal amounts
    private final boolean matchAnyAmount;

    private final Deque<Pending> withdrawals = new ArrayDeque<>();
    private final Deque<Pending> deposits = new ArrayDeque<>();

    private static class Pending {
        final BigDecimal amount;
        BukkitTask task;

        Pending(BigDecimal amount) {
            this.amount = amount;
        }
    }

    public TransactionPairer(Plugin plugin, EconomyTrackerService trackerService, boolean matchAnyAmount) {
        this.plugin = plugin;
        this.trackerService = trackerService;
        this.matchAnyAmount = matchAnyAmount;
    }

    public synchronized void handle(boolean isAdd, BigDecimal amount) {
        Deque<Pending> opposite = isAdd ? withdrawals : deposits;

        for (Pending pending : opposite) {
            if (matchAnyAmount || pending.amount.compareTo(amount) == 0) {
                opposite.remove(pending);
                pending.task.cancel();

                BigDecimal withdrawn = isAdd ? pending.amount : amount;
                BigDecimal deposited = isAdd ? amount : pending.amount;
                settle(withdrawn, deposited);
                return;
            }
        }

        Deque<Pending> own = isAdd ? deposits : withdrawals;
        Pending entry = new Pending(amount);
        // Fallback — fires only if no matching counterpart claims this entry first.
        entry.task = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            synchronized (TransactionPairer.this) {
                if (own.remove(entry)) book(isAdd, amount);
            }
        }, PAIR_WINDOW_TICKS);
        own.add(entry);
    }

    // Books everything still waiting for a counterpart. Used on shutdown.
    public synchronized void flush() {
        for (Pending pending : withdrawals) {
            pending.task.cancel();
            book(false, pending.amount);
        }
        for (Pending pending : deposits) {
            pending.task.cancel();
            book(true, pending.amount);
        }
        withdrawals.clear();
        deposits.clear();
    }

    private void settle(BigDecimal withdrawn, BigDecimal deposited) {
        trackerService.recordTransfer(withdrawn.min(deposited));

        int diff = withdrawn.compareTo(deposited);
        if (diff > 0) trackerService.recordSink(withdrawn.subtract(deposited));
        else if (diff < 0) trackerService.recordSource(deposited.subtract(withdrawn));
    }

    private void book(boolean isAdd, BigDecimal amount) {
        if (isAdd) trackerService.recordSource(amount);
        else trackerService.recordSink(amount);
    }
}
