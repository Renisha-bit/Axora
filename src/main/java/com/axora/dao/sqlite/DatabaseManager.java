package com.axora.dao.sqlite;

import com.axora.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the SQLite connection and schema migrations.
 * Singleton – call getInstance().initialize() once at startup.
 */
public class DatabaseManager {

    private static final DatabaseManager INSTANCE = new DatabaseManager();
    private Connection connection;

    private DatabaseManager() {}

    public static DatabaseManager getInstance() { return INSTANCE; }

    public synchronized void initialize() {
        try {
            String url = "jdbc:sqlite:" + AppConfig.databasePath().toAbsolutePath();
            connection = DriverManager.getConnection(url);
            // Enable foreign keys on every connection
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA journal_mode = WAL");
            }
            runMigrations();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to open Axora database", e);
        }
    }

    public synchronized Connection getConnection() {
        if (connection == null) {
            throw new IllegalStateException("DatabaseManager not initialized. Call initialize() first.");
        }
        return connection;
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}
    }

    // -----------------------------------------------------------------
    // Schema migrations (append-only)
    // -----------------------------------------------------------------

    private void runMigrations() throws SQLException {
        createMetaTable();
        int version = getSchemaVersion();
        if (version < 1) { migration1(); setSchemaVersion(1); }
        // Future: if (version < 2) { migration2(); setSchemaVersion(2); }
    }

    private void createMetaTable() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS schema_meta (
                    key   TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
            """);
        }
    }

    private int getSchemaVersion() throws SQLException {
        try (var ps = connection.prepareStatement(
                "SELECT value FROM schema_meta WHERE key = 'version'");
             var rs = ps.executeQuery()) {
            return rs.next() ? Integer.parseInt(rs.getString("value")) : 0;
        }
    }

    private void setSchemaVersion(int v) throws SQLException {
        try (var ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO schema_meta(key, value) VALUES('version', ?)")) {
            ps.setString(1, String.valueOf(v));
            ps.executeUpdate();
        }
    }

    /** Migration 1: Core schema — categories and transactions. */
    private void migration1() throws SQLException {
        try (Statement st = connection.createStatement()) {
            // Categories
            st.execute("""
                CREATE TABLE IF NOT EXISTS categories (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    name        TEXT NOT NULL UNIQUE,
                    type        TEXT NOT NULL CHECK(type IN ('INCOME','EXPENSE','BOTH')),
                    color       TEXT,
                    is_default  INTEGER NOT NULL DEFAULT 0
                )
            """);

            // Transactions
            st.execute("""
                CREATE TABLE IF NOT EXISTS transactions (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    type         TEXT NOT NULL CHECK(type IN ('INCOME','EXPENSE')),
                    amount_cents INTEGER NOT NULL CHECK(amount_cents > 0),
                    category_id  INTEGER NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
                    tx_date      TEXT NOT NULL,
                    description  TEXT,
                    merchant     TEXT,
                    created_at   TEXT NOT NULL DEFAULT (datetime('now')),
                    updated_at   TEXT NOT NULL DEFAULT (datetime('now'))
                )
            """);

            // Indexes for common query patterns
            st.execute("CREATE INDEX IF NOT EXISTS idx_tx_date         ON transactions(tx_date)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_tx_type         ON transactions(type)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_tx_category     ON transactions(category_id)");

            // Seed default categories
            seedCategories(st);
        }
    }

    private void seedCategories(Statement st) throws SQLException {
        String[][] income = {
            {"Salary", "INCOME"}, {"Freelance", "INCOME"}, {"Business", "INCOME"},
            {"Investment Returns", "INCOME"}, {"Gift", "INCOME"}, {"Other Income", "INCOME"}
        };
        String[][] expense = {
            {"Food & Dining", "EXPENSE"}, {"Transportation", "EXPENSE"}, {"Shopping", "EXPENSE"},
            {"Utilities", "EXPENSE"}, {"Entertainment", "EXPENSE"}, {"Healthcare", "EXPENSE"},
            {"Education", "EXPENSE"}, {"Rent / Housing", "EXPENSE"}, {"Travel", "EXPENSE"},
            {"Personal Care", "EXPENSE"}, {"Subscriptions", "EXPENSE"}, {"Other Expense", "EXPENSE"}
        };
        for (String[] c : income)  insertCategory(st, c[0], c[1]);
        for (String[] c : expense) insertCategory(st, c[0], c[1]);
    }

    private void insertCategory(Statement st, String name, String type) throws SQLException {
        st.execute("INSERT OR IGNORE INTO categories(name, type, is_default) VALUES('"
                   + name.replace("'", "''") + "','" + type + "',1)");
    }
}
