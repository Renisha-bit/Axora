package com.axora.dao;

import com.axora.model.GoalEntry;
import com.axora.model.SavingsGoal;

import java.util.List;
import java.util.Optional;

/** DAO for savings goals and their append-only ledger. */
public interface SavingsGoalDao {
    List<SavingsGoal> findAll();
    Optional<SavingsGoal> findById(long id);
    long insert(SavingsGoal g);
    boolean update(SavingsGoal g);
    boolean delete(long id);

    long insertEntry(GoalEntry e);
    /** Newest first. */
    List<GoalEntry> findEntries(long goalId);
    /** Contributions minus withdrawals for one goal, optionally only entries up to a date (inclusive). */
    long savedCents(long goalId);
}
