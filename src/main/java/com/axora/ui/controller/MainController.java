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

    private Node dashboardView;
    private Node transactionView;

    @FXML
    public void initialize() throws IOException {
        dashboardView   = loadView("/com/axora/ui/view/DashboardView.fxml");
        transactionView = loadView("/com/axora/ui/view/TransactionView.fxml");
        showDashboard();
    }

    @FXML
    public void showDashboard() {
        setActive(navDashboard);
        setContent(dashboardView);
    }

    @FXML
    public void showTransactions() {
        setActive(navTransactions);
        setContent(transactionView);
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private void setContent(Node node) {
        contentArea.getChildren().setAll(node);
    }

    private void setActive(Button active) {
        for (Button btn : new Button[]{navDashboard, navTransactions}) {
            btn.getStyleClass().remove("nav-active");
        }
        if (!active.getStyleClass().contains("nav-active")) {
            active.getStyleClass().add("nav-active");
        }
    }

    private Node loadView(String fxmlPath) throws IOException {
        URL url = getClass().getResource(fxmlPath);
        if (url == null) throw new IOException("FXML not found: " + fxmlPath);
        FXMLLoader loader = new FXMLLoader(url);
        return loader.load();
    }
}
