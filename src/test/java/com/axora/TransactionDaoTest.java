package com.axora;

import com.axora.dao.TransactionFilter;
import com.axora.dao.sqlite.DatabaseManager;
import com.axora.dao.sqlite.SQLiteTransactionDao;
import com.axora.model.Transaction;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SQLiteTransactionDao using an in-memory SQLite database.
 * Each test gets a fresh DB via reflection to swap the connection in DatabaseManager.
 */
class TransactionDaoTest {

    private SQLiteTransactionDao dao;

    @BeforeEach
    void setUp() throws Exception {
        // Real schema (all migrations) in a fresh in-memory database
        TestDb.withLegacyCategories();
        dao = new SQLiteTransactionDao();
    }

    // ----------------------------------------------------------------
    // Insert / FindById
    // ----------------------------------------------------------------

    @Test
    void insertAndFindById() {
        Transaction tx = buildTx(Transaction.Type.EXPENSE, 5000L, 1L, LocalDate.of(2026, 10, 1));
        long id = dao.insert(tx);
        assertTrue(id > 0);

        var found = dao.findById(id);
        assertTrue(found.isPresent());
        assertEquals(5000L, found.get().getAmountCents());
        assertEquals(Transaction.Type.EXPENSE, found.get().getType());
    }

    @Test
    void insertMultipleAndFindAll() {
        dao.insert(buildTx(Transaction.Type.EXPENSE, 1000L, 1L, LocalDate.of(2026, 10, 5)));
        dao.insert(buildTx(Transaction.Type.INCOME,  5000L, 2L, LocalDate.of(2026, 10, 3)));
        dao.insert(buildTx(Transaction.Type.EXPENSE, 2000L, 1L, LocalDate.of(2026, 10, 7)));

        List<Transaction> all = dao.findAll();
        assertEquals(3, all.size());
        // Newest first
        assertEquals(LocalDate.of(2026, 10, 7), all.get(0).getTxDate());
    }

    // ----------------------------------------------------------------
    // Update / Delete
    // ----------------------------------------------------------------

    @Test
    void updateChangesAmount() {
        long id = dao.insert(buildTx(Transaction.Type.EXPENSE, 1000L, 1L, LocalDate.now()));
        Transaction tx = dao.findById(id).orElseThrow();
        tx.setAmountCents(9999L);
        assertTrue(dao.update(tx));
        assertEquals(9999L, dao.findById(id).orElseThrow().getAmountCents());
    }

    @Test
    void deleteRemovesRecord() {
        long id = dao.insert(buildTx(Transaction.Type.EXPENSE, 500L, 1L, LocalDate.now()));
        assertTrue(dao.delete(id));
        assertTrue(dao.findById(id).isEmpty());
    }

    // ----------------------------------------------------------------
    // Aggregations
    // ----------------------------------------------------------------

    @Test
    void sumIncomeCents() {
        dao.insert(buildTx(Transaction.Type.INCOME, 100000L, 2L, LocalDate.of(2026, 10, 1)));
        dao.insert(buildTx(Transaction.Type.INCOME,  50000L, 2L, LocalDate.of(2026, 10, 15)));
        dao.insert(buildTx(Transaction.Type.EXPENSE, 20000L, 1L, LocalDate.of(2026, 10, 10)));

        long sum = dao.sumIncomeCents(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        assertEquals(150000L, sum);
    }

    @Test
    void sumExpenseCents() {
        dao.insert(buildTx(Transaction.Type.EXPENSE, 30000L, 1L, LocalDate.of(2026, 10, 2)));
        dao.insert(buildTx(Transaction.Type.EXPENSE, 15000L, 1L, LocalDate.of(2026, 10, 20)));

        long sum = dao.sumExpenseCents(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        assertEquals(45000L, sum);
    }

    // ----------------------------------------------------------------
    // Filter
    // ----------------------------------------------------------------

    @Test
    void filterByType() {
        dao.insert(buildTx(Transaction.Type.INCOME,  5000L, 2L, LocalDate.now()));
        dao.insert(buildTx(Transaction.Type.EXPENSE, 1000L, 1L, LocalDate.now()));
        dao.insert(buildTx(Transaction.Type.EXPENSE, 2000L, 1L, LocalDate.now()));

        TransactionFilter f = TransactionFilter.builder().type(Transaction.Type.EXPENSE).build();
        List<Transaction> result = dao.findByFilter(f);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(t -> t.getType() == Transaction.Type.EXPENSE));
    }

    @Test
    void filterByKeyword() {
        Transaction t1 = buildTx(Transaction.Type.EXPENSE, 500L, 1L, LocalDate.now());
        t1.setDescription("Lunch at cafe");
        dao.insert(t1);

        Transaction t2 = buildTx(Transaction.Type.EXPENSE, 300L, 1L, LocalDate.now());
        t2.setDescription("Bus pass");
        dao.insert(t2);

        TransactionFilter f = TransactionFilter.builder().keyword("cafe").build();
        List<Transaction> result = dao.findByFilter(f);
        assertEquals(1, result.size());
        assertTrue(result.get(0).getDescription().contains("cafe"));
    }

    @Test
    void filterByAmountRange() {
        dao.insert(buildTx(Transaction.Type.EXPENSE, 500L,  1L, LocalDate.now()));
        dao.insert(buildTx(Transaction.Type.EXPENSE, 1500L, 1L, LocalDate.now()));
        dao.insert(buildTx(Transaction.Type.EXPENSE, 5000L, 1L, LocalDate.now()));

        TransactionFilter f = TransactionFilter.builder()
            .minAmountCents(1000L).maxAmountCents(2000L).build();
        List<Transaction> result = dao.findByFilter(f);
        assertEquals(1, result.size());
        assertEquals(1500L, result.get(0).getAmountCents());
    }

    // ----------------------------------------------------------------
    // Helper
    // ----------------------------------------------------------------

    private Transaction buildTx(Transaction.Type type, long cents, long catId, LocalDate date) {
        Transaction tx = new Transaction();
        tx.setType(type);
        tx.setAmountCents(cents);
        tx.setCategoryId(catId);
        tx.setTxDate(date);
        tx.setDescription("Test");
        tx.setMerchant("");
        return tx;
    }
}
