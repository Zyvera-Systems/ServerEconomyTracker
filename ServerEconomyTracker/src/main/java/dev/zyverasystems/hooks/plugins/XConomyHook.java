package dev.zyverasystems.hooks.plugins;

import dev.zyverasystems.ServerEconomyTracker;
import dev.zyverasystems.hooks.EconomyWealthHook;
import org.bukkit.Bukkit;

import java.math.BigDecimal;
import java.util.UUID;

public class XConomyHook implements EconomyWealthHook {

    private final ServerEconomyTracker plugin;

    public XConomyHook(ServerEconomyTracker plugin) {
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
    public BigDecimal getExtraWealth(UUID playerUuid) {
        return BigDecimal.ZERO;
    }
}