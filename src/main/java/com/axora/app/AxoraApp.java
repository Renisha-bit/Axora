package com.axora.app;

import com.axora.dao.sqlite.DatabaseManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * Axora — Value in every choice. Balance in every step.
 * Application entry point.
 */
public class AxoraApp extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        // Initialize database
        DatabaseManager.getInstance().initialize();
        // Create any recurring transactions that became due since the last launch (idempotent).
        try {
            int n = new com.axora.service.RecurringService().generateDue(java.time.LocalDate.now());
            if (n > 0) System.out.println("Axora: generated " + n + " recurring transaction(s).");
        } catch (RuntimeException e) {
            System.err.println("Axora: recurring generation failed: " + e.getMessage());
        }

        URL fxml = getClass().getResource("/com/axora/ui/view/MainView.fxml");
        if (fxml == null) {
            throw new IllegalStateException("MainView.fxml not found on classpath");
        }

        FXMLLoader loader = new FXMLLoader(fxml);
        Scene scene = new Scene(loader.load(), 1200, 760);
        scene.getStylesheets().add(
            getClass().getResource("/com/axora/ui/css/axora.css").toExternalForm()
        );

        primaryStage.setTitle("Axora");
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        DatabaseManager.getInstance().close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
