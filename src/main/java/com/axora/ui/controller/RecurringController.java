package com.axora.ui.controller;

import com.axora.model.*;
import com.axora.model.RecurringRule.Frequency;
import com.axora.service.AccountService;
import com.axora.service.RecurringService;
import com.axora.service.TransactionService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Recurring rules: manage schedules, inspect upcoming occurrences, see generated history. */
public class RecurringController {

    @FXML private Label statusLabel, upcomingLabel;
    @FXML private TableView<RecurringRule>     ruleTable;
    @FXML private TableView<UpcomingOccurrence> upcomingTable;
    @FXML private TableView<Transaction>       historyTable;

    private final RecurringService   recurring = new RecurringService();
    private final TransactionService txs       = new TransactionService();
    private final AccountService     accounts  = new AccountService();

    private record Data(List<RecurringRule> rules, Map<Long, Long> generated, Map<Long, LocalDate> next,
                        List<UpcomingOccurrence> upcoming, List<Transaction> history) {}

    @FXML
    public void initialize() {
        ruleTable.setPlaceholder(new Label("No recurring rules yet. Create one for rent, salary, subscriptions…"));
        UiKit.columns(ruleTable,
            UiKit.<RecurringRule>column("Description", 120, r -> r.description() == null || r.description().isBlank() ? "—" : r.description()),
            UiKit.<RecurringRule>column("Type", 70, r -> r.type() == Transaction.Type.INCOME ? "Income" : "Expense"),
            UiKit.<RecurringRule>column("Category", 100, RecurringRule::categoryName),
            UiKit.<RecurringRule>column("Account", 85, RecurringRule::accountName),
            UiKit.<RecurringRule>rightColumn("Amount", 92, r -> UiKit.money(r.amountCents())),
            UiKit.<RecurringRule>column("Schedule", 125, r -> r.frequency().toString()
                + (r.endDate() == null ? "" : ", until " + r.endDate())),
            UiKit.<RecurringRule>column("Next due", 100, r -> r.paused() ? "Paused"
                : nextDue.get(r.id()) == null ? "Ended" : nextDue.get(r.id()).toString()),
            UiKit.<RecurringRule>rightColumn("Done", 40, r -> String.valueOf(generated.getOrDefault(r.id(), 0L))),
            UiKit.<RecurringRule>actions(200, r -> new Node[]{
                UiKit.ghost("Edit", () -> openForm(r)),
                UiKit.ghost(r.paused() ? "Resume" : "Pause", () -> togglePause(r)),
                UiKit.danger("Delete", () -> delete(r))}));
        ruleTable.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(RecurringRule r, boolean empty) {
                super.updateItem(r, empty);
                getStyleClass().remove("row-muted");
                if (!empty && r != null && r.paused()) getStyleClass().add("row-muted");
            }
        });

        upcomingTable.setPlaceholder(new Label("Nothing scheduled in the next 60 days."));
        UiKit.fit(upcomingTable);
        UiKit.columns(upcomingTable,
            UiKit.<UpcomingOccurrence>column("Date", 100, u -> u.date().toString()),
            UiKit.<UpcomingOccurrence>column("Type", 80, u -> u.rule().type() == Transaction.Type.INCOME ? "Income" : "Expense"),
            UiKit.<UpcomingOccurrence>column("Description", 220, u -> u.rule().description() == null ? "" : u.rule().description()),
            UiKit.<UpcomingOccurrence>column("Category", 150, u -> u.rule().categoryName()),
            UiKit.<UpcomingOccurrence>column("Account", 130, u -> u.rule().accountName()),
            UiKit.<UpcomingOccurrence>rightColumn("Amount", 120, u -> UiKit.money(u.rule().amountCents())));
        upcomingTable.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(UpcomingOccurrence u, boolean empty) {
                super.updateItem(u, empty);
                getStyleClass().removeAll("row-income", "row-expense");
                if (!empty && u != null) getStyleClass().add(u.rule().type() == Transaction.Type.INCOME ? "row-income" : "row-expense");
            }
        });

        historyTable.setPlaceholder(new Label("No transactions have been generated yet."));
        UiKit.fit(historyTable);
        UiKit.columns(historyTable,
            UiKit.<Transaction>column("Date", 100, t -> t.getTxDate().toString()),
            UiKit.<Transaction>column("Type", 80, t -> t.getType() == Transaction.Type.INCOME ? "Income" : "Expense"),
            UiKit.<Transaction>column("Description", 220, t -> t.getDescription() == null ? "" : t.getDescription()),
            UiKit.<Transaction>column("Category", 150, Transaction::getCategoryName),
            UiKit.<Transaction>column("Account", 130, t -> t.getAccountName() == null ? "" : t.getAccountName()),
            UiKit.<Transaction>column("Rule", 70, t -> "#" + t.getRecurringRuleId()),
            UiKit.<Transaction>rightColumn("Amount", 120, t -> UiKit.money(t.getAmountCents())));
        historyTable.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Transaction t, boolean empty) {
                super.updateItem(t, empty);
                getStyleClass().removeAll("row-income", "row-expense");
                if (!empty && t != null) getStyleClass().add(t.getType() == Transaction.Type.INCOME ? "row-income" : "row-expense");
            }
        });

        // Opening the screen processes anything that has become due (idempotent), then loads.
        refresh(true);
    }

    private Map<Long, Long> generated = Map.of();
    private Map<Long, LocalDate> nextDue = Map.of();

    @FXML private void generateNow() { refresh(true); }
    @FXML private void openCreate() { openForm(null); }

    private void refresh(boolean generate) {
        UiKit.async(() -> {
            int created = generate ? recurring.generateDue(LocalDate.now()) : 0;
            LocalDate today = LocalDate.now();
            List<RecurringRule> rules = recurring.getRules();
            Map<Long, Long> gen = new java.util.HashMap<>();
            Map<Long, LocalDate> next = new java.util.HashMap<>();
            for (RecurringRule r : rules) { gen.put(r.id(), recurring.generatedCount(r.id())); next.put(r.id(), recurring.nextDue(r, today)); }
            List<UpcomingOccurrence> up = recurring.upcoming(today, 60, 200);
            List<Transaction> hist = txs.getAllTransactions().stream().filter(Transaction::isRecurring).toList();
            return new Object[]{created, new Data(rules, gen, next, up, hist)};
        }, res -> {
            int created = (Integer) res[0];
            Data d = (Data) res[1];
            generated = d.generated; nextDue = d.next;
            ruleTable.setItems(FXCollections.observableArrayList(d.rules));
            ruleTable.refresh();
            upcomingTable.setItems(FXCollections.observableArrayList(d.upcoming));
            historyTable.setItems(FXCollections.observableArrayList(d.history));
            long active = d.rules.stream().filter(r -> !r.paused()).count();
            upcomingLabel.setText(d.upcoming.size() + " scheduled occurrence" + (d.upcoming.size() == 1 ? "" : "s")
                + " from " + active + " active rule" + (active == 1 ? "" : "s") + ". These are not transactions yet.");
            if (created > 0) showStatus("Generated " + created + " due transaction" + (created == 1 ? "" : "s") + ".");
            else hideStatus();
        }, ex -> UiKit.error("Failed to load recurring rules: " + UiKit.message(ex)));
    }

    private void showStatus(String m) { statusLabel.setText(m); statusLabel.setVisible(true); statusLabel.setManaged(true); }
    private void hideStatus() { statusLabel.setVisible(false); statusLabel.setManaged(false); }

    // ---- actions
    private void togglePause(RecurringRule r) {
        try {
            if (r.paused()) { recurring.resume(r.id()); showStatus("Resumed. Occurrences missed while paused are skipped."); }
            else { recurring.pause(r.id()); showStatus("Paused. Nothing will be generated until you resume."); }
            refresh(false);
        } catch (RuntimeException ex) { UiKit.error(UiKit.message(ex)); }
    }

    private void delete(RecurringRule r) {
        long n = recurring.generatedCount(r.id());
        if (!UiKit.confirm("Delete Rule", "Delete this recurring rule?",
                "No further transactions will be created.\n" + n + " transaction(s) already generated will be kept in your history."))
            return;
        try { recurring.delete(r.id()); refresh(false); }
        catch (RuntimeException ex) { UiKit.error("Could not delete: " + UiKit.message(ex)); }
    }

    private void openForm(RecurringRule existing) {
        UiKit.Form f = new UiKit.Form();
        ComboBox<String> type = f.add("Type", UiKit.combo());
        type.setItems(FXCollections.observableArrayList("Expense", "Income"));
        type.setValue("Expense");
        TextField amount = f.add("Amount (₹)", UiKit.textField("0.00"));
        ComboBox<Category> category = f.add("Category", UiKit.combo());
        ComboBox<Account> account = f.add("Account", UiKit.combo());
        List<Account> accts = accounts.getAccounts();
        account.setItems(FXCollections.observableArrayList(accts));
        account.setValue(accts.isEmpty() ? null : accts.get(0));
        TextField desc = f.add("Description", UiKit.textField("e.g. Monthly rent"));
        TextField merchant = f.add("Merchant / Source", UiKit.textField("Optional"));
        ComboBox<Frequency> freq = f.add("Repeats", UiKit.combo());
        freq.setItems(FXCollections.observableArrayList(Frequency.values()));
        freq.setValue(Frequency.MONTHLY);
        DatePicker start = f.add("Start date", UiKit.datePicker(LocalDate.now()));
        DatePicker end = f.add("End date (optional)", UiKit.datePicker(null));
        f.hint("A start date in the past creates the missed transactions immediately. Month-end dates are handled safely (31st → last day of shorter months).");

        Runnable loadCats = () -> {
            Transaction.Type t = "Income".equals(type.getValue()) ? Transaction.Type.INCOME : Transaction.Type.EXPENSE;
            Category keep = category.getValue();
            List<Category> cats = txs.getCategoriesForType(t);
            category.setItems(FXCollections.observableArrayList(cats));
            cats.stream().filter(c -> keep != null && c.getId() == keep.getId()).findFirst()
                .ifPresentOrElse(category::setValue, () -> { if (!cats.isEmpty()) category.setValue(cats.get(0)); });
        };
        type.setOnAction(e -> loadCats.run());
        loadCats.run();

        if (existing != null) {
            type.setValue(existing.type() == Transaction.Type.INCOME ? "Income" : "Expense");
            loadCats.run();
            amount.setText(BigDecimal.valueOf(existing.amountCents(), 2).toPlainString());
            category.getItems().stream().filter(c -> c.getId() == existing.categoryId()).findFirst().ifPresent(category::setValue);
            accts.stream().filter(a -> a.id() == existing.accountId()).findFirst().ifPresent(account::setValue);
            desc.setText(existing.description()); merchant.setText(existing.merchant());
            freq.setValue(existing.frequency()); start.setValue(existing.startDate()); end.setValue(existing.endDate());
        }
        Runnable save = () -> {
            Transaction.Type t = "Income".equals(type.getValue()) ? Transaction.Type.INCOME : Transaction.Type.EXPENSE;
            long cat = category.getValue() == null ? 0 : category.getValue().getId();
            long acc = account.getValue() == null ? 0 : account.getValue().id();
            if (existing == null) recurring.create(t, amount.getText(), cat, acc, desc.getText(), merchant.getText(), freq.getValue(), start.getValue(), end.getValue());
            else recurring.update(existing.id(), t, amount.getText(), cat, acc, desc.getText(), merchant.getText(), freq.getValue(), start.getValue(), end.getValue());
        };
        if (UiKit.showForm(ruleTable.getScene().getWindow(), existing == null ? "New Recurring Rule" : "Edit Recurring Rule",
                existing == null ? "Create" : "Update", f, save)) refresh(false);
    }
}
