package com.axora;

import com.axora.model.*;
import com.axora.service.*;
import com.axora.service.OverviewService.Overview;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class OverviewServiceTest {

    @Test
    void emptyDatabaseGivesZeroedOverview() {
        TestDb.fresh();
        Overview o = new OverviewService().build(LocalDate.now());
        assertEquals(0, o.budgetCount());
        assertEquals(0, o.upcomingCount());
        assertEquals(0, o.goalCount());
        assertEquals(0, o.balancesByCurrency().get("INR"));
    }

    @Test
    void aggregatesAllModules() throws Exception {
        TestDb.fresh();
        long food = TestDb.categoryId("Food & Dining"), salary = TestDb.categoryId("Salary");
        TransactionService tx = new TransactionService();
        tx.addTransaction(Transaction.Type.INCOME, "1000", salary, LocalDate.now(), "", "");
        tx.addTransaction(Transaction.Type.EXPENSE, "90", food, LocalDate.now(), "", "");
        new BudgetService().create("Food", food, "100", Budget.Period.MONTHLY, null, null);     // 90% -> near limit
        long cash = new AccountService().getAccounts().get(0).id();
        new RecurringService().create(Transaction.Type.EXPENSE, "40", food, cash, "", "", RecurringRule.Frequency.DAILY,
            LocalDate.now().plusDays(1), null);                                               // daily from tomorrow: 7 in next 7 days
        SavingsGoalService goals = new SavingsGoalService();
        long g = goals.create("G", "100", LocalDate.now().plusDays(30), "");
        goals.contribute(g, "100", LocalDate.now(), "");

        Overview o = new OverviewService().build(LocalDate.now());
        assertEquals(910_00, o.balancesByCurrency().get("INR"));
        assertEquals(1, o.budgetCount());
        assertEquals(1, o.budgetsNearLimit());
        assertEquals(0, o.budgetsExceeded());
        assertEquals(7, o.upcomingCount());
        assertEquals(7 * 40_00, o.upcomingExpenseCents());
        assertEquals(1, o.goalsCompleted());
        assertEquals(100_00, o.goalsSavedCents());
    }
}
