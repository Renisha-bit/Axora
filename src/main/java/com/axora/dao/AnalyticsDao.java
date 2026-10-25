package com.axora.dao;

import com.axora.model.Transaction;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only aggregations over transactions used by budgets and reports.
 * Only the transactions table is read, so transfers can never leak into these figures.
 * accountId / categoryId null = no restriction.
 */
public interface AnalyticsDao {
    long sumCents(Transaction.Type type, Long categoryId, Long accountId, LocalDate from, LocalDate to);
    List<CategoryTotal> expenseByCategory(Long accountId, LocalDate from, LocalDate to);
    /** One entry per calendar month that has data inside the range. */
    List<MonthlyTotal> monthlyTotals(Long accountId, LocalDate from, LocalDate to);
}
