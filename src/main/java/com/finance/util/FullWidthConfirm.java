package com.finance.util;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Confirm dialog with an own OK / Cancel row: equal width, side by side, full dialog width, Cancel red
 * (same look as the Google Drive confirmations in the Admin Panel). OK, Cancel, Esc and the title bar's X all work.
 */
public final class FullWidthConfirm {
    private FullWidthConfirm() {}

    /** @return true only when the user pressed OK */
    public static boolean ask(String message) {
        Alert alert = new AppAlert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm");
        alert.setHeaderText(null);
        alert.setGraphic(null);
        alert.getDialogPane().setMinWidth(460);
        alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

        Label msg = new Label(message);
        msg.setWrapText(true);
        msg.setPrefWidth(420);   // fixed width: an unconstrained wrapped label makes the dialog huge
        msg.setMaxWidth(420);
        msg.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

        Button okBtn = new Button("OK");
        okBtn.getStyleClass().add("btn-primary");
        okBtn.setDefaultButton(true);
        okBtn.setOnAction(e -> alert.setResult(ButtonType.OK));
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().addAll("btn-secondary", "btn-reset");
        cancelBtn.setCancelButton(true);
        cancelBtn.setOnAction(e -> alert.setResult(ButtonType.CANCEL));
        HBox row = new HBox(10, okBtn, cancelBtn);
        for (Button b : new Button[]{okBtn, cancelBtn}) {
            b.setPrefWidth(150);
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
        }

        DialogChrome.hideStandardButtons(alert);
        alert.getDialogPane().setContent(new VBox(14, msg, row));
        return alert.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }
}
