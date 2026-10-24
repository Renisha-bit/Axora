package com.axora;

import com.axora.dao.sqlite.SQLiteAnalyticsDao;
import com.axora.model.*;
import com.axora.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccountServiceTest {

    private AccountService svc;
    private TransactionService tx;
    private long food, salary, cash;

    @BeforeEach
    void setUp() throws Exception {
        TestDb.fresh();
        svc = new AccountService();
        tx = new TransactionService();
        food = TestDb.categoryId("Food & Dining");
        salary = TestDb.categoryId("Salary");
        cash = svc.getAccounts().get(0).id();
    }

    private static LocalDate day(int n) { return LocalDate.now().minusDays(n); }

    private long balance(long accountId) {
        return svc.getBalances(null).stream().filter(b -> b.account().id() == accountId).findFirst().orElseThrow().balanceCents();
    }

    @Test
    void migrationCreatesDefaultAccount() {
        assertEquals(1, svc.getAccounts().size());
        assertTrue(svc.getAccounts().get(0).isDefault());
        assertEquals("INR", svc.getAccounts().get(0).currency());
    }

    @Test
    void balanceIsOpeningPlusIncomeMinusExpense() {
        long bank = svc.createAccount("HDFC", Account.Type.BANK, "1000.50", "INR", "main");
        tx.addTransaction(Transaction.Type.INCOME, "500", salary, day(2), "", "", bank);
        tx.addTransaction(Transaction.Type.EXPENSE, "120.25", food, day(1), "", "", bank);
        assertEquals(1000_50 + 500_00 - 120_25, balance(bank));
        assertEquals(0, balance(cash), "other account unaffected");
    }

    @Test
    void transactionWithoutAccountGoesToDefault() {
        tx.addTransaction(Transaction.Type.INCOME, "100", salary, day(1), "", "");
        assertEquals(100_00, balance(cash));
        assertEquals(cash, tx.getAllTransactions().get(0).getAccountId());
    }

    @Test
    void balanceStaysConsistentAfterEditAndDelete() {
        long bank = svc.createAccount("Bank", Account.Type.BANK, "0", "INR", "");
        long id = tx.addTransaction(Transaction.Type.EXPENSE, "200", food, day(3), "", "", bank);
        assertEquals(-200_00, balance(bank));

        tx.updateTransaction(id, Transaction.Type.EXPENSE, "50", food, day(3), "", "", bank);   // amount edit
        assertEquals(-50_00, balance(bank));

        tx.updateTransaction(id, Transaction.Type.EXPENSE, "50", food, day(3), "", "", cash);   // move accounts
        assertEquals(0, balance(bank));
        assertEquals(-50_00, balance(cash));

        tx.updateTransaction(id, Transaction.Type.INCOME, "50", salary, day(3), "", "");        // type edit, account kept
        assertEquals(50_00, balance(cash));

        tx.deleteTransaction(id);
        assertEquals(0, balance(cash));
    }

    @Test
    void transferMovesMoneyButIsNeitherIncomeNorExpense() {
        long savings = svc.createAccount("Savings", Account.Type.SAVINGS, "0", "INR", "");
        tx.addTransaction(Transaction.Type.INCOME, "1000", salary, day(5), "", "");
        long tid = svc.createTransfer(cash, savings, "300", day(4), "to savings");

        assertEquals(700_00, balance(cash));
        assertEquals(300_00, balance(savings));
        long total = svc.getBalances(null).stream().mapToLong(AccountBalance::balanceCents).sum();
        assertEquals(1000_00, total, "transfers must not create or destroy money");

        var a = new SQLiteAnalyticsDao();
        assertEquals(1000_00, a.sumCents(Transaction.Type.INCOME, null, null, day(30), LocalDate.now()));
        assertEquals(0, a.sumCents(Transaction.Type.EXPENSE, null, null, day(30), LocalDate.now()));

        svc.updateTransfer(tid, cash, savings, "100", day(4), "edited");
        assertEquals(900_00, balance(cash));
        assertEquals(100_00, balance(savings));
        svc.deleteTransfer(tid);
        assertEquals(1000_00, balance(cash));
        assertEquals(0, balance(savings));
    }

    @Test
    void balanceAsOfDateIgnoresLaterActivity() {
        tx.addTransaction(Transaction.Type.INCOME, "100", salary, day(10), "", "");
        tx.addTransaction(Transaction.Type.INCOME, "50",  salary, day(1), "", "");
        assertEquals(100_00, svc.getBalances(day(5)).get(0).balanceCents());
        assertEquals(150_00, svc.getBalances(null).get(0).balanceCents());
    }

    @Test
    void rejectsInvalidTransfers() {
        long usd = svc.createAccount("Dollar", Account.Type.BANK, "0", "USD", "");
        long other = svc.createAccount("Other", Account.Type.BANK, "0", "INR", "");
        assertThrows(IllegalArgumentException.class, () -> svc.createTransfer(cash, cash, "10", day(1), ""), "same account");
        assertThrows(IllegalArgumentException.class, () -> svc.createTransfer(cash, other, "0", day(1), ""));
        assertThrows(IllegalArgumentException.class, () -> svc.createTransfer(cash, other, "-5", day(1), ""));
        assertThrows(IllegalArgumentException.class, () -> svc.createTransfer(cash, usd, "10", day(1), ""), "currency mismatch");
        assertThrows(IllegalArgumentException.class, () -> svc.createTransfer(cash, 9999, "10", day(1), ""));
        assertThrows(IllegalArgumentException.class, () -> svc.createTransfer(cash, other, "10", LocalDate.now().plusDays(10), ""));
        assertTrue(svc.getTransfers().isEmpty());
    }

    @Test
    void validatesAccountFields() {
        assertThrows(IllegalArgumentException.class, () -> svc.createAccount("", Account.Type.BANK, "0", "INR", ""));
        assertThrows(IllegalArgumentException.class, () -> svc.createAccount("X", null, "0", "INR", ""));
        assertThrows(IllegalArgumentException.class, () -> svc.createAccount("X", Account.Type.BANK, "abc", "INR", ""));
        assertThrows(IllegalArgumentException.class, () -> svc.createAccount("X", Account.Type.BANK, "0", "ZZZZ", ""));
        assertThrows(IllegalArgumentException.class, () -> svc.createAccount("X", Account.Type.BANK, "-1", "INR", ""), "negative non-credit");
        assertDoesNotThrow(() -> svc.createAccount("Card", Account.Type.CREDIT, "-2500", "inr", ""));
        assertEquals("INR", svc.getAccounts().stream().filter(a -> a.name().equals("Card")).findFirst().orElseThrow().currency());
        assertThrows(IllegalArgumentException.class, () -> svc.createAccount("card", Account.Type.BANK, "0", "INR", ""), "case-insensitive duplicate");
    }

    @Test
    void deleteIsBlockedWhileHistoryExistsAndForTheLastAccount() {
        long bank = svc.createAccount("Bank", Account.Type.BANK, "0", "INR", "");
        long tid = tx.addTransaction(Transaction.Type.EXPENSE, "10", food, day(1), "", "", bank);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> svc.deleteAccount(bank));
        assertTrue(e.getMessage().contains("1 transaction"));
        assertEquals(1, tx.getAllTransactions().size(), "transaction not orphaned");

        tx.deleteTransaction(tid);
        long tr = svc.createTransfer(cash, bank, "5", day(1), "");
        assertThrows(IllegalArgumentException.class, () -> svc.deleteAccount(bank), "transfer history blocks");
        svc.deleteTransfer(tr);
        svc.deleteAccount(bank);
        assertEquals(1, svc.getAccounts().size());
        assertThrows(IllegalArgumentException.class, () -> svc.deleteAccount(cash), "cannot delete last account");
    }

    @Test
    void invalidAccountIdOnTransactionIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> tx.addTransaction(Transaction.Type.EXPENSE, "10", food, day(1), "", "", 9999));
    }

    @Test
    void historyMergesTransactionsAndTransfersNewestFirst() {
        long savings = svc.createAccount("Savings", Account.Type.SAVINGS, "0", "INR", "");
        tx.addTransaction(Transaction.Type.INCOME, "100", salary, day(5), "pay", "");
        svc.createTransfer(cash, savings, "40", day(3), "save");
        tx.addTransaction(Transaction.Type.EXPENSE, "10", food, day(1), "lunch", "");
        List<AccountActivity> h = svc.history(cash, 50);
        assertEquals(List.of("EXPENSE", "TRANSFER_OUT", "INCOME"), h.stream().map(AccountActivity::kind).toList());
        assertEquals(-40_00, h.get(1).signedCents());
        assertEquals(List.of("TRANSFER_IN"), svc.history(savings, 50).stream().map(AccountActivity::kind).toList());
        assertEquals(1, svc.history(cash, 1).size());
    }

    @Test
    void totalsAreGroupedPerCurrency() {
        svc.createAccount("USD acct", Account.Type.BANK, "10", "USD", "");
        tx.addTransaction(Transaction.Type.INCOME, "100", salary, day(1), "", "");
        var t = svc.totalsByCurrency(null);
        assertEquals(100_00, t.get("INR"));
        assertEquals(10_00, t.get("USD"));
    }
}
