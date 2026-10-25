package com.axora.dao.sqlite;

import com.axora.dao.AccountDao;
import com.axora.model.Account;
import com.axora.model.Transfer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class SQLiteAccountDao implements AccountDao {

    private static final String TRANSFER_SELECT = """
        SELECT t.*, f.name AS from_name, d.name AS to_name FROM transfers t
        JOIN accounts f ON f.id = t.from_account_id
        JOIN accounts d ON d.id = t.to_account_id
        """;

    @Override public List<Account> findAll() {
        return Jdbc.query("SELECT * FROM accounts ORDER BY is_default DESC, name COLLATE NOCASE", null, this::map);
    }

    @Override public Optional<Account> findById(long id) {
        return Jdbc.query("SELECT * FROM accounts WHERE id=?", ps -> ps.setLong(1, id), this::map)
                   .stream().findFirst();
    }

    @Override public Optional<Account> findDefault() {
        return Jdbc.query("SELECT * FROM accounts ORDER BY is_default DESC, id LIMIT 1", null, this::map)
                   .stream().findFirst();
    }

    @Override public long insert(Account a) {
        return Jdbc.insert("""
            INSERT INTO accounts(name,type,opening_balance_cents,currency,description,is_default)
            VALUES(?,?,?,?,?,?)""", ps -> {
            ps.setString(1, a.name()); ps.setString(2, a.type().name());
            ps.setLong(3, a.openingBalanceCents()); ps.setString(4, a.currency());
            ps.setString(5, a.description()); ps.setInt(6, a.isDefault() ? 1 : 0);
        });
    }

    @Override public boolean update(Account a) {
        return Jdbc.update("UPDATE accounts SET name=?, type=?, opening_balance_cents=?, currency=?, description=? WHERE id=?",
            ps -> {
                ps.setString(1, a.name()); ps.setString(2, a.type().name());
                ps.setLong(3, a.openingBalanceCents()); ps.setString(4, a.currency());
                ps.setString(5, a.description()); ps.setLong(6, a.id());
            }) > 0;
    }

    @Override public boolean delete(long id) {
        return Jdbc.update("DELETE FROM accounts WHERE id=?", ps -> ps.setLong(1, id)) > 0;
    }

    @Override public long balanceCents(long accountId, LocalDate asOf) {
        String d = asOf == null ? "9999-12-31" : asOf.toString();
        return Jdbc.scalar("""
            SELECT a.opening_balance_cents
              + COALESCE((SELECT SUM(CASE t.type WHEN 'INCOME' THEN t.amount_cents ELSE -t.amount_cents END)
                          FROM transactions t WHERE t.account_id = a.id AND t.tx_date <= ?), 0)
              + COALESCE((SELECT SUM(amount_cents) FROM transfers WHERE to_account_id = a.id AND transfer_date <= ?), 0)
              - COALESCE((SELECT SUM(amount_cents) FROM transfers WHERE from_account_id = a.id AND transfer_date <= ?), 0)
            FROM accounts a WHERE a.id = ?""", ps -> {
            ps.setString(1, d); ps.setString(2, d); ps.setString(3, d); ps.setLong(4, accountId);
        });
    }

    @Override public long countTransactions(long id) {
        return Jdbc.scalar("SELECT COUNT(*) FROM transactions WHERE account_id=?", ps -> ps.setLong(1, id));
    }
    @Override public long countTransfers(long id) {
        return Jdbc.scalar("SELECT COUNT(*) FROM transfers WHERE from_account_id=? OR to_account_id=?",
            ps -> { ps.setLong(1, id); ps.setLong(2, id); });
    }
    @Override public long countRecurringRules(long id) {
        return Jdbc.scalar("SELECT COUNT(*) FROM recurring_rules WHERE account_id=?", ps -> ps.setLong(1, id));
    }

    // ---- transfers
    @Override public long insertTransfer(Transfer t) {
        return Jdbc.insert("INSERT INTO transfers(from_account_id,to_account_id,amount_cents,transfer_date,description) VALUES(?,?,?,?,?)",
            ps -> bindTransfer(ps, t));
    }
    @Override public boolean updateTransfer(Transfer t) {
        return Jdbc.update("UPDATE transfers SET from_account_id=?, to_account_id=?, amount_cents=?, transfer_date=?, description=? WHERE id=?",
            ps -> { bindTransfer(ps, t); ps.setLong(6, t.id()); }) > 0;
    }
    private void bindTransfer(java.sql.PreparedStatement ps, Transfer t) throws SQLException {
        ps.setLong(1, t.fromAccountId()); ps.setLong(2, t.toAccountId());
        ps.setLong(3, t.amountCents()); ps.setString(4, t.date().toString()); ps.setString(5, t.description());
    }
    @Override public boolean deleteTransfer(long id) {
        return Jdbc.update("DELETE FROM transfers WHERE id=?", ps -> ps.setLong(1, id)) > 0;
    }
    @Override public Optional<Transfer> findTransfer(long id) {
        return Jdbc.query(TRANSFER_SELECT + " WHERE t.id=?", ps -> ps.setLong(1, id), this::mapTransfer).stream().findFirst();
    }
    @Override public List<Transfer> findTransfersForAccount(long id) {
        return Jdbc.query(TRANSFER_SELECT + " WHERE t.from_account_id=? OR t.to_account_id=? ORDER BY t.transfer_date DESC, t.id DESC",
            ps -> { ps.setLong(1, id); ps.setLong(2, id); }, this::mapTransfer);
    }
    @Override public List<Transfer> findAllTransfers() {
        return Jdbc.query(TRANSFER_SELECT + " ORDER BY t.transfer_date DESC, t.id DESC", null, this::mapTransfer);
    }

    private Account map(ResultSet rs) throws SQLException {
        return new Account(rs.getLong("id"), rs.getString("name"),
            Account.Type.valueOf(rs.getString("type")), rs.getLong("opening_balance_cents"),
            rs.getString("currency"), rs.getString("description"), rs.getInt("is_default") == 1);
    }
    private Transfer mapTransfer(ResultSet rs) throws SQLException {
        return new Transfer(rs.getLong("id"), rs.getLong("from_account_id"), rs.getString("from_name"),
            rs.getLong("to_account_id"), rs.getString("to_name"), rs.getLong("amount_cents"),
            LocalDate.parse(rs.getString("transfer_date")), rs.getString("description"));
    }
}
