package com.axora.ui.controller;

import com.axora.dao.CategoryTotal;
import com.axora.export.ReportCsvExporter;
import com.axora.model.Account;
import com.axora.model.AccountBalance;
import com.axora.model.BudgetStatus;
import com.axora.service.AccountService;
import com.axora.service.ReportService;
import com.axora.service.ReportService.FinancialReport;
import com.axora.service.ReportService.MonthRow;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Reports: aggregates come from ReportService; this class only renders them. */
public class ReportController {

    private static final String[] PRESETS = {"Year to date", "This month", "Last month", "Last 3 months", "Last 12 months", "Custom"};
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yy");

    @FXML private ComboBox<String> presetCombo;
    @FXML private DatePicker fromPicker, toPicker;
    @FXML private ComboBox<Account> accountCombo;
    @FXML private Button exportButton;
    @FXML private Label messageLabel, noteLabel, incomeLabel, expenseLabel, netLabel, rateLabel, budgetTitle, balanceTitle;
    @FXML private VBox emptyBox, chartsBox;
    @FXML private BarChart<String, Number> incomeExpenseChart;
    @FXML private LineChart<String, Number> netChart, trendChart;
    @FXML private PieChart categoryChart;
    @FXML private TableView<MonthRow> monthTable;
    @FXML private TableView<BudgetStatus> budgetTable;
    @FXML private TableView<AccountBalance> balanceTable;

    private final ReportService reports = new ReportService();
    private final AccountService accounts = new AccountService();
    private FinancialReport current;
    private boolean adjusting;
    private int requestSeq;

    @FXML
    public void initialize() {
        presetCombo.setItems(FXCollections.observableArrayList(PRESETS));
        Account all = new Account(0, "All accounts", Account.Type.BANK, 0, "INR", "", false);
        List<Account> items = new ArrayList<>(accounts.getAccounts());
        items.add(0, all);
        accountCombo.setItems(FXCollections.observableArrayList(items));
        accountCombo.setValue(all);

        LocalDate[] r = ReportService.defaultRange(LocalDate.now());
        adjusting = true;
        presetCombo.setValue(PRESETS[0]); fromPicker.setValue(r[0]); toPicker.setValue(r[1]);
        adjusting = false;

        presetCombo.setOnAction(e -> applyPreset());
        fromPicker.valueProperty().addListener((o, a, b) -> customEdited());
        toPicker.valueProperty().addListener((o, a, b) -> customEdited());
        accountCombo.setOnAction(e -> load());

        setupTables();
        exportButton.setDisable(true);
        load();
    }

    private void setupTables() {
        UiKit.fit(monthTable);
        UiKit.columns(monthTable,
            UiKit.<MonthRow>column("Month", 100, m -> m.month().format(MONTH)),
            UiKit.<MonthRow>rightColumn("Income", 130, m -> UiKit.money(m.incomeCents())),
            UiKit.<MonthRow>rightColumn("Expenses", 130, m -> UiKit.money(m.expenseCents())),
            UiKit.<MonthRow>rightColumn("Net", 130, m -> UiKit.money(m.netCents())),
            UiKit.<MonthRow>rightColumn("Cumulative net", 150, m -> UiKit.money(m.cumulativeNetCents())));
        UiKit.columns(budgetTable,
            UiKit.<BudgetStatus>column("Budget", 130, b -> b.budget().name()),
            UiKit.<BudgetStatus>column("Applies to", 110, b -> b.budget().isOverall() ? "All expenses" : b.budget().categoryName()),
            UiKit.<BudgetStatus>column("Window", 175, b -> b.from() + " → " + b.to()),
            UiKit.<BudgetStatus>rightColumn("Limit", 90, b -> UiKit.money(b.budget().amountCents())),
            UiKit.<BudgetStatus>rightColumn("Actual", 90, b -> UiKit.money(b.spentCents())),
            UiKit.<BudgetStatus>rightColumn("Remaining", 90, b -> UiKit.money(b.remainingCents())),
            UiKit.<BudgetStatus>rightColumn("Used", 60, b -> b.percentUsed().toPlainString() + "%"),
            UiKit.<BudgetStatus>column("Status", 90, b -> switch (b.level()) { case OK -> "On track"; case WARNING -> "Near limit"; case EXCEEDED -> "Over budget"; }));
        budgetTable.setPlaceholder(new Label("No budgets defined. Create some on the Budgets screen."));
        budgetTable.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(BudgetStatus b, boolean empty) {
                super.updateItem(b, empty);
                getStyleClass().remove("row-over");
                if (!empty && b != null && b.level() == BudgetStatus.Level.EXCEEDED) getStyleClass().add("row-over");
            }
        });
        UiKit.fit(balanceTable);
        UiKit.columns(balanceTable,
            UiKit.<AccountBalance>column("Account", 180, b -> b.account().name()),
            UiKit.<AccountBalance>column("Type", 90, b -> b.account().type().toString()),
            UiKit.<AccountBalance>column("Currency", 80, b -> b.account().currency()),
            UiKit.<AccountBalance>rightColumn("Balance", 150, b -> UiKit.money(b.balanceCents(), b.account().currency())));
        balanceTable.setPlaceholder(new Label("No accounts."));
        monthTable.setPlaceholder(new Label("No data."));
    }

    // ---- range handling
    private void applyPreset() {
        if (adjusting) return;
        LocalDate today = LocalDate.now();
        LocalDate from, to = today;
        switch (presetCombo.getValue()) {
            case "This month" -> from = today.withDayOfMonth(1);
            case "Last month" -> { from = today.minusMonths(1).withDayOfMonth(1); to = from.withDayOfMonth(from.lengthOfMonth()); }
            case "Last 3 months" -> from = today.minusMonths(2).withDayOfMonth(1);
            case "Last 12 months" -> from = today.minusMonths(11).withDayOfMonth(1);
            case "Custom" -> { return; }
            default -> from = today.withDayOfYear(1);
        }
        adjusting = true; fromPicker.setValue(from); toPicker.setValue(to); adjusting = false;
        load();
    }

    private void customEdited() {
        if (adjusting) return;
        adjusting = true; presetCombo.setValue("Custom"); adjusting = false;
        load();
    }

    // ---- loading
    private void load() {
        LocalDate from = fromPicker.getValue(), to = toPicker.getValue();
        Account acc = accountCombo.getValue();
        Long accountId = (acc == null || acc.id() == 0) ? null : acc.id();
        int seq = ++requestSeq;
        hideMessage();
        UiKit.async(() -> reports.build(from, to, accountId), rep -> { if (seq == requestSeq) render(rep); },
            ex -> { if (seq != requestSeq) return; current = null; exportButton.setDisable(true);
                    showMessage(UiKit.message(ex)); });
    }

    private void showMessage(String m) { messageLabel.setText(m); messageLabel.setVisible(true); messageLabel.setManaged(true); }
    private void hideMessage() { messageLabel.setVisible(false); messageLabel.setManaged(false); }

    private void render(FinancialReport r) {
        current = r;
        exportButton.setDisable(false);
        incomeLabel.setText(UiKit.money(r.incomeCents()));
        expenseLabel.setText(UiKit.money(r.expenseCents()));
        netLabel.setText(UiKit.money(r.netCents()));
        netLabel.getStyleClass().removeAll("kpi-positive", "kpi-negative");
        netLabel.getStyleClass().add(r.netCents() >= 0 ? "kpi-positive" : "kpi-negative");
        rateLabel.setText(r.incomeCents() == 0 ? "—" : BigDecimal.valueOf(r.netCents() * 100L)
            .divide(BigDecimal.valueOf(r.incomeCents()), 1, RoundingMode.HALF_UP).toPlainString() + "%");

        boolean empty = r.isEmpty();
        emptyBox.setVisible(empty); emptyBox.setManaged(empty);
        chartsBox.setVisible(!empty); chartsBox.setManaged(!empty);
        noteLabel.setVisible(r.mixedCurrencies()); noteLabel.setManaged(r.mixedCurrencies());
        noteLabel.setText("You have accounts in more than one currency. Income, expenses and budgets are not converted; balances are shown per account.");
        budgetTitle.setText("Budget vs actual — as of " + r.to());
        balanceTitle.setText("Account balances — as of " + r.to());
        budgetTable.setItems(FXCollections.observableArrayList(r.budgets()));
        balanceTable.setItems(FXCollections.observableArrayList(r.balances()));
        if (empty) return;

        monthTable.setItems(FXCollections.observableArrayList(r.months()));
        monthTable.setPrefHeight(Math.min(360, 60 + 28 * r.months().size()));

        XYChart.Series<String, Number> inc = new XYChart.Series<>(); inc.setName("Income");
        XYChart.Series<String, Number> exp = new XYChart.Series<>(); exp.setName("Expenses");
        XYChart.Series<String, Number> net = new XYChart.Series<>(); net.setName("Cumulative net savings");
        XYChart.Series<String, Number> trend = new XYChart.Series<>(); trend.setName("Spending");
        for (MonthRow m : r.months()) {
            String k = m.month().format(MONTH);
            inc.getData().add(new XYChart.Data<>(k, m.incomeCents() / 100.0));
            exp.getData().add(new XYChart.Data<>(k, m.expenseCents() / 100.0));
            net.getData().add(new XYChart.Data<>(k, m.cumulativeNetCents() / 100.0));
            trend.getData().add(new XYChart.Data<>(k, m.expenseCents() / 100.0));
        }
        incomeExpenseChart.getData().setAll(List.of(inc, exp));
        netChart.getData().setAll(List.of(net));
        trendChart.getData().setAll(List.of(trend));

        List<PieChart.Data> slices = new ArrayList<>();
        long other = 0; int i = 0;
        for (CategoryTotal c : r.categories()) {
            if (i++ < 7) slices.add(new PieChart.Data(c.categoryName() + " (" + UiKit.money(c.totalCents()) + ")", c.totalCents() / 100.0));
            else other += c.totalCents();
        }
        if (other > 0) slices.add(new PieChart.Data("Other (" + UiKit.money(other) + ")", other / 100.0));
        categoryChart.setData(FXCollections.observableArrayList(slices));
    }

    // ---- export
    @FXML
    private void exportCsv() {
        if (current == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Export report as CSV");
        fc.setInitialFileName("axora-report-" + current.from() + "_to_" + current.to() + ".csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File f = fc.showSaveDialog(exportButton.getScene().getWindow());
        if (f == null) return;
        try {
            new ReportCsvExporter().export(current, f);
            UiKit.info("Report exported to:\n" + f.getAbsolutePath());
        } catch (Exception ex) {
            UiKit.error("Export failed: " + UiKit.message(ex));
        }
    }
}
