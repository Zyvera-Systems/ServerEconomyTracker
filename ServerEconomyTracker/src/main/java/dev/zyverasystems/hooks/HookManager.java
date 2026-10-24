package dev.zyverasystems.hooks;

import dev.zyverasystems.ServerEconomyTracker;
import dev.zyverasystems.hooks.plugins.EssentialsXHook;
import dev.zyverasystems.hooks.plugins.EssentialsXTransactionHook;
import dev.zyverasystems.hooks.plugins.VaultBankHook;
import dev.zyverasystems.hooks.plugins.XConomyHook;
import dev.zyverasystems.hooks.plugins.XConomyTransactionHook;
import dev.zyverasystems.utils.EconomyTrackerService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

        registerWealthHook(new VaultBankHook(plugin));

        // Only hook into the plugin that actually provides the Vault economy. If e.g. EssentialsX is
        // installed but XConomy handles the money, EssentialsX balances are not the real economy.
        if (isEconomyProvider("Essentials")) {
            registerWealthHook(new EssentialsXHook(plugin));
            registerTransactionHook(new EssentialsXTransactionHook(plugin, trackerService));
        }

        if (isEconomyProvider("XConomy")) {
            XConomyHook xConomyHook = new XConomyHook(plugin);
            registerWealthHook(xConomyHook);
            registerTransactionHook(new XConomyTransactionHook(plugin, trackerService, xConomyHook));
        }
    }

    private boolean isEconomyProvider(String pluginName) {
        if (Bukkit.getPluginManager().getPlugin(pluginName) == null) return false;

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;

        // Older setups register the provider through Vault itself, so also check the economy name.
        String providerName = rsp.getProvider().getName();
        return rsp.getPlugin().getName().equalsIgnoreCase(pluginName)
                || (providerName != null && providerName.toLowerCase().contains(pluginName.toLowerCase()));
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

    // Sum of all non-player accounts (banks, towns, NPC shops).
    public BigDecimal getTotalAccountWealth() {
        BigDecimal total = BigDecimal.ZERO;

        for (EconomyWealthHook hook : wealthHooks) {
            try {
                total = total.add(hook.getAccountWealth());
            } catch (Exception e) {
                plugin.getLogger().warning("Hook error in " + hook.getName() + ": " + e.getMessage());
            }
        }

        return total;
    }

    public void shutdown() {
        for (EconomyTransactionHook hook : transactionHooks) {
            try {
                hook.shutdown();
            } catch (Exception e) {
                plugin.getLogger().warning("Hook error in " + hook.getName() + " during shutdown: " + e.getMessage());
            }
        }
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
