package com.axora.service;

import com.axora.model.BudgetStatus;
import com.axora.model.GoalProgress;
import com.axora.model.Transaction;
import com.axora.model.UpcomingOccurrence;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Cross-module summary for the dashboard cards. All numbers come from the module services. */
public class OverviewService {

    public static final int UPCOMING_DAYS = 7;

    public record Overview(Map<String, Long> balancesByCurrency,
                           int budgetCount, int budgetsExceeded, int budgetsNearLimit,
                           int upcomingCount, long upcomingExpenseCents,
                           int goalCount, int goalsCompleted, long goalsSavedCents, long goalsTargetCents) {}

    private final AccountService accounts;
    private final BudgetService budgets;
    private final RecurringService recurring;
    private final SavingsGoalService goals;

    public OverviewService() { this(new AccountService(), new BudgetService(), new RecurringService(), new SavingsGoalService()); }
    public OverviewService(AccountService a, BudgetService b, RecurringService r, SavingsGoalService g) {
        accounts = a; budgets = b; recurring = r; goals = g;
    }

    public Overview build(LocalDate today) {
        List<BudgetStatus> bs = budgets.statuses(today, null);
        List<UpcomingOccurrence> up = recurring.upcoming(today, UPCOMING_DAYS, 500);
        List<GoalProgress> gs = goals.progressList(today);
        return new Overview(accounts.totalsByCurrency(null),
            bs.size(),
            (int) bs.stream().filter(s -> s.level() == BudgetStatus.Level.EXCEEDED).count(),
            (int) bs.stream().filter(s -> s.level() == BudgetStatus.Level.WARNING).count(),
            up.size(),
            up.stream().filter(u -> u.rule().type() == Transaction.Type.EXPENSE).mapToLong(u -> u.rule().amountCents()).sum(),
            gs.size(),
            (int) gs.stream().filter(p -> p.status() == GoalProgress.Status.COMPLETED).count(),
            gs.stream().mapToLong(GoalProgress::savedCents).sum(),
            gs.stream().mapToLong(p -> p.goal().targetCents()).sum());
    }
}
