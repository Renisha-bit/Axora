package com.axora.service;

import java.time.LocalDate;
import java.util.List;

import com.axora.dao.AccountDao;
import com.axora.dao.CategoryDao;
import com.axora.dao.CategoryTotal;
import com.axora.dao.MonthlyTotal;
import com.axora.dao.TransactionDao;
import com.axora.dao.TransactionFilter;
import com.axora.dao.sqlite.SQLiteAccountDao;
import com.axora.dao.sqlite.SQLiteCategoryDao;
import com.axora.dao.sqlite.SQLiteTransactionDao;
import com.axora.model.Category;
import com.axora.model.Transaction;
import com.axora.validation.TransactionValidator;

/**
 * Business-logic layer for transactions.
 * Controllers call only this service — no SQL here.
 */
public class TransactionService {

    private final TransactionDao txDao;
    private final CategoryDao    catDao;
    private final AccountDao     accountDao;   // null in legacy/test construction: no account validation

    /** Default constructor using SQLite implementations. */
    public TransactionService() {
        this(new SQLiteTransactionDao(), new SQLiteCategoryDao(), new SQLiteAccountDao());
    }

    /** Injectable constructor for testing. */
    public TransactionService(TransactionDao txDao, CategoryDao catDao) {
        this(txDao, catDao, null);
    }

    public TransactionService(TransactionDao txDao, CategoryDao catDao, AccountDao accountDao) {
        this.txDao  = txDao;
        this.catDao = catDao;
        this.accountDao = accountDao;
    }

    // ----------------------------------------------------------------
    // Transactions
    // ----------------------------------------------------------------

    /**
     * Validates and saves a new transaction.
     * @return id of inserted record
     * @throws IllegalArgumentException if validation fails
     */
    public long addTransaction(Transaction.Type type, String amountText, long categoryId,
                               LocalDate date, String description, String merchant) {
        return addTransaction(type, amountText, categoryId, date, description, merchant, 0L);
    }

    /** @param accountId account to book against; 0 = the default account */
    public long addTransaction(Transaction.Type type,
                               String amountText,
                               long categoryId,
                               LocalDate date,
                               String description,
                               String merchant,
                               long accountId) {
        List<String> errors = TransactionValidator.validate(type, amountText, categoryId, date, description);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        validateCategoryType(categoryId, type);

        Transaction tx = new Transaction();
        tx.setAccountId(accountId);
        tx.setType(type);
        tx.setAmountCents(TransactionValidator.parseCents(amountText));
        tx.setCategoryId(categoryId);
        tx.setTxDate(date);
        tx.setDescription(description == null ? "" : description.trim());
        tx.setMerchant(merchant == null ? "" : merchant.trim());
        return txDao.insert(tx);
    }

    /**
     * Validates and updates an existing transaction.
     */
    public void updateTransaction(long id, Transaction.Type type, String amountText, long categoryId,
                                  LocalDate date, String description, String merchant) {
        updateTransaction(id, type, amountText, categoryId, date, description, merchant, 0L);
    }

    /** @param accountId new account; 0 keeps the transaction's current account */
    public void updateTransaction(long id,
                                  Transaction.Type type,
                                  String amountText,
                                  long categoryId,
                                  LocalDate date,
                                  String description,
                                  String merchant,
                                  long accountId) {
        List<String> errors = TransactionValidator.validate(type, amountText, categoryId, date, description);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        validateCategoryType(categoryId, type);

        Transaction tx = txDao.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + id));
        if (accountId > 0) tx.setAccountId(accountId);
        tx.setType(type);
        tx.setAmountCents(TransactionValidator.parseCents(amountText));
        tx.setCategoryId(categoryId);
        tx.setTxDate(date);
        tx.setDescription(description == null ? "" : description.trim());
        tx.setMerchant(merchant == null ? "" : merchant.trim());
        txDao.update(tx);
    }

    private void validateCategoryType(long categoryId, Transaction.Type type) {
        Category category = catDao.findById(categoryId)
            .orElseThrow(() -> new IllegalArgumentException("Please select a valid category."));
        Category.CategoryType expectedType = type == Transaction.Type.INCOME
            ? Category.CategoryType.INCOME : Category.CategoryType.EXPENSE;
        if (category.getType() != Category.CategoryType.BOTH
                && category.getType() != expectedType) {
            throw new IllegalArgumentException(
                "Selected category is not valid for this transaction type.");
        }
    }

    public void deleteTransaction(long id) {
        txDao.delete(id);
    }

    public List<Transaction> getAllTransactions() {
        return txDao.findAll();
    }

    public List<Transaction> getFiltered(TransactionFilter filter) {
        return txDao.findByFilter(filter);
    }

    // ----------------------------------------------------------------
    // Dashboard calculations
    // ----------------------------------------------------------------

    public long totalIncomeCents(LocalDate from, LocalDate to) {
        return txDao.sumIncomeCents(from, to);
    }

    public long totalExpenseCents(LocalDate from, LocalDate to) {
        return txDao.sumExpenseCents(from, to);
    }

    public long netCashFlowCents(LocalDate from, LocalDate to) {
        return totalIncomeCents(from, to) - totalExpenseCents(from, to);
    }

    public List<CategoryTotal> categoryExpenseTotals(LocalDate from, LocalDate to) {
        return txDao.expenseCategoryTotals(from, to);
    }

    public List<MonthlyTotal> monthlyTotals(int year) {
        return txDao.monthlyTotals(year);
    }

    // ----------------------------------------------------------------
    // Categories
    // ----------------------------------------------------------------

    public List<Category> getAllCategories() {
        return catDao.findAll();
    }

    public List<Category> getCategoriesForType(Transaction.Type type) {
        Category.CategoryType ct = type == Transaction.Type.INCOME
            ? Category.CategoryType.INCOME : Category.CategoryType.EXPENSE;
        return catDao.findByType(ct);
    }
}
