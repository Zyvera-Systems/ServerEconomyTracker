package dev.zyverasystems.hooks.plugins;

import dev.zyverasystems.ServerEconomyTracker;
import dev.zyverasystems.hooks.EconomyTransactionHook;
import me.yic.xconomy.api.event.AccountEvent;
import me.yic.xconomy.api.event.NonPlayerAccountEvent;
import me.yic.xconomy.api.event.PlayerAccountEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class XConomyTransactionHook implements EconomyTransactionHook, Listener {

    private final ServerEconomyTracker plugin;

    public XConomyTransactionHook(ServerEconomyTracker plugin) {
        this.plugin = plugin;
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

    @EventHandler
    public void onAccount(AccountEvent event) {
        plugin.getLogger().info("[XConomy/AccountEvent] account="
                + event.getaccountname() + " amount=" + event.getamount());
    }

    @EventHandler
    public void onPlayerAccount(PlayerAccountEvent event) {
        plugin.getLogger().info("[XConomy/PlayerAccountEvent] uuid="
                + event.getUniqueId() + " amount=" + event.getamount());
    }

    @EventHandler
    public void onNonPlayerAccount(NonPlayerAccountEvent event) {
        plugin.getLogger().info("[XConomy/NonPlayerAccountEvent] account="
                + event.getaccountname() + " amount=" + event.getamount());
    }
}