package com.axora.ui.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;

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

    private Node dashboardView;
    private Node transactionView;
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

        dashboardView   = loadView("/com/axora/ui/view/DashboardView.fxml");
        transactionView = loadView("/com/axora/ui/view/TransactionView.fxml");
        settingsView    = loadView("/com/axora/ui/view/SettingsView.fxml");
        showDashboard();
    }

    @FXML public void showDashboard()    { setActive(navDashboard);    setContent(dashboardView);   }
    @FXML public void showTransactions() { setActive(navTransactions);  setContent(transactionView); }
    @FXML public void showSettings()     { setActive(navSettings);      setContent(settingsView);    }
    @FXML public void showBudgets()      { showPlaceholder("Budgets", navBudgets); }
    @FXML public void showRecurring()    { showPlaceholder("Recurring", navRecurring); }
    @FXML public void showAccounts()     { showPlaceholder("Accounts", navAccounts); }
    @FXML public void showSavings()      { showPlaceholder("Savings Goals", navSavings); }
    @FXML public void showReports()      { showPlaceholder("Reports", navReports); }

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

    private void showPlaceholder(String title, Button active) {
        VBox page = new VBox(24);
        page.setPadding(new Insets(32));
        page.getStyleClass().add("page-content");

        Label pageTitle = new Label(title);
        pageTitle.getStyleClass().add("page-title");

        VBox notice = new VBox(12);
        notice.getStyleClass().add("chart-card");
        Label noticeTitle = new Label(title);
        noticeTitle.getStyleClass().add("card-title");
        Label message = new Label(
            "This section is not implemented yet. You can use the Dashboard and Transactions sections in the meantime.");
        message.getStyleClass().add("results-label");
        message.setWrapText(true);
        notice.getChildren().addAll(noticeTitle, message);
        page.getChildren().addAll(pageTitle, notice);

        setActive(active);
        setContent(page);
    }

    private Node loadView(String fxmlPath) throws IOException {
        URL url = getClass().getResource(fxmlPath);
        if (url == null) throw new IOException("FXML not found: " + fxmlPath);
        return new FXMLLoader(url).load();
    }
}
