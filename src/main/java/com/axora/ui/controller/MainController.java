package com.axora.ui.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.net.URL;

/**
 * Main shell controller: swaps views in the content area.
 */
public class MainController {

    @FXML private StackPane contentArea;
    @FXML private Button navDashboard;
    @FXML private Button navTransactions;
    @FXML private Button navBudgets;
    @FXML private Button navRecurring;
    @FXML private Button navAccounts;
    @FXML private Button navSavings;
    @FXML private Button navReports;
    @FXML private Button navSettings;

    private Node settingsView;

    private final Button[] allNavBtns = new Button[8];

    @FXML
    public void initialize() throws IOException {
        allNavBtns[0] = navDashboard;
        allNavBtns[1] = navTransactions;
        allNavBtns[2] = navBudgets;
        allNavBtns[3] = navRecurring;
        allNavBtns[4] = navAccounts;
        allNavBtns[5] = navSavings;
        allNavBtns[6] = navReports;
        allNavBtns[7] = navSettings;

        settingsView = loadView("/com/axora/ui/view/SettingsView.fxml");
        showDashboard();
    }

    // Data screens are reloaded on every visit so they always reflect the latest persisted data
    // (e.g. the dashboard after a recurring rule generated transactions).
    @FXML public void showDashboard()    { showFresh(navDashboard,    "/com/axora/ui/view/DashboardView.fxml"); }
    @FXML public void showTransactions() { showFresh(navTransactions, "/com/axora/ui/view/TransactionView.fxml"); }
    @FXML public void showBudgets()      { showFresh(navBudgets,      "/com/axora/ui/view/BudgetView.fxml"); }
    @FXML public void showRecurring()    { showFresh(navRecurring,    "/com/axora/ui/view/RecurringView.fxml"); }
    @FXML public void showAccounts()     { showFresh(navAccounts,     "/com/axora/ui/view/AccountView.fxml"); }
    @FXML public void showSavings()      { showFresh(navSavings,      "/com/axora/ui/view/SavingsView.fxml"); }
    @FXML public void showReports()      { showFresh(navReports,      "/com/axora/ui/view/ReportView.fxml"); }
    @FXML public void showSettings()     { setActive(navSettings);    setContent(settingsView); }

    private void showFresh(Button nav, String fxml) {
        try {
            Node view = loadView(fxml);
            setActive(nav);
            setContent(view);
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,
                "Could not open this section: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage())).showAndWait();
        }
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private void setContent(Node node) {
        contentArea.getChildren().setAll(node);
    }

    private void setActive(Button active) {
        for (Button btn : allNavBtns) {
            if (btn != null) btn.getStyleClass().remove("nav-active");
        }
        if (!active.getStyleClass().contains("nav-active")) {
            active.getStyleClass().add("nav-active");
        }
    }

    private Node loadView(String fxmlPath) throws IOException {
        URL url = getClass().getResource(fxmlPath);
        if (url == null) throw new IOException("FXML not found: " + fxmlPath);
        return new FXMLLoader(url).load();
    }
}
