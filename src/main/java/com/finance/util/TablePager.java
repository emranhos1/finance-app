package com.finance.util;

import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pages a table whose rows are all held in memory (small lists): "◀ Prev", "Page X of Y (N total)", "Next ▶" and a
 * "Rows per page" box (5, 10, 20, 50, 100; starts at 5). Give it the table and the four controls from the FXML;
 * any control may be null. {@link #getAll()} is the full list, for totals and counts (the table only holds one page).
 */
public final class TablePager<T> {
    public static final List<Integer> PAGE_SIZE_OPTIONS = Arrays.asList(5, 10, 20, 50, 100);
    public static final int DEFAULT_PAGE_SIZE = 5;

    private final TableView<T> table;
    private final ComboBox<Integer> sizeBox;
    private final Label label;
    private final Button prev;
    private final Button next;
    private List<T> all = new ArrayList<>();
    private int page;

    public TablePager(TableView<T> table, ComboBox<Integer> sizeBox, Label label, Button prev, Button next) {
        this.table = table;
        this.sizeBox = sizeBox;
        this.label = label;
        this.prev = prev;
        this.next = next;
        if (sizeBox != null) {
            sizeBox.setItems(FXCollections.observableArrayList(PAGE_SIZE_OPTIONS));
            sizeBox.setValue(DEFAULT_PAGE_SIZE);
            sizeBox.setOnAction(e -> { page = 0; render(); });
        }
        if (prev != null) prev.setOnAction(e -> { page--; render(); });
        if (next != null) next.setOnAction(e -> { page++; render(); });
        render();
    }

    /** Replaces the rows; the page stays where it was (clamped when the list got shorter). */
    public void setAll(List<T> items) {
        all = items == null ? new ArrayList<>() : items;
        render();
    }

    /** Back to the first page (a different set of rows, e.g. another person's history). */
    public void reset() {
        page = 0;
        render();
    }

    public List<T> getAll() {
        return all;
    }

    public void render() {
        int size = sizeBox != null && sizeBox.getValue() != null ? sizeBox.getValue() : DEFAULT_PAGE_SIZE;
        int pages = Math.max(1, (all.size() + size - 1) / size);
        page = Math.min(Math.max(page, 0), pages - 1);
        table.setItems(FXCollections.observableArrayList(all.subList(page * size, Math.min(all.size(), (page + 1) * size))));
        if (label != null) label.setText("Page " + (page + 1) + " of " + pages + " (" + all.size() + " total)");
        if (prev != null) prev.setDisable(page <= 0);
        if (next != null) next.setDisable(page >= pages - 1);
    }
}
