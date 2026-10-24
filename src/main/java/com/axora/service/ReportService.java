package com.axora.service;

import com.axora.dao.AnalyticsDao;
import com.axora.dao.CategoryTotal;
import com.axora.dao.MonthlyTotal;
import com.axora.dao.sqlite.SQLiteAnalyticsDao;
import com.axora.model.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Builds financial reports for a date range. Everything is derived from persisted transactions
 * (transfers live in a separate table and are therefore excluded from income/expense by design).
 */
public class ReportService {

    /** Largest month span the report will chart (keeps charts readable). */
    public static final int MAX_MONTHS = 120;

    public record MonthRow(YearMonth month, long incomeCents, long expenseCents, long netCents, long cumulativeNetCents) {}

    public record FinancialReport(LocalDate from, LocalDate to, Long accountId,
                                  long incomeCents, long expenseCents, long netCents,
                                  List<MonthRow> months, List<CategoryTotal> categories,
                                  List<BudgetStatus> budgets, List<AccountBalance> balances,
                                  Map<String, Long> balanceTotalsByCurrency, boolean mixedCurrencies) {
        /** True when the range contains no income or expense at all. */
        public boolean isEmpty() { return incomeCents == 0 && expenseCents == 0; }
    }

    private final AnalyticsDao analytics;
    private final BudgetService budgets;
    private final AccountService accounts;

    public ReportService() { this(new SQLiteAnalyticsDao(), new BudgetService(), new AccountService()); }
    public ReportService(AnalyticsDao a, BudgetService b, AccountService acc) { analytics = a; budgets = b; accounts = acc; }

    /** Default range: January 1st of the current year up to today. */
    public static LocalDate[] defaultRange(LocalDate today) { return new LocalDate[]{today.withDayOfYear(1), today}; }

    /**
     * @param accountId null = all accounts. Budgets are evaluated as of {@code to}; balances are as of {@code to}.
     */
    public FinancialReport build(LocalDate from, LocalDate to, Long accountId) {
        if (from == null || to == null) throw new IllegalArgumentException("Select both a start and an end date.");
        if (to.isBefore(from)) throw new IllegalArgumentException("The end date cannot be before the start date.");
        long months = YearMonth.from(from).until(YearMonth.from(to), java.time.temporal.ChronoUnit.MONTHS);
        if (months > MAX_MONTHS)
            throw new IllegalArgumentException("Please choose a range of at most " + MAX_MONTHS / 12 + " years.");

        long income  = analytics.sumCents(Transaction.Type.INCOME,  null, accountId, from, to);
        long expense = analytics.sumCents(Transaction.Type.EXPENSE, null, accountId, from, to);

        Map<YearMonth, MonthlyTotal> byMonth = new HashMap<>();
        for (MonthlyTotal m : analytics.monthlyTotals(accountId, from, to)) byMonth.put(YearMonth.of(m.year(), m.month()), m);
        List<MonthRow> rows = new ArrayList<>();
        long cumulative = 0;
        for (YearMonth ym = YearMonth.from(from); !ym.isAfter(YearMonth.from(to)); ym = ym.plusMonths(1)) {
            MonthlyTotal m = byMonth.get(ym);
            long inc = m == null ? 0 : m.incomeCents(), exp = m == null ? 0 : m.expenseCents();
            cumulative += inc - exp;
            rows.add(new MonthRow(ym, inc, exp, inc - exp, cumulative));
        }

        List<AccountBalance> balances = accounts.getBalances(to);
        if (accountId != null) balances = balances.stream().filter(b -> b.account().id() == accountId).toList();
        Map<String, Long> totals = new TreeMap<>();
        for (AccountBalance b : balances) totals.merge(b.account().currency(), b.balanceCents(), Long::sum);
        boolean mixed = balances.stream().map(b -> b.account().currency()).distinct().count() > 1;

        return new FinancialReport(from, to, accountId, income, expense, income - expense, rows,
            analytics.expenseByCategory(accountId, from, to), budgets.statuses(to, null),
            balances, totals, mixed);
    }
}
