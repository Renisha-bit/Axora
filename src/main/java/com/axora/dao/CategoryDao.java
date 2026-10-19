package com.axora.dao;

import com.axora.model.Category;
import java.util.List;
import java.util.Optional;

/** DAO interface for categories. */
public interface CategoryDao {
    List<Category> findAll();
    List<Category> findByType(Category.CategoryType type);
    Optional<Category> findById(long id);
    long insert(Category category);
    boolean update(Category category);
    boolean delete(long id);
}
