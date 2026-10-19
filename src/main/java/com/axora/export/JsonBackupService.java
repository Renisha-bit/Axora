package com.axora.export;

import com.axora.dao.sqlite.DatabaseManager;
import com.axora.model.Category;
import com.axora.model.Transaction;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON backup (export) and restore for the full Axora database.
 *
 * Backup format (versioned):
 * {
 *   "version": 1,
 *   "exportedAt": "2026-10-19T12:00:00",
 *   "categories": [...],
 *   "transactions": [...]
 * }
 *
 * Restore is atomic: wrapped in a single DB transaction.
 * If any step fails, the database is unchanged.
 */
public class JsonBackupService {

    private static final int BACKUP_VERSION = 1;

    private final ObjectMapper mapper;

    public JsonBackupService() {
        mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    }

    // ----------------------------------------------------------------
    // Backup (Export)
    // ----------------------------------------------------------------

    public void backup(File target) throws Exception {
        Connection conn = DatabaseManager.getInstance().getConnection();
        BackupDocument doc = new BackupDocument();
        doc.version    = BACKUP_VERSION;
        doc.exportedAt = LocalDateTime.now();

        // Export categories
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM categories ORDER BY id")) {
            while (rs.next()) {
                CategoryRecord r = new CategoryRecord();
                r.id        = rs.getLong("id");
                r.name      = rs.getString("name");
                r.type      = rs.getString("type");
                r.color     = rs.getString("color");
                r.isDefault = rs.getInt("is_default") == 1;
                doc.categories.add(r);
            }
        }

        // Export transactions
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT * FROM transactions ORDER BY id")) {
            while (rs.next()) {
                TransactionRecord r = new TransactionRecord();
                r.id          = rs.getLong("id");
                r.type        = rs.getString("type");
                r.amountCents = rs.getLong("amount_cents");
                r.categoryId  = rs.getLong("category_id");
                r.txDate      = LocalDate.parse(rs.getString("tx_date"));
                r.description = rs.getString("description");
                r.merchant    = rs.getString("merchant");
                String ca = rs.getString("created_at");
                if (ca != null) r.createdAt = LocalDateTime.parse(ca.replace(' ', 'T'));
                doc.transactions.add(r);
            }
        }

        mapper.writeValue(target, doc);
    }

    // ----------------------------------------------------------------
    // Restore (Import)
    // ----------------------------------------------------------------

    /**
     * Validates then atomically replaces all data.
     * On any error, the database is rolled back to its pre-restore state.
     *
     * @throws IllegalArgumentException if the backup file is invalid or incompatible
     * @throws Exception                on I/O or DB errors
     */
    public void restore(File source) throws Exception {
        // 1. Parse and validate
        BackupDocument doc = mapper.readValue(source, BackupDocument.class);
        validate(doc);

        Connection conn = DatabaseManager.getInstance().getConnection();
        boolean oldAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            // 2. Clear existing data (FK must be off during delete ordering)
            try (Statement st = conn.createStatement()) {
                st.execute("DELETE FROM transactions");
                st.execute("DELETE FROM categories");
            }

            // 3. Restore categories (preserve original IDs so FK refs are valid)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO categories(id,name,type,color,is_default) VALUES(?,?,?,?,?)")) {
                for (CategoryRecord r : doc.categories) {
                    ps.setLong(1, r.id);
                    ps.setString(2, r.name);
                    ps.setString(3, r.type);
                    ps.setString(4, r.color);
                    ps.setInt(5, r.isDefault ? 1 : 0);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // 4. Restore transactions
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO transactions(id,type,amount_cents,category_id,tx_date,description,merchant,created_at,updated_at)" +
                    " VALUES(?,?,?,?,?,?,?,datetime('now'),datetime('now'))")) {
                for (TransactionRecord r : doc.transactions) {
                    ps.setLong(1, r.id);
                    ps.setString(2, r.type);
                    ps.setLong(3, r.amountCents);
                    ps.setLong(4, r.categoryId);
                    ps.setString(5, r.txDate.toString());
                    ps.setString(6, r.description);
                    ps.setString(7, r.merchant);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // Reset SQLite auto-increment sequences (table may not exist in empty DBs)
            try (Statement st = conn.createStatement()) {
                long maxCat = doc.categories.stream().mapToLong(c -> c.id).max().orElse(0);
                long maxTx  = doc.transactions.stream().mapToLong(t -> t.id).max().orElse(0);
                if (maxCat > 0)
                    st.execute("INSERT OR REPLACE INTO sqlite_sequence(name,seq) VALUES('categories'," + maxCat + ")");
                if (maxTx > 0)
                    st.execute("INSERT OR REPLACE INTO sqlite_sequence(name,seq) VALUES('transactions'," + maxTx + ")");
            } catch (SQLException ignored) {
                // sqlite_sequence doesn't exist yet (no AUTOINCREMENT rows inserted) — safe to ignore
            }

            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(oldAutoCommit);
        }
    }

    // ----------------------------------------------------------------
    // Validation
    // ----------------------------------------------------------------

    private void validate(BackupDocument doc) {
        if (doc == null) throw new IllegalArgumentException("Backup file is empty or invalid JSON.");
        if (doc.version != BACKUP_VERSION) {
            throw new IllegalArgumentException(
                "Unsupported backup version: " + doc.version + ". Expected: " + BACKUP_VERSION);
        }
        if (doc.categories == null || doc.categories.isEmpty()) {
            throw new IllegalArgumentException("Backup contains no categories.");
        }
        // Validate each transaction
        for (TransactionRecord r : doc.transactions) {
            if (r.amountCents <= 0) {
                throw new IllegalArgumentException("Invalid amount_cents in transaction id=" + r.id);
            }
            if (!"INCOME".equals(r.type) && !"EXPENSE".equals(r.type)) {
                throw new IllegalArgumentException("Invalid type '" + r.type + "' in transaction id=" + r.id);
            }
            if (r.txDate == null) {
                throw new IllegalArgumentException("Missing date in transaction id=" + r.id);
            }
            // Verify category reference exists in backup
            long catId = r.categoryId;
            boolean catExists = doc.categories.stream().anyMatch(c -> c.id == catId);
            if (!catExists) {
                throw new IllegalArgumentException(
                    "Transaction id=" + r.id + " references unknown category_id=" + catId);
            }
        }
    }

    // ----------------------------------------------------------------
    // DTO records for JSON (public fields for Jackson)
    // ----------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BackupDocument {
        public int                    version;
        public LocalDateTime          exportedAt;
        public List<CategoryRecord>    categories   = new ArrayList<>();
        public List<TransactionRecord> transactions = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CategoryRecord {
        public long    id;
        public String  name;
        public String  type;
        public String  color;
        public boolean isDefault;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransactionRecord {
        public long         id;
        public String       type;
        public long         amountCents;
        public long         categoryId;
        public LocalDate    txDate;
        public String       description;
        public String       merchant;
        public LocalDateTime createdAt;
    }
}
