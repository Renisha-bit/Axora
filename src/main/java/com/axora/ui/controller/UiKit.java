package com.axora.ui.controller;

import com.axora.util.CurrencyUtil;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/** Small shared helpers for the Phase 2 screens: dialogs, async loading, badges, bars, columns. */
final class UiKit {
    private UiKit() {}

    static final String CSS = "/com/axora/ui/css/axora.css";

    // ---- formatting
    static String money(long cents) { return cents < 0 ? "-" + CurrencyUtil.format(-cents) : CurrencyUtil.format(cents); }

    /** Amount with the account's own currency code (e.g. "INR 1,000.00"); ₹ symbol for INR. */
    static String money(long cents, String currency) {
        if (currency == null || "INR".equals(currency)) return money(cents);
        String n = money(Math.abs(cents)).substring(1);
        return (cents < 0 ? "-" : "") + currency + " " + n;
    }

    static String date(LocalDate d) { return d == null ? "—" : d.toString(); }

    // ---- alerts
    static void error(String msg)   { new Alert(Alert.AlertType.ERROR, msg).showAndWait(); }
    static void info(String msg)    { new Alert(Alert.AlertType.INFORMATION, msg).showAndWait(); }

    static boolean confirm(String title, String header, String content) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(title); a.setHeaderText(header); a.setContentText(content);
        return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    // ---- small widgets
    static Label label(String text, String... styles) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styles);
        return l;
    }

    static Label badge(String text, String variant) {
        Label l = new Label(text);
        l.getStyleClass().addAll("badge", variant);
        return l;
    }

    static VBox metric(String caption, String value, boolean negative) {
        Label v = label(value, "metric-value");
        if (negative) v.getStyleClass().add("metric-negative");
        VBox b = new VBox(2, label(caption, "metric-label"), v);
        b.setMinWidth(110);
        return b;
    }

    static ProgressBar bar(double ratio, String stateClass) {
        ProgressBar pb = new ProgressBar(Math.max(0, Math.min(1, ratio)));
        pb.setMaxWidth(Double.MAX_VALUE);
        pb.getStyleClass().add(stateClass);
        return pb;
    }

    static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    static Button ghost(String text, Runnable r)  { Button b = new Button(text); b.getStyleClass().add("btn-ghost-sm");  b.setOnAction(e -> r.run()); return b; }
    static Button danger(String text, Runnable r) { Button b = new Button(text); b.getStyleClass().add("btn-danger-sm"); b.setOnAction(e -> r.run()); return b; }

    static <T> TableColumn<T, String> column(String title, double width, Function<T, String> f) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(f.apply(cd.getValue())));
        return c;
    }

    static <T> TableColumn<T, String> rightColumn(String title, double width, Function<T, String> f) {
        TableColumn<T, String> c = column(title, width, f);
        c.setStyle("-fx-alignment: CENTER_RIGHT;");
        return c;
    }

    /** Actions column; the factory returns the buttons for a row item. */
    static <T> TableColumn<T, Void> actions(double width, Function<T, Node[]> buttons) {
        TableColumn<T, Void> c = new TableColumn<>("");
        c.setPrefWidth(width);
        c.setMinWidth(width);
        c.setSortable(false);
        c.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                T item = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (item == null) { setGraphic(null); return; }
                HBox box = new HBox(6);
                box.setAlignment(Pos.CENTER_LEFT);
                box.getChildren().addAll(buttons.apply(item));
                setGraphic(box);
            }
        });
        return c;
    }

    @SafeVarargs @SuppressWarnings("varargs")
    static <T> void columns(TableView<T> t, TableColumn<T, ?>... cols) { t.getColumns().setAll(java.util.Arrays.asList(cols)); }

    /** Make a table's columns shrink to fit instead of scrolling sideways. */
    static void fit(TableView<?> t) { t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); }

    static <T> TableView<T> table(String emptyTitle, String emptySub) {
        TableView<T> t = new TableView<>();
        t.getStyleClass().add("table-clean");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox ph = new VBox(6, label(emptyTitle, "empty-state-label"), label(emptySub, "empty-state-sublabel"));
        ph.setAlignment(Pos.CENTER);
        t.setPlaceholder(ph);
        return t;
    }

    // ---- async
    static <T> void async(Callable<T> work, Consumer<T> onOk, Consumer<Throwable> onFail) {
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return work.call(); } };
        task.setOnSucceeded(e -> onOk.accept(task.getValue()));
        task.setOnFailed(e -> onFail.accept(task.getException()));
        Thread t = new Thread(task, "axora-load");
        t.setDaemon(true);
        t.start();
    }

    static String message(Throwable t) { return t.getMessage() == null ? t.toString() : t.getMessage(); }

    // ---- dialog forms
    /** A label/field grid with an inline error line. */
    static final class Form {
        final GridPane grid = new GridPane();
        final Label error = new Label();
        private int row = 0;

        Form() {
            grid.setHgap(14); grid.setVgap(10);
            grid.setPadding(new Insets(8, 8, 4, 8));
            ColumnConstraints a = new ColumnConstraints(); a.setMinWidth(120);
            ColumnConstraints b = new ColumnConstraints(); b.setHgrow(Priority.ALWAYS); b.setMinWidth(260);
            grid.getColumnConstraints().addAll(a, b);
            error.getStyleClass().add("error-label");
            error.setWrapText(true);
            error.setVisible(false); error.setManaged(false);
        }

        <T extends Node> T add(String caption, T field) {
            Label l = label(caption, "form-label");
            grid.add(l, 0, row);
            grid.add(field, 1, row);
            if (field instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
            row++;
            return field;
        }

        /** Adds a row whose visibility can be toggled (both caption and field). */
        <T extends Node> T addToggleable(String caption, T field, javafx.beans.value.ObservableValue<Boolean> visible) {
            Label l = label(caption, "form-label");
            grid.add(l, 0, row); grid.add(field, 1, row);
            for (Node n : new Node[]{l, field}) { n.visibleProperty().bind(visible); n.managedProperty().bind(visible); }
            if (field instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
            row++;
            return field;
        }

        void hint(String text) {
            Label l = label(text, "hint-label");
            grid.add(l, 1, row++);
        }

        void showError(String msg) { error.setText(msg); error.setVisible(true); error.setManaged(true); }
    }

    static TextField textField(String prompt) {
        TextField t = new TextField();
        t.setPromptText(prompt);
        t.getStyleClass().add("text-field-clean");
        return t;
    }

    static <T> ComboBox<T> combo() {
        ComboBox<T> c = new ComboBox<>();
        c.getStyleClass().add("combo-clean");
        c.setMaxWidth(Double.MAX_VALUE);
        return c;
    }

    static DatePicker datePicker(LocalDate initial) {
        DatePicker d = new DatePicker(initial);
        d.setMaxWidth(Double.MAX_VALUE);
        return d;
    }

    /**
     * Shows a modal form. onSave runs when the user presses the primary button; throwing
     * IllegalArgumentException keeps the dialog open and shows the message inline.
     * @return true if saved
     */
    static boolean showForm(Window owner, String title, String okText, Form form, Runnable onSave) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle(title);
        if (owner != null) dlg.initOwner(owner);
        ButtonType ok = new ButtonType(okText, ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        dlg.getDialogPane().getStylesheets().add(UiKit.class.getResource(CSS).toExternalForm());
        VBox content = new VBox(12, label(title, "form-title"), form.grid, form.error);
        content.setPadding(new Insets(16, 20, 8, 20));
        dlg.getDialogPane().setContent(content);

        Button okBtn = (Button) dlg.getDialogPane().lookupButton(ok);
        okBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            try { onSave.run(); }
            catch (IllegalArgumentException ex) { form.showError(ex.getMessage()); ev.consume(); }
            catch (RuntimeException ex) { form.showError("Could not save: " + message(ex)); ev.consume(); }
        });
        return dlg.showAndWait().filter(b -> b == ok).isPresent();
    }

    /** Runs on the FX thread (used by async callbacks that may fire after navigation). */
    static void later(Runnable r) { Platform.runLater(r); }
}
