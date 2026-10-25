package com.axora.dao.sqlite;

import com.axora.dao.BudgetDao;
import com.axora.model.Budget;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class SQLiteBudgetDao implements BudgetDao {

    private static final String SELECT = """
        SELECT b.*, c.name AS cat_name FROM budgets b
        LEFT JOIN categories c ON c.id = b.category_id
        """;

    @Override public List<Budget> findAll() {
        return Jdbc.query(SELECT + " ORDER BY b.period, COALESCE(c.name,''), b.name", null, this::map);
    }
    @Override public Optional<Budget> findById(long id) {
        return Jdbc.query(SELECT + " WHERE b.id=?", ps -> ps.setLong(1, id), this::map).stream().findFirst();
    }
    @Override public long insert(Budget b) {
        return Jdbc.insert("INSERT INTO budgets(name,category_id,amount_cents,period,start_date,end_date) VALUES(?,?,?,?,?,?)",
            ps -> bind(ps, b));
    }
    @Override public boolean update(Budget b) {
        return Jdbc.update("UPDATE budgets SET name=?, category_id=?, amount_cents=?, period=?, start_date=?, end_date=? WHERE id=?",
            ps -> { bind(ps, b); ps.setLong(7, b.id()); }) > 0;
    }
    @Override public boolean delete(long id) {
        return Jdbc.update("DELETE FROM budgets WHERE id=?", ps -> ps.setLong(1, id)) > 0;
    }

    private void bind(PreparedStatement ps, Budget b) throws SQLException {
        ps.setString(1, b.name());
        Jdbc.setNullableLong(ps, 2, b.categoryId());
        ps.setLong(3, b.amountCents());
        ps.setString(4, b.period().name());
        Jdbc.setDate(ps, 5, b.period() == Budget.Period.CUSTOM ? b.startDate() : null);
        Jdbc.setDate(ps, 6, b.period() == Budget.Period.CUSTOM ? b.endDate() : null);
    }
    private Budget map(ResultSet rs) throws SQLException {
        return new Budget(rs.getLong("id"), rs.getString("name"), Jdbc.nullableLong(rs, "category_id"),
            rs.getString("cat_name"), rs.getLong("amount_cents"), Budget.Period.valueOf(rs.getString("period")),
            Jdbc.date(rs, "start_date"), Jdbc.date(rs, "end_date"));
    }
}
