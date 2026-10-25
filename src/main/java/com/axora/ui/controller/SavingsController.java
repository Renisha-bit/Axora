package com.axora.ui.controller;

import com.axora.model.GoalEntry;
import com.axora.model.GoalProgress;
import com.axora.model.SavingsGoal;
import com.axora.service.SavingsGoalService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Savings goals with their own contribution ledger (separate from account balances). */
public class SavingsController {

    @FXML private Label summaryLabel;
    @FXML private VBox  cardsBox, emptyBox;

    private final SavingsGoalService goals = new SavingsGoalService();

    @FXML public void initialize() { refresh(); }
    @FXML private void openCreate() { openGoalForm(null); }

    private void refresh() {
        UiKit.async(() -> goals.progressList(LocalDate.now()), this::render,
            ex -> UiKit.error("Failed to load goals: " + UiKit.message(ex)));
    }

    private void render(List<GoalProgress> list) {
        cardsBox.getChildren().clear();
        emptyBox.setVisible(list.isEmpty()); emptyBox.setManaged(list.isEmpty());
        if (list.isEmpty()) { summaryLabel.setText(""); return; }
        long done = list.stream().filter(p -> p.status() == GoalProgress.Status.COMPLETED).count();
        long saved = list.stream().mapToLong(GoalProgress::savedCents).sum();
        summaryLabel.setText(list.size() + " goal" + (list.size() == 1 ? "" : "s") + " · " + done + " completed · "
            + UiKit.money(saved) + " saved in total. Goal savings are tracked separately from your account balances.");
        for (GoalProgress p : list) cardsBox.getChildren().add(card(p));
    }

    private VBox card(GoalProgress p) {
        SavingsGoal g = p.goal();
        long days = ChronoUnit.DAYS.between(LocalDate.now(), g.targetDate());
        String when = p.status() == GoalProgress.Status.COMPLETED ? "Target date " + g.targetDate()
            : days >= 0 ? "Target " + g.targetDate() + " · " + days + " day" + (days == 1 ? "" : "s") + " left"
            : "Target " + g.targetDate() + " · " + (-days) + " day" + (days == -1 ? "" : "s") + " overdue";

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        VBox titles = new VBox(2, UiKit.label(g.name(), "item-title"),
            UiKit.label(when + (g.description() == null || g.description().isBlank() ? "" : " · " + g.description()), "item-sub"));
        top.getChildren().addAll(titles, UiKit.spacer(), statusBadge(p));

        HBox metrics = new HBox(28,
            UiKit.metric("Target", UiKit.money(g.targetCents()), false),
            UiKit.metric("Saved", UiKit.money(p.savedCents()), false),
            UiKit.metric("Remaining", UiKit.money(p.remainingCents()), false),
            UiKit.metric("Progress", p.percent().toPlainString() + "%", false));

        String cls = switch (p.status()) { case COMPLETED -> "progress-ok"; case ON_TRACK -> "progress-brand";
            case BEHIND -> "progress-warn"; case OVERDUE -> "progress-danger"; };
        HBox buttons = new HBox(8,
            UiKit.ghost("+ Contribute", () -> openEntryForm(p, GoalEntry.Type.CONTRIBUTION)),
            UiKit.ghost("Withdraw", () -> openEntryForm(p, GoalEntry.Type.WITHDRAWAL)),
            UiKit.ghost("History", () -> showHistory(p)),
            UiKit.spacer(),
            UiKit.ghost("Edit", () -> openGoalForm(g)),
            UiKit.danger("Delete", () -> delete(g)));
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10, top, metrics, UiKit.bar(p.percent().doubleValue() / 100.0, cls), buttons);
        card.getStyleClass().add("item-card");
        return card;
    }

    private Label statusBadge(GoalProgress p) {
        return switch (p.status()) {
            case COMPLETED -> UiKit.badge("Completed", "badge-ok");
            case ON_TRACK  -> UiKit.badge("On track", "badge-info");
            case BEHIND    -> UiKit.badge("Behind schedule", "badge-warn");
            case OVERDUE   -> UiKit.badge("Overdue", "badge-danger");
        };
    }

    // ---- goal CRUD
    private void openGoalForm(SavingsGoal existing) {
        UiKit.Form f = new UiKit.Form();
        TextField name = f.add("Goal name", UiKit.textField("e.g. Emergency fund"));
        TextField target = f.add("Target amount (₹)", UiKit.textField("0.00"));
        DatePicker date = f.add("Target date", UiKit.datePicker(LocalDate.now().plusMonths(6)));
        TextField desc = f.add("Description", UiKit.textField("Optional"));
        f.hint("“On track” compares what you have saved with an even pace between the goal's start and target date.");
        if (existing != null) {
            name.setText(existing.name());
            target.setText(BigDecimal.valueOf(existing.targetCents(), 2).toPlainString());
            date.setValue(existing.targetDate()); desc.setText(existing.description());
        }
        Runnable save = () -> {
            if (existing == null) goals.create(name.getText(), target.getText(), date.getValue(), desc.getText());
            else goals.update(existing.id(), name.getText(), target.getText(), date.getValue(), desc.getText());
        };
        if (UiKit.showForm(cardsBox.getScene().getWindow(), existing == null ? "New Goal" : "Edit Goal",
                existing == null ? "Create" : "Update", f, save)) refresh();
    }

    private void delete(SavingsGoal g) {
        if (!UiKit.confirm("Delete Goal", "Delete '" + g.name() + "'?",
                "Its contribution history will be deleted too. Your accounts and transactions are not affected.")) return;
        try { goals.delete(g.id()); refresh(); }
        catch (RuntimeException ex) { UiKit.error("Could not delete: " + UiKit.message(ex)); }
    }

    // ---- ledger
    private void openEntryForm(GoalProgress p, GoalEntry.Type type) {
        boolean contrib = type == GoalEntry.Type.CONTRIBUTION;
        UiKit.Form f = new UiKit.Form();
        TextField amount = f.add("Amount (₹)", UiKit.textField("0.00"));
        DatePicker date = f.add("Date", UiKit.datePicker(LocalDate.now()));
        TextField note = f.add("Note", UiKit.textField("Optional"));
        f.hint(contrib ? "Currently saved: " + UiKit.money(p.savedCents())
                       : "You can withdraw up to " + UiKit.money(p.savedCents()) + ".");
        Runnable save = () -> {
            if (contrib) goals.contribute(p.goal().id(), amount.getText(), date.getValue(), note.getText());
            else goals.withdraw(p.goal().id(), amount.getText(), date.getValue(), note.getText());
        };
        if (UiKit.showForm(cardsBox.getScene().getWindow(), (contrib ? "Contribute to " : "Withdraw from ") + p.goal().name(),
                contrib ? "Add" : "Withdraw", f, save)) refresh();
    }

    private void showHistory(GoalProgress p) {
        TableView<GoalEntry> t = UiKit.table("No history yet.", "Contributions and withdrawals appear here.");
        UiKit.columns(t,
            UiKit.<GoalEntry>column("Date", 100, e -> e.date().toString()),
            UiKit.<GoalEntry>column("Type", 110, e -> e.type() == GoalEntry.Type.CONTRIBUTION ? "Contribution" : "Withdrawal"),
            UiKit.<GoalEntry>rightColumn("Amount", 130, e -> (e.signedCents() > 0 ? "+" : "") + UiKit.money(e.signedCents())),
            UiKit.<GoalEntry>column("Note", 220, e -> e.note() == null ? "" : e.note()));
        t.setItems(FXCollections.observableArrayList(goals.history(p.goal().id())));
        t.setPrefSize(640, 320);
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("History — " + p.goal().name());
        dlg.initOwner(cardsBox.getScene().getWindow());
        dlg.getDialogPane().getStylesheets().add(UiKit.class.getResource(UiKit.CSS).toExternalForm());
        dlg.getDialogPane().setContent(t);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.showAndWait();
    }
}
