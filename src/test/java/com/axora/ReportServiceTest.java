package com.axora;

import com.axora.export.ReportCsvExporter;
import com.axora.model.*;
import com.axora.service.*;
import com.axora.service.ReportService.FinancialReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportServiceTest {

    private ReportService report;
    private TransactionService tx;
    private AccountService accounts;
    private BudgetService budgets;
    private long food, rent, salary, cash, bank;

    private static LocalDate d(int y, int m, int day) { return LocalDate.of(y, m, day); }

    @BeforeEach
    void setUp() throws Exception {
        TestDb.fresh();
        report = new ReportService();
        tx = new TransactionService();
        accounts = new AccountService();
        budgets = new BudgetService();
        food = TestDb.categoryId("Food & Dining");
        rent = TestDb.categoryId("Rent / Housing");
        salary = TestDb.categoryId("Salary");
        cash = accounts.getAccounts().get(0).id();
        bank = accounts.createAccount("Bank", Account.Type.BANK, "500", "INR", "");

        tx.addTransaction(Transaction.Type.INCOME,  "1000", salary, d(2026,1,5),  "", "", cash);
        tx.addTransaction(Transaction.Type.EXPENSE, "300",  food,   d(2026,1,20), "", "", cash);
        tx.addTransaction(Transaction.Type.INCOME,  "2000", salary, d(2026,2,1),  "", "", bank);
        tx.addTransaction(Transaction.Type.EXPENSE, "500",  food,   d(2026,2,10), "", "", bank);
        tx.addTransaction(Transaction.Type.EXPENSE, "100",  rent,   d(2026,2,28), "", "", cash);
        // outside the Jan..Mar range
        tx.addTransaction(Transaction.Type.EXPENSE, "7777", food,   d(2025,12,31), "", "", cash);
        tx.addTransaction(Transaction.Type.EXPENSE, "8888", food,   d(2026,4,1),   "", "", cash);
        // transfer inside range: must not affect income/expense
        accounts.createTransfer(cash, bank, "250", d(2026,2,15), "");
    }

    @Test
    void totalsRespectRangeBoundariesInclusively() {
        FinancialReport r = report.build(d(2026,1,1), d(2026,3,31), null);
        assertEquals(3000_00, r.incomeCents());
        assertEquals(900_00, r.expenseCents());
        assertEquals(2100_00, r.netCents());
        // boundary days are included
        assertEquals(300_00, report.build(d(2026,1,20), d(2026,1,20), null).expenseCents());
        assertEquals(8888_00, report.build(d(2026,4,1), d(2026,4,1), null).expenseCents());
    }

    @Test
    void monthlyRowsFillGapsAndAccumulateNet() {
        FinancialReport r = report.build(d(2026,1,1), d(2026,3,31), null);
        List<ReportService.MonthRow> m = r.months();
        assertEquals(3, m.size());
        assertEquals(YearMonth.of(2026,1), m.get(0).month());
        assertEquals(700_00, m.get(0).netCents());
        assertEquals(1400_00, m.get(1).netCents());          // 2000 - 500 - 100
        assertEquals(0, m.get(2).netCents());                // empty month still listed
        assertEquals(700_00, m.get(0).cumulativeNetCents());
        assertEquals(2100_00, m.get(1).cumulativeNetCents());
        assertEquals(2100_00, m.get(2).cumulativeNetCents());
    }

    @Test
    void spendingByCategoryIsOrderedAndSumsToExpenses() {
        FinancialReport r = report.build(d(2026,1,1), d(2026,3,31), null);
        assertEquals("Food & Dining", r.categories().get(0).categoryName());
        assertEquals(800_00, r.categories().get(0).totalCents());
        assertEquals(100_00, r.categories().get(1).totalCents());
        assertEquals(r.expenseCents(), r.categories().stream().mapToLong(c -> c.totalCents()).sum());
    }

    @Test
    void accountFilterRestrictsFiguresButKeepsTransfersOut() {
        FinancialReport bankOnly = report.build(d(2026,1,1), d(2026,3,31), bank);
        assertEquals(2000_00, bankOnly.incomeCents());
        assertEquals(500_00, bankOnly.expenseCents());
        assertEquals(1, bankOnly.balances().size());
        // bank: 500 opening + 2000 - 500 + 250 transfer in (as of Mar 31)
        assertEquals(2250_00, bankOnly.balances().get(0).balanceCents());
        FinancialReport cashOnly = report.build(d(2026,1,1), d(2026,3,31), cash);
        assertEquals(1000_00, cashOnly.incomeCents());
        assertEquals(400_00, cashOnly.expenseCents());
    }

    @Test
    void balancesAreAsOfRangeEnd() {
        FinancialReport early = report.build(d(2026,1,1), d(2026,1,31), null);
        long cashBal = early.balances().stream().filter(b -> b.account().id() == cash).findFirst().orElseThrow().balanceCents();
        long bankBal = early.balances().stream().filter(b -> b.account().id() == bank).findFirst().orElseThrow().balanceCents();
        assertEquals(1000_00 - 300_00 - 7777_00, cashBal, "includes earlier Dec 2025 expense, excludes later activity");
        assertEquals(500_00, bankBal);
        assertEquals(cashBal + bankBal, early.balanceTotalsByCurrency().get("INR"));
    }

    @Test
    void budgetVsActualEvaluatedAtRangeEnd() {
        budgets.create("Food Feb", food, "1000", Budget.Period.CUSTOM, d(2026,2,1), d(2026,2,28));
        budgets.create("All Mar", null, "100", Budget.Period.MONTHLY, null, null);
        FinancialReport r = report.build(d(2026,1,1), d(2026,2,28), null);
        BudgetStatus feb = r.budgets().stream().filter(b -> b.budget().name().equals("Food Feb")).findFirst().orElseThrow();
        assertEquals(500_00, feb.spentCents());
        BudgetStatus monthly = r.budgets().stream().filter(b -> b.budget().name().equals("All Mar")).findFirst().orElseThrow();
        assertEquals(d(2026,2,1), monthly.from());
        assertEquals(600_00, monthly.spentCents(), "Feb expenses: 500 + 100");
        assertEquals(BudgetStatus.Level.EXCEEDED, monthly.level());
    }

    @Test
    void emptyRangeProducesEmptyReportNotErrors() {
        FinancialReport r = report.build(d(2024,1,1), d(2024,3,31), null);
        assertTrue(r.isEmpty());
        assertEquals(3, r.months().size());
        assertTrue(r.categories().isEmpty());
        assertEquals(0, r.netCents());
    }

    @Test
    void rejectsInvalidRanges() {
        assertThrows(IllegalArgumentException.class, () -> report.build(d(2026,3,1), d(2026,1,1), null));
        assertThrows(IllegalArgumentException.class, () -> report.build(null, d(2026,1,1), null));
        assertThrows(IllegalArgumentException.class, () -> report.build(d(2000,1,1), d(2026,1,1), null), "absurd span");
    }

    @Test
    void defaultRangeIsYearToDate() {
        LocalDate[] r = ReportService.defaultRange(d(2026,10,22));
        assertEquals(d(2026,1,1), r[0]);
        assertEquals(d(2026,10,22), r[1]);
    }

    @Test
    void mixedCurrencyFlagged() {
        assertFalse(report.build(d(2026,1,1), d(2026,3,31), null).mixedCurrencies());
        accounts.createAccount("Dollars", Account.Type.BANK, "0", "USD", "");
        assertTrue(report.build(d(2026,1,1), d(2026,3,31), null).mixedCurrencies());
    }

    @Test
    void csvExportContainsSectionsAndEscapesProperly(@TempDir File dir) throws Exception {
        accounts.createAccount("Odd, \"name\"", Account.Type.BANK, "0", "INR", "");
        FinancialReport r = report.build(d(2026,1,1), d(2026,3,31), null);
        File f = new File(dir, "report.csv");
        new ReportCsvExporter().export(r, f);
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.startsWith("\uFEFF"), "UTF-8 BOM");
        for (String section : new String[]{"Summary", "Monthly", "Spending by category", "Budget vs actual", "Account balances"})
            assertTrue(text.contains(section), section);
        assertTrue(text.contains("Net savings,2100.00"));
        assertTrue(text.contains("2026-01,1000.00,300.00,700.00,700.00"));
        assertTrue(text.contains("\"Odd, \"\"name\"\"\""), "RFC 4180 quoting");
    }
}
