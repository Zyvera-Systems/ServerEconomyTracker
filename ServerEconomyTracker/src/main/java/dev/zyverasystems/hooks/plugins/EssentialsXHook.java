package dev.zyverasystems.hooks.plugins;

import dev.zyverasystems.ServerEconomyTracker;
import dev.zyverasystems.hooks.EconomyWealthHook;

import java.math.BigDecimal;
import java.util.UUID;

public class EssentialsXHook implements EconomyWealthHook {

    private final ServerEconomyTracker plugin;

    public EssentialsXHook(ServerEconomyTracker plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "EssentialsX";
    }

    @Override
    public boolean isAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("Essentials") != null;
    }

    @Override
    public BigDecimal getExtraWealth(UUID playerUuid) {
        return BigDecimal.ZERO;
    }
}