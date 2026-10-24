package com.axora.dao.sqlite;

import com.axora.dao.CategoryTotal;
import com.axora.dao.MonthlyTotal;
import com.axora.dao.TransactionDao;
import com.axora.dao.TransactionFilter;
import com.axora.model.Transaction;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SQLiteTransactionDao implements TransactionDao {

    private final DatabaseManager db = DatabaseManager.getInstance();

    // ----------------------------------------------------------------
    // CRUD
    // ----------------------------------------------------------------

    @Override
    public long insert(Transaction tx) {
        String sql = """
            INSERT INTO transactions(type, amount_cents, category_id, tx_date, description, merchant,
                                     account_id, recurring_rule_id)
            VALUES(?,?,?,?,?,?,COALESCE(NULLIF(?,0),(SELECT id FROM accounts ORDER BY is_default DESC, id LIMIT 1)),NULLIF(?,0))
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, tx.getType().name());
            ps.setLong(2, tx.getAmountCents());
            ps.setLong(3, tx.getCategoryId());
            ps.setString(4, tx.getTxDate().toString());
            ps.setString(5, tx.getDescription());
            ps.setString(6, tx.getMerchant());
            ps.setLong(7, tx.getAccountId());
            ps.setLong(8, tx.getRecurringRuleId());
            ps.executeUpdate();
            // last_insert_rowid() works on every sqlite-jdbc build (getGeneratedKeys does not).
            try (Statement st = db.getConnection().createStatement();
                 ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert transaction", e);
        }
    }

    @Override
    public boolean update(Transaction tx) {
        String sql = """
            UPDATE transactions
            SET type=?, amount_cents=?, category_id=?, tx_date=?,
                description=?, merchant=?, account_id=COALESCE(NULLIF(?,0),(SELECT id FROM accounts ORDER BY is_default DESC, id LIMIT 1)), updated_at=datetime('now')
            WHERE id=?
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, tx.getType().name());
            ps.setLong(2, tx.getAmountCents());
            ps.setLong(3, tx.getCategoryId());
            ps.setString(4, tx.getTxDate().toString());
            ps.setString(5, tx.getDescription());
            ps.setString(6, tx.getMerchant());
            ps.setLong(7, tx.getAccountId());
            ps.setLong(8, tx.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update transaction", e);
        }
    }

    @Override
    public boolean delete(long id) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM transactions WHERE id=?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete transaction", e);
        }
    }

    @Override
    public Optional<Transaction> findById(long id) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "SELECT t.*, c.name AS cat_name, a.name AS acct_name FROM transactions t " +
                "JOIN categories c ON c.id = t.category_id LEFT JOIN accounts a ON a.id = t.account_id WHERE t.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find transaction", e);
        }
    }

    @Override
    public List<Transaction> findAll() {
        return query(
            "SELECT t.*, c.name AS cat_name, a.name AS acct_name FROM transactions t " +
            "JOIN categories c ON c.id = t.category_id LEFT JOIN accounts a ON a.id = t.account_id " +
            "ORDER BY t.tx_date DESC, t.id DESC",
            ps -> {}
        );
    }

    // ----------------------------------------------------------------
    // Filtered query
    // ----------------------------------------------------------------

    @Override
    public List<Transaction> findByFilter(TransactionFilter f) {
        StringBuilder sql = new StringBuilder(
            "SELECT t.*, c.name AS cat_name, a.name AS acct_name FROM transactions t " +
            "JOIN categories c ON c.id = t.category_id LEFT JOIN accounts a ON a.id = t.account_id WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();

        if (f.getFrom() != null) {
            sql.append(" AND t.tx_date >= ?"); params.add(f.getFrom().toString());
        }
        if (f.getTo() != null) {
            sql.append(" AND t.tx_date <= ?"); params.add(f.getTo().toString());
        }
        if (f.getType() != null) {
            sql.append(" AND t.type = ?"); params.add(f.getType().name());
        }
        if (f.getCategoryId() != null) {
            sql.append(" AND t.category_id = ?"); params.add(f.getCategoryId());
        }
        if (f.getAccountId() != null) {
            sql.append(" AND t.account_id = ?"); params.add(f.getAccountId());
        }
        if (f.getKeyword() != null && !f.getKeyword().isBlank()) {
            sql.append(" AND (LOWER(t.description) LIKE ? OR LOWER(t.merchant) LIKE ?)");
            String kw = "%" + f.getKeyword().toLowerCase() + "%";
            params.add(kw); params.add(kw);
        }
        if (f.getMinAmountCents() != null) {
            sql.append(" AND t.amount_cents >= ?"); params.add(f.getMinAmountCents());
        }
        if (f.getMaxAmountCents() != null) {
            sql.append(" AND t.amount_cents <= ?"); params.add(f.getMaxAmountCents());
        }
        sql.append(" ORDER BY t.tx_date DESC, t.id DESC");

        return query(sql.toString(), ps -> {
            for (int i = 0; i < params.size(); i++) {
                Object v = params.get(i);
                if (v instanceof String s)  ps.setString(i + 1, s);
                else if (v instanceof Long l) ps.setLong(i + 1, l);
            }
        });
    }

    // ----------------------------------------------------------------
    // Aggregations
    // ----------------------------------------------------------------

    @Override
    public long sumIncomeCents(LocalDate from, LocalDate to) {
        return sumCents("INCOME", from, to);
    }

    @Override
    public long sumExpenseCents(LocalDate from, LocalDate to) {
        return sumCents("EXPENSE", from, to);
    }

    private long sumCents(String type, LocalDate from, LocalDate to) {
        String sql = "SELECT COALESCE(SUM(amount_cents),0) FROM transactions " +
                     "WHERE type=? AND tx_date BETWEEN ? AND ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setString(2, from.toString());
            ps.setString(3, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Sum query failed", e);
        }
    }

    @Override
    public List<CategoryTotal> expenseCategoryTotals(LocalDate from, LocalDate to) {
        String sql = """
            SELECT t.category_id, c.name AS cat_name, SUM(t.amount_cents) AS total
            FROM transactions t
            JOIN categories c ON c.id = t.category_id
            WHERE t.type = 'EXPENSE' AND t.tx_date BETWEEN ? AND ?
            GROUP BY t.category_id
            ORDER BY total DESC
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<CategoryTotal> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new CategoryTotal(
                        rs.getLong("category_id"),
                        rs.getString("cat_name"),
                        rs.getLong("total")
                    ));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Category totals query failed", e);
        }
    }

    @Override
    public List<MonthlyTotal> monthlyTotals(int year) {
        String sql = """
            SELECT
                strftime('%Y', tx_date) AS yr,
                strftime('%m', tx_date) AS mo,
                SUM(CASE WHEN type='INCOME'  THEN amount_cents ELSE 0 END) AS inc,
                SUM(CASE WHEN type='EXPENSE' THEN amount_cents ELSE 0 END) AS exp
            FROM transactions
            WHERE strftime('%Y', tx_date) = ?
            GROUP BY yr, mo
            ORDER BY mo
            """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, String.valueOf(year));
            try (ResultSet rs = ps.executeQuery()) {
                List<MonthlyTotal> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new MonthlyTotal(
                        rs.getInt("yr"),
                        rs.getInt("mo"),
                        rs.getLong("inc"),
                        rs.getLong("exp")
                    ));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Monthly totals query failed", e);
        }
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    @FunctionalInterface
    interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private List<Transaction> query(String sql, Binder binder) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<Transaction> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Transaction query failed", e);
        }
    }

    private Transaction map(ResultSet rs) throws SQLException {
        Transaction tx = new Transaction();
        tx.setId(rs.getLong("id"));
        tx.setType(Transaction.Type.valueOf(rs.getString("type")));
        tx.setAmountCents(rs.getLong("amount_cents"));
        tx.setCategoryId(rs.getLong("category_id"));
        tx.setCategoryName(rs.getString("cat_name"));
        tx.setTxDate(LocalDate.parse(rs.getString("tx_date")));
        tx.setAccountId(rs.getLong("account_id"));          // NULL -> 0
        tx.setAccountName(rs.getString("acct_name"));
        tx.setRecurringRuleId(rs.getLong("recurring_rule_id"));
        tx.setDescription(rs.getString("description"));
        tx.setMerchant(rs.getString("merchant"));
        String createdAt = rs.getString("created_at");
        if (createdAt != null) tx.setCreatedAt(LocalDateTime.parse(createdAt.replace(' ', 'T')));
        String updatedAt = rs.getString("updated_at");
        if (updatedAt != null) tx.setUpdatedAt(LocalDateTime.parse(updatedAt.replace(' ', 'T')));
        return tx;
    }
}
