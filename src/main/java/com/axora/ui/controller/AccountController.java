package com.axora.ui.controller;

import com.axora.model.*;
import com.axora.service.AccountService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Accounts screen: balances (derived), history per account, transfers. */
public class AccountController {

    @FXML private HBox totalsBox;
    @FXML private Label noteLabel, activityTitle;
    @FXML private TableView<AccountBalance> accountTable;
    @FXML private TableView<AccountActivity> activityTable;
    @FXML private TableView<Transfer> transferTable;

    private final AccountService svc = new AccountService();

    private record Data(List<AccountBalance> balances, Map<String, Long> totals, List<Transfer> transfers) {}

    @FXML
    public void initialize() {
        accountTable.setPlaceholder(new Label("No accounts."));
        UiKit.columns(accountTable,
            UiKit.<AccountBalance>column("Account", 165, b -> b.account().name() + (b.account().isDefault() ? "  (default)" : "")),
            UiKit.<AccountBalance>column("Type", 70, b -> b.account().type().toString()),
            UiKit.<AccountBalance>column("Currency", 70, b -> b.account().currency()),
            UiKit.<AccountBalance>rightColumn("Opening", 105, b -> UiKit.money(b.account().openingBalanceCents(), b.account().currency())),
            UiKit.<AccountBalance>rightColumn("Current balance", 125, b -> UiKit.money(b.balanceCents(), b.account().currency())),
            UiKit.<AccountBalance>column("Description", 120, b -> b.account().description()),
            UiKit.<AccountBalance>actions(135, b -> new Node[]{
                UiKit.ghost("Edit", () -> openForm(b.account())), UiKit.danger("Delete", () -> delete(b.account()))}));
        accountTable.getSelectionModel().selectedItemProperty().addListener((o, a, sel) -> loadActivity(sel));

        activityTable.setPlaceholder(new Label("No activity for this account yet."));
        UiKit.columns(activityTable,
            UiKit.<AccountActivity>column("Date", 95, a -> a.date().toString()),
            UiKit.<AccountActivity>column("Kind", 100, a -> switch (a.kind()) {
                case "INCOME" -> "Income"; case "EXPENSE" -> "Expense";
                case "TRANSFER_IN" -> "Transfer in"; default -> "Transfer out"; }),
            UiKit.<AccountActivity>column("Category / counterparty", 190, AccountActivity::label),
            UiKit.<AccountActivity>column("Description", 250, a -> a.description() == null ? "" : a.description()),
            UiKit.<AccountActivity>rightColumn("Effect on balance", 150, a -> (a.signedCents() > 0 ? "+" : "") + UiKit.money(a.signedCents())));
        activityTable.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(AccountActivity a, boolean empty) {
                super.updateItem(a, empty);
                getStyleClass().removeAll("row-income", "row-expense", "row-transfer");
                if (!empty && a != null)
                    getStyleClass().add(a.kind().startsWith("TRANSFER") ? "row-transfer" : a.kind().equals("INCOME") ? "row-income" : "row-expense");
            }
        });

        transferTable.setPlaceholder(new Label("No transfers yet. Use “Transfer” to move money between accounts."));
        UiKit.columns(transferTable,
            UiKit.<Transfer>column("Date", 95, t -> t.date().toString()),
            UiKit.<Transfer>column("From", 140, Transfer::fromAccountName),
            UiKit.<Transfer>column("To", 140, Transfer::toAccountName),
            UiKit.<Transfer>rightColumn("Amount", 120, t -> UiKit.money(t.amountCents())),
            UiKit.<Transfer>column("Description", 190, t -> t.description() == null ? "" : t.description()),
            UiKit.<Transfer>actions(135, t -> new Node[]{
                UiKit.ghost("Edit", () -> openTransferForm(t)), UiKit.danger("Delete", () -> deleteTransfer(t))}));
        refresh();
    }

    private void refresh() {
        AccountBalance selected = accountTable.getSelectionModel().getSelectedItem();
        long selectedId = selected == null ? -1 : selected.account().id();
        UiKit.async(() -> new Data(svc.getBalances(null), svc.totalsByCurrency(null), svc.getTransfers()),
            d -> {
                accountTable.setItems(FXCollections.observableArrayList(d.balances));
                transferTable.setItems(FXCollections.observableArrayList(d.transfers));
                renderTotals(d.totals);
                d.balances.stream().filter(b -> b.account().id() == selectedId).findFirst()
                    .ifPresentOrElse(b -> accountTable.getSelectionModel().select(b), () -> {
                        if (!d.balances.isEmpty()) accountTable.getSelectionModel().selectFirst(); else loadActivity(null);
                    });
            }, ex -> UiKit.error("Failed to load accounts: " + UiKit.message(ex)));
    }

    private void renderTotals(Map<String, Long> totals) {
        totalsBox.getChildren().clear();
        boolean multi = totals.size() > 1;
        for (var e : totals.entrySet()) {
            VBox card = new VBox(UiKit.label(multi ? "Total balance (" + e.getKey() + ")" : "Total balance", "kpi-label"),
                UiKit.label(UiKit.money(e.getValue(), e.getKey()), "kpi-value", e.getValue() >= 0 ? "kpi-positive" : "kpi-negative"));
            card.getStyleClass().addAll("kpi-card", "kpi-net");
            HBox.setHgrow(card, javafx.scene.layout.Priority.ALWAYS);
            card.setMaxWidth(Double.MAX_VALUE);
            totalsBox.getChildren().add(card);
        }
        noteLabel.setVisible(multi); noteLabel.setManaged(multi);
        noteLabel.setText("Accounts in different currencies are totalled separately; Axora does not convert between currencies.");
    }

    private void loadActivity(AccountBalance sel) {
        if (sel == null) { activityTitle.setText("Activity"); activityTable.getItems().clear(); return; }
        activityTitle.setText("Activity — " + sel.account().name());
        long id = sel.account().id();
        UiKit.async(() -> svc.history(id, 200), list -> {
            AccountBalance now = accountTable.getSelectionModel().getSelectedItem();
            if (now != null && now.account().id() == id) activityTable.setItems(FXCollections.observableArrayList(list));
        }, ex -> UiKit.error("Failed to load activity: " + UiKit.message(ex)));
    }

    // ---- account CRUD
    @FXML private void openCreate() { openForm(null); }

    private void openForm(Account existing) {
        UiKit.Form f = new UiKit.Form();
        TextField name = f.add("Name", UiKit.textField("e.g. HDFC Savings"));
        ComboBox<Account.Type> type = f.add("Type", UiKit.combo());
        type.setItems(FXCollections.observableArrayList(Account.Type.values()));
        type.setValue(Account.Type.BANK);
        TextField opening = f.add("Opening balance", UiKit.textField("0.00"));
        TextField currency = f.add("Currency", UiKit.textField("INR"));
        currency.setText("INR");
        TextField desc = f.add("Description", UiKit.textField("Optional"));
        f.hint("Credit accounts may start negative (money owed). Balances are calculated from this opening balance plus transactions and transfers.");
        if (existing != null) {
            name.setText(existing.name()); type.setValue(existing.type());
            opening.setText(BigDecimal.valueOf(existing.openingBalanceCents(), 2).toPlainString());
            currency.setText(existing.currency()); desc.setText(existing.description());
        }
        Runnable save = () -> {
            if (existing == null) svc.createAccount(name.getText(), type.getValue(), opening.getText(), currency.getText(), desc.getText());
            else svc.updateAccount(existing.id(), name.getText(), type.getValue(), opening.getText(), currency.getText(), desc.getText());
        };
        if (UiKit.showForm(accountTable.getScene().getWindow(), existing == null ? "New Account" : "Edit Account",
                existing == null ? "Create" : "Update", f, save)) refresh();
    }

    private void delete(Account a) {
        if (!UiKit.confirm("Delete Account", "Delete '" + a.name() + "'?", "This cannot be undone.")) return;
        try { svc.deleteAccount(a.id()); refresh(); }
        catch (IllegalArgumentException ex) { UiKit.error(ex.getMessage()); }
        catch (RuntimeException ex) { UiKit.error("Could not delete: " + UiKit.message(ex)); }
    }

    // ---- transfers
    @FXML private void openTransfer() { openTransferForm(null); }

    private void openTransferForm(Transfer existing) {
        List<Account> accts = svc.getAccounts();
        if (accts.size() < 2 && existing == null) { UiKit.info("Create a second account first — a transfer needs a source and a destination."); return; }
        UiKit.Form f = new UiKit.Form();
        ComboBox<Account> from = f.add("From", UiKit.combo());
        ComboBox<Account> to   = f.add("To", UiKit.combo());
        from.setItems(FXCollections.observableArrayList(accts));
        to.setItems(FXCollections.observableArrayList(accts));
        TextField amount = f.add("Amount (₹)", UiKit.textField("0.00"));
        DatePicker date = f.add("Date", UiKit.datePicker(LocalDate.now()));
        TextField desc = f.add("Description", UiKit.textField("Optional"));
        f.hint("Transfers move money between your own accounts and are never counted as income or expenses.");
        if (existing != null) {
            accts.stream().filter(a -> a.id() == existing.fromAccountId()).findFirst().ifPresent(from::setValue);
            accts.stream().filter(a -> a.id() == existing.toAccountId()).findFirst().ifPresent(to::setValue);
            amount.setText(BigDecimal.valueOf(existing.amountCents(), 2).toPlainString());
            date.setValue(existing.date()); desc.setText(existing.description());
        } else {
            from.setValue(accts.get(0)); to.setValue(accts.get(1));
        }
        Runnable save = () -> {
            long fid = from.getValue() == null ? 0 : from.getValue().id(), tid = to.getValue() == null ? 0 : to.getValue().id();
            if (existing == null) svc.createTransfer(fid, tid, amount.getText(), date.getValue(), desc.getText());
            else svc.updateTransfer(existing.id(), fid, tid, amount.getText(), date.getValue(), desc.getText());
        };
        if (UiKit.showForm(accountTable.getScene().getWindow(), existing == null ? "New Transfer" : "Edit Transfer",
                existing == null ? "Transfer" : "Update", f, save)) refresh();
    }

    private void deleteTransfer(Transfer t) {
        if (!UiKit.confirm("Delete Transfer", "Delete this transfer?",
                UiKit.money(t.amountCents()) + " from " + t.fromAccountName() + " to " + t.toAccountName()
                + "\nBoth account balances will be adjusted.")) return;
        try { svc.deleteTransfer(t.id()); refresh(); }
        catch (RuntimeException ex) { UiKit.error("Could not delete: " + UiKit.message(ex)); }
    }
}
