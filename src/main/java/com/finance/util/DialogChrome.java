package com.finance.util;

import javafx.geometry.Pos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.shape.SVGPath;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.net.URL;

/**
 * Gives a Dialog/Alert the app's own metallic title bar (same look as {@link WindowChrome}): the window becomes
 * undecorated and the bar is the dialog pane's header. Use through AppAlert, AppDialog and AppTextInputDialog.
 * Call before the dialog is shown. A header text set on the dialog is not shown (the bar's title says it).
 */
public final class DialogChrome {
    private DialogChrome() {}

    /**
     * For a dialog that builds its own button row: hides the standard button bar completely (no empty strip under the
     * content) but keeps one CANCEL-type button type, because a dialog without one refuses the title bar's X.
     */
    public static void hideStandardButtons(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().setAll(ButtonType.CANCEL);
        javafx.scene.Node bar = pane.lookup(".button-bar");
        if (bar instanceof Region) {
            Region r = (Region) bar;
            r.setVisible(false);
            r.setManaged(false);
            r.setMinHeight(0);
            r.setPrefHeight(0);
            r.setMaxHeight(0);
        }
    }

    public static void apply(Dialog<?> dialog) {
        dialog.initStyle(StageStyle.UNDECORATED);
        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("app-window");

        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("app-titlebar");
        URL logoUrl = DialogChrome.class.getResource("/images/logo.png");
        if (logoUrl != null) {
            bar.getChildren().add(new ImageView(new Image(logoUrl.toExternalForm(), 18, 18, true, true)));
        }
        Label title = new Label();
        title.textProperty().bind(dialog.titleProperty());
        title.getStyleClass().add("app-titlebar-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().addAll(title, spacer,
                WindowChrome.button(new SVGPath(), "M0,0 L10,10 M10,0 L0,10", e -> dialog.close(), true));

        // drag the dialog by its bar (the stage exists once the dialog is shown)
        double[] offset = new double[2];
        bar.setOnMousePressed(e -> {
            Window w = bar.getScene() == null ? null : bar.getScene().getWindow();
            if (w == null) return;
            offset[0] = e.getScreenX() - w.getX();
            offset[1] = e.getScreenY() - w.getY();
        });
        bar.setOnMouseDragged(e -> {
            Window w = bar.getScene() == null ? null : bar.getScene().getWindow();
            if (w == null || e.getButton() != MouseButton.PRIMARY) return;
            w.setX(e.getScreenX() - offset[0]);
            w.setY(e.getScreenY() - offset[1]);
        });
        pane.setHeader(bar);
    }
}
