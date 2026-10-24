package com.axora.dao.sqlite;

import com.axora.dao.SavingsGoalDao;
import com.axora.model.GoalEntry;
import com.axora.model.SavingsGoal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class SQLiteSavingsGoalDao implements SavingsGoalDao {

    @Override public List<SavingsGoal> findAll() {
        return Jdbc.query("SELECT * FROM savings_goals ORDER BY target_date, name COLLATE NOCASE", null, this::map);
    }
    @Override public Optional<SavingsGoal> findById(long id) {
        return Jdbc.query("SELECT * FROM savings_goals WHERE id=?", ps -> ps.setLong(1, id), this::map).stream().findFirst();
    }
    @Override public long insert(SavingsGoal g) {
        return Jdbc.insert("INSERT INTO savings_goals(name,target_cents,target_date,description,start_date) VALUES(?,?,?,?,?)",
            ps -> {
                ps.setString(1, g.name()); ps.setLong(2, g.targetCents()); ps.setString(3, g.targetDate().toString());
                ps.setString(4, g.description()); ps.setString(5, g.startDate().toString());
            });
    }
    @Override public boolean update(SavingsGoal g) {
        return Jdbc.update("UPDATE savings_goals SET name=?, target_cents=?, target_date=?, description=? WHERE id=?",
            ps -> {
                ps.setString(1, g.name()); ps.setLong(2, g.targetCents()); ps.setString(3, g.targetDate().toString());
                ps.setString(4, g.description()); ps.setLong(5, g.id());
            }) > 0;
    }
    @Override public boolean delete(long id) {
        return Jdbc.update("DELETE FROM savings_goals WHERE id=?", ps -> ps.setLong(1, id)) > 0;
    }

    @Override public long insertEntry(GoalEntry e) {
        return Jdbc.insert("INSERT INTO goal_entries(goal_id,entry_type,amount_cents,entry_date,note) VALUES(?,?,?,?,?)",
            ps -> {
                ps.setLong(1, e.goalId()); ps.setString(2, e.type().name()); ps.setLong(3, e.amountCents());
                ps.setString(4, e.date().toString()); ps.setString(5, e.note());
            });
    }
    @Override public List<GoalEntry> findEntries(long goalId) {
        return Jdbc.query("SELECT * FROM goal_entries WHERE goal_id=? ORDER BY entry_date DESC, id DESC",
            ps -> ps.setLong(1, goalId), rs -> new GoalEntry(rs.getLong("id"), rs.getLong("goal_id"),
                GoalEntry.Type.valueOf(rs.getString("entry_type")), rs.getLong("amount_cents"),
                LocalDate.parse(rs.getString("entry_date")), rs.getString("note")));
    }
    @Override public long savedCents(long goalId) {
        return Jdbc.scalar("""
            SELECT COALESCE(SUM(CASE entry_type WHEN 'CONTRIBUTION' THEN amount_cents ELSE -amount_cents END),0)
            FROM goal_entries WHERE goal_id=?""", ps -> ps.setLong(1, goalId));
    }

    private SavingsGoal map(ResultSet rs) throws SQLException {
        return new SavingsGoal(rs.getLong("id"), rs.getString("name"), rs.getLong("target_cents"),
            LocalDate.parse(rs.getString("target_date")), rs.getString("description"),
            LocalDate.parse(rs.getString("start_date")));
    }
}
