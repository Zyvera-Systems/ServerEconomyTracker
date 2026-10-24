package dev.zyverasystems.hooks.plugins;

import dev.zyverasystems.ServerEconomyTracker;
import dev.zyverasystems.hooks.EconomyWealthHook;
import me.yic.xconomy.api.XConomyAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Sums all XConomy non-player accounts (banks, towns). Account names come from XConomy's
// database, plus every name seen in a NonPlayerAccountEvent as a fallback.
public class XConomyHook implements EconomyWealthHook {

    private final ServerEconomyTracker plugin;
    private final File file;
    private final Set<String> knownAccounts = ConcurrentHashMap.newKeySet();
    private XConomyAPI api;
    private boolean reflectionWarningShown;

    public XConomyHook(ServerEconomyTracker plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "xconomy-accounts.yml");
        load();
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
    public BigDecimal getAccountWealth() {
        if (api == null) api = new XConomyAPI();

        // Pick up every account XConomy has stored, including ones we never saw in an event.
        List<String> storedAccounts = loadAccountsFromDatabase();
        if (storedAccounts != null) {
            boolean changed = knownAccounts.addAll(storedAccounts);
            if (changed) save();
        }

        BigDecimal total = BigDecimal.ZERO;

        for (String account : knownAccounts) {
            try {
                BigDecimal balance = api.getNonPlayerBalance(account);
                if (balance != null) total = total.add(balance);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not read XConomy account " + account + ": " + e.getMessage());
            }
        }

        return total;
    }

    // XConomy keeps non-player accounts in its own table (default "xconomynon"). The public API
    // has no way to list them, so we use XConomy's own connection via reflection. That way
    // SQLite, MySQL, MariaDB, connection pools and table suffixes all work like XConomy configured them.
    // Returns null if XConomy's internals changed, then only the remembered accounts are used.
    private List<String> loadAccountsFromDatabase() {
        Plugin xConomy = Bukkit.getPluginManager().getPlugin("XConomy");
        if (xConomy == null) return null;

        try {
            ClassLoader loader = xConomy.getClass().getClassLoader();
            Class<?> sqlClass = Class.forName("me.yic.xconomy.data.sql.SQL", true, loader);
            Object database = sqlClass.getField("database").get(null);
            String table = (String) sqlClass.getField("tableNonPlayerName").get(null);

            Method getConnection = database.getClass().getMethod("getConnectionAndCheck");
            Method releaseConnection = database.getClass().getMethod("closeHikariConnection", Connection.class);

            Connection connection = (Connection) getConnection.invoke(database);
            if (connection == null) return null;

            List<String> accounts = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT account FROM " + table);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    accounts.add(resultSet.getString("account"));
                }
            } finally {
                // Returns the connection to XConomy's pool; does nothing for its shared single connection.
                releaseConnection.invoke(database, connection);
            }

            return accounts;
        } catch (SQLException e) {
            // Table does not exist when XConomy has non-player accounts disabled.
            plugin.getLogger().fine("No XConomy non-player account table: " + e.getMessage());
            return new ArrayList<>();
        } catch (ReflectiveOperationException | ClassCastException e) {
            if (!reflectionWarningShown) {
                reflectionWarningShown = true;
                plugin.getLogger().warning("Could not read XConomy accounts directly (" + e.getMessage()
                        + "). Only accounts seen in transactions are counted.");
            }
            return null;
        }
    }

    public void rememberAccount(String accountName) {
        if (accountName == null || !knownAccounts.add(accountName)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    private void load() {
        if (!file.exists()) return;
        knownAccounts.addAll(YamlConfiguration.loadConfiguration(file).getStringList("accounts"));
    }

    private synchronized void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("accounts", new ArrayList<>(knownAccounts));
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save " + file.getName() + ": " + e.getMessage());
        }
    }
}
