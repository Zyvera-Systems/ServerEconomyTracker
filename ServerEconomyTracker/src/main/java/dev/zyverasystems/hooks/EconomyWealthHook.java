package dev.zyverasystems.hooks;

import java.math.BigDecimal;
import java.util.UUID;

public interface EconomyWealthHook {

    String getName();

    boolean isAvailable();

    BigDecimal getExtraWealth(UUID playerUuid);

}
