package com.axora.ui.controller;

import com.axora.export.CsvExporter;
import com.axora.export.JsonBackupService;
import com.axora.service.TransactionService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/** Controller for the Settings / Data Portability view. */
public class SettingsController {

    @FXML private Label statusLabel;

    private final JsonBackupService backupService  = new JsonBackupService();
    private final TransactionService txService     = new TransactionService();

    // ----------------------------------------------------------------
    // Export Backup (JSON)
    // ----------------------------------------------------------------

    @FXML
    private void exportBackup() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Backup");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Backup", "*.json"));
        String name = "axora-backup-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".json";
        fc.setInitialFileName(name);
        File file = fc.showSaveDialog(statusLabel.getScene().getWindow());
        if (file == null) return;

        runAsync(() -> backupService.backup(file),
                 "Backup saved to " + file.getName(),
                 "Backup failed");
    }

    // ----------------------------------------------------------------
    // Restore from Backup
    // ----------------------------------------------------------------

    @FXML
    private void importBackup() {
        Alert warn = new Alert(Alert.AlertType.WARNING);
        warn.setTitle("Restore from Backup");
        warn.setHeaderText("Replace all data?");
        warn.setContentText(
            "Restoring will permanently replace ALL current transactions and categories.\n\n" +
            "Make sure you have a recent backup before proceeding.\n\n" +
            "This action cannot be undone.");
        warn.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        Optional<ButtonType> result = warn.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        FileChooser fc = new FileChooser();
        fc.setTitle("Open Backup File");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Backup", "*.json"));
        File file = fc.showOpenDialog(statusLabel.getScene().getWindow());
        if (file == null) return;

        runAsync(() -> backupService.restore(file),
                 "Restore complete. Restart the application or navigate away and back to see the updated data.",
                 "Restore failed — your data was not changed");
    }

    // ----------------------------------------------------------------
    // Export All CSV
    // ----------------------------------------------------------------

    @FXML
    private void exportAllCsv() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export All Transactions");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        fc.setInitialFileName("axora-all-transactions.csv");
        File file = fc.showSaveDialog(statusLabel.getScene().getWindow());
        if (file == null) return;

        var txList = txService.getAllTransactions();
        runAsync(() -> new CsvExporter().export(txList, file),
                 "Exported " + txList.size() + " transactions to " + file.getName(),
                 "CSV export failed");
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    @FunctionalInterface
    interface ThrowingRunnable { void run() throws Exception; }

    private void runAsync(ThrowingRunnable work, String successMsg, String errorPrefix) {
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception { work.run(); return null; }
        };
        task.setOnSucceeded(e -> showStatus(successMsg, false));
        task.setOnFailed(e -> {
            String detail = task.getException() != null ? task.getException().getMessage() : "";
            showStatus(errorPrefix + ": " + detail, true);
        });
        new Thread(task, "axora-settings-op").start();
    }

    private void showStatus(String msg, boolean isError) {
        statusLabel.setText(msg);
        statusLabel.getStyleClass().removeAll("settings-status-ok", "settings-status-error");
        statusLabel.getStyleClass().add(isError ? "settings-status-error" : "settings-status-ok");
        statusLabel.setVisible(true);
    }
}
