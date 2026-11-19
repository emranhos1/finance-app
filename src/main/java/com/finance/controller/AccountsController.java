package com.finance.controller;

import com.finance.context.SessionContext;
import com.finance.entity.Account;
import com.finance.entity.AccountType;
import com.finance.entity.Category;
import com.finance.service.CategoryService;
import com.finance.service.AccountService;
import com.finance.service.AccountTypeService;
import com.finance.service.TransactionService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class AccountsController {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private final SessionContext sessionContext;
    private final AccountService accountService;
    private final AccountTypeService accountTypeService;
    private final TransactionService transactionService;
    private final CategoryService categoryService;

    // "Is Matured" controls: optional, a theme layout may leave them out
    @FXML private CheckBox isMaturedCheck;
    @FXML private VBox maturedBox;
    @FXML private TextField interestField;
    @FXML private ComboBox<Account> maturedTargetCombo;
    @FXML private TableColumn<Account, String> statusColumn;

    @FXML private TableView<Account> accountsTable;
    @FXML private TableColumn<Account, Long> idColumn;
    @FXML private TableColumn<Account, String> nameColumn;
    @FXML private TableColumn<Account, String> typeColumn;
    @FXML private TableColumn<Account, BigDecimal> balanceColumn;
    @FXML private TableColumn<Account, String> maturityColumn;
    @FXML private TableColumn<Account, String> installmentColumn;
    @FXML private TableView<Account> closedAccountsTable;
    @FXML private TableColumn<Account, Long> closedIdColumn;
    @FXML private TableColumn<Account, String> closedNameColumn;
    @FXML private TableColumn<Account, String> closedTypeColumn;
    @FXML private TableColumn<Account, BigDecimal> closedBalanceColumn;
    @FXML private TableColumn<Account, String> closedMaturityColumn;
    @FXML private TableColumn<Account, String> closedInstallmentColumn;
    @FXML private TableColumn<Account, String> closedStatusColumn;
    @FXML private TextField nameField;
    @FXML private ComboBox<AccountType> typeComboBox;
    @FXML private TextField balanceField;
    @FXML private javafx.scene.layout.Pane maturityInstallmentBox;
    @FXML private DatePicker maturityDatePicker;
    @FXML private TextField installmentField;
    @FXML private TextField lowBalanceThresholdField;
    @FXML private Button accAddBtn;
    @FXML private HBox accEditButtons;

    @FXML private TableView<AccountType> typesTable;
    @FXML private TableColumn<AccountType, Long> typeIdColumn;
    @FXML private TableColumn<AccountType, String> typeNameColumn;
    @FXML private TextField typeNameField;
    @FXML private CheckBox typeHasMaturityCheck;
    @FXML private TableColumn<AccountType, String> typeMaturityColumn;
    @FXML private Button typeAddBtn;

    // optional display-only labels: a layout may leave any of them out (Bento tiles, Gold totals, Midnight subtitle)
    @FXML private Label accountsCountLabel;
    @FXML private Label accountsMaturityCountLabel;
    @FXML private Label accountsTotalLabel;
    @FXML private Label typesCountLabel;
    @FXML private Label typesMaturityCountLabel;
    @FXML private Label accountsSubtitleLabel;
    @FXML private TabPane accountsTabPane;

    /** Names of the account types that have a maturity date (DPS, FDR ...): their chip is amber. */
    private final java.util.Set<String> maturityTypeNames = new java.util.HashSet<>();
    @FXML private HBox typeEditButtons;

    @FXML
    public void initialize() {
        setupAccountsTable();
        setupPagers();
        setupTypesTable();
        setupSummaries();
        loadAccountTypes();
        loadAccounts();
        resetAccountForm();
        resetTypeForm();
    }

    /** Same columns for the open and the closed accounts table. */
    private void bindAccountColumns(TableColumn<Account, Long> id, TableColumn<Account, String> name,
                                    TableColumn<Account, String> type, TableColumn<Account, BigDecimal> balance,
                                    TableColumn<Account, String> maturity, TableColumn<Account, String> installment,
                                    TableColumn<Account, String> status) {
        id.setCellValueFactory(new PropertyValueFactory<>("id"));
        name.setCellValueFactory(new PropertyValueFactory<>("name"));
        type.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getAccountType().getName()));
        com.finance.util.UiFormat.chipColumn(type, v -> v, v -> maturityTypeNames.contains(v) ? "chip-open" : null);
        balance.setCellValueFactory(new PropertyValueFactory<>("balance"));
        com.finance.util.UiFormat.amountColumn(balance, (r, v) -> v != null && v.signum() < 0 ? com.finance.util.UiFormat.Tone.NEG : com.finance.util.UiFormat.Tone.NEUTRAL);
        maturity.setCellValueFactory(cd -> {
            LocalDate d = cd.getValue().getMaturityDate();
            return new SimpleStringProperty(d == null ? "—" : d.format(DATE_FMT));
        });
        installment.setCellValueFactory(cd -> {
            BigDecimal amt = cd.getValue().getInstallmentAmount();
            return new SimpleStringProperty(amt == null ? "—" : com.finance.util.UiFormat.number(amt));
        });
        if (status != null) {
            status.setCellValueFactory(cd ->
                    new SimpleStringProperty(Boolean.TRUE.equals(cd.getValue().getClosed()) ? "Closed" : "Open"));
        }
    }

    private void onAccountSelected(Account sel) {
        if (sel == null) return;
        nameField.setText(sel.getName());
        typeComboBox.setValue(sel.getAccountType());
        balanceField.setText(sel.getBalance().toPlainString());
        maturityDatePicker.setValue(sel.getMaturityDate());
        installmentField.setText(sel.getInstallmentAmount() != null ? sel.getInstallmentAmount().toPlainString() : "");
        lowBalanceThresholdField.setText(sel.getLowBalanceThreshold() != null ? sel.getLowBalanceThreshold().toPlainString() : "");
        accAddBtn.setVisible(false);
        accAddBtn.setManaged(false);
        accEditButtons.setVisible(true);
        accEditButtons.setManaged(true);
        setupMaturedControls(sel);
    }

    /** The selected account, from whichever of the two tables holds the selection. */
    private Account selectedAccount() {
        Account sel = accountsTable.getSelectionModel().getSelectedItem();
        if (sel == null && closedAccountsTable != null) sel = closedAccountsTable.getSelectionModel().getSelectedItem();
        return sel;
    }

    private void setupAccountsTable() {
        bindAccountColumns(idColumn, nameColumn, typeColumn, balanceColumn, maturityColumn, installmentColumn, statusColumn);
        if (closedAccountsTable != null) {
            bindAccountColumns(closedIdColumn, closedNameColumn, closedTypeColumn, closedBalanceColumn,
                    closedMaturityColumn, closedInstallmentColumn, closedStatusColumn);
            closedAccountsTable.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
                if (sel != null) {
                    accountsTable.getSelectionModel().clearSelection();
                    onAccountSelected(sel);
                }
            });
        }
        if (maturedTargetCombo != null) {
            maturedTargetCombo.setConverter(new StringConverter<Account>() {
                public String toString(Account a) { return a == null ? "" : a.getName(); }
                public Account fromString(String s) { return null; }
            });
        }
        if (isMaturedCheck != null) {
            isMaturedCheck.selectedProperty().addListener((obs, o, on) -> showMaturedBox(on));
        }
        typeComboBox.setConverter(new StringConverter<AccountType>() {
            public String toString(AccountType t) { return t == null ? "" : t.getName(); }
            public AccountType fromString(String s) { return null; }
        });
        typeComboBox.valueProperty().addListener((obs, o, type) -> {
            boolean matures = type != null && Boolean.TRUE.equals(type.getHasMaturity());
            maturityInstallmentBox.setVisible(matures);
            maturityInstallmentBox.setManaged(matures);
            if (!matures) { maturityDatePicker.setValue(null); installmentField.clear(); }
        });

        accountsTable.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null) {
                if (closedAccountsTable != null) closedAccountsTable.getSelectionModel().clearSelection();
                onAccountSelected(sel);
            }
        });
    }

    private static void show(javafx.scene.Node node, boolean on) {
        if (node == null) return;
        node.setVisible(on);
        node.setManaged(on);
    }

    private void showMaturedBox(boolean on) {
        show(maturedBox, on);
        if (!on) {
            if (interestField != null) interestField.clear();
            if (maturedTargetCombo != null) maturedTargetCombo.setValue(null);
        }
    }

    /** Edit mode: the checkbox shows only for an open account whose type has maturity; a closed account is read-only. */
    private void setupMaturedControls(Account sel) {
        boolean closed = Boolean.TRUE.equals(sel.getClosed());
        boolean matures = Boolean.TRUE.equals(sel.getAccountType().getHasMaturity());
        if (isMaturedCheck != null) {
            isMaturedCheck.setSelected(false);
            show(isMaturedCheck, matures && !closed);
        }
        showMaturedBox(false);
        if (maturedTargetCombo != null) {
            List<Account> targets = new java.util.ArrayList<>();
            for (Account a : accountService.getOpenAccountsByUserId(sessionContext.getCurrentUserId())) {
                if (!a.getId().equals(sel.getId())) targets.add(a);
            }
            maturedTargetCombo.setItems(FXCollections.observableArrayList(targets));
        }
        // closed account: nothing can be changed, only Cancel (and Delete, which the service refuses while it has history)
        for (javafx.scene.Node n : accEditButtons.getChildren()) {
            if (n instanceof Button && "Update".equals(((Button) n).getText())) n.setDisable(closed);
        }
        nameField.setDisable(closed); typeComboBox.setDisable(closed); balanceField.setDisable(closed);
        maturityDatePicker.setDisable(closed); installmentField.setDisable(closed); lowBalanceThresholdField.setDisable(closed);
    }

    /** Counts and totals worked out from the rows the two tables already hold (no new query). */
    private void setupSummaries() {
        com.finance.util.UiFormat.watchItems(accountsTable, this::updateAccountSummary);
        com.finance.util.UiFormat.watchItems(typesTable, this::updateTypeSummary);
        if (accountsTabPane != null) {
            accountsTabPane.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> updateSubtitle());
        }
    }

    private void updateAccountSummary() {
        java.util.List<Account> rows = openAccounts; // all open accounts, not just the page on screen
        if (rows == null) rows = java.util.Collections.emptyList();
        BigDecimal total = BigDecimal.ZERO;
        int withMaturity = 0;
        for (Account a : rows) {
            if (a.getBalance() != null) total = total.add(a.getBalance());
            if (a.getMaturityDate() != null) withMaturity++;
        }
        com.finance.util.UiFormat.setText(accountsCountLabel, String.valueOf(rows.size()));
        com.finance.util.UiFormat.setText(accountsMaturityCountLabel, String.valueOf(withMaturity));
        com.finance.util.UiFormat.setText(accountsTotalLabel, com.finance.util.UiFormat.money(total));
        updateSubtitle();
    }

    private void updateTypeSummary() {
        java.util.List<AccountType> rows = allTypes; // all types, not just the page on screen
        if (rows == null) rows = java.util.Collections.emptyList();
        int withMaturity = 0;
        for (AccountType t : rows) if (Boolean.TRUE.equals(t.getHasMaturity())) withMaturity++;
        com.finance.util.UiFormat.setText(typesCountLabel, String.valueOf(rows.size()));
        com.finance.util.UiFormat.setText(typesMaturityCountLabel, String.valueOf(withMaturity));
        updateSubtitle();
    }

    /** Midnight shows the count under the page title: accounts and their total on the first tab, types on the second. */
    private void updateSubtitle() {
        if (accountsSubtitleLabel == null) return;
        boolean typesTab = accountsTabPane != null && accountsTabPane.getSelectionModel().getSelectedIndex() == 1;
        if (typesTab) {
            int n = allTypes.size();
            accountsSubtitleLabel.setText(n + (n == 1 ? " account type" : " account types"));
        } else {
            java.util.List<Account> rows = openAccounts;
            BigDecimal total = BigDecimal.ZERO;
            for (Account a : rows) if (a.getBalance() != null) total = total.add(a.getBalance());
            accountsSubtitleLabel.setText(rows.size() + (rows.size() == 1 ? " account" : " accounts")
                    + " · total " + com.finance.util.UiFormat.money(total));
        }
    }

    // ---- paging of the two account tables (the lists are small, so the pages are cut in memory) ----
    private static final List<Integer> PAGE_SIZE_OPTIONS = java.util.Arrays.asList(5, 10, 20, 50, 100);
    private static final int DEFAULT_PAGE_SIZE = 5;
    private List<Account> openAccounts = new java.util.ArrayList<>();
    private List<Account> closedAccounts = new java.util.ArrayList<>();
    private int openPage;
    private int closedPage;

    // the paging controls are optional, a layout may leave them out
    @FXML private Button openPrevPageBtn;
    @FXML private Button openNextPageBtn;
    @FXML private Label openPageLabel;
    @FXML private ComboBox<Integer> openPageSizeComboBox;
    @FXML private Button closedPrevPageBtn;
    @FXML private Button closedNextPageBtn;
    @FXML private Label closedPageLabel;
    @FXML private ComboBox<Integer> closedPageSizeComboBox;

    // the account types table pages the same way
    private List<AccountType> allTypes = new java.util.ArrayList<>();
    private int typesPage;
    @FXML private Button typesPrevPageBtn;
    @FXML private Button typesNextPageBtn;
    @FXML private Label typesPageLabel;
    @FXML private ComboBox<Integer> typesPageSizeComboBox;

    private void renderTypesPage() {
        typesPage = showPage(typesTable, allTypes, typesPage, typesPageSizeComboBox,
                typesPageLabel, typesPrevPageBtn, typesNextPageBtn);
    }

    @FXML public void handleTypesPrevPage() { typesPage--; renderTypesPage(); }
    @FXML public void handleTypesNextPage() { typesPage++; renderTypesPage(); }

    private void setupPagers() {
        if (typesPageSizeComboBox != null) {
            typesPageSizeComboBox.setItems(FXCollections.observableArrayList(PAGE_SIZE_OPTIONS));
            typesPageSizeComboBox.setValue(DEFAULT_PAGE_SIZE);
            typesPageSizeComboBox.setOnAction(e -> { typesPage = 0; renderTypesPage(); });
        }
        if (openPageSizeComboBox != null) {
            openPageSizeComboBox.setItems(FXCollections.observableArrayList(PAGE_SIZE_OPTIONS));
            openPageSizeComboBox.setValue(DEFAULT_PAGE_SIZE);
            openPageSizeComboBox.setOnAction(e -> { openPage = 0; renderOpenPage(); });
        }
        if (closedPageSizeComboBox != null) {
            closedPageSizeComboBox.setItems(FXCollections.observableArrayList(PAGE_SIZE_OPTIONS));
            closedPageSizeComboBox.setValue(DEFAULT_PAGE_SIZE);
            closedPageSizeComboBox.setOnAction(e -> { closedPage = 0; renderClosedPage(); });
        }
    }

    /** Fills the table with one page of the list and updates the pager controls; returns the (clamped) page shown. */
    private <T> int showPage(TableView<T> table, List<T> all, int page, ComboBox<Integer> sizeBox,
                         Label label, Button prev, Button next) {
        int size = sizeBox != null && sizeBox.getValue() != null ? sizeBox.getValue() : DEFAULT_PAGE_SIZE;
        int pages = Math.max(1, (all.size() + size - 1) / size);
        int p = Math.min(Math.max(page, 0), pages - 1);
        table.setItems(FXCollections.observableArrayList(all.subList(p * size, Math.min(all.size(), (p + 1) * size))));
        if (label != null) label.setText("Page " + (p + 1) + " of " + pages + " (" + all.size() + " total)");
        if (prev != null) prev.setDisable(p <= 0);
        if (next != null) next.setDisable(p >= pages - 1);
        return p;
    }

    private void renderOpenPage() {
        openPage = showPage(accountsTable, openAccounts, openPage, openPageSizeComboBox,
                openPageLabel, openPrevPageBtn, openNextPageBtn);
    }

    private void renderClosedPage() {
        if (closedAccountsTable == null) return;
        closedPage = showPage(closedAccountsTable, closedAccounts, closedPage, closedPageSizeComboBox,
                closedPageLabel, closedPrevPageBtn, closedNextPageBtn);
    }

    @FXML public void handleOpenPrevPage() { openPage--; renderOpenPage(); }
    @FXML public void handleOpenNextPage() { openPage++; renderOpenPage(); }
    @FXML public void handleClosedPrevPage() { closedPage--; renderClosedPage(); }
    @FXML public void handleClosedNextPage() { closedPage++; renderClosedPage(); }

    private void loadAccounts() {
        openAccounts = new java.util.ArrayList<>();
        closedAccounts = new java.util.ArrayList<>();
        for (Account a : accountService.getAccountsByUserId(sessionContext.getCurrentUserId())) {
            if (Boolean.TRUE.equals(a.getClosed())) closedAccounts.add(a); else openAccounts.add(a);
        }
        // the page stays where it was after a save, clamped when the list got shorter
        renderOpenPage();
        renderClosedPage();
    }

    private void loadAccountTypes() {
        List<AccountType> types = accountTypeService.getAccountTypesByUserId(sessionContext.getCurrentUserId());
        typeComboBox.setItems(FXCollections.observableArrayList(types));
        allTypes = types;
        renderTypesPage();
        maturityTypeNames.clear();
        for (AccountType t : types) if (Boolean.TRUE.equals(t.getHasMaturity())) maturityTypeNames.add(t.getName());
        accountsTable.refresh();
        if (closedAccountsTable != null) closedAccountsTable.refresh();
    }

    @FXML public void handleAddAccount() {
        String name = nameField.getText().trim();
        AccountType type = typeComboBox.getValue();
        String balStr = balanceField.getText().trim();
        if (name.isEmpty() || type == null || balStr.isEmpty()) { showAlert("Validation Error", "All fields are required"); return; }
        String instStr = installmentField.getText().trim();
        if (!instStr.isEmpty()) {
            try { new BigDecimal(instStr); } catch (NumberFormatException e) { showAlert("Validation Error", "Installment amount must be a valid number"); return; }
        }
        String thresholdStr = lowBalanceThresholdField.getText().trim();
        if (!thresholdStr.isEmpty()) {
            try { new BigDecimal(thresholdStr); } catch (NumberFormatException e) { showAlert("Validation Error", "Low balance alert threshold must be a valid number"); return; }
        }
        if (!confirm("Add account '" + name + "'?")) return;
        try {
            LocalDate maturityDate = maturityDatePicker.getValue();
            BigDecimal installmentAmount = instStr.isEmpty() ? null : new BigDecimal(instStr);
            BigDecimal lowBalanceThreshold = thresholdStr.isEmpty() ? null : new BigDecimal(thresholdStr);
            accountService.createAccount(sessionContext.getCurrentUserId(), name, type.getId(), new BigDecimal(balStr),
                    maturityDate, installmentAmount, lowBalanceThreshold);
            resetAccountForm(); loadAccounts();
        } catch (NumberFormatException e) { showAlert("Validation Error", "Balance must be a valid number");
        } catch (Exception e) { showAlert("Error", e.getMessage()); }
    }

    @FXML public void handleUpdateAccount() {
        Account sel = selectedAccount();
        if (sel == null) return;
        if (isMaturedCheck != null && isMaturedCheck.isSelected()) { handleMature(sel); return; }
        String name = nameField.getText().trim();
        AccountType type = typeComboBox.getValue();
        String balStr = balanceField.getText().trim();
        if (name.isEmpty() || type == null || balStr.isEmpty()) { showAlert("Validation Error", "Name, type and balance are required"); return; }
        String instStr = installmentField.getText().trim();
        if (!instStr.isEmpty()) {
            try { new BigDecimal(instStr); } catch (NumberFormatException e) { showAlert("Validation Error", "Installment amount must be a valid number"); return; }
        }
        String thresholdStr = lowBalanceThresholdField.getText().trim();
        if (!thresholdStr.isEmpty()) {
            try { new BigDecimal(thresholdStr); } catch (NumberFormatException e) { showAlert("Validation Error", "Low balance alert threshold must be a valid number"); return; }
        }
        if (!confirm("Update account '" + sel.getName() + "'?")) return;
        try {
            BigDecimal balance = new BigDecimal(balStr);
            LocalDate maturityDate = maturityDatePicker.getValue();
            BigDecimal installmentAmount = instStr.isEmpty() ? null : new BigDecimal(instStr);
            BigDecimal lowBalanceThreshold = thresholdStr.isEmpty() ? null : new BigDecimal(thresholdStr);
            accountService.updateAccount(sel.getId(), name, type.getId(), balance, maturityDate, installmentAmount, lowBalanceThreshold);
            resetAccountForm(); loadAccounts();
        } catch (NumberFormatException e) { showAlert("Validation Error", "Balance must be a valid number");
        } catch (Exception e) { showAlert("Error", e.getMessage()); }
    }

    /** Update with "Is Matured" ticked: principal and interest go to the chosen account, this account is closed. */
    private void handleMature(Account sel) {
        Account target = maturedTargetCombo == null ? null : maturedTargetCombo.getValue();
        if (target == null) { showAlert("Validation Error", "Select the account to add the money to"); return; }
        String intStr = interestField == null ? "" : interestField.getText().trim();
        BigDecimal interest;
        try { interest = intStr.isEmpty() ? BigDecimal.ZERO : new BigDecimal(intStr); }
        catch (NumberFormatException e) { showAlert("Validation Error", "Interest must be a valid number"); return; }
        if (interest.signum() < 0) { showAlert("Validation Error", "Interest cannot be negative"); return; }
        BigDecimal total = sel.getBalance().add(interest);
        Long categoryId = null;

        Alert dlg = new com.finance.util.AppAlert(Alert.AlertType.CONFIRMATION);
        dlg.setTitle("Confirm"); dlg.setHeaderText(null);
        Label msg = new Label("Mature '" + sel.getName() + "'?\n\n৳ " + sel.getBalance().toPlainString() + " + interest ৳ "
                + interest.toPlainString() + " = ৳ " + total.toPlainString() + " will be added to '" + target.getName()
                + "'.\nThis account will be closed and can no longer be used.");
        msg.setWrapText(true);
        VBox box = new VBox(10, msg);
        ComboBox<Category> categoryBox = new ComboBox<>();
        if (interest.signum() > 0) {
            categoryBox.setItems(FXCollections.observableArrayList(
                    categoryService.getIncomeCategories(sessionContext.getCurrentUserId())));
            categoryBox.setConverter(new StringConverter<Category>() {
                public String toString(Category c) { return c == null ? "" : c.getName(); }
                public Category fromString(String s) { return null; }
            });
            categoryBox.setPromptText("Select");
            categoryBox.setMaxWidth(Double.MAX_VALUE);
            box.getChildren().addAll(new Label("Category for the interest income"), categoryBox);
            // keep the dialog open until a category is chosen
            dlg.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
                if (categoryBox.getValue() == null) {
                    ev.consume();
                    categoryBox.requestFocus();
                    categoryBox.setStyle("-fx-border-color: #c0392b; -fx-border-radius: 4;");
                }
            });
        }
        dlg.getDialogPane().setContent(box);
        Optional<ButtonType> answer = dlg.showAndWait();
        if (!answer.isPresent() || answer.get() != ButtonType.OK) return;
        if (interest.signum() > 0) categoryId = categoryBox.getValue().getId();
        try {
            transactionService.matureAccount(sessionContext.getCurrentUserId(), sel.getId(), target.getId(), interest, categoryId);
            resetAccountForm(); loadAccounts();
        } catch (Exception e) { showAlert("Error", e.getMessage()); }
    }

    @FXML public void handleDeleteAccount() {
        Account sel = selectedAccount();
        if (sel == null) return;
        if (!confirm("Delete account '" + sel.getName() + "'?")) return;
        try { accountService.deleteAccount(sel.getId(), sessionContext.getCurrentUserId()); resetAccountForm(); loadAccounts();
        } catch (Exception e) { showAlert("Error", e.getMessage()); }
    }

    @FXML public void handleCancelAccount() { resetAccountForm(); }

    private void resetAccountForm() {
        nameField.clear(); balanceField.clear(); typeComboBox.setValue(null);
        maturityDatePicker.setValue(null); installmentField.clear(); lowBalanceThresholdField.clear();
        accountsTable.getSelectionModel().clearSelection();
        if (closedAccountsTable != null) closedAccountsTable.getSelectionModel().clearSelection();
        accAddBtn.setVisible(true); accAddBtn.setManaged(true);
        accEditButtons.setVisible(false); accEditButtons.setManaged(false);
        if (isMaturedCheck != null) { isMaturedCheck.setSelected(false); show(isMaturedCheck, false); }
        showMaturedBox(false);
        for (javafx.scene.Node n : accEditButtons.getChildren()) n.setDisable(false);
        nameField.setDisable(false); typeComboBox.setDisable(false); balanceField.setDisable(false);
        maturityDatePicker.setDisable(false); installmentField.setDisable(false); lowBalanceThresholdField.setDisable(false);
    }

    private void setupTypesTable() {
        typeIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        typeNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        typeMaturityColumn.setCellValueFactory(cd ->
                new SimpleStringProperty(Boolean.TRUE.equals(cd.getValue().getHasMaturity()) ? "Yes" : "—"));
        typesTable.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null) {
                typeNameField.setText(sel.getName());
                typeHasMaturityCheck.setSelected(Boolean.TRUE.equals(sel.getHasMaturity()));
                typeAddBtn.setVisible(false); typeAddBtn.setManaged(false);
                typeEditButtons.setVisible(true); typeEditButtons.setManaged(true);
            }
        });
    }

    @FXML public void handleAddType() {
        String name = typeNameField.getText().trim();
        if (name.isEmpty()) { showAlert("Validation Error", "Type name cannot be empty"); return; }
        if (!confirm("Add account type '" + name + "'?")) return;
        try { accountTypeService.createAccountType(sessionContext.getCurrentUserId(), name, typeHasMaturityCheck.isSelected()); resetTypeForm(); loadAccountTypes();
        } catch (Exception e) { showAlert("Error", e.getMessage()); }
    }

    @FXML public void handleUpdateType() {
        AccountType sel = typesTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        String name = typeNameField.getText().trim();
        if (name.isEmpty()) { showAlert("Validation Error", "Name cannot be empty"); return; }
        if (!confirm("Update type '" + sel.getName() + "' to '" + name + "'?")) return;
        try { accountTypeService.updateAccountType(sel.getId(), sessionContext.getCurrentUserId(), name, typeHasMaturityCheck.isSelected()); resetTypeForm(); loadAccountTypes();
        } catch (Exception e) { showAlert("Error", e.getMessage()); }
    }

    @FXML public void handleDeleteType() {
        AccountType sel = typesTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (!confirm("Delete type '" + sel.getName() + "'?")) return;
        try { accountTypeService.deleteAccountType(sel.getId(), sessionContext.getCurrentUserId()); resetTypeForm(); loadAccountTypes();
        } catch (Exception e) { showAlert("Error", e.getMessage()); }
    }

    @FXML public void handleCancelType() { resetTypeForm(); }

    private void resetTypeForm() {
        typeNameField.clear();
        typeHasMaturityCheck.setSelected(false);
        typesTable.getSelectionModel().clearSelection();
        typeAddBtn.setVisible(true); typeAddBtn.setManaged(true);
        typeEditButtons.setVisible(false); typeEditButtons.setManaged(false);
    }

    private boolean confirm(String msg) {
        Alert a = new com.finance.util.AppAlert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Confirm"); a.setHeaderText(null); a.setContentText(msg);
        Optional<ButtonType> r = a.showAndWait(); return r.isPresent() && r.get() == ButtonType.OK;
    }
    private void showAlert(String title, String msg) {
        Alert a = new com.finance.util.AppAlert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
}