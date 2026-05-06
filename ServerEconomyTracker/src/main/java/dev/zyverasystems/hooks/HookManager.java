package dev.zyverasystems.hooks;

import dev.zyverasystems.ServerEconomyTracker;
import dev.zyverasystems.hooks.plugins.EssentialsXHook;
import dev.zyverasystems.hooks.plugins.XConomyHook;
import dev.zyverasystems.hooks.plugins.XConomyTransactionHook;
import org.bukkit.Bukkit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HookManager {

    private final ServerEconomyTracker plugin;
    private final List<EconomyWealthHook> wealthHooks = new ArrayList<>();
    private final List<EconomyTransactionHook> transactionHooks = new ArrayList<>();

    public HookManager(ServerEconomyTracker plugin) {
        this.plugin = plugin;
    }

    public void loadHooks() {
        wealthHooks.clear();
        transactionHooks.clear();

        if (Bukkit.getPluginManager().getPlugin("Essentials") != null) {
            EconomyWealthHook essentialsHook = new EssentialsXHook(plugin);
            if (essentialsHook.isAvailable()) {
                wealthHooks.add(essentialsHook);
                plugin.getLogger().info("Loaded wealth hook: " + essentialsHook.getName());
            }
        }

        if (Bukkit.getPluginManager().getPlugin("XConomy") != null) {
            EconomyWealthHook xConomyWealthHook = new XConomyHook(plugin);
            if (xConomyWealthHook.isAvailable()) {
                wealthHooks.add(xConomyWealthHook);
                plugin.getLogger().info("Loaded wealth hook: " + xConomyWealthHook.getName());
            }

            EconomyTransactionHook xConomyTransactionHook = new XConomyTransactionHook(plugin);
            if (xConomyTransactionHook.isAvailable()) {
                transactionHooks.add(xConomyTransactionHook);
                xConomyTransactionHook.register();
                plugin.getLogger().info("Loaded transaction hook: " + xConomyTransactionHook.getName());
            }
        }
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
}