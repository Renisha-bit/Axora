package com.axora.ui.controller;

import com.axora.dao.TransactionFilter;
import com.axora.model.Category;
import com.axora.model.Transaction;
import com.axora.service.TransactionService;
import com.axora.util.CurrencyUtil;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Controller for the Transactions list/filter view.
 */
public class TransactionController {

    @FXML private ComboBox<String>      periodCombo;
    @FXML private ComboBox<String>      typeCombo;
    @FXML private ComboBox<Category>    categoryCombo;
    @FXML private TextField             keywordField;
    @FXML private TextField             minAmountField;
    @FXML private TextField             maxAmountField;
    @FXML private Label                 resultsLabel;
    @FXML private TableView<Transaction> transactionTable;
    @FXML private TableColumn<Transaction, String> colDate, colType, colCategory,
                                                   colDesc, colMerchant, colAmount;
    @FXML private TableColumn<Transaction, Void>   colActions;

    private final TransactionService service = new TransactionService();

    @FXML
    public void initialize() {
        setupPeriodCombo();
        setupTypeCombo();
        setupCategoryCombo();
        setupTable();
        loadTransactions(buildFilter());
    }

    // ----------------------------------------------------------------
    // Setup
    // ----------------------------------------------------------------

    private void setupPeriodCombo() {
        periodCombo.setItems(FXCollections.observableArrayList(
            "All Time", "Today", "This Week", "This Month",
            "Last Month", "This Year"
        ));
        periodCombo.setValue("All Time");
    }

    private void setupTypeCombo() {
        typeCombo.setItems(FXCollections.observableArrayList("All", "Income", "Expense"));
        typeCombo.setValue("All");
    }

    private void setupCategoryCombo() {
        List<Category> cats = service.getAllCategories();
        Category all = new Category("All Categories", null);
        cats.add(0, all);
        categoryCombo.setItems(FXCollections.observableArrayList(cats));
        categoryCombo.setValue(all);
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
        colMerchant.setCellValueFactory(cd ->
            new javafx.beans.property.SimpleStringProperty(
                cd.getValue().getMerchant()));
        colAmount.setCellValueFactory(cd ->
            new javafx.beans.property.SimpleStringProperty(
                CurrencyUtil.format(cd.getValue().getAmountCents())));

        // Actions column — Edit + Delete buttons
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn   = new Button("Edit");
            private final Button deleteBtn = new Button("Del");
            private final HBox   box       = new HBox(6, editBtn, deleteBtn);
            {
                editBtn.getStyleClass().add("btn-ghost-sm");
                deleteBtn.getStyleClass().add("btn-danger-sm");
                editBtn.setOnAction(e -> openEditForm(getTableRow().getItem()));
                deleteBtn.setOnAction(e -> confirmDelete(getTableRow().getItem()));
            }
            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : box);
            }
        });

        // Color rows by type
        transactionTable.setRowFactory(tv -> new TableRow<>() {
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
    // Filter actions
    // ----------------------------------------------------------------

    @FXML
    private void applyFilters() {
        loadTransactions(buildFilter());
    }

    @FXML
    private void clearFilters() {
        periodCombo.setValue("All Time");
        typeCombo.setValue("All");
        categoryCombo.getSelectionModel().selectFirst();
        keywordField.clear();
        minAmountField.clear();
        maxAmountField.clear();
        loadTransactions(buildFilter());
    }

    private TransactionFilter buildFilter() {
        TransactionFilter.Builder b = TransactionFilter.builder();

        // Date range from period
        LocalDate[] range = getDateRange();
        if (range != null) { b.from(range[0]).to(range[1]); }

        // Type
        String typeVal = typeCombo.getValue();
        if ("Income".equals(typeVal))  b.type(Transaction.Type.INCOME);
        if ("Expense".equals(typeVal)) b.type(Transaction.Type.EXPENSE);

        // Category
        Category cat = categoryCombo.getValue();
        if (cat != null && cat.getId() > 0) b.categoryId(cat.getId());

        // Keyword
        String kw = keywordField.getText();
        if (kw != null && !kw.isBlank()) b.keyword(kw);

        // Amount range
        parseLong(minAmountField.getText(), b, true);
        parseLong(maxAmountField.getText(), b, false);

        return b.build();
    }

    private void parseLong(String text, TransactionFilter.Builder b, boolean isMin) {
        if (text == null || text.isBlank()) return;
        long cents = CurrencyUtil.parseCents(text);
        if (cents >= 0) {
            if (isMin) b.minAmountCents(cents);
            else        b.maxAmountCents(cents);
        }
    }

    private LocalDate[] getDateRange() {
        LocalDate now = LocalDate.now();
        return switch (periodCombo.getValue()) {
            case "Today"      -> new LocalDate[]{now, now};
            case "This Week"  -> new LocalDate[]{now.minusDays(now.getDayOfWeek().getValue() - 1), now};
            case "This Month" -> new LocalDate[]{now.withDayOfMonth(1), now};
            case "Last Month" -> {
                LocalDate s = now.withDayOfMonth(1).minusMonths(1);
                yield new LocalDate[]{s, s.withDayOfMonth(s.lengthOfMonth())};
            }
            case "This Year"  -> new LocalDate[]{now.withDayOfYear(1), now};
            default           -> null; // All Time
        };
    }

    // ----------------------------------------------------------------
    // Data loading
    // ----------------------------------------------------------------

    private void loadTransactions(TransactionFilter filter) {
        Task<List<Transaction>> task = new Task<>() {
            @Override
            protected List<Transaction> call() {
                return filter.isEmpty()
                    ? service.getAllTransactions()
                    : service.getFiltered(filter);
            }
        };
        task.setOnSucceeded(e -> {
            List<Transaction> list = task.getValue();
            transactionTable.setItems(FXCollections.observableArrayList(list));
            resultsLabel.setText(list.size() + " transaction" + (list.size() == 1 ? "" : "s"));
        });
        task.setOnFailed(e -> task.getException().printStackTrace());
        Thread t = new Thread(task, "axora-tx-load");
        t.setDaemon(true);
        t.start();
    }

    // ----------------------------------------------------------------
    // Add / Edit forms
    // ----------------------------------------------------------------

    @FXML
    private void openAddForm() {
        openForm(null);
    }

    private void openEditForm(Transaction tx) {
        if (tx == null) return;
        openForm(tx);
    }

    private void openForm(Transaction existing) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/axora/ui/view/TransactionFormView.fxml"));
            javafx.scene.Parent root = loader.load();
            TransactionFormController ctrl = loader.getController();
            ctrl.setTransaction(existing);
            ctrl.setOnSaved(() -> loadTransactions(buildFilter()));

            Stage dialog = new Stage();
            dialog.setTitle(existing == null ? "Add Transaction" : "Edit Transaction");
            dialog.initModality(Modality.WINDOW_MODAL);
            Window owner = transactionTable.getScene().getWindow();
            dialog.initOwner(owner);
            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                getClass().getResource("/com/axora/ui/css/axora.css").toExternalForm());
            dialog.setScene(scene);
            dialog.setResizable(false);
            ctrl.setStage(dialog);
            dialog.showAndWait();
        } catch (IOException e) {
            showError("Could not open form: " + e.getMessage());
        }
    }

    // ----------------------------------------------------------------
    // Delete
    // ----------------------------------------------------------------

    private void confirmDelete(Transaction tx) {
        if (tx == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Transaction");
        alert.setHeaderText("Delete this transaction?");
        alert.setContentText("Amount: " + CurrencyUtil.format(tx.getAmountCents()) +
                             "\nDate: " + tx.getTxDate() +
                             "\n\nThis action cannot be undone.");
        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                service.deleteTransaction(tx.getId());
                loadTransactions(buildFilter());
            }
        });
    }

    // ----------------------------------------------------------------
    // CSV Export
    // ----------------------------------------------------------------

    @FXML
    private void exportCsv() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Transactions");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        fc.setInitialFileName("axora-transactions.csv");
        File file = fc.showSaveDialog(transactionTable.getScene().getWindow());
        if (file == null) return;

        List<Transaction> rows = transactionTable.getItems();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                new com.axora.export.CsvExporter().export(rows, file);
                return null;
            }
        };
        task.setOnSucceeded(e -> showInfo("Exported " + rows.size() + " rows to " + file.getName()));
        task.setOnFailed(e -> showError("Export failed: " + task.getException().getMessage()));
        new Thread(task, "axora-export").start();
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).showAndWait();
    }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg).showAndWait();
    }
}
