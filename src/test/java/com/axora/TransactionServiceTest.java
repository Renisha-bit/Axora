package com.axora;

import com.axora.dao.CategoryDao;
import com.axora.dao.CategoryTotal;
import com.axora.dao.MonthlyTotal;
import com.axora.dao.TransactionDao;
import com.axora.dao.TransactionFilter;
import com.axora.model.Category;
import com.axora.model.Transaction;
import com.axora.service.TransactionService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TransactionServiceTest {

    @Test
    void rejectsCategoryForDifferentTransactionType() {
        TestTransactionDao txDao = new TestTransactionDao();
        TestCategoryDao catDao = new TestCategoryDao(
            category(1L, Category.CategoryType.INCOME));
        TransactionService service = new TransactionService(txDao, catDao);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> service.addTransaction(Transaction.Type.EXPENSE, "10.00", 1L,
                LocalDate.now(), "Test", ""));

        assertTrue(error.getMessage().contains("not valid for this transaction type"));
        assertEquals(0, txDao.insertCount);
    }

    @Test
    void acceptsCategoryAvailableToBothTransactionTypes() {
        TestTransactionDao txDao = new TestTransactionDao();
        TestCategoryDao catDao = new TestCategoryDao(
            category(1L, Category.CategoryType.BOTH));
        TransactionService service = new TransactionService(txDao, catDao);

        long id = service.addTransaction(Transaction.Type.EXPENSE, "10.00", 1L,
            LocalDate.now(), "Test", "");

        assertEquals(1L, id);
        assertEquals(1, txDao.insertCount);
    }

    private static Category category(long id, Category.CategoryType type) {
        Category category = new Category("Test", type);
        category.setId(id);
        return category;
    }

    private static final class TestCategoryDao implements CategoryDao {
        private final Category category;

        private TestCategoryDao(Category category) {
            this.category = category;
        }

        @Override
        public List<Category> findAll() {
            return List.of(category);
        }

        @Override
        public List<Category> findByType(Category.CategoryType type) {
            return category.getType() == type || category.getType() == Category.CategoryType.BOTH
                ? List.of(category) : List.of();
        }

        @Override
        public Optional<Category> findById(long id) {
            return category.getId() == id ? Optional.of(category) : Optional.empty();
        }

        @Override
        public long insert(Category category) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean update(Category category) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean delete(long id) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TestTransactionDao implements TransactionDao {
        private int insertCount;

        @Override
        public long insert(Transaction tx) {
            insertCount++;
            return 1L;
        }

        @Override
        public boolean update(Transaction tx) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean delete(long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Transaction> findById(long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Transaction> findAll() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Transaction> findByFilter(TransactionFilter filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long sumIncomeCents(LocalDate from, LocalDate to) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long sumExpenseCents(LocalDate from, LocalDate to) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<CategoryTotal> expenseCategoryTotals(LocalDate from, LocalDate to) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<MonthlyTotal> monthlyTotals(int year) {
            throw new UnsupportedOperationException();
        }
    }
}
