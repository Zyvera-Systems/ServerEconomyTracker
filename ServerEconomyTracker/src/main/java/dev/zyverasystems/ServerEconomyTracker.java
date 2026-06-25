package dev.zyverasystems;

import dev.faststats.bukkit.BukkitContext;
import dev.faststats.data.Metric;
import dev.zyverasystems.commands.EconomyTrackerCommand;
import dev.zyverasystems.hooks.HookManager;
import dev.zyverasystems.listener.PlayerJoinListener;
import dev.zyverasystems.utils.EconomyTrackerService;
import dev.zyverasystems.utils.MessagesManager;
import dev.zyverasystems.utils.database.DatabaseManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ServerEconomyTracker extends JavaPlugin {


    private Economy economy;
    private DatabaseManager databaseManager;
    private EconomyTrackerService trackerService;
    private MessagesManager messagesManager;
    private HookManager hookManager;
    private BukkitTask scanTask;

    // FastStats
    private final BukkitContext context = new BukkitContext.Factory(this, "536bbd0e7a9e33beb90b5403ef89ef83")
            .metrics(factory -> factory
                    .addMetric(Metric.string("plugin_version", () -> getDescription().getVersion()))
                    .addMetric(Metric.string("minecraft_version", Bukkit::getMinecraftVersion))
                    .addMetric(Metric.string("server_software", Bukkit::getName))
                    .create())
            .create();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.messagesManager = new MessagesManager(this);
        this.messagesManager.load();

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

        try {
            trackerService.loadOrCreateTotals();
        } catch (Exception e) {
            getLogger().severe("Error initializing tracker: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.hookManager = new HookManager(this, trackerService);
        this.hookManager.loadHooks();

        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(trackerService), this);

        EconomyTrackerCommand command = new EconomyTrackerCommand(this, trackerService, messagesManager);
        getCommand("economytracker").setExecutor(command);
        getCommand("economytracker").setTabCompleter(command);

        sendEnabled();

        // Run baseline import async to avoid blocking the main thread, then start scanner
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                trackerService.performInitialBaselineIfNeeded();
            } catch (Exception e) {
                getLogger().severe("Error during baseline import: " + e.getMessage());
                e.printStackTrace();
            }
            Bukkit.getScheduler().runTask(ServerEconomyTracker.this, ServerEconomyTracker.this::startScanTask);
        });
        context.ready();
    }

    @Override
    public void onDisable() {
        if (scanTask != null) {
            scanTask.cancel();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        context.shutdown();
        getLogger().info("EconomyTracker has been disabled.");
    }

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

    private void startScanTask() {
        if (scanTask != null) {
            scanTask.cancel();
        }
        int scanIntervalSeconds = getConfig().getInt("tracker.scan-interval-seconds", 10);
        long scanIntervalTicks = scanIntervalSeconds * 20L;

        scanTask = Bukkit.getScheduler().runTaskTimer(
                this,
                () -> {
                    try {
                        trackerService.scanAndSchedule();
                    } catch (Exception e) {
                        getLogger().severe("Error starting economy scan: " + e.getMessage());
                        e.printStackTrace();
                    }
                },
                scanIntervalTicks,
                scanIntervalTicks
        );
    }

    public Economy getEconomy() {
        return economy;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public EconomyTrackerService getTrackerService() {
        return trackerService;
    }

    public MessagesManager getMessagesManager() {
        return messagesManager;
    }

    public HookManager getHookManager() {
        return hookManager;
    }

    public void reloadPluginFiles() {
        reloadConfig();
        messagesManager.reload();
        startScanTask();
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
        getLogger().info("Author: ZyveraSystems, GreenFPS");
        getLogger().info("EconomyTracker is active.");
        getLogger().info("Economy-Provider: " + economy.getName());
        getLogger().info("Database type: " + databaseManager.getDatabaseType());
    }
}