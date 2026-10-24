package dev.zyverasystems.hooks;

public interface EconomyTransactionHook {

    String getName();

    boolean isAvailable();

    void register();

    // Books transactions that are still waiting to be paired. Called on shutdown.
    default void shutdown() {}
}
