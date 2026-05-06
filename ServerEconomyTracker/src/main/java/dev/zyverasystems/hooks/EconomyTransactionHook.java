package dev.zyverasystems.hooks;

public interface EconomyTransactionHook {

    String getName();

    boolean isAvailable();

    void register();
}