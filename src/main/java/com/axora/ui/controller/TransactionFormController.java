package com.axora.ui.controller;

import com.axora.model.Category;
import com.axora.model.Transaction;
import com.axora.service.TransactionService;
import com.axora.validation.TransactionValidator;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.List;

/**
 * Add/Edit transaction form controller.
 */
public class TransactionFormController {

    @FXML private Label          formTitle;
    @FXML private ToggleButton   btnIncome, btnExpense;
    // ToggleGroup created programmatically in initialize()
    private ToggleGroup    typeGroup;
    @FXML private TextField      amountField;
    @FXML private ComboBox<Category> categoryCombo;
    @FXML private DatePicker     datePicker;
    @FXML private TextField      descField;
    @FXML private TextField      merchantField;
    @FXML private Label          errorLabel;
    @FXML private Button         saveBtn;

    private final TransactionService service = new TransactionService();
    private Transaction editTarget;
    private Runnable    onSaved;
    private Stage       stage;

    // ----------------------------------------------------------------
    // Initialization
    // ----------------------------------------------------------------

    @FXML
    public void initialize() {
        // Create ToggleGroup programmatically (not from FXML)
        typeGroup = new ToggleGroup();
        btnIncome.setToggleGroup(typeGroup);
        btnExpense.setToggleGroup(typeGroup);
        btnExpense.setSelected(true);
        datePicker.setValue(LocalDate.now());
        typeGroup.selectedToggleProperty().addListener((obs, old, nw) -> refreshCategories());
        refreshCategories();
    }

    /** Call with null for "add" mode, or a Transaction for "edit" mode. */
    public void setTransaction(Transaction tx) {
        this.editTarget = tx;
        if (tx == null) {
            formTitle.setText("Add Transaction");
            saveBtn.setText("Save");
            return;
        }
        formTitle.setText("Edit Transaction");
        saveBtn.setText("Update");
        if (tx.getType() == Transaction.Type.INCOME) btnIncome.setSelected(true);
        else                                          btnExpense.setSelected(true);
        refreshCategories();
        amountField.setText(tx.getAmount().toPlainString());
        datePicker.setValue(tx.getTxDate());
        descField.setText(tx.getDescription());
        merchantField.setText(tx.getMerchant());
        // Select category
        categoryCombo.getItems().stream()
            .filter(c -> c.getId() == tx.getCategoryId())
            .findFirst()
            .ifPresent(categoryCombo::setValue);
    }

    public void setOnSaved(Runnable callback) { this.onSaved = callback; }
    public void setStage(Stage s)             { this.stage = s; }

    // ----------------------------------------------------------------
    // Actions
    // ----------------------------------------------------------------

    @FXML
    private void save() {
        clearError();
        Transaction.Type type = getSelectedType();
        String amountText     = amountField.getText();
        Category cat          = categoryCombo.getValue();
        long categoryId       = cat == null ? 0 : cat.getId();
        LocalDate date        = datePicker.getValue();
        String description    = descField.getText();
        String merchant       = merchantField.getText();

        List<String> errors = TransactionValidator.validate(
            type, amountText, categoryId, date, description);
        if (!errors.isEmpty()) {
            showError(String.join("\n", errors));
            return;
        }

        try {
            if (editTarget == null) {
                service.addTransaction(type, amountText, categoryId, date, description, merchant);
            } else {
                service.updateTransaction(editTarget.getId(), type, amountText,
                                          categoryId, date, description, merchant);
            }
            if (onSaved != null) onSaved.run();
            if (stage != null)   stage.close();
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void cancel() {
        if (stage != null) stage.close();
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private void refreshCategories() {
        Transaction.Type type = getSelectedType();
        List<Category> cats = service.getCategoriesForType(type);
        Category current = categoryCombo.getValue();
        categoryCombo.setItems(FXCollections.observableArrayList(cats));
        // Try to retain selection
        if (current != null) {
            cats.stream().filter(c -> c.getId() == current.getId())
                .findFirst().ifPresent(categoryCombo::setValue);
        }
        if (categoryCombo.getValue() == null && !cats.isEmpty()) {
            categoryCombo.setValue(cats.get(0));
        }
    }

    private Transaction.Type getSelectedType() {
        Toggle sel = typeGroup.getSelectedToggle();
        if (sel == btnIncome) return Transaction.Type.INCOME;
        return Transaction.Type.EXPENSE;
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }

    private void clearError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
    }
}
