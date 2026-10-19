package com.axora.dao;

import com.axora.model.Transaction;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** DAO interface for transactions. */
public interface TransactionDao {

    long insert(Transaction tx);

    boolean update(Transaction tx);

    boolean delete(long id);

    Optional<Transaction> findById(long id);

    /** Returns all transactions, newest-first. */
    List<Transaction> findAll();

    /** Returns transactions matching the given filter. */
    List<Transaction> findByFilter(TransactionFilter filter);

    /** Sum of amount_cents for all INCOME records in the date range (inclusive). */
    long sumIncomeCents(LocalDate from, LocalDate to);

    /** Sum of amount_cents for all EXPENSE records in the date range (inclusive). */
    long sumExpenseCents(LocalDate from, LocalDate to);

    /** Category totals for EXPENSE in the date range, ordered by total descending. */
    List<CategoryTotal> expenseCategoryTotals(LocalDate from, LocalDate to);

    /** Monthly totals for income and expense in the given year. */
    List<MonthlyTotal> monthlyTotals(int year);
}
