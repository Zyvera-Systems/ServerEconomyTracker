package dev.zyverasystems.hooks;

import dev.zyverasystems.ServerEconomyTracker;
import dev.zyverasystems.hooks.plugins.EssentialsXTransactionHook;
import dev.zyverasystems.hooks.plugins.XConomyTransactionHook;
import dev.zyverasystems.utils.EconomyTrackerService;
import org.bukkit.Bukkit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HookManager {

    private final ServerEconomyTracker plugin;
    private final EconomyTrackerService trackerService;
    private final List<EconomyWealthHook> wealthHooks = new ArrayList<>();
    private final List<EconomyTransactionHook> transactionHooks = new ArrayList<>();

    public HookManager(ServerEconomyTracker plugin, EconomyTrackerService trackerService) {
        this.plugin = plugin;
        this.trackerService = trackerService;
    }

    public void loadHooks() {
        wealthHooks.clear();
        transactionHooks.clear();

        if (Bukkit.getPluginManager().getPlugin("Essentials") != null) {
            registerTransactionHook(new EssentialsXTransactionHook(plugin, trackerService));
        }

        if (Bukkit.getPluginManager().getPlugin("XConomy") != null) {
            registerTransactionHook(new XConomyTransactionHook(plugin, trackerService));
        }
    }

    private void registerWealthHook(EconomyWealthHook hook) {
        if (!hook.isAvailable()) return;
        wealthHooks.add(hook);
        plugin.getLogger().info("Loaded wealth hook: " + hook.getName());
    }

    private void registerTransactionHook(EconomyTransactionHook hook) {
        if (!hook.isAvailable()) return;
        transactionHooks.add(hook);
        hook.register();
        plugin.getLogger().info("Loaded transaction hook: " + hook.getName());
    }

    public BigDecimal getTotalExtraWealth(UUID playerUuid) {
        BigDecimal total = BigDecimal.ZERO;

        for (EconomyWealthHook hook : wealthHooks) {
            try {
                total = total.add(hook.getExtraWealth(playerUuid));
            } catch (Exception e) {
                plugin.getLogger().warning("Hook error in " + hook.getName() + ": " + e.getMessage());
            }
        }

        return total;
    }

    public boolean hasTransactionHooks() {
        return !transactionHooks.isEmpty();
    }

    public List<EconomyWealthHook> getWealthHooks() {
        return wealthHooks;
    }

    public List<EconomyTransactionHook> getTransactionHooks() {
        return transactionHooks;
    }
}