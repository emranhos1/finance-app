package com.finance.controller;

import com.finance.context.SessionContext;
import com.finance.entity.Category;
import com.finance.entity.Transaction;
import com.finance.service.CategoryService;
import com.finance.service.TransactionService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.StringConverter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Read-only calculator over transactions in a date range and category. */
@Controller
@RequiredArgsConstructor
public class CalculatorController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SessionContext sessionContext;
    private final TransactionService transactionService;
    private final CategoryService categoryService;

    @FXML
    private DatePicker startDatePicker;

    @FXML
    private DatePicker endDatePicker;

    @FXML
    private ComboBox<Category> categoryComboBox;

    @FXML
    private Button addBtn;

    @FXML
    private Button subtractBtn;

    @FXML
    private Button removeSelectedBtn;

    @FXML
    private Button clearAllBtn;

    @FXML
    private Label errorLabel;

    @FXML
    private TableView<CalcEntry> entryTableView;

    @FXML
    private TableColumn<CalcEntry, String> dateColumn;

    @FXML
    private TableColumn<CalcEntry, String> categoryColumn;

    @FXML
    private TableColumn<CalcEntry, String> noteColumn;

    @FXML
    private TableColumn<CalcEntry, String> typeColumn;

    @FXML
    private TableColumn<CalcEntry, String> amountColumn;

    @FXML
    private Label totalLabel;

    private final ObservableList<CalcEntry> entries = FXCollections.observableArrayList();
    private double runningTotal = 0.0;

    private List<Transaction> transactionsInRange = new ArrayList<>();

    @FXML
    public void initialize() {
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("dateText"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        noteColumn.setCellValueFactory(new PropertyValueFactory<>("note"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("typeText"));
        amountColumn.setCellValueFactory(new PropertyValueFactory<>("amountText"));
        entryTableView.setItems(entries);

        categoryComboBox.setConverter(new StringConverter<Category>() {
            public String toString(Category c) { return c == null ? "" : c.getName() + " (" + c.getType().name() + ")"; }
            public Category fromString(String s) { return null; }
        });

        startDatePicker.setOnAction(e -> loadCategoriesForDateRange());
        endDatePicker.setOnAction(e -> loadCategoriesForDateRange());

        addBtn.setOnAction(e -> handleAddCategoryEntries(true));
        subtractBtn.setOnAction(e -> handleAddCategoryEntries(false));
        removeSelectedBtn.setOnAction(e -> handleRemoveSelected());
        clearAllBtn.setOnAction(e -> handleClearAll());

        updateTotalLabel();
    }

    private void loadCategoriesForDateRange() {
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        categoryComboBox.setValue(null);

        if (start == null || end == null) {
            transactionsInRange = new ArrayList<>();
            categoryComboBox.setItems(FXCollections.observableArrayList());
            categoryComboBox.setPromptText("— Select date range first —");
            return;
        }
        if (start.isAfter(end)) {
            showError("'From' date must be before 'To' date.");
            transactionsInRange = new ArrayList<>();
            categoryComboBox.setItems(FXCollections.observableArrayList());
            return;
        }
        hideError();

        Long userId = sessionContext.getCurrentUserId();
        transactionsInRange = transactionService.getTransactionsByDateRange(userId, start, end);

        Set<Long> categoryIdsInRange = new LinkedHashSet<>();
        for (Transaction tx : transactionsInRange) {
            if (tx.getCategoryId() != null) {
                categoryIdsInRange.add(tx.getCategoryId());
            }
        }

        List<Category> allUserCategories = categoryService.getCategoriesByUserId(userId);
        List<Category> categoriesInRange = new ArrayList<>();
        for (Category c : allUserCategories) {
            if (categoryIdsInRange.contains(c.getId())) {
                categoriesInRange.add(c);
            }
        }

        categoryComboBox.setItems(FXCollections.observableArrayList(categoriesInRange));
        categoryComboBox.setPromptText(categoriesInRange.isEmpty()
                ? "No categories in this range" : "— Select Category —");
    }

    private void handleAddCategoryEntries(boolean isAddition) {
        hideError();
        Category selected = categoryComboBox.getValue();
        if (selected == null) {
            showError("Select a category first.");
            return;
        }

        List<Transaction> matches = new ArrayList<>();
        for (Transaction tx : transactionsInRange) {
            if (selected.getId().equals(tx.getCategoryId())) {
                matches.add(tx);
            }
        }
        if (matches.isEmpty()) {
            showError("No transactions found for this category in the selected range.");
            return;
        }

        matches.sort(Comparator.comparing(Transaction::getDate));

        for (Transaction tx : matches) {
            double amount = tx.getAmount().doubleValue();
            double signedValue = isAddition ? amount : -amount;

            String note = (tx.getNote() == null) ? "" : tx.getNote().trim();

            runningTotal += signedValue;

            entries.add(new CalcEntry(
                    tx.getDate().format(DATE_FMT),
                    selected.getName(),
                    note,
                    isAddition ? "+" : "-",
                    amount,
                    signedValue));
        }

        updateTotalLabel();
    }

    private void handleRemoveSelected() {
        int index = entryTableView.getSelectionModel().getSelectedIndex();
        if (index < 0) {
            showError("Select a row from the table to remove it.");
            return;
        }
        hideError();
        CalcEntry removed = entries.remove(index);
        runningTotal -= removed.getSignedValue();
        updateTotalLabel();
    }

    private void handleClearAll() {
        hideError();
        entries.clear();
        runningTotal = 0.0;
        updateTotalLabel();
    }

    private void updateTotalLabel() {
        totalLabel.setText(String.format(Locale.US, "৳ %.2f", runningTotal));
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    @Getter
    public static class CalcEntry {
        private final String dateText;
        private final String category;
        private final String note;
        private final String typeText;
        private final double amount;
        private final double signedValue;

        public CalcEntry(String dateText, String category, String note, String typeText, double amount, double signedValue) {
            this.dateText = dateText;
            this.category = category;
            this.note = note;
            this.typeText = typeText;
            this.amount = amount;
            this.signedValue = signedValue;
        }

        public String getAmountText() {
            return String.format(Locale.US, "৳ %.2f", amount);
        }
    }
}