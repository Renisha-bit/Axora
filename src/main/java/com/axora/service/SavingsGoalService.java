package com.axora.service;

import com.axora.dao.SavingsGoalDao;
import com.axora.dao.sqlite.DatabaseManager;
import com.axora.dao.sqlite.SQLiteSavingsGoalDao;
import com.axora.model.GoalEntry;
import com.axora.model.GoalProgress;
import com.axora.model.SavingsGoal;
import com.axora.util.MoneyParser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Savings goals backed by their own append-only ledger. Goal progress is deliberately separate
 * from account balances: contributing to a goal does not move money between accounts.
 */
public class SavingsGoalService {

    private final SavingsGoalDao dao;

    public SavingsGoalService() { this(new SQLiteSavingsGoalDao()); }
    public SavingsGoalService(SavingsGoalDao dao) { this.dao = dao; }

    public List<GoalProgress> progressList(LocalDate asOf) {
        List<GoalProgress> out = new ArrayList<>();
        for (SavingsGoal g : dao.findAll()) out.add(progress(g, dao.savedCents(g.id()), asOf));
        return out;
    }

    public GoalProgress progress(long goalId, LocalDate asOf) {
        SavingsGoal g = dao.findById(goalId).orElseThrow(() -> new IllegalArgumentException("Goal not found."));
        return progress(g, dao.savedCents(goalId), asOf);
    }

    /** Pure status calculation. On track = saved is at least the linear share of the target for elapsed time. */
    public static GoalProgress progress(SavingsGoal g, long saved, LocalDate asOf) {
        BigDecimal pct = BigDecimal.valueOf(saved).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(g.targetCents()), 1, RoundingMode.HALF_UP);
        long remaining = Math.max(0, g.targetCents() - saved);
        GoalProgress.Status st;
        if (saved >= g.targetCents()) st = GoalProgress.Status.COMPLETED;
        else if (asOf.isAfter(g.targetDate())) st = GoalProgress.Status.OVERDUE;
        else {
            long total = Math.max(1, ChronoUnit.DAYS.between(g.startDate(), g.targetDate()));
            long elapsed = Math.max(0, Math.min(total, ChronoUnit.DAYS.between(g.startDate(), asOf)));
            long expected = BigDecimal.valueOf(g.targetCents()).multiply(BigDecimal.valueOf(elapsed))
                .divide(BigDecimal.valueOf(total), 0, RoundingMode.FLOOR).longValue();
            st = saved >= expected ? GoalProgress.Status.ON_TRACK : GoalProgress.Status.BEHIND;
        }
        return new GoalProgress(g, saved, remaining, pct, st);
    }

    // ---- goals
    public long create(String name, String targetText, LocalDate targetDate, String description) {
        SavingsGoal g = build(0, name, targetText, targetDate, description, LocalDate.now());
        if (targetDate.isBefore(LocalDate.now())) throw new IllegalArgumentException("Target date cannot be in the past.");
        return dao.insert(g);
    }

    public void update(long id, String name, String targetText, LocalDate targetDate, String description) {
        SavingsGoal old = dao.findById(id).orElseThrow(() -> new IllegalArgumentException("Goal not found."));
        SavingsGoal g = build(id, name, targetText, targetDate, description, old.startDate());
        if (targetDate.isBefore(old.startDate()))
            throw new IllegalArgumentException("Target date cannot be before the goal's start date (" + old.startDate() + ").");
        dao.update(g);
    }

    /** Deletes the goal and its history. */
    public void delete(long id) { dao.delete(id); }

    private SavingsGoal build(long id, String name, String targetText, LocalDate targetDate, String desc, LocalDate start) {
        List<String> errors = new ArrayList<>();
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) errors.add("Goal name is required.");
        if (n.length() > 80) errors.add("Goal name must not exceed 80 characters.");
        long cents = 0;
        try { cents = MoneyParser.parsePositiveCents(targetText, "Target amount"); } catch (IllegalArgumentException e) { errors.add(e.getMessage()); }
        if (targetDate == null) errors.add("Target date is required.");
        if (desc != null && desc.length() > 500) errors.add("Description must not exceed 500 characters.");
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        return new SavingsGoal(id, n, cents, targetDate, desc == null ? "" : desc.trim(), start);
    }

    // ---- ledger
    public void contribute(long goalId, String amountText, LocalDate date, String note) {
        addEntry(goalId, GoalEntry.Type.CONTRIBUTION, amountText, date, note);
    }

    public void withdraw(long goalId, String amountText, LocalDate date, String note) {
        addEntry(goalId, GoalEntry.Type.WITHDRAWAL, amountText, date, note);
    }

    private void addEntry(long goalId, GoalEntry.Type type, String amountText, LocalDate date, String note) {
        dao.findById(goalId).orElseThrow(() -> new IllegalArgumentException("Goal not found."));
        List<String> errors = new ArrayList<>();
        long cents = 0;
        try { cents = MoneyParser.parsePositiveCents(amountText, "Amount"); } catch (IllegalArgumentException e) { errors.add(e.getMessage()); }
        if (date == null) errors.add("Date is required.");
        else if (date.isAfter(LocalDate.now())) errors.add("Date cannot be in the future.");
        if (note != null && note.length() > 500) errors.add("Note must not exceed 500 characters.");
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        final long amount = cents;
        DatabaseManager.getInstance().runInTransaction(() -> {
            if (type == GoalEntry.Type.WITHDRAWAL && amount > dao.savedCents(goalId))
                throw new IllegalArgumentException("Cannot withdraw more than the amount saved.");
            dao.insertEntry(new GoalEntry(0, goalId, type, amount, date, note == null ? "" : note.trim()));
        });
    }

    public List<GoalEntry> history(long goalId) { return dao.findEntries(goalId); }
}
