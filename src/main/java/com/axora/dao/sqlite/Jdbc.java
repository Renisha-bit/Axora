package com.axora.dao.sqlite;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Tiny JDBC helpers shared by the SQLite DAOs. */
final class Jdbc {
    private Jdbc() {}

    @FunctionalInterface interface Binder { void bind(PreparedStatement ps) throws SQLException; }
    @FunctionalInterface interface RowMapper<T> { T map(ResultSet rs) throws SQLException; }

    static Connection conn() { return DatabaseManager.getInstance().getConnection(); }

    static <T> List<T> query(String sql, Binder b, RowMapper<T> m) {
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            if (b != null) b.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) out.add(m.map(rs));
                return out;
            }
        } catch (SQLException e) { throw new RuntimeException("Query failed: " + e.getMessage(), e); }
    }

    static long scalar(String sql, Binder b) {
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            if (b != null) b.bind(ps);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0; }
        } catch (SQLException e) { throw new RuntimeException("Query failed: " + e.getMessage(), e); }
    }

    /** Executes an INSERT and returns its rowid (via last_insert_rowid, driver independent). */
    static long insert(String sql, Binder b) {
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            b.bind(ps);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("Insert failed: " + e.getMessage(), e); }
        return scalar("SELECT last_insert_rowid()", null);
    }

    static int update(String sql, Binder b) {
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            if (b != null) b.bind(ps);
            return ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("Update failed: " + e.getMessage(), e); }
    }

    static void setNullableLong(PreparedStatement ps, int i, Long v) throws SQLException {
        if (v == null) ps.setNull(i, Types.INTEGER); else ps.setLong(i, v);
    }
    static void setDate(PreparedStatement ps, int i, LocalDate d) throws SQLException {
        if (d == null) ps.setNull(i, Types.VARCHAR); else ps.setString(i, d.toString());
    }
    static LocalDate date(ResultSet rs, String col) throws SQLException {
        String s = rs.getString(col);
        return s == null ? null : LocalDate.parse(s);
    }
    static Long nullableLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }
}
