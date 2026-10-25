package com.axora.dao.sqlite;

import com.axora.dao.RecurringRuleDao;
import com.axora.model.RecurringRule;
import com.axora.model.Transaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SQLiteRecurringRuleDao implements RecurringRuleDao {

    private static final String SELECT = """
        SELECT r.*, c.name AS cat_name, a.name AS acct_name FROM recurring_rules r
        JOIN categories c ON c.id = r.category_id
        JOIN accounts a   ON a.id = r.account_id
        """;

    @Override public List<RecurringRule> findAll() {
        return Jdbc.query(SELECT + " ORDER BY r.paused, r.start_date, r.id", null, this::map);
    }
    @Override public Optional<RecurringRule> findById(long id) {
        return Jdbc.query(SELECT + " WHERE r.id=?", ps -> ps.setLong(1, id), this::map).stream().findFirst();
    }
    @Override public long insert(RecurringRule r) {
        return Jdbc.insert("""
            INSERT INTO recurring_rules(type,amount_cents,category_id,account_id,description,merchant,
                                        frequency,start_date,end_date,generate_from,paused)
            VALUES(?,?,?,?,?,?,?,?,?,?,?)""", ps -> bind(ps, r));
    }
    @Override public boolean update(RecurringRule r) {
        return Jdbc.update("""
            UPDATE recurring_rules SET type=?, amount_cents=?, category_id=?, account_id=?, description=?,
                merchant=?, frequency=?, start_date=?, end_date=?, generate_from=?, paused=? WHERE id=?""",
            ps -> { bind(ps, r); ps.setLong(12, r.id()); }) > 0;
    }
    @Override public boolean delete(long id) {
        return Jdbc.update("DELETE FROM recurring_rules WHERE id=?", ps -> ps.setLong(1, id)) > 0;
    }
    @Override public boolean setPaused(long id, boolean paused, LocalDate generateFrom) {
        return Jdbc.update("UPDATE recurring_rules SET paused=?, generate_from=? WHERE id=?", ps -> {
            ps.setInt(1, paused ? 1 : 0); ps.setString(2, generateFrom.toString()); ps.setLong(3, id);
        }) > 0;
    }

    @Override public Set<LocalDate> occurrenceDates(long ruleId) {
        return new HashSet<>(Jdbc.query("SELECT occurrence_date FROM recurring_occurrences WHERE rule_id=?",
            ps -> ps.setLong(1, ruleId), rs -> LocalDate.parse(rs.getString(1))));
    }
    @Override public void recordOccurrence(long ruleId, LocalDate date, long txId) {
        Jdbc.update("INSERT INTO recurring_occurrences(rule_id, occurrence_date, transaction_id) VALUES(?,?,?)",
            ps -> { ps.setLong(1, ruleId); ps.setString(2, date.toString()); ps.setLong(3, txId); });
    }
    @Override public long countGenerated(long ruleId) {
        return Jdbc.scalar("SELECT COUNT(*) FROM recurring_occurrences WHERE rule_id=?", ps -> ps.setLong(1, ruleId));
    }

    private void bind(PreparedStatement ps, RecurringRule r) throws SQLException {
        ps.setString(1, r.type().name()); ps.setLong(2, r.amountCents());
        ps.setLong(3, r.categoryId()); ps.setLong(4, r.accountId());
        ps.setString(5, r.description()); ps.setString(6, r.merchant());
        ps.setString(7, r.frequency().name()); ps.setString(8, r.startDate().toString());
        Jdbc.setDate(ps, 9, r.endDate()); ps.setString(10, r.generateFrom().toString());
        ps.setInt(11, r.paused() ? 1 : 0);
    }
    private RecurringRule map(ResultSet rs) throws SQLException {
        return new RecurringRule(rs.getLong("id"), Transaction.Type.valueOf(rs.getString("type")),
            rs.getLong("amount_cents"), rs.getLong("category_id"), rs.getString("cat_name"),
            rs.getLong("account_id"), rs.getString("acct_name"), rs.getString("description"),
            rs.getString("merchant"), RecurringRule.Frequency.valueOf(rs.getString("frequency")),
            Jdbc.date(rs, "start_date"), Jdbc.date(rs, "end_date"), Jdbc.date(rs, "generate_from"),
            rs.getInt("paused") == 1);
    }
}
