package com.axora.dao;

import com.axora.model.Budget;
import java.util.List;
import java.util.Optional;

/** DAO for budgets. */
public interface BudgetDao {
    List<Budget> findAll();
    Optional<Budget> findById(long id);
    long insert(Budget b);
    boolean update(Budget b);
    boolean delete(long id);
}
