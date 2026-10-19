package com.axora.dao.sqlite;

import com.axora.dao.CategoryDao;
import com.axora.model.Category;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SQLiteCategoryDao implements CategoryDao {

    private final DatabaseManager db = DatabaseManager.getInstance();

    @Override
    public List<Category> findAll() {
        return query("SELECT * FROM categories ORDER BY type, name", null);
    }

    @Override
    public List<Category> findByType(Category.CategoryType type) {
        return query("SELECT * FROM categories WHERE type = ? OR type = 'BOTH' ORDER BY name",
                     ps -> ps.setString(1, type.name()));
    }

    @Override
    public Optional<Category> findById(long id) {
        List<Category> list = query("SELECT * FROM categories WHERE id = ?",
                                    ps -> ps.setLong(1, id));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public long insert(Category category) {
        String sql = "INSERT INTO categories(name, type, color, is_default) VALUES(?,?,?,?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql,
                                         Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getType().name());
            ps.setString(3, category.getColor());
            ps.setInt(4, category.isDefault() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert category", e);
        }
    }

    @Override
    public boolean update(Category category) {
        String sql = "UPDATE categories SET name=?, type=?, color=? WHERE id=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getType().name());
            ps.setString(3, category.getColor());
            ps.setLong(4, category.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update category", e);
        }
    }

    @Override
    public boolean delete(long id) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(
                "DELETE FROM categories WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete category", e);
        }
    }

    // --- helpers ---

    @FunctionalInterface
    interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private List<Category> query(String sql, Binder binder) {
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            if (binder != null) binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<Category> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Category query failed", e);
        }
    }

    private Category map(ResultSet rs) throws SQLException {
        Category c = new Category();
        c.setId(rs.getLong("id"));
        c.setName(rs.getString("name"));
        c.setType(Category.CategoryType.valueOf(rs.getString("type")));
        c.setColor(rs.getString("color"));
        c.setDefault(rs.getInt("is_default") == 1);
        return c;
    }
}
