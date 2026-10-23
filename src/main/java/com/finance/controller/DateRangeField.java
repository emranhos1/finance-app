package com.finance.controller;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.skin.DatePickerSkin;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Date range picker on one DatePicker calendar. One click then close means a single day. */
public class DateRangeField extends Button {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String PROMPT = "Select dates  ▾";

    private final DatePicker calendarSource = new DatePicker();
    private final Popup popup = new Popup();
    private final Label hint = new Label();
    private LocalDate start;
    private LocalDate end;
    private Runnable onChange = () -> { };

    public DateRangeField() {
        setText(PROMPT);
        getStyleClass().add("date-range-field");
        setMinWidth(210);

        Node calendar = new DatePickerSkin(calendarSource).getPopupContent();
        hint.setStyle("-fx-font-size: 11; -fx-text-fill: #555555; -fx-padding: 4 6 4 6;");
        VBox box = new VBox(hint, calendar);
        box.setStyle("-fx-background-color: white; -fx-border-color: #B7D9CE; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 8, 0, 0, 2);");
        popup.getContent().add(box);
        popup.setAutoHide(true);
        popup.setOnHidden(e -> {
            if (start != null && end == null) { end = start; refresh(); onChange.run(); }
        });

        calendarSource.valueProperty().addListener((obs, oldV, picked) -> {
            if (picked == null) return;
            if (start == null || end != null || picked.isBefore(start)) {
                start = picked;
                end = null;
                refresh();
            } else {
                end = picked;
                refresh();
                popup.hide();
                onChange.run();
            }
            // clear so the same day can be clicked again
            Platform.runLater(() -> calendarSource.setValue(null));
        });

        setOnAction(e -> {
            if (popup.isShowing()) { popup.hide(); return; }
            refresh();
            Bounds b = localToScreen(getBoundsInLocal());
            popup.show(this, b.getMinX(), b.getMaxY());
        });
        refresh();
    }

    public LocalDate getStart() { return start; }

    public LocalDate getEnd() { return end != null ? end : start; }

    public void setOnChange(Runnable onChange) { this.onChange = onChange != null ? onChange : () -> { }; }

    public void setRange(LocalDate start, LocalDate end) {
        this.start = start;
        this.end = end;
        refresh();
    }

    public void clear() {
        start = null;
        end = null;
        refresh();
    }

    private void refresh() {
        if (start == null) setText(PROMPT);
        else if (end == null || end.equals(start)) setText(start.format(FMT));
        else setText(start.format(FMT) + " - " + end.format(FMT));

        hint.setText(start == null || end != null ? "Click the first day" : "Now click the last day");

        calendarSource.setDayCellFactory(dp -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || start == null) { setStyle(null); return; }
                LocalDate last = end != null ? end : start;
                boolean inRange = !item.isBefore(start) && !item.isAfter(last);
                setStyle(inRange ? "-fx-background-color: #CFE9DF; -fx-text-fill: #1B5446; -fx-font-weight: bold;" : null);
            }
        });
    }
}
