package dev.zyverasystems.utils;

import java.math.BigDecimal;
import java.util.UUID;

public class PlayerBalanceData {

    private final UUID uuid;
    private final String name;
    private final BigDecimal balance;
    private final boolean knownPlayer;
    private final long lastUpdated;

    public PlayerBalanceData(UUID uuid, String name, BigDecimal balance, boolean knownPlayer, long lastUpdated) {
        this.uuid = uuid;
        this.name = name;
        this.balance = balance;
        this.knownPlayer = knownPlayer;
        this.lastUpdated = lastUpdated;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public boolean isKnownPlayer() {
        return knownPlayer;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }
}