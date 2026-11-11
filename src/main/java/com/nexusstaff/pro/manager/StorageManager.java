package com.nexusstaff.pro.manager;

import com.nexusstaff.pro.NexusStaffPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class StorageManager implements AutoCloseable {
    private final NexusStaffPlugin plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "NexusStaff-Storage");
        thread.setDaemon(true);
        return thread;
    });
    private volatile HikariDataSource dataSource;

    public StorageManager(NexusStaffPlugin plugin) {
        this.plugin = plugin;
        HikariConfig config = new HikariConfig();
        config.setPoolName("NexusStaff-Pool");
        config.setMaximumPoolSize(Math.max(2, plugin.getConfig().getInt("storage.pool-size", 4)));
        if (plugin.getConfig().getString("storage.type", "sqlite").equalsIgnoreCase("mysql")) {
            String host = plugin.getConfig().getString("storage.host", "localhost");
            int port = plugin.getConfig().getInt("storage.port", 3306);
            String database = plugin.getConfig().getString("storage.database", "nexusstaff");
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC");
            config.setUsername(plugin.getConfig().getString("storage.username", "staff"));
            config.setPassword(plugin.getConfig().getString("storage.password", ""));
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        } else {
            File database = new File(plugin.getDataFolder(), plugin.getConfig().getString("storage.sqlite-file", "data.db"));
            config.setJdbcUrl("jdbc:sqlite:" + database.getAbsolutePath());
            config.setDriverClassName("com.nexusstaff.lib.sqlite.JDBC");
            config.setMaximumPoolSize(1);
        }
        executor.execute(() -> {
            try {
                dataSource = new HikariDataSource(config);
                migrate();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not initialize storage pool", exception);
            }
        });
    }

    private void migrate() {
        HikariDataSource source = dataSource;
        if (source == null) return;
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            String id = source.getJdbcUrl().startsWith("jdbc:sqlite:") ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "INTEGER PRIMARY KEY AUTO_INCREMENT";
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS punishments (id " + id + ", uuid VARCHAR(36) NOT NULL, staff_uuid VARCHAR(36) NOT NULL, type VARCHAR(24) NOT NULL, reason TEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, expires_at TIMESTAMP NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS reports (id " + id + ", report_key VARCHAR(36) UNIQUE, target_uuid VARCHAR(36) NOT NULL, reporter_uuid VARCHAR(36) NOT NULL, reason TEXT NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'OPEN', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            try {
                statement.executeUpdate("ALTER TABLE reports ADD COLUMN report_key VARCHAR(36)");
            } catch (SQLException ignored) {
            }
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS ac_flags (id " + id + ", player_uuid VARCHAR(36) NOT NULL, source VARCHAR(24) NOT NULL, check_name VARCHAR(128) NOT NULL, vl DOUBLE NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not apply storage schema migration", exception);
        }
    }

    public void executeAsync(SqlWork work) {
        executor.execute(() -> {
            HikariDataSource source = dataSource;
            if (source == null) return;
            try (Connection connection = source.getConnection()) {
                work.run(connection);
            } catch (SQLException exception) {
                plugin.getLogger().log(Level.WARNING, "Asynchronous storage operation failed", exception);
            }
        });
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        HikariDataSource source = dataSource;
        if (source != null) source.close();
    }

    @FunctionalInterface
    public interface SqlWork {
        void run(Connection connection) throws SQLException;
    }
}