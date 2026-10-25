package com.axora.export;

import com.axora.dao.sqlite.DatabaseManager;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
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
 *   "version": 2,
 *   "exportedAt": "2026-10-19T12:00:00",
 *   "categories": [...],
 *   "transactions": [...],
 *   "accounts", "transfers", "budgets", "recurringRules", "recurringOccurrences",
 *   "savingsGoals", "goalEntries"   (added in v2; absent in v1 files)
 * }
 *
 * Restore is atomic: wrapped in a single DB transaction.
 * If any step fails, the database is unchanged.
 */
public class JsonBackupService {

    /** Current format. Version 1 (transactions/categories only) is still accepted on restore. */
    private static final int BACKUP_VERSION = 2;

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
                long acc = rs.getLong("account_id");
                r.accountId = rs.wasNull() ? null : acc;
                long rule = rs.getLong("recurring_rule_id");
                r.recurringRuleId = rs.wasNull() ? null : rule;
                String ca = rs.getString("created_at");
                if (ca != null) r.createdAt = LocalDateTime.parse(ca.replace(' ', 'T'));
                doc.transactions.add(r);
            }
        }

        readV2Tables(conn, doc);
        mapper.writeValue(target, doc);
    }

    private void readV2Tables(Connection conn, BackupDocument doc) throws SQLException {
        try (Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT * FROM accounts ORDER BY id")) {
                while (rs.next()) {
                    AccountRecord r = new AccountRecord();
                    r.id = rs.getLong("id"); r.name = rs.getString("name"); r.type = rs.getString("type");
                    r.openingBalanceCents = rs.getLong("opening_balance_cents");
                    r.currency = rs.getString("currency"); r.description = rs.getString("description");
                    r.isDefault = rs.getInt("is_default") == 1;
                    doc.accounts.add(r);
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT * FROM transfers ORDER BY id")) {
                while (rs.next()) {
                    TransferRecord r = new TransferRecord();
                    r.id = rs.getLong("id"); r.fromAccountId = rs.getLong("from_account_id");
                    r.toAccountId = rs.getLong("to_account_id"); r.amountCents = rs.getLong("amount_cents");
                    r.date = LocalDate.parse(rs.getString("transfer_date")); r.description = rs.getString("description");
                    doc.transfers.add(r);
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT * FROM budgets ORDER BY id")) {
                while (rs.next()) {
                    BudgetRecord r = new BudgetRecord();
                    r.id = rs.getLong("id"); r.name = rs.getString("name");
                    long c = rs.getLong("category_id"); r.categoryId = rs.wasNull() ? null : c;
                    r.amountCents = rs.getLong("amount_cents"); r.period = rs.getString("period");
                    String sd = rs.getString("start_date"), ed = rs.getString("end_date");
                    r.startDate = sd == null ? null : LocalDate.parse(sd); r.endDate = ed == null ? null : LocalDate.parse(ed);
                    doc.budgets.add(r);
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT * FROM recurring_rules ORDER BY id")) {
                while (rs.next()) {
                    RecurringRuleRecord r = new RecurringRuleRecord();
                    r.id = rs.getLong("id"); r.type = rs.getString("type"); r.amountCents = rs.getLong("amount_cents");
                    r.categoryId = rs.getLong("category_id"); r.accountId = rs.getLong("account_id");
                    r.description = rs.getString("description"); r.merchant = rs.getString("merchant");
                    r.frequency = rs.getString("frequency"); r.startDate = LocalDate.parse(rs.getString("start_date"));
                    String ed = rs.getString("end_date"); r.endDate = ed == null ? null : LocalDate.parse(ed);
                    r.generateFrom = LocalDate.parse(rs.getString("generate_from")); r.paused = rs.getInt("paused") == 1;
                    doc.recurringRules.add(r);
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT * FROM recurring_occurrences ORDER BY rule_id, occurrence_date")) {
                while (rs.next()) {
                    OccurrenceRecord r = new OccurrenceRecord();
                    r.ruleId = rs.getLong("rule_id"); r.occurrenceDate = LocalDate.parse(rs.getString("occurrence_date"));
                    long t = rs.getLong("transaction_id"); r.transactionId = rs.wasNull() ? null : t;
                    doc.recurringOccurrences.add(r);
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT * FROM savings_goals ORDER BY id")) {
                while (rs.next()) {
                    GoalRecord r = new GoalRecord();
                    r.id = rs.getLong("id"); r.name = rs.getString("name"); r.targetCents = rs.getLong("target_cents");
                    r.targetDate = LocalDate.parse(rs.getString("target_date")); r.description = rs.getString("description");
                    r.startDate = LocalDate.parse(rs.getString("start_date"));
                    doc.savingsGoals.add(r);
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT * FROM goal_entries ORDER BY id")) {
                while (rs.next()) {
                    GoalEntryRecord r = new GoalEntryRecord();
                    r.id = rs.getLong("id"); r.goalId = rs.getLong("goal_id"); r.type = rs.getString("entry_type");
                    r.amountCents = rs.getLong("amount_cents"); r.date = LocalDate.parse(rs.getString("entry_date"));
                    r.note = rs.getString("note");
                    doc.goalEntries.add(r);
                }
            }
        }
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
                st.execute("DELETE FROM goal_entries");
                st.execute("DELETE FROM savings_goals");
                st.execute("DELETE FROM budgets");
                st.execute("DELETE FROM recurring_occurrences");
                st.execute("DELETE FROM transfers");
                st.execute("DELETE FROM transactions");
                st.execute("DELETE FROM recurring_rules");
                st.execute("DELETE FROM accounts");
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

            // 3b. Accounts. A v1 backup has none: recreate the default account so every
            //     transaction still belongs to an account (same as the v2 migration does).
            boolean synthesizedDefault = doc.accounts.isEmpty();
            if (synthesizedDefault) {
                AccountRecord d = new AccountRecord();
                d.id = 1; d.name = "Cash"; d.type = "CASH"; d.currency = "INR";
                d.description = "Default account"; d.isDefault = true;
                doc.accounts.add(d);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO accounts(id,name,type,opening_balance_cents,currency,description,is_default) VALUES(?,?,?,?,?,?,?)")) {
                for (AccountRecord r : doc.accounts) {
                    ps.setLong(1, r.id); ps.setString(2, r.name); ps.setString(3, r.type);
                    ps.setLong(4, r.openingBalanceCents); ps.setString(5, r.currency == null ? "INR" : r.currency);
                    ps.setString(6, r.description); ps.setInt(7, r.isDefault ? 1 : 0);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            final long defaultAccountId = doc.accounts.stream().filter(a -> a.isDefault).mapToLong(a -> a.id)
                .findFirst().orElse(doc.accounts.get(0).id);

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO recurring_rules(id,type,amount_cents,category_id,account_id,description,merchant,frequency,start_date,end_date,generate_from,paused) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")) {
                for (RecurringRuleRecord r : doc.recurringRules) {
                    ps.setLong(1, r.id); ps.setString(2, r.type); ps.setLong(3, r.amountCents);
                    ps.setLong(4, r.categoryId); ps.setLong(5, r.accountId); ps.setString(6, r.description);
                    ps.setString(7, r.merchant); ps.setString(8, r.frequency); ps.setString(9, r.startDate.toString());
                    ps.setString(10, r.endDate == null ? null : r.endDate.toString());
                    ps.setString(11, (r.generateFrom == null ? r.startDate : r.generateFrom).toString());
                    ps.setInt(12, r.paused ? 1 : 0);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // 4. Restore transactions
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO transactions(id,type,amount_cents,category_id,tx_date,description,merchant,account_id,recurring_rule_id,created_at,updated_at)" +
                    " VALUES(?,?,?,?,?,?,?,?,?,datetime('now'),datetime('now'))")) {
                for (TransactionRecord r : doc.transactions) {
                    ps.setLong(1, r.id);
                    ps.setString(2, r.type);
                    ps.setLong(3, r.amountCents);
                    ps.setLong(4, r.categoryId);
                    ps.setString(5, r.txDate.toString());
                    ps.setString(6, r.description);
                    ps.setString(7, r.merchant);
                    ps.setLong(8, r.accountId == null ? defaultAccountId : r.accountId);
                    if (r.recurringRuleId == null) ps.setNull(9, java.sql.Types.INTEGER); else ps.setLong(9, r.recurringRuleId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // 4b. Remaining v2 tables
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO recurring_occurrences(rule_id,occurrence_date,transaction_id) VALUES(?,?,?)")) {
                for (OccurrenceRecord r : doc.recurringOccurrences) {
                    ps.setLong(1, r.ruleId); ps.setString(2, r.occurrenceDate.toString());
                    if (r.transactionId == null) ps.setNull(3, java.sql.Types.INTEGER); else ps.setLong(3, r.transactionId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO transfers(id,from_account_id,to_account_id,amount_cents,transfer_date,description) VALUES(?,?,?,?,?,?)")) {
                for (TransferRecord r : doc.transfers) {
                    ps.setLong(1, r.id); ps.setLong(2, r.fromAccountId); ps.setLong(3, r.toAccountId);
                    ps.setLong(4, r.amountCents); ps.setString(5, r.date.toString()); ps.setString(6, r.description);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO budgets(id,name,category_id,amount_cents,period,start_date,end_date) VALUES(?,?,?,?,?,?,?)")) {
                for (BudgetRecord r : doc.budgets) {
                    ps.setLong(1, r.id); ps.setString(2, r.name);
                    if (r.categoryId == null) ps.setNull(3, java.sql.Types.INTEGER); else ps.setLong(3, r.categoryId);
                    ps.setLong(4, r.amountCents); ps.setString(5, r.period);
                    ps.setString(6, r.startDate == null ? null : r.startDate.toString());
                    ps.setString(7, r.endDate == null ? null : r.endDate.toString());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO savings_goals(id,name,target_cents,target_date,description,start_date) VALUES(?,?,?,?,?,?)")) {
                for (GoalRecord r : doc.savingsGoals) {
                    ps.setLong(1, r.id); ps.setString(2, r.name); ps.setLong(3, r.targetCents);
                    ps.setString(4, r.targetDate.toString()); ps.setString(5, r.description);
                    ps.setString(6, r.startDate.toString());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO goal_entries(id,goal_id,entry_type,amount_cents,entry_date,note) VALUES(?,?,?,?,?,?)")) {
                for (GoalEntryRecord r : doc.goalEntries) {
                    ps.setLong(1, r.id); ps.setLong(2, r.goalId); ps.setString(3, r.type);
                    ps.setLong(4, r.amountCents); ps.setString(5, r.date.toString()); ps.setString(6, r.note);
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
                seq(st, "accounts", doc.accounts.stream().mapToLong(a -> a.id).max().orElse(0));
                seq(st, "transfers", doc.transfers.stream().mapToLong(a -> a.id).max().orElse(0));
                seq(st, "budgets", doc.budgets.stream().mapToLong(a -> a.id).max().orElse(0));
                seq(st, "recurring_rules", doc.recurringRules.stream().mapToLong(a -> a.id).max().orElse(0));
                seq(st, "savings_goals", doc.savingsGoals.stream().mapToLong(a -> a.id).max().orElse(0));
                seq(st, "goal_entries", doc.goalEntries.stream().mapToLong(a -> a.id).max().orElse(0));
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

    private static void seq(Statement st, String table, long max) throws SQLException {
        if (max > 0) st.execute("INSERT OR REPLACE INTO sqlite_sequence(name,seq) VALUES('" + table + "'," + max + ")");
    }

    // ----------------------------------------------------------------
    // Validation
    // ----------------------------------------------------------------

    private void validate(BackupDocument doc) {
        if (doc == null) throw new IllegalArgumentException("Backup file is empty or invalid JSON.");
        if (doc.version < 1 || doc.version > BACKUP_VERSION) {
            throw new IllegalArgumentException(
                "Unsupported backup version: " + doc.version + ". Supported: 1 to " + BACKUP_VERSION);
        }
        if (doc.categories == null || doc.categories.isEmpty()) {
            throw new IllegalArgumentException("Backup contains no categories.");
        }
        for (AccountRecord a : doc.accounts) {
            if (a.name == null || a.name.isBlank() || a.type == null)
                throw new IllegalArgumentException("Invalid account id=" + a.id);
        }
        for (TransferRecord t : doc.transfers) {
            boolean ok = doc.accounts.stream().anyMatch(a -> a.id == t.fromAccountId)
                      && doc.accounts.stream().anyMatch(a -> a.id == t.toAccountId);
            if (!ok || t.amountCents <= 0 || t.date == null)
                throw new IllegalArgumentException("Invalid transfer id=" + t.id);
        }
        for (RecurringRuleRecord r : doc.recurringRules) {
            if (r.startDate == null || r.amountCents <= 0 || r.frequency == null
                    || doc.accounts.stream().noneMatch(a -> a.id == r.accountId)
                    || doc.categories.stream().noneMatch(c -> c.id == r.categoryId))
                throw new IllegalArgumentException("Invalid recurring rule id=" + r.id);
        }
        for (OccurrenceRecord o : doc.recurringOccurrences) {
            if (o.occurrenceDate == null || doc.recurringRules.stream().noneMatch(r -> r.id == o.ruleId))
                throw new IllegalArgumentException("Invalid recurring occurrence for rule " + o.ruleId);
        }
        for (BudgetRecord b : doc.budgets) {
            if (b.amountCents <= 0 || b.period == null
                    || (b.categoryId != null && doc.categories.stream().noneMatch(c -> c.id == b.categoryId)))
                throw new IllegalArgumentException("Invalid budget id=" + b.id);
        }
        for (GoalRecord g : doc.savingsGoals) {
            if (g.targetCents <= 0 || g.targetDate == null || g.startDate == null)
                throw new IllegalArgumentException("Invalid savings goal id=" + g.id);
        }
        for (GoalEntryRecord e : doc.goalEntries) {
            if (e.amountCents <= 0 || e.date == null || doc.savingsGoals.stream().noneMatch(g -> g.id == e.goalId))
                throw new IllegalArgumentException("Invalid goal entry id=" + e.id);
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
            if (r.accountId != null && doc.accounts.stream().noneMatch(a -> a.id == r.accountId)
                    && !doc.accounts.isEmpty()) {
                throw new IllegalArgumentException(
                    "Transaction id=" + r.id + " references unknown account_id=" + r.accountId);
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
        public List<AccountRecord>     accounts     = new ArrayList<>();
        public List<TransferRecord>    transfers    = new ArrayList<>();
        public List<BudgetRecord>      budgets      = new ArrayList<>();
        public List<RecurringRuleRecord> recurringRules = new ArrayList<>();
        public List<OccurrenceRecord>  recurringOccurrences = new ArrayList<>();
        public List<GoalRecord>        savingsGoals = new ArrayList<>();
        public List<GoalEntryRecord>   goalEntries  = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AccountRecord {
        public long id; public String name, type, currency, description;
        public long openingBalanceCents; public boolean isDefault;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransferRecord {
        public long id, fromAccountId, toAccountId, amountCents; public LocalDate date; public String description;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BudgetRecord {
        public long id; public String name, period; public Long categoryId; public long amountCents;
        public LocalDate startDate, endDate;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecurringRuleRecord {
        public long id, amountCents, categoryId, accountId; public String type, description, merchant, frequency;
        public LocalDate startDate, endDate, generateFrom; public boolean paused;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OccurrenceRecord {
        public long ruleId; public LocalDate occurrenceDate; public Long transactionId;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GoalRecord {
        public long id, targetCents; public String name, description; public LocalDate targetDate, startDate;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GoalEntryRecord {
        public long id, goalId, amountCents; public String type, note; public LocalDate date;
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
        public Long         accountId;        // null in v1 backups
        public Long         recurringRuleId;
    }
}
