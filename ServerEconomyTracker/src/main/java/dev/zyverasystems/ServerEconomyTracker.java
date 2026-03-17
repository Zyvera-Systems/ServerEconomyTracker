package dev.zyverasystems;

import dev.zyverasystems.commands.EconomyTrackerCommand;
import dev.zyverasystems.listener.PlayerJoinListener;
import dev.zyverasystems.utils.EconomyTrackerService;
import dev.zyverasystems.utils.MessagesManager;
import dev.zyverasystems.utils.database.DatabaseManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class ServerEconomyTracker extends JavaPlugin {

    private Economy economy;
    private DatabaseManager databaseManager;
    private EconomyTrackerService trackerService;
    private MessagesManager messagesManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.messagesManager = new MessagesManager(this);
        this.messagesManager.load();

        // Economy setup + Logging
        if (!setupEconomy()) {
            getLogger().severe("Vault or Economy-Provider not found.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.databaseManager = new DatabaseManager(this);

        try {
            databaseManager.connect();
            databaseManager.createTables();
        } catch (Exception e) {
            getLogger().severe("Database connection error: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.trackerService = new EconomyTrackerService(this, databaseManager, economy);

        this.trackerService = new EconomyTrackerService(this, databaseManager, economy);

        try {
            trackerService.loadOrCreateTotals();
            trackerService.performInitialBaselineIfNeeded();
        } catch (Exception e) {
            getLogger().severe("Error initializing tracker: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Listener
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(trackerService), this);

        // Comands
        EconomyTrackerCommand command = new EconomyTrackerCommand(this, trackerService, messagesManager);
        getCommand("economytracker").setExecutor(command);
        getCommand("economytracker").setTabCompleter(command);

        // Scann invervall
        int scanIntervalSeconds = getConfig().getInt("tracker.scan-interval-seconds", 10);
        long scanIntervalTicks = scanIntervalSeconds * 20L;

        Bukkit.getScheduler().runTaskTimerAsynchronously(
                this,
                () -> {
                    try {
                        trackerService.scanOnlinePlayers();
                    } catch (Exception e) {
                        getLogger().severe("Fehler beim Economy-Scan: " + e.getMessage());
                        e.printStackTrace();
                    }
                },
                scanIntervalTicks,
                scanIntervalTicks
        );

        sendEnabled();
    }

    @Override
    public void onDisable() {
        getLogger().info("EconomyTracker is disableing.");
    }

    // Economy setup
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);

        if (rsp == null) {
            return false;
        }

        this.economy = rsp.getProvider();
        return this.economy != null;
    }

    public Economy getEconomy() {
        return economy;
    }

    public DatabaseManager gedtDatabaseManager() {
        return databaseManager;
    }

    public EconomyTrackerService getTrackerService() {
        return trackerService;
    }

    public MessagesManager getMessagesManager() {
        return messagesManager;
    }

    public void reloadPluginFiles() {
        reloadConfig();
        messagesManager.reload();
    }

    public void sendEnabled() {
        getLogger().info("-----------------------------------------------------------------------------------------------------");
        getLogger().info("__________                                   _________               __                         ");
        getLogger().info("\\____    /___.__.___  __ ________________   /   _____/__.__. _______/  |_  ____   _____   ______");
        getLogger().info("  /     /<   |  |\\  \\/ // __ \\_  __ \\__  \\  \\_____  <   |  |/  ___/\\   __\\/ __ \\ /     \\ /  ___/");
        getLogger().info(" /     /_ \\___  | \\   /\\  ___/|  | \\// __ \\_/        \\___  |\\___ \\  |  | \\  ___/|  Y Y  \\\\___ \\ ");
        getLogger().info("/_______ \\/ ____|  \\_/  \\___  >__|  (____  /_______  / ____/____  > |__|  \\___  >__|_|  /____  >");
        getLogger().info("        \\/\\/                \\/           \\/        \\/\\/         \\/            \\/      \\/     \\/ ");
        getLogger().info("-----------------------------------------------------------------------------------------------------");
        getLogger().info("Author: ZyveraSystems, GrueneKatze");
        getLogger().info("EconomyTracker is activ.");
        getLogger().info("Economy-Provider: " + economy.getName());
        getLogger().info("Datenbanktyp: " + databaseManager.getDatabaseType());
    }
}
