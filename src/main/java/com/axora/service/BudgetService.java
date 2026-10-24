package com.axora.service;

import com.axora.dao.*;
import com.axora.dao.sqlite.*;
import com.axora.model.*;
import com.axora.util.MoneyParser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Budget CRUD and evaluation against actual EXPENSE transactions (income/transfers never count). */
public class BudgetService {

    /** Usage at or above this percentage (of the limit) is flagged as a warning. */
    public static final int WARNING_PERCENT = 80;

    private final BudgetDao budgetDao;
    private final CategoryDao categoryDao;
    private final AnalyticsDao analytics;

    public BudgetService() { this(new SQLiteBudgetDao(), new SQLiteCategoryDao(), new SQLiteAnalyticsDao()); }
    public BudgetService(BudgetDao b, CategoryDao c, AnalyticsDao a) { budgetDao = b; categoryDao = c; analytics = a; }

    public List<Budget> getBudgets() { return budgetDao.findAll(); }

    /** Evaluates budgets for the window containing asOf; periodFilter null = all periods. */
    public List<BudgetStatus> statuses(LocalDate asOf, Budget.Period periodFilter) {
        List<BudgetStatus> out = new ArrayList<>();
        for (Budget b : budgetDao.findAll())
            if (periodFilter == null || b.period() == periodFilter) out.add(evaluate(b, asOf));
        return out;
    }

    public BudgetStatus evaluate(Budget b, LocalDate asOf) {
        LocalDate[] w = BudgetPeriods.window(b, asOf);
        long spent = analytics.sumCents(Transaction.Type.EXPENSE, b.categoryId(), null, w[0], w[1]);
        return compute(b, w[0], w[1], spent);
    }

    /** Pure calculation, public for testing. */
    public static BudgetStatus compute(Budget b, LocalDate from, LocalDate to, long spent) {
        long limit = b.amountCents();
        BigDecimal pct = BigDecimal.valueOf(spent).multiply(BigDecimal.valueOf(100))
                                   .divide(BigDecimal.valueOf(limit), 1, RoundingMode.HALF_UP);
        BudgetStatus.Level level = spent > limit ? BudgetStatus.Level.EXCEEDED
            : spent * 100 >= limit * WARNING_PERCENT ? BudgetStatus.Level.WARNING : BudgetStatus.Level.OK;
        return new BudgetStatus(b, from, to, spent, limit - spent, pct, level);
    }

    public long create(String name, Long categoryId, String amountText, Budget.Period period, LocalDate start, LocalDate end) {
        return budgetDao.insert(build(0, name, categoryId, amountText, period, start, end));
    }

    public void update(long id, String name, Long categoryId, String amountText, Budget.Period period, LocalDate start, LocalDate end) {
        budgetDao.findById(id).orElseThrow(() -> new IllegalArgumentException("Budget not found."));
        budgetDao.update(build(id, name, categoryId, amountText, period, start, end));
    }

    public void delete(long id) { budgetDao.delete(id); }

    private Budget build(long id, String name, Long categoryId, String amountText,
                         Budget.Period period, LocalDate start, LocalDate end) {
        List<String> errors = new ArrayList<>();
        if (categoryId != null && categoryId <= 0) categoryId = null;
        String catName = null;
        if (categoryId != null) {
            Category c = categoryDao.findById(categoryId).orElse(null);
            if (c == null) errors.add("Selected category does not exist.");
            else {
                catName = c.getName();
                if (c.getType() == Category.CategoryType.INCOME)
                    errors.add("Budgets apply to expense categories only.");
            }
        }
        long cents = 0;
        try { cents = MoneyParser.parsePositiveCents(amountText, "Budget amount"); } catch (IllegalArgumentException e) { errors.add(e.getMessage()); }
        if (period == null) errors.add("Period is required.");
        if (period == Budget.Period.CUSTOM) {
            if (start == null || end == null) errors.add("Custom budgets need a start and end date.");
            else if (end.isBefore(start)) errors.add("End date cannot be before the start date.");
        }
        String n = name == null ? "" : name.trim();
        if (n.length() > 80) errors.add("Name must not exceed 80 characters.");
        if (n.isEmpty() && period != null) n = (catName == null ? "Overall" : catName) + " – " + period;
        if (errors.isEmpty()) checkConflicts(id, categoryId, period, start, end, errors);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("\n", errors));
        boolean custom = period == Budget.Period.CUSTOM;
        return new Budget(id, n, categoryId, catName, cents, period, custom ? start : null, custom ? end : null);
    }

    private void checkConflicts(long selfId, Long categoryId, Budget.Period period,
                                LocalDate start, LocalDate end, List<String> errors) {
        String scope = categoryId == null ? "overall spending" : "this category";
        for (Budget o : budgetDao.findAll()) {
            if (o.id() == selfId || !java.util.Objects.equals(o.categoryId(), categoryId) || o.period() != period) continue;
            if (period != Budget.Period.CUSTOM) {
                errors.add("A " + period.toString().toLowerCase() + " budget for " + scope + " already exists ('" + o.name() + "'). Edit it instead.");
                return;
            }
            if (BudgetPeriods.overlaps(start, end, o.startDate(), o.endDate())) {
                errors.add("This custom budget overlaps '" + o.name() + "' (" + o.startDate() + " – " + o.endDate() + ") for " + scope + ".");
                return;
            }
        }
    }
}
