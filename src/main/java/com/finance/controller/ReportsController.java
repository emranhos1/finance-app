package com.finance.controller;

import com.finance.context.SessionContext;
import com.finance.entity.Account;
import com.finance.entity.AccountLog;
import com.finance.entity.Category;
import com.finance.service.AccountService;
import com.finance.service.CategoryService;
import com.finance.service.TransactionService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ReportsController {
    private final SessionContext sessionContext;
    private final TransactionService transactionService;
    private final AccountService accountService;
    private final CategoryService categoryService;

    @FXML private Label totalIncomeLabel;
    @FXML private Label totalExpenseLabel;
    @FXML private Label netIncomeLabel;

    @FXML private LineChart<String, Number> trendChart;

    @FXML private TableView<CategoryRow> expenseCategoryTable;
    @FXML private TableColumn<CategoryRow, String> expCategoryNameColumn;
    @FXML private TableColumn<CategoryRow, BigDecimal> expCategoryAmountColumn;

    @FXML private TableView<CategoryRow> incomeCategoryTable;
    @FXML private TableColumn<CategoryRow, String> incCategoryNameColumn;
    @FXML private TableColumn<CategoryRow, BigDecimal> incCategoryAmountColumn;

    @FXML private TableView<AccountRow> accountTable;
    @FXML private TableColumn<AccountRow, String> accountNameColumn;
    @FXML private TableColumn<AccountRow, String> accountTypeColumn;
    @FXML private TableColumn<AccountRow, BigDecimal> accountBalanceColumn;

    @FXML private TableView<AccountLog> accountLogTable;
    @FXML private TableColumn<AccountLog, String> logDateColumn;
    @FXML private TableColumn<AccountLog, String> logChangeTypeColumn;
    @FXML private TableColumn<AccountLog, String> logRefTypeColumn;
    @FXML private TableColumn<AccountLog, BigDecimal> logAmountColumn;
    @FXML private TableColumn<AccountLog, BigDecimal> logBalanceBeforeColumn;
    @FXML private TableColumn<AccountLog, BigDecimal> logBalanceAfterColumn;
    @FXML private TableColumn<AccountLog, String> logNoteColumn;
    @FXML private Button logPrevPageBtn;
    @FXML private Button logNextPageBtn;
    @FXML private Label logPageLabel;
    @FXML private ComboBox<Integer> logPageSizeComboBox;

    @FXML private MenuButton accountFilterMenuButton;
    @FXML private DateRangeField filterDateRangeField;
    @FXML private ComboBox<String> filterRefTypeComboBox;
    @FXML private ComboBox<String> filterCategoryComboBox;
    private static final String ALL_REFERENCES = "All references";
    private static final String ALL_CATEGORIES = "All categories";
    private final Map<String, Long> categoryIdsByLabel = new LinkedHashMap<>();
    private static final LocalDate ALL_TIME_START = LocalDate.of(1970, 1, 1);
    private static final LocalDate ALL_TIME_END = LocalDate.of(9999, 12, 31);

    private List<Account> allAccounts = new ArrayList<>();
    private final Map<Long, CheckMenuItem> accountCheckItems = new LinkedHashMap<>();
    private CheckMenuItem allAccountsItem;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final List<Integer> PAGE_SIZE_OPTIONS = Arrays.asList(5, 10, 20, 50, 100);
    private int logCurrentPage = 0;
    private int logTotalPages = 1;
    private Map<Long, LocalDate> logTxDates = new java.util.HashMap<>();

    @FXML
    public void initialize() {
        expCategoryNameColumn.setCellValueFactory(new PropertyValueFactory<>("categoryName"));
        expCategoryAmountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        incCategoryNameColumn.setCellValueFactory(new PropertyValueFactory<>("categoryName"));
        incCategoryAmountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        accountNameColumn.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        accountTypeColumn.setCellValueFactory(new PropertyValueFactory<>("accountType"));
        accountBalanceColumn.setCellValueFactory(new PropertyValueFactory<>("balance"));

        logDateColumn.setCellValueFactory(cd -> new SimpleStringProperty(logDate(cd.getValue(), logTxDates).format(DATE_FORMAT)));
        logAmountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        logBalanceBeforeColumn.setCellValueFactory(new PropertyValueFactory<>("balanceBefore"));
        logBalanceAfterColumn.setCellValueFactory(new PropertyValueFactory<>("balanceAfter"));
        logNoteColumn.setCellValueFactory(new PropertyValueFactory<>("note"));
        logChangeTypeColumn.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getChangeType().name()));
        logRefTypeColumn.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getReferenceType().name()));

        logPageSizeComboBox.setItems(FXCollections.observableArrayList(PAGE_SIZE_OPTIONS));
        logPageSizeComboBox.setValue(20);
        logPageSizeComboBox.setOnAction(e -> loadAccountLogPage(0));

        setupFilters();
        loadAccountDropdown();
        refreshAll();
    }

    private void refreshAll() {
        loadReport();
        loadTrendChart();
        loadAccountLogPage(0);
    }


    private void setupFilters() {
        LocalDate today = LocalDate.now();
        filterDateRangeField.setRange(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()));

        loadCategoryOptions();
        filterCategoryComboBox.setValue(ALL_CATEGORIES);
        // reload so new categories show up
        filterCategoryComboBox.setOnShowing(e -> loadCategoryOptions());

        List<String> refTypes = new ArrayList<>();
        refTypes.add(ALL_REFERENCES);
        for (AccountLog.ReferenceType t : AccountLog.ReferenceType.values()) refTypes.add(t.name());
        filterRefTypeComboBox.setItems(FXCollections.observableArrayList(refTypes));
        filterRefTypeComboBox.setValue(ALL_REFERENCES);

        filterDateRangeField.setOnChange(this::refreshAll);
        filterRefTypeComboBox.setOnAction(e -> refreshAll());
        filterCategoryComboBox.setOnAction(e -> refreshAll());
    }

    private void loadCategoryOptions() {
        String selected = filterCategoryComboBox.getValue();
        categoryIdsByLabel.clear();
        categoryIdsByLabel.put(ALL_CATEGORIES, null);
        for (Category c : categoryService.getCategoriesByUserId(sessionContext.getCurrentUserId())) {
            String label = c.getName();
            if (categoryIdsByLabel.containsKey(label)) label = label + " (" + c.getType() + ")";
            categoryIdsByLabel.put(label, c.getId());
        }
        filterCategoryComboBox.getItems().setAll(categoryIdsByLabel.keySet());
        filterCategoryComboBox.setValue(selected != null && categoryIdsByLabel.containsKey(selected) ? selected : ALL_CATEGORIES);
    }

    @FXML
    public void handleClearFilters() {
        filterDateRangeField.clear();
        filterRefTypeComboBox.setValue(ALL_REFERENCES);
        filterCategoryComboBox.setValue(ALL_CATEGORIES);
        allAccountsItem.setSelected(true);
        for (CheckMenuItem item : accountCheckItems.values()) item.setSelected(true);
        updateAccountButtonText();
        refreshAll();
    }

    private void loadAccountDropdown() {
        allAccounts = accountService.getAccountsByUserId(sessionContext.getCurrentUserId());
        accountFilterMenuButton.getItems().clear();
        accountCheckItems.clear();

        allAccountsItem = new CheckMenuItem("All Accounts");
        allAccountsItem.setSelected(true);
        allAccountsItem.setOnAction(e -> {
            boolean select = allAccountsItem.isSelected();
            for (CheckMenuItem item : accountCheckItems.values()) item.setSelected(select);
            onAccountSelectionChanged();
        });
        accountFilterMenuButton.getItems().add(allAccountsItem);
        accountFilterMenuButton.getItems().add(new SeparatorMenuItem());

        for (Account acc : allAccounts) {
            CheckMenuItem item = new CheckMenuItem(acc.getName());
            item.setSelected(true);
            item.setOnAction(e -> {
                if (!item.isSelected()) allAccountsItem.setSelected(false);
                else if (accountCheckItems.values().stream().allMatch(CheckMenuItem::isSelected)) allAccountsItem.setSelected(true);
                onAccountSelectionChanged();
            });
            accountCheckItems.put(acc.getId(), item);
            accountFilterMenuButton.getItems().add(item);
        }
        updateAccountButtonText();
    }

    private void onAccountSelectionChanged() {
        updateAccountButtonText();
        refreshAll();
    }

    private void updateAccountButtonText() {
        List<Account> selected = getSelectedAccounts();
        if (selected.isEmpty()) {
            accountFilterMenuButton.setText("Select accounts");
        } else if (selected.size() == allAccounts.size()) {
            accountFilterMenuButton.setText("All Accounts");
        } else if (selected.size() == 1) {
            accountFilterMenuButton.setText(selected.get(0).getName());
        } else {
            accountFilterMenuButton.setText(selected.size() + " accounts selected");
        }
    }

    private List<Account> getSelectedAccounts() {
        List<Account> selected = new ArrayList<>();
        for (Account acc : allAccounts) {
            CheckMenuItem item = accountCheckItems.get(acc.getId());
            if (item != null && item.isSelected()) selected.add(acc);
        }
        return selected;
    }

    private List<Long> getSelectedAccountIds() {
        List<Long> ids = new ArrayList<>();
        for (Account acc : getSelectedAccounts()) ids.add(acc.getId());
        return ids;
    }

    private AccountLog.ReferenceType getSelectedReference() {
        String ref = filterRefTypeComboBox.getValue();
        return ref == null || ALL_REFERENCES.equals(ref) ? null : AccountLog.ReferenceType.valueOf(ref);
    }

    private Long getSelectedCategoryId() {
        String cat = filterCategoryComboBox.getValue();
        return cat == null ? null : categoryIdsByLabel.get(cat);
    }

    private boolean includeIncome() {
        AccountLog.ReferenceType ref = getSelectedReference();
        return ref == null || ref == AccountLog.ReferenceType.INCOME;
    }

    private boolean includeExpense() {
        AccountLog.ReferenceType ref = getSelectedReference();
        return ref == null || ref == AccountLog.ReferenceType.EXPENSE;
    }


    /** Transfers and loans are never income or expense. Balances are current, only the account filter applies. */
    private void loadReport() {
        Long userId = sessionContext.getCurrentUserId();
        List<Long> accountIds = getSelectedAccountIds();
        Long categoryId = getSelectedCategoryId();
        LocalDate start = filterDateRangeField.getStart() != null ? filterDateRangeField.getStart() : ALL_TIME_START;
        LocalDate end = filterDateRangeField.getEnd() != null ? filterDateRangeField.getEnd() : ALL_TIME_END;

        BigDecimal totalIncome = includeIncome()
                ? transactionService.getIncomeFiltered(userId, accountIds, categoryId, start, end) : BigDecimal.ZERO;
        BigDecimal totalExpense = includeExpense()
                ? transactionService.getExpenseFiltered(userId, accountIds, categoryId, start, end) : BigDecimal.ZERO;
        totalIncomeLabel.setText(String.format("৳ %.2f", totalIncome));
        totalExpenseLabel.setText(String.format("৳ %.2f", totalExpense));
        netIncomeLabel.setText(String.format("৳ %.2f", totalIncome.subtract(totalExpense)));

        List<CategoryRow> expenseRows = new ArrayList<>();
        if (includeExpense()) {
            for (Category cat : categoryService.getExpenseCategories(userId)) {
                if (categoryId != null && !categoryId.equals(cat.getId())) continue;
                BigDecimal amount = transactionService.getExpenseFiltered(userId, accountIds, cat.getId(), start, end);
                if (amount.compareTo(BigDecimal.ZERO) != 0) expenseRows.add(new CategoryRow(cat.getName(), amount));
            }
        }
        expenseCategoryTable.setItems(FXCollections.observableArrayList(expenseRows));

        List<CategoryRow> incomeRows = new ArrayList<>();
        if (includeIncome()) {
            for (Category cat : categoryService.getIncomeCategories(userId)) {
                if (categoryId != null && !categoryId.equals(cat.getId())) continue;
                BigDecimal amount = transactionService.getIncomeFiltered(userId, accountIds, cat.getId(), start, end);
                if (amount.compareTo(BigDecimal.ZERO) != 0) incomeRows.add(new CategoryRow(cat.getName(), amount));
            }
        }
        incomeCategoryTable.setItems(FXCollections.observableArrayList(incomeRows));

        List<AccountRow> accountRows = new ArrayList<>();
        for (Account acc : getSelectedAccounts()) {
            if (acc.getBalance().compareTo(BigDecimal.ZERO) != 0)
                accountRows.add(new AccountRow(acc.getName(), acc.getAccountType().getName(), acc.getBalance()));
        }
        accountTable.setItems(FXCollections.observableArrayList(accountRows));
    }

    private void loadTrendChart() {
        Long userId = sessionContext.getCurrentUserId();
        List<Long> accountIds = getSelectedAccountIds();
        Long categoryId = getSelectedCategoryId();
        boolean withIncome = includeIncome();
        boolean withExpense = includeExpense();

        XYChart.Series<String, Number> incomeSeries = new XYChart.Series<>();
        incomeSeries.setName("Income");
        XYChart.Series<String, Number> expenseSeries = new XYChart.Series<>();
        expenseSeries.setName("Expense");

        LocalDate now = LocalDate.now();
        DateTimeFormatter monthFormat = DateTimeFormatter.ofPattern("MMM yyyy");
        for (int i = 11; i >= 0; i--) {
            LocalDate monthDate = now.minusMonths(i);
            LocalDate monthStart = monthDate.withDayOfMonth(1);
            LocalDate monthEnd = monthDate.withDayOfMonth(monthDate.lengthOfMonth());
            String label = monthDate.format(monthFormat);

            BigDecimal income = withIncome
                    ? transactionService.getIncomeFiltered(userId, accountIds, categoryId, monthStart, monthEnd) : BigDecimal.ZERO;
            BigDecimal expense = withExpense
                    ? transactionService.getExpenseFiltered(userId, accountIds, categoryId, monthStart, monthEnd) : BigDecimal.ZERO;
            incomeSeries.getData().add(new XYChart.Data<>(label, income));
            expenseSeries.getData().add(new XYChart.Data<>(label, expense));
        }
        trendChart.getData().setAll(incomeSeries, expenseSeries);
        if (incomeSeries.getNode() != null) incomeSeries.getNode().setStyle("-fx-stroke: #E74C3C; -fx-stroke-width: 2;");
        if (expenseSeries.getNode() != null) expenseSeries.getNode().setStyle("-fx-stroke: #F39C12; -fx-stroke-width: 2; -fx-stroke-dash-array: 6 4;");
        for (XYChart.Data<String, Number> data : incomeSeries.getData()) {
            if (data.getNode() != null) data.getNode().setStyle("-fx-background-color: #E74C3C, white;");
        }
        for (XYChart.Data<String, Number> data : expenseSeries.getData()) {
            if (data.getNode() != null) data.getNode().setStyle("-fx-background-color: #F39C12, white;");
        }
    }

    private Page<AccountLog> searchLogs(List<Long> accountIds, Pageable pageable) {
        return accountService.searchAccountLogs(accountIds,
                filterDateRangeField.getStart(), filterDateRangeField.getEnd(),
                getSelectedReference(), getSelectedCategoryId(), pageable);
    }

    private void loadAccountLogPage(int page) {
        List<Long> accountIds = getSelectedAccountIds();
        if (accountIds.isEmpty()) {
            accountLogTable.setItems(FXCollections.observableArrayList());
            logCurrentPage = 0;
            logTotalPages = 1;
            logPageLabel.setText("Page 0 of 0 (0 total)");
            logPrevPageBtn.setDisable(true);
            logNextPageBtn.setDisable(true);
            return;
        }

        int pageSize = logPageSizeComboBox.getValue() != null ? logPageSizeComboBox.getValue() : 20;
        Page<AccountLog> result = searchLogs(accountIds, PageRequest.of(page, pageSize));

        logCurrentPage = result.getNumber();
        logTotalPages = Math.max(result.getTotalPages(), 1);
        logTxDates = txDatesFor(result.getContent());
        accountLogTable.setItems(FXCollections.observableArrayList(result.getContent()));
        logPageLabel.setText("Page " + (logCurrentPage + 1) + " of " + logTotalPages + " (" + result.getTotalElements() + " total)");
        logPrevPageBtn.setDisable(logCurrentPage <= 0);
        logNextPageBtn.setDisable(logCurrentPage >= logTotalPages - 1);
    }

    private Map<Long, LocalDate> txDatesFor(List<AccountLog> logs) {
        List<Long> ids = new ArrayList<>();
        for (AccountLog l : logs) if (l.getReferenceId() != null) ids.add(l.getReferenceId());
        return transactionService.getDatesByIds(ids);
    }

    /** Transaction date, or the log time if the row has no transaction. */
    private static LocalDate logDate(AccountLog l, Map<Long, LocalDate> txDates) {
        LocalDate txDate = l.getReferenceId() != null ? txDates.get(l.getReferenceId()) : null;
        return txDate != null ? txDate : l.getCreatedAt().toLocalDate();
    }

    @FXML
    public void handleLogPrevPage() {
        if (logCurrentPage > 0) loadAccountLogPage(logCurrentPage - 1);
    }

    @FXML
    public void handleLogNextPage() {
        if (logCurrentPage < logTotalPages - 1) loadAccountLogPage(logCurrentPage + 1);
    }

    @FXML
    public void handleExportCSV() {
        List<Account> selectedAccounts = getSelectedAccounts();
        if (selectedAccounts.isEmpty()) { showAlert("Error", "Select at least one account to export"); return; }

        Map<String, List<AccountLog>> exportMap = new LinkedHashMap<>();
        for (Account acc : selectedAccounts) {
            List<AccountLog> logs = searchLogs(List.of(acc.getId()), Pageable.unpaged()).getContent();
            if (!logs.isEmpty()) exportMap.put(acc.getName(), logs);
        }
        if (exportMap.isEmpty()) { showAlert("Error", "No log data found"); return; }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Account Log");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        chooser.setInitialFileName(selectedAccounts.size() == 1 ? selectedAccounts.get(0).getName() + "_log.csv" : "accounts_log.csv");
        File file = chooser.showSaveDialog(null);
        if (file == null) return;

        try (OutputStreamWriter fw = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            fw.write("﻿"); // UTF-8 BOM so Excel detects the encoding and renders Bengali correctly
            for (Map.Entry<String, List<AccountLog>> entry : exportMap.entrySet()) {
                fw.write("Account: " + entry.getKey() + "\n");
                fw.write("Date,Change,Reference,Amount,Balance Before,Balance After,Note\n");
                Map<Long, LocalDate> txDates = txDatesFor(entry.getValue());
                for (AccountLog l : entry.getValue()) {
                    fw.write(logDate(l, txDates).format(DATE_FORMAT) + ","
                            + l.getChangeType().name() + ","
                            + l.getReferenceType().name() + ","
                            + l.getAmount() + ","
                            + l.getBalanceBefore() + ","
                            + l.getBalanceAfter() + ","
                            + (l.getNote() != null ? l.getNote().replace(",", ";") : "") + "\n");
                }
                fw.write("\n");
            }
            showAlert("Success", "Exported to " + file.getName());
        } catch (IOException e) {
            showAlert("Error", "Failed to export: " + e.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(message); a.showAndWait();
    }

    public static class CategoryRow {
        private final String categoryName;
        private final BigDecimal amount;
        public CategoryRow(String categoryName, BigDecimal amount) { this.categoryName = categoryName; this.amount = amount; }
        public String getCategoryName() { return categoryName; }
        public BigDecimal getAmount() { return amount; }
    }

    public static class AccountRow {
        private final String accountName;
        private final String accountType;
        private final BigDecimal balance;
        public AccountRow(String accountName, String accountType, BigDecimal balance) {
            this.accountName = accountName; this.accountType = accountType; this.balance = balance;
        }
        public String getAccountName() { return accountName; }
        public String getAccountType() { return accountType; }
        public BigDecimal getBalance() { return balance; }
    }
}
