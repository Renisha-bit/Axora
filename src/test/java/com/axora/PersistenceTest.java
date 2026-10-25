package com.axora;

import com.axora.dao.sqlite.DatabaseManager;
import com.axora.export.JsonBackupService;
import com.axora.model.*;
import com.axora.model.RecurringRule.Frequency;
import com.axora.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Restart persistence, v1 -> v2 upgrade of an existing database, and backup round trips. */
class PersistenceTest {

    @AfterEach
    void closeDb() { DatabaseManager.getInstance().close(); }

    private static long scalar(String sql) throws Exception {
        try (Statement st = DatabaseManager.getInstance().getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) { return rs.next() ? rs.getLong(1) : -1; }
    }

    @Test
    void allModulesSurviveClosingAndReopeningTheDatabase(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("axora.db");
        DatabaseManager db = DatabaseManager.getInstance();
        db.initialize(file);

        long food = TestDb.categoryId("Food & Dining"), salary = TestDb.categoryId("Salary");
        AccountService accounts = new AccountService();
        long cash = accounts.getAccounts().get(0).id();
        long bank = accounts.createAccount("Bank", Account.Type.BANK, "100", "INR", "d");
        accounts.createTransfer(cash, bank, "20", LocalDate.now().minusDays(1), "t");
        new TransactionService().addTransaction(Transaction.Type.INCOME, "500", salary, LocalDate.now().minusDays(3), "pay", "", cash);
        new BudgetService().create("Food", food, "300", Budget.Period.MONTHLY, null, null);
        new RecurringService().create(Transaction.Type.EXPENSE, "10", food, cash, "sub", "", Frequency.WEEKLY,
            LocalDate.now().minusDays(15), null);
        SavingsGoalService goals = new SavingsGoalService();
        long g = goals.create("Trip", "900", LocalDate.now().plusDays(60), "");
        goals.contribute(g, "100", LocalDate.now(), "a");
        goals.withdraw(g, "30", LocalDate.now(), "b");
        int txCount = new TransactionService().getAllTransactions().size();
        assertTrue(txCount >= 3, "income plus generated recurring transactions");

        db.close();
        db.initialize(file);      // "restart"

        assertEquals(2, new AccountService().getAccounts().size());
        assertEquals(1, new AccountService().getTransfers().size());
        assertEquals(1, new BudgetService().getBudgets().size());
        assertEquals(1, new RecurringService().getRules().size());
        assertEquals(txCount, new TransactionService().getAllTransactions().size());
        assertEquals(0, new RecurringService().generateDue(LocalDate.now()), "no duplicates after restart");
        assertEquals(txCount, new TransactionService().getAllTransactions().size());
        GoalProgress p = new SavingsGoalService().progress(g, LocalDate.now());
        assertEquals(70_00, p.savedCents());
        assertEquals(2, new SavingsGoalService().history(g).size());
        assertEquals(2, scalar("SELECT value FROM schema_meta WHERE key='version'"));
    }

    @Test
    void existingV1DatabaseIsUpgradedWithoutLosingOrOrphaningData(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("old.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE schema_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            st.execute("INSERT INTO schema_meta VALUES('version','1')");
            st.execute("""
                CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE,
                  type TEXT NOT NULL CHECK(type IN ('INCOME','EXPENSE','BOTH')), color TEXT, is_default INTEGER NOT NULL DEFAULT 0)""");
            st.execute("""
                CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT,
                  type TEXT NOT NULL CHECK(type IN ('INCOME','EXPENSE')), amount_cents INTEGER NOT NULL CHECK(amount_cents > 0),
                  category_id INTEGER NOT NULL REFERENCES categories(id) ON DELETE RESTRICT, tx_date TEXT NOT NULL,
                  description TEXT, merchant TEXT, created_at TEXT NOT NULL DEFAULT (datetime('now')),
                  updated_at TEXT NOT NULL DEFAULT (datetime('now')))""");
            st.execute("INSERT INTO categories(name,type) VALUES('Pay','INCOME')");
            st.execute("INSERT INTO categories(name,type) VALUES('Food','EXPENSE')");
            st.execute("INSERT INTO transactions(type,amount_cents,category_id,tx_date,description,merchant) VALUES('INCOME',100000,1,'2026-01-01','p','e')");
            st.execute("INSERT INTO transactions(type,amount_cents,category_id,tx_date,description,merchant) VALUES('EXPENSE',25050,2,'2026-01-02','l','c')");
        }

        DatabaseManager.getInstance().initialize(file);

        assertEquals(2, scalar("SELECT COUNT(*) FROM transactions"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM transactions WHERE account_id IS NULL"), "every old transaction gets an account");
        assertEquals(1, scalar("SELECT COUNT(*) FROM accounts"));
        AccountService svc = new AccountService();
        assertEquals(100000 - 25050, svc.getBalances(null).get(0).balanceCents());
        TransactionService tx = new TransactionService();
        assertEquals(svc.getAccounts().get(0).id(), tx.getAllTransactions().get(0).getAccountId());
        assertEquals("Cash", tx.getAllTransactions().get(0).getAccountName());
        assertEquals(2, scalar("SELECT value FROM schema_meta WHERE key='version'"));
        // reopening an already-migrated DB must not re-run the migration
        DatabaseManager.getInstance().close();
        assertDoesNotThrow(() -> DatabaseManager.getInstance().initialize(file));
        assertEquals(1, scalar("SELECT COUNT(*) FROM accounts"));
    }

    @Test
    void backupV2RoundTripPreservesEveryModule(@TempDir Path dir) throws Exception {
        TestDb.fresh();
        long food = TestDb.categoryId("Food & Dining"), salary = TestDb.categoryId("Salary");
        AccountService accounts = new AccountService();
        long cash = accounts.getAccounts().get(0).id();
        long bank = accounts.createAccount("Bank", Account.Type.BANK, "100", "INR", "desc");
        accounts.createTransfer(cash, bank, "20", LocalDate.now().minusDays(1), "t");
        new TransactionService().addTransaction(Transaction.Type.INCOME, "500", salary, LocalDate.now().minusDays(3), "pay", "", bank);
        new BudgetService().create("Cat", food, "300", Budget.Period.WEEKLY, null, null);
        new BudgetService().create("Cust", null, "50", Budget.Period.CUSTOM, LocalDate.of(2026,1,1), LocalDate.of(2026,2,1));
        new RecurringService().create(Transaction.Type.EXPENSE, "10", food, bank, "sub", "m", Frequency.WEEKLY,
            LocalDate.now().minusDays(15), LocalDate.now().plusDays(100));
        SavingsGoalService goals = new SavingsGoalService();
        long g = goals.create("Trip", "900", LocalDate.now().plusDays(60), "");
        goals.contribute(g, "100", LocalDate.now(), "a");

        List<AccountBalance> balancesBefore = new AccountService().getBalances(null);
        int txBefore = new TransactionService().getAllTransactions().size();
        File f = dir.resolve("b.json").toFile();
        new JsonBackupService().backup(f);

        TestDb.fresh();                                        // a different, empty database
        new JsonBackupService().restore(f);

        assertEquals(balancesBefore, new AccountService().getBalances(null));
        assertEquals(txBefore, new TransactionService().getAllTransactions().size());
        assertEquals(2, new BudgetService().getBudgets().size());
        assertEquals(1, new RecurringService().getRules().size());
        assertEquals(1, new AccountService().getTransfers().size());
        assertEquals(100_00, new SavingsGoalService().progress(
            new SavingsGoalService().progressList(LocalDate.now()).get(0).goal().id(), LocalDate.now()).savedCents());
        assertEquals(0, new RecurringService().generateDue(LocalDate.now()), "occurrence ledger restored: no re-generation");
        // new rows after restore must not collide with restored ids
        assertDoesNotThrow(() -> new AccountService().createAccount("After", Account.Type.CASH, "0", "INR", ""));
    }

    @Test
    void v1BackupFileStillRestoresAndGetsADefaultAccount(@TempDir Path dir) throws Exception {
        TestDb.fresh();
        new BudgetService().create("Old", null, "5", Budget.Period.MONTHLY, null, null);
        File f = dir.resolve("v1.json").toFile();
        Files.writeString(f.toPath(), """
            {"version":1,"exportedAt":"2026-01-01T00:00:00",
             "categories":[{"id":1,"name":"Food","type":"EXPENSE","isDefault":true},{"id":2,"name":"Pay","type":"INCOME","isDefault":true}],
             "transactions":[{"id":1,"type":"EXPENSE","amountCents":5000,"categoryId":1,"txDate":"2026-01-02","description":"x","merchant":"y"},
                             {"id":2,"type":"INCOME","amountCents":20000,"categoryId":2,"txDate":"2026-01-03","description":"z","merchant":"w"}]}
            """);
        new JsonBackupService().restore(f);

        assertEquals(2, new TransactionService().getAllTransactions().size());
        List<Account> accts = new AccountService().getAccounts();
        assertEquals(1, accts.size());
        assertEquals(15000, new AccountService().getBalances(null).get(0).balanceCents());
        assertEquals(0, scalar("SELECT COUNT(*) FROM transactions WHERE account_id IS NULL"));
        assertTrue(new BudgetService().getBudgets().isEmpty(), "restore replaces everything, including newer tables");
    }

    @Test
    void failedRestoreOfInconsistentV2BackupLeavesDataUntouched(@TempDir Path dir) throws Exception {
        TestDb.fresh();
        new AccountService().createAccount("Keep", Account.Type.BANK, "1", "INR", "");
        File f = dir.resolve("bad.json").toFile();
        Files.writeString(f.toPath(), """
            {"version":2,"categories":[{"id":1,"name":"Food","type":"EXPENSE"}],
             "accounts":[{"id":1,"name":"A","type":"BANK","currency":"INR","isDefault":true}],
             "transfers":[{"id":1,"fromAccountId":1,"toAccountId":77,"amountCents":5,"date":"2026-01-01"}],
             "transactions":[]}""");
        assertThrows(IllegalArgumentException.class, () -> new JsonBackupService().restore(f));
        assertEquals(2, new AccountService().getAccounts().size());
    }
}
