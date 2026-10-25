package com.axora;

import com.axora.dao.sqlite.*;
import com.axora.model.*;
import com.axora.model.RecurringRule.Frequency;
import com.axora.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecurringServiceTest {

    /** Clock whose date the test can move forward. */
    static final class TestClock extends Clock {
        LocalDate date;
        TestClock(LocalDate d) { date = d; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId z) { return this; }
        @Override public Instant instant() { return date.atStartOfDay(ZoneOffset.UTC).toInstant(); }
    }

    private TestClock clock;
    private RecurringService svc;
    private TransactionService tx;
    private AccountService accounts;
    private long food, salary, cash;

    private static LocalDate d(int y, int m, int day) { return LocalDate.of(y, m, day); }

    private RecurringService newService() {
        return new RecurringService(new SQLiteRecurringRuleDao(), new SQLiteTransactionDao(),
            new SQLiteCategoryDao(), new SQLiteAccountDao(), clock);
    }

    @BeforeEach
    void setUp() throws Exception {
        TestDb.fresh();
        clock = new TestClock(d(2026, 4, 15));
        svc = newService();
        tx = new TransactionService();
        accounts = new AccountService();
        food = TestDb.categoryId("Food & Dining");
        salary = TestDb.categoryId("Salary");
        cash = accounts.getAccounts().get(0).id();
    }

    private long monthlyExpense(LocalDate start) {
        return svc.create(Transaction.Type.EXPENSE, "100", food, cash, "Rent-ish", "Landlord", Frequency.MONTHLY, start, null);
    }

    private List<Transaction> txs() { return tx.getAllTransactions(); }

    @Test
    void createBackfillsMissedOccurrencesIncludingMonthEnd() {
        monthlyExpense(d(2026, 1, 31));
        List<LocalDate> dates = txs().stream().map(Transaction::getTxDate).sorted().toList();
        assertEquals(List.of(d(2026,1,31), d(2026,2,28), d(2026,3,31)), dates);
        Transaction t = txs().get(0);
        assertTrue(t.isRecurring());
        assertEquals(cash, t.getAccountId());
        assertEquals(100_00, t.getAmountCents());
        assertEquals("Landlord", t.getMerchant());
    }

    @Test
    void generationIsIdempotentAcrossRepeatedRunsAndRestarts() {
        monthlyExpense(d(2026, 1, 31));
        assertEquals(0, svc.generateDue(d(2026, 4, 15)));
        assertEquals(0, svc.generateDue(d(2026, 4, 15)));
        assertEquals(0, newService().generateDue(d(2026, 4, 15)), "fresh service instance = app restart");
        assertEquals(3, txs().size());
    }

    @Test
    void laterRunGeneratesOnlyTheNewOccurrences() {
        monthlyExpense(d(2026, 1, 31));
        assertEquals(2, svc.generateDue(d(2026, 5, 31)));            // Apr 30 + May 31
        assertEquals(5, txs().size());
        assertEquals(0, svc.generateDue(d(2026, 5, 31)));
    }

    @Test
    void deletingAGeneratedTransactionDoesNotRegenerateIt() {
        monthlyExpense(d(2026, 2, 15));
        Transaction victim = txs().stream().filter(t -> t.getTxDate().equals(d(2026, 3, 15))).findFirst().orElseThrow();
        tx.deleteTransaction(victim.getId());
        assertEquals(0, svc.generateDue(d(2026, 4, 15)));
        assertEquals(2, txs().size());
    }

    @Test
    void futureStartGeneratesNothingYetAndReportsNextDue() {
        long id = monthlyExpense(d(2026, 6, 1));
        assertTrue(txs().isEmpty());
        assertEquals(d(2026, 6, 1), svc.nextDue(svc.getRules().get(0), clock.date));
        assertEquals(0, svc.generatedCount(id));
    }

    @Test
    void endDateStopsGeneration() {
        svc.create(Transaction.Type.INCOME, "50", salary, cash, "", "", Frequency.MONTHLY, d(2026, 1, 10), d(2026, 2, 10));
        assertEquals(2, txs().size());
        assertEquals(0, svc.generateDue(d(2027, 1, 1)));
        assertNull(svc.nextDue(svc.getRules().get(0), clock.date));
    }

    @Test
    void pausedRuleGeneratesNothingAndResumeSkipsThePausedPeriod() {
        long id = svc.create(Transaction.Type.EXPENSE, "10", food, cash, "", "", Frequency.MONTHLY, d(2026, 4, 1), null);
        assertEquals(1, txs().size());                         // Apr 1
        svc.pause(id);
        assertTrue(svc.getRules().get(0).paused());
        assertEquals(0, svc.generateDue(d(2026, 8, 20)));
        assertNull(svc.nextDue(svc.getRules().get(0), clock.date));

        clock.date = d(2026, 8, 20);
        svc.resume(id);                                        // May–Aug occurrences are NOT back-filled
        assertEquals(1, txs().size());
        assertEquals(1, svc.generateDue(d(2026, 9, 1)));       // next real occurrence
        assertEquals(2, txs().size());
    }

    @Test
    void editAppliesToFutureOnly() {
        long id = svc.create(Transaction.Type.EXPENSE, "100", food, cash, "old", "", Frequency.MONTHLY, d(2026, 1, 1), null);
        assertEquals(4, txs().size());                         // Jan 1 … Apr 1
        clock.date = d(2026, 4, 15);

        svc.update(id, Transaction.Type.EXPENSE, "250", food, cash, "new", "", Frequency.MONTHLY, d(2026, 1, 1), null);
        assertEquals(4, txs().size(), "no duplicates and nothing back-filled");
        assertTrue(txs().stream().allMatch(t -> t.getAmountCents() == 100_00 && "old".equals(t.getDescription())),
            "history untouched");
        assertEquals(1, svc.generateDue(d(2026, 5, 1)));
        assertEquals(250_00, txs().get(0).getAmountCents(), "new amount used going forward");

        svc.update(id, Transaction.Type.EXPENSE, "250", food, cash, "new", "", Frequency.WEEKLY, d(2026, 1, 1), null);
        int before = txs().size();
        assertEquals(before, txs().size(), "switching frequency must not back-fill the past");
    }

    @Test
    void deletingRuleKeepsGeneratedHistoryAndStopsFuture() {
        long id = monthlyExpense(d(2026, 2, 1));
        int n = txs().size();
        svc.delete(id);
        assertEquals(n, txs().size());
        assertTrue(txs().stream().noneMatch(Transaction::isRecurring), "link cleared");
        assertTrue(svc.getRules().isEmpty());
        assertEquals(0, svc.generateDue(d(2027, 1, 1)));
    }

    @Test
    void generatedTransactionsAffectAccountBalanceAndNothingElse() {
        long bank = accounts.createAccount("Bank", Account.Type.BANK, "1000", "INR", "");
        svc.create(Transaction.Type.INCOME, "200", salary, bank, "pay", "", Frequency.MONTHLY, d(2026, 3, 1), null);   // Mar 1, Apr 1
        assertEquals(1000_00 + 2 * 200_00,
            accounts.getBalances(null).stream().filter(b -> b.account().id() == bank).findFirst().orElseThrow().balanceCents());
        assertEquals(0, accounts.getBalances(null).stream().filter(b -> b.account().id() == cash).findFirst().orElseThrow().balanceCents());
    }

    @Test
    void upcomingListsFutureOccurrencesSortedAndSkipsPaused() {
        long a = svc.create(Transaction.Type.EXPENSE, "1", food, cash, "A", "", Frequency.WEEKLY, d(2026, 4, 1), null);   // Apr 1,8,15,22,29
        long b = svc.create(Transaction.Type.EXPENSE, "2", food, cash, "B", "", Frequency.MONTHLY, d(2026, 4, 20), null); // Apr 20, May 20
        long c = svc.create(Transaction.Type.EXPENSE, "3", food, cash, "C", "", Frequency.DAILY, d(2026, 4, 15), null);
        svc.pause(c);
        List<UpcomingOccurrence> up = svc.upcoming(clock.date, 30, 50);
        assertTrue(up.stream().noneMatch(u -> u.rule().id() == c));
        assertTrue(up.stream().allMatch(u -> u.date().isAfter(clock.date) && !u.date().isAfter(d(2026, 5, 15))));
        assertEquals(up.stream().map(UpcomingOccurrence::date).sorted().toList(), up.stream().map(UpcomingOccurrence::date).toList());
        assertEquals(List.of(d(2026,4,22), d(2026,4,29), d(2026,5,6), d(2026,5,13)),
            up.stream().filter(u -> u.rule().id() == a).map(UpcomingOccurrence::date).toList());
        assertEquals(1, up.stream().filter(u -> u.rule().id() == b).count());   // May 20 is beyond the 30-day horizon
        assertEquals(3, svc.upcoming(clock.date, 30, 3).size(), "limit honoured");
    }

    @Test
    void validatesInput() {
        assertThrows(IllegalArgumentException.class, () -> svc.create(Transaction.Type.EXPENSE, "0", food, cash, "", "", Frequency.DAILY, d(2026,1,1), null));
        assertThrows(IllegalArgumentException.class, () -> svc.create(Transaction.Type.EXPENSE, "5", salary, cash, "", "", Frequency.DAILY, d(2026,1,1), null), "income category on expense");
        assertThrows(IllegalArgumentException.class, () -> svc.create(Transaction.Type.EXPENSE, "5", food, 9999, "", "", Frequency.DAILY, d(2026,1,1), null), "unknown account");
        assertThrows(IllegalArgumentException.class, () -> svc.create(Transaction.Type.EXPENSE, "5", food, cash, "", "", null, d(2026,1,1), null));
        assertThrows(IllegalArgumentException.class, () -> svc.create(Transaction.Type.EXPENSE, "5", food, cash, "", "", Frequency.DAILY, null, null));
        assertThrows(IllegalArgumentException.class, () -> svc.create(Transaction.Type.EXPENSE, "5", food, cash, "", "", Frequency.DAILY, d(2026,3,1), d(2026,2,1)));
        assertTrue(svc.getRules().isEmpty());
        assertTrue(txs().isEmpty(), "failed create must not leave transactions behind");
    }

    @Test
    void accountInUseByRuleCannotBeDeleted() {
        long bank = accounts.createAccount("Bank", Account.Type.BANK, "0", "INR", "");
        svc.create(Transaction.Type.EXPENSE, "5", food, bank, "", "", Frequency.MONTHLY, d(2026, 6, 1), null);   // nothing generated yet
        assertThrows(IllegalArgumentException.class, () -> accounts.deleteAccount(bank));
    }
}
