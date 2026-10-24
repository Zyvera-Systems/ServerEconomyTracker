package dev.zyverasystems.hooks;

import java.math.BigDecimal;

// Provides money that sits in non-player accounts (banks, towns, NPC shops).
// That money is still part of the server economy, so it belongs into the total balance.
public interface EconomyWealthHook {

    String getName();

    boolean isAvailable();

    BigDecimal getAccountWealth();

}
