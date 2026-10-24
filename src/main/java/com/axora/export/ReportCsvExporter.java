package com.axora.export;

import com.axora.model.AccountBalance;
import com.axora.model.BudgetStatus;
import com.axora.service.ReportService.FinancialReport;
import com.axora.service.ReportService.MonthRow;
import com.axora.dao.CategoryTotal;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Writes a report as one CSV with titled sections (amounts in rupees, plain decimals). */
public class ReportCsvExporter {

    private static String money(long cents) { return BigDecimal.valueOf(cents, 2).toPlainString(); }

    public List<String[]> toRows(FinancialReport r) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Axora report", r.from().toString(), r.to().toString()});
        rows.add(new String[0]);
        rows.add(new String[]{"Summary"});
        rows.add(new String[]{"Income", money(r.incomeCents())});
        rows.add(new String[]{"Expenses", money(r.expenseCents())});
        rows.add(new String[]{"Net savings", money(r.netCents())});
        rows.add(new String[0]);
        rows.add(new String[]{"Monthly"});
        rows.add(new String[]{"Month", "Income", "Expenses", "Net", "Cumulative net"});
        for (MonthRow m : r.months())
            rows.add(new String[]{m.month().toString(), money(m.incomeCents()), money(m.expenseCents()),
                                  money(m.netCents()), money(m.cumulativeNetCents())});
        rows.add(new String[0]);
        rows.add(new String[]{"Spending by category"});
        rows.add(new String[]{"Category", "Amount", "Share %"});
        for (CategoryTotal c : r.categories()) {
            String share = r.expenseCents() == 0 ? "0.0" : BigDecimal.valueOf(c.totalCents() * 100L)
                .divide(BigDecimal.valueOf(r.expenseCents()), 1, java.math.RoundingMode.HALF_UP).toPlainString();
            rows.add(new String[]{c.categoryName(), money(c.totalCents()), share});
        }
        rows.add(new String[0]);
        rows.add(new String[]{"Budget vs actual (as of " + r.to() + ")"});
        rows.add(new String[]{"Budget", "Scope", "Period window", "Limit", "Spent", "Remaining", "Used %", "Status"});
        for (BudgetStatus b : r.budgets())
            rows.add(new String[]{b.budget().name(), b.budget().isOverall() ? "Overall" : b.budget().categoryName(),
                b.from() + " to " + b.to(), money(b.budget().amountCents()), money(b.spentCents()),
                money(b.remainingCents()), b.percentUsed().toPlainString(), b.level().name()});
        rows.add(new String[0]);
        rows.add(new String[]{"Account balances (as of " + r.to() + ")"});
        rows.add(new String[]{"Account", "Type", "Currency", "Balance"});
        for (AccountBalance b : r.balances())
            rows.add(new String[]{b.account().name(), b.account().type().toString(), b.account().currency(), money(b.balanceCents())});
        return rows;
    }

    public void export(FinancialReport report, File target) throws IOException {
        new CsvExporter().exportRows(toRows(report), target);
    }
}
