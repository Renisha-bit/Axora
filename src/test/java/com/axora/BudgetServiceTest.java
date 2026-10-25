package com.axora;

import com.axora.dao.sqlite.*;
import com.axora.model.*;
import com.axora.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BudgetServiceTest {

    private BudgetService svc;
    private TransactionService tx;
    private AccountService accounts;
    private long food, rent, salary;

    @BeforeEach
    void setUp() throws Exception {
        TestDb.fresh();
        svc = new BudgetService();
        tx = new TransactionService();
        accounts = new AccountService();
        food = TestDb.categoryId("Food & Dining");
        rent = TestDb.categoryId("Rent / Housing");
        salary = TestDb.categoryId("Salary");
    }

    private void spend(long cat, String amt, LocalDate d) { tx.addTransaction(Transaction.Type.EXPENSE, amt, cat, d, "", ""); }

    private static LocalDate d(int y, int m, int day) { return LocalDate.of(y, m, day); }

    // ---- pure calculation
    @Test
    void computeFieldsAndLevels() {
        Budget b = new Budget(1, "B", null, null, 100_00, Budget.Period.MONTHLY, null, null);
        BudgetStatus ok = BudgetService.compute(b, d(2026,1,1), d(2026,1,31), 79_99);
        assertEquals(BudgetStatus.Level.OK, ok.level());
        assertEquals(20_01, ok.remainingCents());
        assertEquals(new BigDecimal("80.0"), ok.percentUsed());   // 79.99% rounds to 80.0 but level uses exact cents

        assertEquals(BudgetStatus.Level.WARNING, BudgetService.compute(b, d(2026,1,1), d(2026,1,31), 80_00).level());
        assertEquals(BudgetStatus.Level.WARNING, BudgetService.compute(b, d(2026,1,1), d(2026,1,31), 100_00).level(), "exactly at limit is still not exceeded");
        BudgetStatus over = BudgetService.compute(b, d(2026,1,1), d(2026,1,31), 100_01);
        assertEquals(BudgetStatus.Level.EXCEEDED, over.level());
        assertEquals(-1, over.remainingCents());
    }

    // ---- period boundaries
    @Test
    void monthWindowCoversWholeCalendarMonthIncludingLeapFebruary() {
        Budget b = new Budget(1, "B", null, null, 1, Budget.Period.MONTHLY, null, null);
        LocalDate[] w = BudgetPeriods.window(b, d(2028, 2, 10));
        assertEquals(d(2028,2,1), w[0]);
        assertEquals(d(2028,2,29), w[1]);
    }

    @Test
    void weekWindowIsMondayToSunday() {
        Budget b = new Budget(1, "B", null, null, 1, Budget.Period.WEEKLY, null, null);
        LocalDate[] w = BudgetPeriods.window(b, d(2026, 10, 22)); // Thursday
        assertEquals(d(2026,10,19), w[0]);
        assertEquals(d(2026,10,25), w[1]);
        assertArrayEquals(w, BudgetPeriods.window(b, d(2026,10,19)));   // Monday itself
        assertArrayEquals(w, BudgetPeriods.window(b, d(2026,10,25)));   // Sunday
        assertEquals(d(2026,10,26), BudgetPeriods.window(b, d(2026,10,26))[0]);
    }

    // ---- real calculations against persisted transactions
    @Test
    void spentUsesOnlyExpensesInWindow_excludesIncomeTransfersAndOtherMonths() {
        long id = svc.create("Food", food, "500", Budget.Period.MONTHLY, null, null);
        spend(food, "100", d(2026, 1, 1));     // first day  -> counts
        spend(food, "50",  d(2026, 1, 31));    // last day   -> counts
        spend(food, "999", d(2025, 12, 31));     // previous month -> no
        spend(food, "999", d(2026, 2, 1));     // next month -> no
        spend(rent, "300", d(2026, 1, 5));     // other category -> no
        tx.addTransaction(Transaction.Type.INCOME, "5000", salary, d(2026,1,2), "", "");   // income -> no
        long savings = accounts.createAccount("Savings", Account.Type.SAVINGS, "0", "INR", "");
        accounts.createTransfer(1, savings, "1000", d(2026,1,3), "move");                  // transfer -> no

        BudgetStatus s = svc.statuses(d(2026,1,15), null).stream().filter(x -> x.budget().id() == id).findFirst().orElseThrow();
        assertEquals(150_00, s.spentCents());
        assertEquals(350_00, s.remainingCents());
        assertEquals(new BigDecimal("30.0"), s.percentUsed());
        assertEquals(BudgetStatus.Level.OK, s.level());
    }

    @Test
    void overallBudgetCountsAllExpenseCategories() {
        svc.create("All", null, "1000", Budget.Period.MONTHLY, null, null);
        spend(food, "100", d(2026,1,2)); spend(rent, "200", d(2026,1,3));
        assertEquals(300_00, svc.statuses(d(2026,1,10), null).get(0).spentCents());
    }

    @Test
    void periodFilterAndCustomWindow() {
        svc.create("M", null, "100", Budget.Period.MONTHLY, null, null);
        svc.create("C", food, "100", Budget.Period.CUSTOM, d(2026,1,10), d(2026,1,20));
        spend(food, "40", d(2026,1,9)); spend(food, "30", d(2026,1,10)); spend(food, "20", d(2026,1,20)); spend(food, "10", d(2026,1,21));
        List<BudgetStatus> custom = svc.statuses(d(2026,6,1), Budget.Period.CUSTOM);
        assertEquals(1, custom.size());
        assertEquals(50_00, custom.get(0).spentCents(), "custom window is fixed, inclusive on both ends");
        assertEquals(1, svc.statuses(d(2026,1,1), Budget.Period.MONTHLY).size());
    }

    // ---- validation / conflicts
    @Test
    void rejectsDuplicateAndOverlappingBudgets() {
        svc.create("Food monthly", food, "500", Budget.Period.MONTHLY, null, null);
        assertThrows(IllegalArgumentException.class, () -> svc.create("again", food, "600", Budget.Period.MONTHLY, null, null));
        assertDoesNotThrow(() -> svc.create("Food weekly", food, "100", Budget.Period.WEEKLY, null, null));
        assertDoesNotThrow(() -> svc.create("Overall", null, "5000", Budget.Period.MONTHLY, null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.create("Overall 2", null, "1", Budget.Period.MONTHLY, null, null));

        svc.create("Q", rent, "100", Budget.Period.CUSTOM, d(2026,1,1), d(2026,3,31));
        assertThrows(IllegalArgumentException.class, () -> svc.create("Q-overlap", rent, "100", Budget.Period.CUSTOM, d(2026,3,31), d(2026,5,1)));
        assertDoesNotThrow(() -> svc.create("Q2", rent, "100", Budget.Period.CUSTOM, d(2026,4,1), d(2026,6,30)));
    }

    @Test
    void editingABudgetDoesNotConflictWithItself() {
        long id = svc.create("Food", food, "500", Budget.Period.MONTHLY, null, null);
        assertDoesNotThrow(() -> svc.update(id, "Food", food, "750", Budget.Period.MONTHLY, null, null));
        assertEquals(750_00, svc.getBudgets().get(0).amountCents());
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> svc.create("x", food, "0", Budget.Period.MONTHLY, null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.create("x", food, "-5", Budget.Period.MONTHLY, null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.create("x", food, "abc", Budget.Period.MONTHLY, null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.create("x", food, "10", Budget.Period.CUSTOM, null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.create("x", food, "10", Budget.Period.CUSTOM, d(2026,5,2), d(2026,5,1)));
        assertThrows(IllegalArgumentException.class, () -> svc.create("x", salary, "10", Budget.Period.MONTHLY, null, null), "income category");
        assertThrows(IllegalArgumentException.class, () -> svc.create("x", 99999L, "10", Budget.Period.MONTHLY, null, null), "unknown category");
        assertTrue(svc.getBudgets().isEmpty());
    }

    @Test
    void deleteRemovesBudgetAndBlankNameGetsDefault() {
        long id = svc.create("", food, "10", Budget.Period.WEEKLY, null, null);
        assertEquals("Food & Dining – Weekly", svc.getBudgets().get(0).name());
        svc.delete(id);
        assertTrue(svc.getBudgets().isEmpty());
    }
}
