package dev.zyverasystems.hooks.plugins;

import dev.zyverasystems.ServerEconomyTracker;
import dev.zyverasystems.hooks.EconomyTransactionHook;
import dev.zyverasystems.hooks.TransactionPairer;
import dev.zyverasystems.utils.EconomyTrackerService;
import me.yic.xconomy.api.event.NonPlayerAccountEvent;
import me.yic.xconomy.api.event.PlayerAccountEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.math.BigDecimal;
import java.util.Set;

public class XConomyTransactionHook implements EconomyTransactionHook, Listener {

    private static final Set<String> KNOWN_TRANSFER_METHODS = Set.of("pay");

    private final ServerEconomyTracker plugin;
    private final EconomyTrackerService trackerService;
    private final XConomyHook accountHook;
    private final TransactionPairer pairer;
    // /pay can have a payment-tax, so sender and receiver amounts may differ.
    private final TransactionPairer payPairer;

    public XConomyTransactionHook(ServerEconomyTracker plugin, EconomyTrackerService trackerService, XConomyHook accountHook) {
        this.plugin = plugin;
        this.trackerService = trackerService;
        this.accountHook = accountHook;
        this.pairer = new TransactionPairer(plugin, trackerService, false);
        this.payPairer = new TransactionPairer(plugin, trackerService, true);
    }

    @Override
    public String getName() {
        return "XConomy";
    }

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("XConomy") != null;
    }

    @Override
    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void shutdown() {
        pairer.flush();
        payPairer.flush();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAccount(PlayerAccountEvent event) {
        BigDecimal amount = event.getamount();
        Boolean isAdd = event.getisadd();
        String method = event.getmethod();
        String key = event.getUniqueId().toString();

        if (isAdd == null) {
            handleSet(key, event.getbalance(), amount);
            return;
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;

        trackerService.noteBalanceChange(key, isAdd ? amount : amount.negate());

        plugin.getLogger().fine("[XConomy] player=" + event.getUniqueId()
                + " add=" + isAdd + " amount=" + amount + " method=" + method);

        if (method != null && KNOWN_TRANSFER_METHODS.contains(method.toLowerCase())) {
            // Two events per /pay (sender + receiver); the pairer books them as one transfer
            // and a missing difference (tax) as a sink.
            payPairer.handle(isAdd, amount);
            return;
        }

        pairer.handle(isAdd, amount);
    }

    // Non-player accounts (banks, towns, NPC shops) are part of the economy too.
    // Player -> bank pairs up as a transfer, money only leaving/entering the bank is a sink/source.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onNonPlayerAccount(NonPlayerAccountEvent event) {
        if (accountHook != null) accountHook.rememberAccount(event.getaccountname());

        BigDecimal amount = event.getamount();
        Boolean isAdd = event.getisadd();

        if (isAdd == null) {
            handleSet(EconomyTrackerService.ACCOUNTS_KEY, event.getbalance(), amount);
            return;
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;

        trackerService.noteBalanceChange(EconomyTrackerService.ACCOUNTS_KEY, isAdd ? amount : amount.negate());

        plugin.getLogger().fine("[XConomy] account=" + event.getaccountname()
                + " add=" + isAdd + " amount=" + amount + " method=" + event.getmethod());

        pairer.handle(isAdd, amount);
    }

    // isAdd == null means "set" (e.g. /money set). XConomy fires the event before the change,
    // so balance is the old value and amount the new one.
    private void handleSet(String key, BigDecimal oldBalance, BigDecimal newBalance) {
        if (oldBalance == null || newBalance == null) return;

        BigDecimal delta = newBalance.subtract(oldBalance);
        if (delta.signum() == 0) return;

        trackerService.noteBalanceChange(key, delta);

        plugin.getLogger().fine("[XConomy] set account=" + key + " old=" + oldBalance + " new=" + newBalance);

        if (delta.signum() > 0) trackerService.recordSource(delta);
        else trackerService.recordSink(delta.abs());
    }
}
