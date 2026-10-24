package com.axora;

import com.axora.model.*;
import com.axora.model.GoalProgress.Status;
import com.axora.service.SavingsGoalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SavingsGoalServiceTest {

    private SavingsGoalService svc;

    @BeforeEach
    void setUp() { TestDb.fresh(); svc = new SavingsGoalService(); }

    private static LocalDate d(int y, int m, int day) { return LocalDate.of(y, m, day); }

    private static SavingsGoal goal(long target, LocalDate start, LocalDate end) {
        return new SavingsGoal(1, "G", target, end, "", start);
    }

    // ---- pure status rules
    @Test
    void statusRules() {
        SavingsGoal g = goal(1000_00, d(2026,1,1), d(2026,1,11));          // 10 day goal
        assertEquals(Status.ON_TRACK, SavingsGoalService.progress(g, 0, d(2026,1,1)).status());          // nothing expected yet
        assertEquals(Status.BEHIND,   SavingsGoalService.progress(g, 499_99, d(2026,1,6)).status());     // half time, <50%
        assertEquals(Status.ON_TRACK, SavingsGoalService.progress(g, 500_00, d(2026,1,6)).status());
        assertEquals(Status.COMPLETED, SavingsGoalService.progress(g, 1000_00, d(2026,1,6)).status());
        assertEquals(Status.COMPLETED, SavingsGoalService.progress(g, 1200_00, d(2026,2,1)).status(), "completed beats overdue");
        assertEquals(Status.OVERDUE,  SavingsGoalService.progress(g, 999_99, d(2026,1,12)).status());
    }

    @Test
    void progressNumbers() {
        SavingsGoal g = goal(400_00, d(2026,1,1), d(2026,12,31));
        GoalProgress p = SavingsGoalService.progress(g, 100_00, d(2026,2,1));
        assertEquals(300_00, p.remainingCents());
        assertEquals(new BigDecimal("25.0"), p.percent());
        GoalProgress over = SavingsGoalService.progress(g, 500_00, d(2026,2,1));
        assertEquals(0, over.remainingCents());
        assertEquals(new BigDecimal("125.0"), over.percent());
    }

    // ---- persistence-backed behaviour
    private long newGoal() { return svc.create("Laptop", "1000", LocalDate.now().plusDays(100), "for work"); }

    @Test
    void contributionsAndWithdrawalsTrackSavedAmountWithHistory() {
        long id = newGoal();
        svc.contribute(id, "300", LocalDate.now().minusDays(2), "first");
        svc.contribute(id, "200.50", LocalDate.now().minusDays(1), "");
        svc.withdraw(id, "100", LocalDate.now(), "oops");
        GoalProgress p = svc.progress(id, LocalDate.now());
        assertEquals(400_50, p.savedCents());
        assertEquals(599_50, p.remainingCents());

        List<GoalEntry> h = svc.history(id);
        assertEquals(3, h.size());
        assertEquals(GoalEntry.Type.WITHDRAWAL, h.get(0).type(), "newest first");
        assertEquals(400_50, h.stream().mapToLong(GoalEntry::signedCents).sum());
    }

    @Test
    void rejectsInvalidAmountsAndOverWithdrawal() {
        long id = newGoal();
        svc.contribute(id, "50", LocalDate.now(), "");
        assertThrows(IllegalArgumentException.class, () -> svc.contribute(id, "-5", LocalDate.now(), ""));
        assertThrows(IllegalArgumentException.class, () -> svc.contribute(id, "0", LocalDate.now(), ""));
        assertThrows(IllegalArgumentException.class, () -> svc.contribute(id, "x", LocalDate.now(), ""));
        assertThrows(IllegalArgumentException.class, () -> svc.contribute(id, "5", LocalDate.now().plusDays(1), ""), "future date");
        assertThrows(IllegalArgumentException.class, () -> svc.contribute(id, "5", null, ""));
        assertThrows(IllegalArgumentException.class, () -> svc.withdraw(id, "50.01", LocalDate.now(), ""), "more than saved");
        assertDoesNotThrow(() -> svc.withdraw(id, "50", LocalDate.now(), ""));
        assertEquals(0, svc.progress(id, LocalDate.now()).savedCents());
        assertEquals(2, svc.history(id).size(), "rejected entries leave no trace");
    }

    @Test
    void validatesGoalFields() {
        assertThrows(IllegalArgumentException.class, () -> svc.create("", "10", LocalDate.now().plusDays(5), ""));
        assertThrows(IllegalArgumentException.class, () -> svc.create("G", "0", LocalDate.now().plusDays(5), ""));
        assertThrows(IllegalArgumentException.class, () -> svc.create("G", "-1", LocalDate.now().plusDays(5), ""));
        assertThrows(IllegalArgumentException.class, () -> svc.create("G", "10", null, ""));
        assertThrows(IllegalArgumentException.class, () -> svc.create("G", "10", LocalDate.now().minusDays(1), ""));
        assertTrue(svc.progressList(LocalDate.now()).isEmpty());
    }

    @Test
    void editKeepsLedgerAndRecomputesStatus() {
        long id = newGoal();
        svc.contribute(id, "600", LocalDate.now(), "");
        svc.update(id, "Laptop Pro", "500", LocalDate.now().plusDays(10), "bigger");
        GoalProgress p = svc.progress(id, LocalDate.now());
        assertEquals("Laptop Pro", p.goal().name());
        assertEquals(Status.COMPLETED, p.status());
        assertEquals(1, svc.history(id).size());
        assertThrows(IllegalArgumentException.class, () -> svc.update(id, "x", "10", LocalDate.now().minusDays(5), ""), "before start");
    }

    @Test
    void deleteRemovesGoalAndItsHistory() {
        long id = newGoal();
        svc.contribute(id, "10", LocalDate.now(), "");
        svc.delete(id);
        assertTrue(svc.progressList(LocalDate.now()).isEmpty());
        assertTrue(svc.history(id).isEmpty());
    }

    @Test
    void goalContributionsDoNotTouchAccountBalances() {
        long id = newGoal();
        svc.contribute(id, "500", LocalDate.now(), "");
        assertEquals(0, new com.axora.service.AccountService().getBalances(null).get(0).balanceCents());
    }
}
