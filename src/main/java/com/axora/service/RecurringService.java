package com.axora.service;

import com.axora.dao.*;
import com.axora.dao.sqlite.*;
import com.axora.model.*;
import com.axora.util.MoneyParser;

import java.time.LocalDate;
import java.util.*;

/**
 * Recurring rules and idempotent generation of due transactions.
 *
 * Idempotency: every (rule, scheduled date) that has been processed is recorded in
 * recurring_occurrences (primary key) in the same DB transaction as the generated transaction.
 * Re-running generation, restarting the app, or deleting a generated transaction therefore never
 * creates a duplicate.
 */
public class RecurringService {

    /** Safety valve: max occurrences created per rule per run (e.g. a daily rule started decades ago). */
    static final int MAX_PER_RUN = 5000;

    private final RecurringRuleDao ruleDao;
    private final TransactionDao txDao;
    private final CategoryDao categoryDao;
    private final AccountDao accountDao;
    private final java.time.Clock clock;

    public RecurringService() {
        this(new SQLiteRecurringRuleDao(), new SQLiteTransactionDao(), new SQLiteCategoryDao(), new SQLiteAccountDao());
    }
    public RecurringService(RecurringRuleDao r, TransactionDao t, CategoryDao c, AccountDao a) {
        this(r, t, c, a, java.time.Clock.systemDefaultZone());
    }
    public RecurringService(RecurringRuleDao r, TransactionDao t, CategoryDao c, AccountDao a, java.time.Clock clock) {
        ruleDao = r; txDao = t; categoryDao = c; accountDao = a; this.clock = clock;
    }

    private LocalDate today() { return LocalDate.now(clock); }

    public List<RecurringRule> getRules() { return ruleDao.findAll(); }
    public long generatedCount(long ruleId) { return ruleDao.countGenerated(ruleId); }

    // ---- CRUD
    /** Creates the rule and immediately generates any occurrences already due. */
    public long create(Transaction.Type type, String amountText, long categoryId, long accountId, String description,
                       String merchant, RecurringRule.Frequency freq, LocalDate start, LocalDate end) {
        RecurringRule r = build(0, type, amountText, categoryId, accountId, description, merchant, freq, start, end, start, false);
        return DatabaseManager.getInstance().inTransaction(() -> {
            long id = ruleDao.insert(r);
            generateForRule(ruleDao.findById(id).orElseThrow(), today());
            return id;
        });
    }

    /**
     * Edits apply to future occurrences only: already generated transactions are untouched and,
     * if the schedule itself (frequency / start date) changed, nothing before today is back-filled.
     */
    public void update(long id, Transaction.Type type, String amountText, long categoryId, long accountId,
                       String description, String merchant, RecurringRule.Frequency freq, LocalDate start, LocalDate end) {
        RecurringRule old = ruleDao.findById(id).orElseThrow(() -> new IllegalArgumentException("Rule not found."));
        boolean scheduleChanged = old.frequency() != freq || !old.startDate().equals(start);
        LocalDate from = scheduleChanged ? max(old.generateFrom(), today()) : old.generateFrom();
        RecurringRule r = build(id, type, amountText, categoryId, accountId, description, merchant, freq, start, end, from, old.paused());
        DatabaseManager.getInstance().runInTransaction(() -> {
            ruleDao.update(r);
            if (!r.paused()) generateForRule(r, today());
        });
    }

    public void pause(long id) {
        RecurringRule r = ruleDao.findById(id).orElseThrow(() -> new IllegalArgumentException("Rule not found."));
        ruleDao.setPaused(id, true, r.generateFrom());
    }

    /** Resuming skips whatever fell due while paused (generation restarts from today). */
    public void resume(long id) {
        ruleDao.findById(id).orElseThrow(() -> new IllegalArgumentException("Rule not found."));
        DatabaseManager.getInstance().runInTransaction(() -> {
            ruleDao.setPaused(id, false, today());
            generateForRule(ruleDao.findById(id).orElseThrow(), today());
        });
    }

    /** Deletes the schedule. Transactions it already generated are kept as history. */
    public void delete(long id) { ruleDao.delete(id); }

    // ---- generation
    /** Generates all due, not-yet-generated occurrences up to and including today. @return number created */
    public int generateDue(LocalDate today) {
        return DatabaseManager.getInstance().inTransaction(() -> {
            int created = 0;
            for (RecurringRule r : ruleDao.findAll()) if (!r.paused()) created += generateForRule(r, today);
            return created;
        });
    }

    private int generateForRule(RecurringRule r, LocalDate today) {
        LocalDate from = max(r.startDate(), r.generateFrom());
        List<LocalDate> due = RecurrenceCalculator.between(r.startDate(), r.frequency(), from, today, r.endDate(), MAX_PER_RUN);
        if (due.isEmpty()) return 0;
        Set<LocalDate> done = ruleDao.occurrenceDates(r.id());
        int created = 0;
        for (LocalDate d : due) {
            if (done.contains(d)) continue;
            Transaction tx = new Transaction(r.type(), r.amountCents(), r.categoryId(), d,
                r.description() == null ? "" : r.description(), r.merchant() == null ? "" : r.merchant());
            tx.setAccountId(r.accountId());
            tx.setRecurringRuleId(r.id());
            long txId = txDao.insert(tx);
            ruleDao.recordOccurrence(r.id(), d, txId);
            created++;
        }
        return created;
    }

    // ---- upcoming
    /** Scheduled occurrences after today and within the horizon, soonest first. */
    public List<UpcomingOccurrence> upcoming(LocalDate today, int horizonDays, int limit) {
        List<UpcomingOccurrence> out = new ArrayList<>();
        LocalDate to = today.plusDays(horizonDays);
        for (RecurringRule r : ruleDao.findAll()) {
            if (r.paused()) continue;
            LocalDate from = max(max(r.startDate(), r.generateFrom()), today.plusDays(1));
            for (LocalDate d : RecurrenceCalculator.between(r.startDate(), r.frequency(), from, to, r.endDate(), limit))
                out.add(new UpcomingOccurrence(r, d));
        }
        out.sort(Comparator.comparing(UpcomingOccurrence::date));
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    /** Next scheduled date after today for display, or null when paused / finished. */
    public LocalDate nextDue(RecurringRule r, LocalDate today) {
        if (r.paused()) return null;
        LocalDate after = max(today, r.generateFrom().minusDays(1));
        return RecurrenceCalculator.nextAfter(r.startDate(), r.frequency(), after, r.endDate());
    }

    // ---- validation
    private RecurringRule build(long id, Transaction.Type type, String amountText, long categoryId, long accountId,
                                String description, String merchant, RecurringRule.Frequency freq,
                                LocalDate start, LocalDate end, LocalDate generateFrom, boolean paused) {
        List<String> errors = new ArrayList<>();
        if (type == null) errors.add("Type is required.");
        long cents = 0;
        try { cents = MoneyParser.parsePositiveCents(amountText, "Amount"); } catch (IllegalArgumentException e) { errors.add(e.getMessage()); }
        Category cat = categoryDao.findById(categoryId).orElse(null);
        String catName = null, acctName = null;
        if (cat == null) errors.add("Please select a category.");
        else {
            catName = cat.getName();
            Category.CategoryType expected = type == Transaction.Type.INCOME ? Category.CategoryType.INCOME : Category.CategoryType.EXPENSE;
            if (type != null && cat.getType() != Category.CategoryType.BOTH && cat.getType() != expected)
                errors.add("Selected category is not valid for this transaction type.");
        }
        Account acct = accountDao.findById(accountId).orElse(null);
        if (acct == null) errors.add("Please select an account."); else acctName = acct.name();
        if (freq == null) errors.add("Frequency is required.");
        if (start == null) errors.add("Start date is required.");
        if (start != null && end != null && end.isBefore(start)) errors.add("End date cannot be before the start date.");
        if (description != null && description.length() > 500) errors.add("Description must not exceed 500 characters.");
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        return new RecurringRule(id, type, cents, categoryId, catName, accountId, acctName,
            description == null ? "" : description.trim(), merchant == null ? "" : merchant.trim(),
            freq, start, end, generateFrom, paused);
    }

    private static LocalDate max(LocalDate a, LocalDate b) { return a.isAfter(b) ? a : b; }
}
