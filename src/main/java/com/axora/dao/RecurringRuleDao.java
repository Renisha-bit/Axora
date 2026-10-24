package com.axora.dao;

import com.axora.model.RecurringRule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** DAO for recurring rules and their occurrence ledger. */
public interface RecurringRuleDao {
    List<RecurringRule> findAll();
    Optional<RecurringRule> findById(long id);
    long insert(RecurringRule r);
    boolean update(RecurringRule r);
    boolean delete(long id);
    boolean setPaused(long id, boolean paused, LocalDate generateFrom);

    /** Scheduled dates already processed for the rule (generated, even if the tx was later deleted). */
    Set<LocalDate> occurrenceDates(long ruleId);
    void recordOccurrence(long ruleId, LocalDate date, long transactionId);
    long countGenerated(long ruleId);
}
