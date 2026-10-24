package dev.zyverasystems.hooks.plugins;

import dev.zyverasystems.ServerEconomyTracker;
import dev.zyverasystems.hooks.EconomyWealthHook;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import java.math.BigDecimal;
import java.util.List;

// Works with every economy plugin that supports banks through Vault.
public class VaultBankHook implements EconomyWealthHook {

    private final ServerEconomyTracker plugin;

    public VaultBankHook(ServerEconomyTracker plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Vault Banks";
    }

    @Override
    public boolean isAvailable() {
        Economy economy = plugin.getEconomy();
        return economy != null && economy.hasBankSupport();
    }

    @Override
    public BigDecimal getAccountWealth() {
        Economy economy = plugin.getEconomy();
        List<String> banks = economy.getBanks();
        if (banks == null) return BigDecimal.ZERO;

        BigDecimal total = BigDecimal.ZERO;

        for (String bank : banks) {
            EconomyResponse response = economy.bankBalance(bank);
            if (response != null && response.transactionSuccess()) {
                total = total.add(BigDecimal.valueOf(response.balance));
            }
        }

        return total;
    }
}
