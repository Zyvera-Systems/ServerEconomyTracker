package dev.zyverasystems.hooks.plugins;

import dev.zyverasystems.ServerEconomyTracker;
import dev.zyverasystems.hooks.EconomyTransactionHook;
import dev.zyverasystems.utils.EconomyTrackerService;
import net.ess3.api.events.UserBalanceUpdateEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentLinkedQueue;

public class EssentialsXTransactionHook implements EconomyTransactionHook, Listener {

    private static final long PAIR_WINDOW_TICKS = 5L;

    private final ServerEconomyTracker plugin;
    private final EconomyTrackerService trackerService;
    private final ConcurrentLinkedQueue<PendingWithdrawal> pendingWithdrawals = new ConcurrentLinkedQueue<>();

    private static class PendingWithdrawal {
        final BigDecimal amount;
        BukkitTask task;

        PendingWithdrawal(BigDecimal amount) {
            this.amount = amount;
        }
    }

    public EssentialsXTransactionHook(ServerEconomyTracker plugin, EconomyTrackerService trackerService) {
        this.plugin = plugin;
        this.trackerService = trackerService;
    }

    @Override
    public String getName() {
        return "EssentialsX";
    }

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("Essentials") != null;
    }

    @Override
    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onBalanceUpdate(UserBalanceUpdateEvent event) {
        BigDecimal delta = event.getNewBalance().subtract(event.getOldBalance());
        if (delta.compareTo(BigDecimal.ZERO) == 0) return;

        boolean isAdd = delta.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal amount = delta.abs();
        UserBalanceUpdateEvent.Cause cause = event.getCause();

        plugin.getLogger().fine("[EssentialsX] player=" + event.getPlayer().getName()
                + " cause=" + cause + " delta=" + delta);

        switch (cause) {
            case COMMAND_PAY -> {
                if (!isAdd) {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                            trackerService.recordTransfer(amount));
                }
            }
            case API -> handleApiEvent(isAdd, amount);
            default -> Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (isAdd) trackerService.recordSource(amount);
                else trackerService.recordSink(amount);
            });
        }
    }

    private void handleApiEvent(boolean isAdd, BigDecimal amount) {
        if (!isAdd) {
            PendingWithdrawal entry = new PendingWithdrawal(amount);
            // Schedule the fallback Sink — fires only if no matching deposit claims this entry first.
            entry.task = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                if (pendingWithdrawals.remove(entry)) {
                    trackerService.recordSink(amount);
                }
            }, PAIR_WINDOW_TICKS);
            pendingWithdrawals.add(entry);
        } else {
            // Try to pair with a pending withdrawal of the same amount.
            for (PendingWithdrawal pending : pendingWithdrawals) {
                if (pending.amount.compareTo(amount) == 0 && pendingWithdrawals.remove(pending)) {
                    pending.task.cancel();
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                            trackerService.recordTransfer(amount));
                    return;
                }
            }
            // No matching withdrawal → money came from the server (e.g. admin shop, reward plugin).
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                    trackerService.recordSource(amount));
        }
    }
}