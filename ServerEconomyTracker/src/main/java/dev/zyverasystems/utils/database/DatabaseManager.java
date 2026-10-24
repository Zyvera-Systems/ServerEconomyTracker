package dev.zyverasystems.utils.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private HikariDataSource dataSource;
    private DatabaseType databaseType;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        FileConfiguration config = plugin.getConfig();

        String typeRaw = config.getString("database.type", "SQLITE");
        this.databaseType = DatabaseType.valueOf(typeRaw.toUpperCase(Locale.ROOT));

        HikariConfig hikariConfig = new HikariConfig();

        if (databaseType == DatabaseType.MYSQL) {
            setupMySql(config, hikariConfig);
        } else {
            setupSqlite(config, hikariConfig);
        }

        hikariConfig.setAutoCommit(true);
        this.dataSource = new HikariDataSource(hikariConfig);
    }

    // MySQL setup
    private void setupMySql(FileConfiguration config, HikariConfig hikariConfig) {
        String host = config.getString("database.mysql.host", "127.0.0.1");
        int port = config.getInt("database.mysql.port", 3306);
        String database = config.getString("database.mysql.database", "economytracker");
        String username = config.getString("database.mysql.username", "root");
        String password = config.getString("database.mysql.password", "");
        int poolSize = config.getInt("database.mysql.pool-size", 10);
        long connectionTimeout = config.getLong("database.mysql.connection-timeout", 10000L);
        long maxLifetime = config.getLong("database.mysql.max-lifetime", 1800000L);

        hikariConfig.setPoolName("EconomyTracker-MySQL");
        hikariConfig.setJdbcUrl(
                "jdbc:mysql://" + host + ":" + port + "/" + database +
                        "?useSSL=false&serverTimezone=UTC&characterEncoding=utf8"
        );
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(poolSize);
        hikariConfig.setMinimumIdle(Math.min(2, poolSize));
        hikariConfig.setConnectionTimeout(connectionTimeout);
        hikariConfig.setMaxLifetime(maxLifetime);
    }

    // SQLLite setup
    private void setupSqlite(FileConfiguration config, HikariConfig hikariConfig) {
        String filePath = config.getString("database.sqlite.file", "plugins/EconomyTracker/data.db");
        int poolSize = config.getInt("database.sqlite.pool-size", 1);
        long connectionTimeout = config.getLong("database.sqlite.connection-timeout", 10000L);

        File dbFile = new File(filePath);
        File parent = dbFile.getParentFile();

        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        hikariConfig.setPoolName("EconomyTracker-SQLite");
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getPath());
        hikariConfig.setMaximumPoolSize(poolSize);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setConnectionTimeout(connectionTimeout);
    }

    public void createTables() throws SQLException {
        String playerBalancesTable = """
                CREATE TABLE IF NOT EXISTS player_balances (
                    uuid VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(64) NOT NULL,
                    balance DECIMAL(18,2) NOT NULL,
                    known_player BOOLEAN NOT NULL,
                    last_updated BIGINT NOT NULL
                )
                """;

        String economyTotalsTable = """
                CREATE TABLE IF NOT EXISTS economy_totals (
                    id INT PRIMARY KEY,
                    baseline_import_done BOOLEAN NOT NULL,
                    current_total_balance DECIMAL(18,2) NOT NULL,
                    total_sources DECIMAL(18,2) NOT NULL,
                    total_sinks DECIMAL(18,2) NOT NULL,
                    total_transfer_volume DECIMAL(18,2) NOT NULL,
                    total_net_change DECIMAL(18,2) NOT NULL,
                    manual_correction DECIMAL(18,2) NOT NULL DEFAULT 0,
                    updated_at BIGINT NOT NULL
                )
                """;

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(playerBalancesTable);
            statement.executeUpdate(economyTotalsTable);

            migrateNameColumn(connection, statement);
            migrateManualCorrectionColumn(connection, statement);
        }
    }

    // Older versions have no manual_correction column yet.
    private void migrateManualCorrectionColumn(Connection connection, Statement statement) throws SQLException {
        boolean exists = false;
        for (String tableName : new String[] {"economy_totals", "ECONOMY_TOTALS"}) {
            try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, tableName, null)) {
                while (columns.next()) {
                    if (columns.getString("COLUMN_NAME").equalsIgnoreCase("manual_correction")) exists = true;
                }
            }
        }
        if (exists) return;

        statement.executeUpdate("ALTER TABLE economy_totals ADD COLUMN manual_correction DECIMAL(18,2) NOT NULL DEFAULT 0");
        plugin.getLogger().info("Added column economy_totals.manual_correction.");
    }

    // Older versions created the name column as VARCHAR(16). MySQL rejects longer names
    // (offline-mode / Bedrock prefixes), so widen it. SQLite ignores VARCHAR lengths.
    private void migrateNameColumn(Connection connection, Statement statement) throws SQLException {
        if (databaseType != DatabaseType.MYSQL) return;

        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "player_balances", "name")) {
            if (columns.next() && columns.getInt("COLUMN_SIZE") < PlayerBalanceFunc.MAX_NAME_LENGTH) {
                statement.executeUpdate("ALTER TABLE player_balances MODIFY name VARCHAR(" + PlayerBalanceFunc.MAX_NAME_LENGTH + ") NOT NULL");
                plugin.getLogger().info("Widened player_balances.name to VARCHAR(" + PlayerBalanceFunc.MAX_NAME_LENGTH + ").");
            }
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("DataSource has not been initialized yet.");
        }
        return dataSource.getConnection();
    }

    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}