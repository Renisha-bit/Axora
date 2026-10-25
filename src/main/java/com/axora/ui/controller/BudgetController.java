package com.axora.ui.controller;

import com.axora.model.Budget;
import com.axora.model.BudgetStatus;
import com.axora.model.Category;
import com.axora.model.Transaction;
import com.axora.service.BudgetService;
import com.axora.service.TransactionService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Budgets screen: create / edit / delete, live status against actual expenses. */
public class BudgetController {

    @FXML private ComboBox<String> periodFilter;
    @FXML private DatePicker       asOfPicker;
    @FXML private Label            summaryLabel;
    @FXML private VBox             cardsBox, emptyBox;
    @FXML private Label            emptyTitle, emptySub;

    private final BudgetService      budgets = new BudgetService();
    private final TransactionService txs     = new TransactionService();

    private record View(List<BudgetStatus> statuses, int totalBudgets) {}

    @FXML
    public void initialize() {
        periodFilter.setItems(FXCollections.observableArrayList("All periods", "Weekly", "Monthly", "Custom"));
        periodFilter.setValue("All periods");
        asOfPicker.setValue(LocalDate.now());
        periodFilter.setOnAction(e -> refresh());
        asOfPicker.valueProperty().addListener((o, a, b) -> refresh());
        refresh();
    }

    @FXML private void resetToday() { asOfPicker.setValue(LocalDate.now()); }
    @FXML private void openCreate() { openForm(null); }

    private Budget.Period selectedPeriod() {
        return switch (periodFilter.getValue()) {
            case "Weekly" -> Budget.Period.WEEKLY;
            case "Monthly" -> Budget.Period.MONTHLY;
            case "Custom" -> Budget.Period.CUSTOM;
            default -> null;
        };
    }

    private void refresh() {
        LocalDate asOf = asOfPicker.getValue() == null ? LocalDate.now() : asOfPicker.getValue();
        Budget.Period p = selectedPeriod();
        summaryLabel.setText("Loading…");
        UiKit.async(() -> new View(budgets.statuses(asOf, p), budgets.getBudgets().size()),
            this::render, ex -> { summaryLabel.setText(""); UiKit.error("Failed to load budgets: " + UiKit.message(ex)); });
    }

    private void render(View v) {
        cardsBox.getChildren().clear();
        boolean empty = v.statuses.isEmpty();
        emptyBox.setVisible(empty); emptyBox.setManaged(empty);
        if (empty) {
            boolean none = v.totalBudgets == 0;
            emptyTitle.setText(none ? "No budgets yet." : "No budgets match this filter.");
            emptySub.setText(none ? "Create a budget to track spending against a limit." : "Choose another period or clear the filter.");
            summaryLabel.setText("");
            return;
        }
        long exceeded = v.statuses.stream().filter(s -> s.level() == BudgetStatus.Level.EXCEEDED).count();
        long warning  = v.statuses.stream().filter(s -> s.level() == BudgetStatus.Level.WARNING).count();
        summaryLabel.setText(v.statuses.size() + " budget" + (v.statuses.size() == 1 ? "" : "s")
            + (exceeded > 0 ? " · " + exceeded + " over limit" : "")
            + (warning > 0 ? " · " + warning + " near limit" : ""));
        for (BudgetStatus s : v.statuses) cardsBox.getChildren().add(card(s));
    }

    private VBox card(BudgetStatus s) {
        Budget b = s.budget();
        String scope = b.isOverall() ? "All expenses" : b.categoryName();
        String window = b.period() == Budget.Period.CUSTOM ? s.from() + " → " + s.to()
            : b.period() + " · " + s.from() + " → " + s.to();

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        VBox titles = new VBox(2, UiKit.label(b.name(), "item-title"), UiKit.label(scope + " · " + window, "item-sub"));
        top.getChildren().addAll(titles, UiKit.spacer(), statusBadge(s),
            UiKit.ghost("Edit", () -> openForm(b)), UiKit.danger("Delete", () -> delete(b)));

        boolean over = s.level() == BudgetStatus.Level.EXCEEDED;
        HBox metrics = new HBox(28,
            UiKit.metric("Limit", UiKit.money(b.amountCents()), false),
            UiKit.metric("Spent", UiKit.money(s.spentCents()), false),
            UiKit.metric(over ? "Over by" : "Remaining", UiKit.money(over ? -s.remainingCents() : s.remainingCents()), over),
            UiKit.metric("Used", s.percentUsed().toPlainString() + "%", over));

        String stateClass = switch (s.level()) { case OK -> "progress-ok"; case WARNING -> "progress-warn"; case EXCEEDED -> "progress-danger"; };
        double ratio = b.amountCents() == 0 ? 0 : (double) s.spentCents() / b.amountCents();
        VBox card = new VBox(10, top, metrics, UiKit.bar(ratio, stateClass));
        card.getStyleClass().add("item-card");
        return card;
    }

    private Label statusBadge(BudgetStatus s) {
        return switch (s.level()) {
            case OK       -> UiKit.badge("On track", "badge-ok");
            case WARNING  -> UiKit.badge(s.spentCents() >= s.budget().amountCents() ? "At limit" : "Near limit", "badge-warn");
            case EXCEEDED -> UiKit.badge("Over budget", "badge-danger");
        };
    }

    // ---- actions
    private void delete(Budget b) {
        if (!UiKit.confirm("Delete Budget", "Delete '" + b.name() + "'?",
                "Your transactions are not affected. This cannot be undone.")) return;
        try { budgets.delete(b.id()); refresh(); }
        catch (RuntimeException ex) { UiKit.error("Could not delete: " + UiKit.message(ex)); }
    }

    private void openForm(Budget existing) {
        UiKit.Form f = new UiKit.Form();
        TextField name = f.add("Name", UiKit.textField("Optional – defaults to category + period"));
        ComboBox<Category> scope = f.add("Applies to", UiKit.combo());
        Category overall = new Category("Overall (all expenses)", Category.CategoryType.EXPENSE);
        List<Category> cats = new ArrayList<>(txs.getCategoriesForType(Transaction.Type.EXPENSE));
        cats.add(0, overall);
        scope.setItems(FXCollections.observableArrayList(cats));
        scope.setValue(overall);
        TextField amount = f.add("Limit (₹)", UiKit.textField("0.00"));
        ComboBox<Budget.Period> period = f.add("Period", UiKit.combo());
        period.setItems(FXCollections.observableArrayList(Budget.Period.values()));
        period.setValue(Budget.Period.MONTHLY);
        var custom = period.valueProperty().isEqualTo(Budget.Period.CUSTOM);
        DatePicker start = f.addToggleable("Start date", UiKit.datePicker(LocalDate.now().withDayOfMonth(1)), custom);
        DatePicker end   = f.addToggleable("End date", UiKit.datePicker(LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1)), custom);
        f.hint("Weekly budgets run Monday–Sunday; monthly budgets follow the calendar month.");

        if (existing != null) {
            name.setText(existing.name());
            amount.setText(java.math.BigDecimal.valueOf(existing.amountCents(), 2).toPlainString());
            period.setValue(existing.period());
            if (existing.period() == Budget.Period.CUSTOM) { start.setValue(existing.startDate()); end.setValue(existing.endDate()); }
            cats.stream().filter(c -> existing.categoryId() != null && c.getId() == existing.categoryId())
                .findFirst().ifPresent(scope::setValue);
        }
        Runnable save = () -> {
            Category c = scope.getValue();
            Long catId = (c == null || c == overall) ? null : c.getId();
            if (existing == null) budgets.create(name.getText(), catId, amount.getText(), period.getValue(), start.getValue(), end.getValue());
            else budgets.update(existing.id(), name.getText(), catId, amount.getText(), period.getValue(), start.getValue(), end.getValue());
        };
        if (UiKit.showForm(summaryLabel.getScene().getWindow(), existing == null ? "New Budget" : "Edit Budget",
                existing == null ? "Create" : "Update", f, save)) refresh();
    }
}
