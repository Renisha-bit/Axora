package com.axora.ui.controller;

import com.axora.dao.CategoryTotal;
import com.axora.dao.MonthlyTotal;
import com.axora.model.Transaction;
import com.axora.service.TransactionService;
import com.axora.util.CurrencyUtil;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Dashboard controller — KPIs, charts, recent transactions.
 * All DB access runs off the JavaFX Application Thread.
 */
public class DashboardController {

    @FXML private ComboBox<String>      periodCombo;
    @FXML private Label                 lblIncome, lblExpense, lblNet;
    @FXML private PieChart              categoryPieChart;
    @FXML private Label                 pieEmptyLabel;
    @FXML private BarChart<String, Number> monthlyBarChart;
    @FXML private CategoryAxis          barXAxis;
    @FXML private NumberAxis            barYAxis;
    @FXML private Label                 barEmptyLabel;
    @FXML private TableView<Transaction> recentTable;
    @FXML private TableColumn<Transaction, String> colDate, colType, colCategory, colDesc, colAmount;

    private final TransactionService service = new TransactionService();

    @FXML
    public void initialize() {
        setupPeriodCombo();
        setupTable();
        refresh();
    }

    // ----------------------------------------------------------------
    // Setup
    // ----------------------------------------------------------------

    private void setupPeriodCombo() {
        periodCombo.setItems(FXCollections.observableArrayList(
            "This Month", "Last Month", "This Year", "All Time"
        ));
        periodCombo.setValue("This Month");
        periodCombo.setOnAction(e -> refresh());
    }

    private void setupTable() {
        colDate.setCellValueFactory(cd ->
            new javafx.beans.property.SimpleStringProperty(
                cd.getValue().getTxDate().toString()));
        colType.setCellValueFactory(cd ->
            new javafx.beans.property.SimpleStringProperty(
                cd.getValue().getType().name()));
        colCategory.setCellValueFactory(cd ->
            new javafx.beans.property.SimpleStringProperty(
                cd.getValue().getCategoryName()));
        colDesc.setCellValueFactory(cd ->
            new javafx.beans.property.SimpleStringProperty(
                cd.getValue().getDescription()));
        colAmount.setCellValueFactory(cd ->
            new javafx.beans.property.SimpleStringProperty(
                CurrencyUtil.format(cd.getValue().getAmountCents())));

        // Color income/expense rows
        recentTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Transaction tx, boolean empty) {
                super.updateItem(tx, empty);
                getStyleClass().removeAll("row-income", "row-expense");
                if (!empty && tx != null) {
                    getStyleClass().add(
                        tx.getType() == Transaction.Type.INCOME ? "row-income" : "row-expense");
                }
            }
        });
    }

    // ----------------------------------------------------------------
    // Refresh
    // ----------------------------------------------------------------

    public void refresh() {
        LocalDate[] range = getDateRange();
        LocalDate from = range[0], to = range[1];
        int year = LocalDate.now().getYear();

        Task<DashboardData> task = new Task<>() {
            @Override
            protected DashboardData call() {
                long income   = service.totalIncomeCents(from, to);
                long expense  = service.totalExpenseCents(from, to);
                long net      = income - expense;
                List<CategoryTotal> cats    = service.categoryExpenseTotals(from, to);
                List<MonthlyTotal>  monthly = service.monthlyTotals(year);
                List<Transaction>   recent  = service.getAllTransactions()
                    .stream().limit(10).toList();
                return new DashboardData(income, expense, net, cats, monthly, recent);
            }
        };

        task.setOnSucceeded(e -> applyData(task.getValue()));
        task.setOnFailed(e -> {
            task.getException().printStackTrace();
        });

        Thread t = new Thread(task, "axora-dashboard");
        t.setDaemon(true);
        t.start();
    }

    private void applyData(DashboardData d) {
        lblIncome.setText(CurrencyUtil.format(d.income));
        lblExpense.setText(CurrencyUtil.format(d.expense));
        lblNet.setText(CurrencyUtil.format(d.net));
        // Net coloring
        lblNet.getStyleClass().removeAll("kpi-positive", "kpi-negative");
        lblNet.getStyleClass().add(d.net >= 0 ? "kpi-positive" : "kpi-negative");

        updatePieChart(d.categories);
        updateBarChart(d.monthly);
        recentTable.setItems(FXCollections.observableArrayList(d.recent));
    }

    private void updatePieChart(List<CategoryTotal> cats) {
        categoryPieChart.getData().clear();
        if (cats.isEmpty()) {
            pieEmptyLabel.setVisible(true);
            categoryPieChart.setVisible(false);
            return;
        }
        pieEmptyLabel.setVisible(false);
        categoryPieChart.setVisible(true);
        for (CategoryTotal ct : cats) {
            double amount = ct.totalCents() / 100.0;
            categoryPieChart.getData().add(
                new PieChart.Data(ct.categoryName(), amount));
        }
    }

    private void updateBarChart(List<MonthlyTotal> monthly) {
        monthlyBarChart.getData().clear();
        if (monthly.isEmpty()) {
            barEmptyLabel.setVisible(true);
            monthlyBarChart.setVisible(false);
            return;
        }
        barEmptyLabel.setVisible(false);
        monthlyBarChart.setVisible(true);

        XYChart.Series<String, Number> incomeSeries  = new XYChart.Series<>();
        XYChart.Series<String, Number> expenseSeries = new XYChart.Series<>();
        incomeSeries.setName("Income");
        expenseSeries.setName("Expenses");

        for (MonthlyTotal mt : monthly) {
            String mo = Month.of(mt.month()).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            incomeSeries.getData().add(new XYChart.Data<>(mo, mt.incomeCents() / 100.0));
            expenseSeries.getData().add(new XYChart.Data<>(mo, mt.expenseCents() / 100.0));
        }
        monthlyBarChart.getData().addAll(incomeSeries, expenseSeries);
    }

    // ----------------------------------------------------------------
    // Period logic
    // ----------------------------------------------------------------

    private LocalDate[] getDateRange() {
        LocalDate now = LocalDate.now();
        return switch (periodCombo.getValue()) {
            case "Last Month"  -> {
                LocalDate start = now.withDayOfMonth(1).minusMonths(1);
                yield new LocalDate[]{start, start.withDayOfMonth(start.lengthOfMonth())};
            }
            case "This Year"   -> new LocalDate[]{now.withDayOfYear(1), now};
            case "All Time"    -> new LocalDate[]{LocalDate.of(2000, 1, 1), now};
            default            -> new LocalDate[]{now.withDayOfMonth(1), now}; // This Month
        };
    }

    // ----------------------------------------------------------------
    // Inner data holder
    // ----------------------------------------------------------------

    private record DashboardData(
        long income, long expense, long net,
        List<CategoryTotal> categories,
        List<MonthlyTotal> monthly,
        List<Transaction> recent) {}
}
