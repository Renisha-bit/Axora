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
        initialize(AppConfig.databasePath());
    }

    /** Opens (creating if needed) the database file at the given path and migrates it. */
    public synchronized void initialize(java.nio.file.Path dbFile) {
        open("jdbc:sqlite:" + dbFile.toAbsolutePath());
    }

    /** Opens a fresh, empty in-memory database (migrated). Intended for tests. */
    public synchronized void initializeInMemory() {
        open("jdbc:sqlite::memory:");
    }

    private void open(String url) {
        close();
        try {
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

    private int txDepth = 0;

    /**
     * Runs the work atomically on the shared connection. Re-entrant: nested calls join the
     * outermost transaction. Any exception rolls the whole unit back.
     */
    public synchronized <T> T inTransaction(java.util.function.Supplier<T> work) {
        Connection c = getConnection();
        boolean outermost = txDepth == 0;
        try {
            if (outermost) c.setAutoCommit(false);
            txDepth++;
            T result = work.get();
            if (outermost) c.commit();
            return result;
        } catch (RuntimeException | Error e) {
            if (outermost) { try { c.rollback(); } catch (SQLException ignored) {} }
            throw e;
        } catch (SQLException e) {
            if (outermost) { try { c.rollback(); } catch (SQLException ignored) {} }
            throw new RuntimeException(e);
        } finally {
            txDepth--;
            if (outermost) { try { c.setAutoCommit(true); } catch (SQLException ignored) {} }
        }
    }

    public synchronized void runInTransaction(Runnable work) {
        inTransaction(() -> { work.run(); return null; });
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
        connection = null;
        txDepth = 0;
    }

    // -----------------------------------------------------------------
    // Schema migrations (append-only)
    // -----------------------------------------------------------------

    private void runMigrations() throws SQLException {
        createMetaTable();
        int version = getSchemaVersion();
        if (version < 1) { migration1(); setSchemaVersion(1); }
        if (version < 2) { migration2(); setSchemaVersion(2); }
        // Future: if (version < 3) { migration3(); setSchemaVersion(3); }
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

    /**
     * Migration 2: accounts, transfers, budgets, recurring rules, savings goals.
     * Adds account_id / recurring_rule_id to transactions and attaches every pre-existing
     * transaction to a default account so no historical data is orphaned.
     * Runs atomically: either the whole migration applies or none of it.
     */
    private void migration2() throws SQLException {
        boolean oldAuto = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement st = connection.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS accounts (
                    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
                    name                  TEXT NOT NULL UNIQUE COLLATE NOCASE,
                    type                  TEXT NOT NULL CHECK(type IN ('CASH','BANK','SAVINGS','CREDIT')),
                    opening_balance_cents INTEGER NOT NULL DEFAULT 0,
                    currency              TEXT NOT NULL DEFAULT 'INR',
                    description           TEXT,
                    is_default            INTEGER NOT NULL DEFAULT 0,
                    created_at            TEXT NOT NULL DEFAULT (datetime('now'))
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS transfers (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    from_account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
                    to_account_id   INTEGER NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
                    amount_cents    INTEGER NOT NULL CHECK(amount_cents > 0),
                    transfer_date   TEXT NOT NULL,
                    description     TEXT,
                    created_at      TEXT NOT NULL DEFAULT (datetime('now')),
                    CHECK(from_account_id <> to_account_id)
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS budgets (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    name         TEXT NOT NULL,
                    category_id  INTEGER REFERENCES categories(id) ON DELETE RESTRICT,
                    amount_cents INTEGER NOT NULL CHECK(amount_cents > 0),
                    period       TEXT NOT NULL CHECK(period IN ('WEEKLY','MONTHLY','CUSTOM')),
                    start_date   TEXT,
                    end_date     TEXT,
                    created_at   TEXT NOT NULL DEFAULT (datetime('now'))
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS recurring_rules (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    type          TEXT NOT NULL CHECK(type IN ('INCOME','EXPENSE')),
                    amount_cents  INTEGER NOT NULL CHECK(amount_cents > 0),
                    category_id   INTEGER NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
                    account_id    INTEGER NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
                    description   TEXT,
                    merchant      TEXT,
                    frequency     TEXT NOT NULL CHECK(frequency IN ('DAILY','WEEKLY','MONTHLY','YEARLY')),
                    start_date    TEXT NOT NULL,
                    end_date      TEXT,
                    generate_from TEXT NOT NULL,
                    paused        INTEGER NOT NULL DEFAULT 0,
                    created_at    TEXT NOT NULL DEFAULT (datetime('now'))
                )
            """);
            // One row per (rule, scheduled date): the idempotency ledger. It survives deletion
            // of the generated transaction so a deleted occurrence is never regenerated.
            st.execute("""
                CREATE TABLE IF NOT EXISTS recurring_occurrences (
                    rule_id         INTEGER NOT NULL REFERENCES recurring_rules(id) ON DELETE CASCADE,
                    occurrence_date TEXT NOT NULL,
                    transaction_id  INTEGER REFERENCES transactions(id) ON DELETE SET NULL,
                    PRIMARY KEY (rule_id, occurrence_date)
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS savings_goals (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    name         TEXT NOT NULL,
                    target_cents INTEGER NOT NULL CHECK(target_cents > 0),
                    target_date  TEXT NOT NULL,
                    description  TEXT,
                    start_date   TEXT NOT NULL,
                    created_at   TEXT NOT NULL DEFAULT (datetime('now'))
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS goal_entries (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    goal_id      INTEGER NOT NULL REFERENCES savings_goals(id) ON DELETE CASCADE,
                    entry_type   TEXT NOT NULL CHECK(entry_type IN ('CONTRIBUTION','WITHDRAWAL')),
                    amount_cents INTEGER NOT NULL CHECK(amount_cents > 0),
                    entry_date   TEXT NOT NULL,
                    note         TEXT,
                    created_at   TEXT NOT NULL DEFAULT (datetime('now'))
                )
            """);

            st.execute("ALTER TABLE transactions ADD COLUMN account_id INTEGER REFERENCES accounts(id) ON DELETE RESTRICT");
            st.execute("ALTER TABLE transactions ADD COLUMN recurring_rule_id INTEGER REFERENCES recurring_rules(id) ON DELETE SET NULL");

            st.execute("CREATE INDEX IF NOT EXISTS idx_tx_account     ON transactions(account_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_tx_rule        ON transactions(recurring_rule_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_transfer_from  ON transfers(from_account_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_transfer_to    ON transfers(to_account_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_goal_entry     ON goal_entries(goal_id)");

            // Default account + backfill of existing transactions (backward compatibility).
            st.execute("INSERT INTO accounts(name,type,opening_balance_cents,currency,description,is_default) "
                     + "VALUES('Cash','CASH',0,'INR','Default account',1)");
            st.execute("UPDATE transactions SET account_id = (SELECT id FROM accounts WHERE is_default = 1) "
                     + "WHERE account_id IS NULL");
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(oldAuto);
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
