package dev.zyverasystems.hooks.plugins;

import com.earth2me.essentials.User;
import dev.zyverasystems.ServerEconomyTracker;
import dev.zyverasystems.hooks.EconomyTransactionHook;
import dev.zyverasystems.hooks.TransactionPairer;
import dev.zyverasystems.utils.EconomyTrackerService;
import net.ess3.api.IEssentials;
import net.ess3.api.events.UserBalanceUpdateEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.math.BigDecimal;

public class EssentialsXTransactionHook implements EconomyTransactionHook, Listener {

    private final ServerEconomyTracker plugin;
    private final EconomyTrackerService trackerService;
    private final TransactionPairer apiPairer;
    private final TransactionPairer payPairer;

    public EssentialsXTransactionHook(ServerEconomyTracker plugin, EconomyTrackerService trackerService) {
        this.plugin = plugin;
        this.trackerService = trackerService;
        this.apiPairer = new TransactionPairer(plugin, trackerService, false);
        this.payPairer = new TransactionPairer(plugin, trackerService, true);
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

    @Override
    public void shutdown() {
        apiPairer.flush();
        payPairer.flush();
    }

    // MONITOR: other plugins may still change the new balance at lower priorities.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBalanceUpdate(UserBalanceUpdateEvent event) {
        BigDecimal delta = event.getNewBalance().subtract(event.getOldBalance());
        if (delta.compareTo(BigDecimal.ZERO) == 0) return;

        boolean isAdd = delta.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal amount = delta.abs();
        UserBalanceUpdateEvent.Cause cause = event.getCause();
        Player account = event.getPlayer();

        // NPC accounts (banks, towns) belong to the economy as well: player -> bank pairs up as a transfer.
        trackerService.noteBalanceChange(accountKey(account), delta);

        plugin.getLogger().fine("[EssentialsX] account=" + (account != null ? account.getName() : "unknown")
                + " cause=" + cause + " delta=" + delta);

        switch (cause) {
            // Sender and receiver both fire an event; pairing them also catches a difference between them.
            case COMMAND_PAY -> payPairer.handle(isAdd, amount);
            case API -> apiPairer.handle(isAdd, amount);
            default -> {
                if (isAdd) trackerService.recordSource(amount);
                else trackerService.recordSink(amount);
            }
        }
    }

    private String accountKey(Player account) {
        if (account == null) return EconomyTrackerService.ACCOUNTS_KEY;

        if (Bukkit.getPluginManager().getPlugin("Essentials") instanceof IEssentials essentials) {
            User user = essentials.getUser(account.getUniqueId());
            if (user != null && user.isNPC()) return EconomyTrackerService.ACCOUNTS_KEY;
        }

        return account.getUniqueId().toString();
    }
}
