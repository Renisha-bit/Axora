package com.axora.dao.sqlite;

import com.axora.dao.AnalyticsDao;
import com.axora.dao.CategoryTotal;
import com.axora.dao.MonthlyTotal;
import com.axora.model.Transaction;

import java.time.LocalDate;
import java.util.List;

public class SQLiteAnalyticsDao implements AnalyticsDao {

    @Override
    public long sumCents(Transaction.Type type, Long categoryId, Long accountId, LocalDate from, LocalDate to) {
        return Jdbc.scalar("""
            SELECT COALESCE(SUM(amount_cents),0) FROM transactions
            WHERE type=? AND tx_date BETWEEN ? AND ?
              AND (? IS NULL OR category_id=?) AND (? IS NULL OR account_id=?)""", ps -> {
            ps.setString(1, type.name()); ps.setString(2, from.toString()); ps.setString(3, to.toString());
            Jdbc.setNullableLong(ps, 4, categoryId); Jdbc.setNullableLong(ps, 5, categoryId);
            Jdbc.setNullableLong(ps, 6, accountId);  Jdbc.setNullableLong(ps, 7, accountId);
        });
    }

    @Override
    public List<CategoryTotal> expenseByCategory(Long accountId, LocalDate from, LocalDate to) {
        return Jdbc.query("""
            SELECT t.category_id, c.name AS cat_name, SUM(t.amount_cents) AS total
            FROM transactions t JOIN categories c ON c.id = t.category_id
            WHERE t.type='EXPENSE' AND t.tx_date BETWEEN ? AND ? AND (? IS NULL OR t.account_id=?)
            GROUP BY t.category_id ORDER BY total DESC""", ps -> {
            ps.setString(1, from.toString()); ps.setString(2, to.toString());
            Jdbc.setNullableLong(ps, 3, accountId); Jdbc.setNullableLong(ps, 4, accountId);
        }, rs -> new CategoryTotal(rs.getLong("category_id"), rs.getString("cat_name"), rs.getLong("total")));
    }

    @Override
    public List<MonthlyTotal> monthlyTotals(Long accountId, LocalDate from, LocalDate to) {
        return Jdbc.query("""
            SELECT CAST(strftime('%Y', tx_date) AS INTEGER) AS yr, CAST(strftime('%m', tx_date) AS INTEGER) AS mo,
                   SUM(CASE WHEN type='INCOME'  THEN amount_cents ELSE 0 END) AS inc,
                   SUM(CASE WHEN type='EXPENSE' THEN amount_cents ELSE 0 END) AS exp
            FROM transactions
            WHERE tx_date BETWEEN ? AND ? AND (? IS NULL OR account_id=?)
            GROUP BY yr, mo ORDER BY yr, mo""", ps -> {
            ps.setString(1, from.toString()); ps.setString(2, to.toString());
            Jdbc.setNullableLong(ps, 3, accountId); Jdbc.setNullableLong(ps, 4, accountId);
        }, rs -> new MonthlyTotal(rs.getInt("yr"), rs.getInt("mo"), rs.getLong("inc"), rs.getLong("exp")));
    }
}
