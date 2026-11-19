package com.finance.util;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The simple dialogs of the app, in one place, with the text passed in:
 * <ul>
 *   <li>{@link #confirm(String)}: OK and Cancel, equal width side by side across the dialog, Cancel red; returns true on OK.</li>
 *   <li>{@link #info(String, String)}, {@link #error(String, String)}, {@link #show}: a title, a text and one OK button across the dialog.</li>
 * </ul>
 * The text wraps at 480 px and the dialog is at least 520 px wide, so a long message never makes the window huge.
 * OK, Cancel, Esc and the title bar's X all work. Dialogs with their own content (pickers, forms, the Logout steps) do
 * not use this.
 */
public final class AppDialogs {
    private static final double TEXT_WIDTH = 280;
    private static final double MIN_WIDTH = 320;

    private AppDialogs() {}

    /** @return true only when the user pressed OK */
    public static boolean confirm(String message) {
        Alert alert = new AppAlert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm");
        alert.setGraphic(null);
        Button ok = okButton(alert);
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().addAll("btn-secondary", "btn-reset");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> alert.setResult(ButtonType.CANCEL));
        return build(alert, message, ok, cancel).showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    public static void info(String title, String message) {
        show(Alert.AlertType.INFORMATION, title, message);
    }

    public static void error(String title, String message) {
        show(Alert.AlertType.ERROR, title, message);
    }

    public static void show(Alert.AlertType type, String title, String message) {
        Alert alert = new AppAlert(type);
        alert.setTitle(title);
        build(alert, message, okButton(alert)).showAndWait();
    }

    private static Button okButton(Alert alert) {
        Button ok = new Button("OK");
        ok.getStyleClass().add("btn-primary");
        ok.setDefaultButton(true);
        ok.setOnAction(e -> alert.setResult(ButtonType.OK));
        // a message dialog has no Cancel: Esc and X close it the same way
        return ok;
    }

    /** Puts the text and the button row into the dialog, hides the standard button bar, returns the dialog. */
    private static Alert build(Alert alert, String message, Button... buttons) {
        alert.setHeaderText(null);
        alert.getDialogPane().setMinWidth(MIN_WIDTH);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        Label msg = new Label(message);
        msg.setWrapText(true);
        msg.setPrefWidth(TEXT_WIDTH);   // fixed width: an unconstrained wrapped label makes the dialog huge
        msg.setMaxWidth(TEXT_WIDTH);
        msg.setMinHeight(Region.USE_PREF_SIZE);

        HBox row = new HBox(10, buttons);
        for (Button b : buttons) {
            b.setPrefWidth(150);
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
        }

        DialogChrome.hideStandardButtons(alert);
        alert.getDialogPane().setContent(new VBox(14, msg, row));
        return alert;
    }
}
