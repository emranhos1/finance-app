package com.finance.controller;

import com.finance.context.SessionContext;
import com.finance.entity.Account;
import com.finance.entity.AccountType;
import com.finance.service.AccountService;
import com.finance.service.AccountTypeService;
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

    @FXML private TableView<Account> accountsTable;
    @FXML private TableColumn<Account, Long> idColumn;
    @FXML private TableColumn<Account, String> nameColumn;
    @FXML private TableColumn<Account, String> typeColumn;
    @FXML private TableColumn<Account, BigDecimal> balanceColumn;
    @FXML private TableColumn<Account, String> maturityColumn;
    @FXML private TableColumn<Account, String> installmentColumn;
    @FXML private TextField nameField;
    @FXML private ComboBox<AccountType> typeComboBox;
    @FXML private TextField balanceField;
    @FXML private VBox maturityInstallmentBox;
    @FXML private DatePicker maturityDatePicker;
    @FXML private TextField installmentField;
    @FXML private TextField lowBalanceThresholdField;
    @FXML private Button accAddBtn;
    @FXML private HBox accEditButtons;

    @FXML private TableView<AccountType> typesTable;
    @FXML private TableColumn<AccountType, Long> typeIdColumn;
    @FXML private TableColumn<AccountType, String> typeNameColumn;
    @FXML private TextField typeNameField;
    @FXML private Button typeAddBtn;
    @FXML private HBox typeEditButtons;

    @FXML
    public void initialize() {
        setupAccountsTable();
        setupTypesTable();
        loadAccountTypes();
        loadAccounts();
        resetAccountForm();
        resetTypeForm();
    }

    private void setupAccountsTable() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        typeColumn.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getAccountType().getName()));
        balanceColumn.setCellValueFactory(new PropertyValueFactory<>("balance"));
        maturityColumn.setCellValueFactory(cd -> {
            LocalDate d = cd.getValue().getMaturityDate();
            return new SimpleStringProperty(d == null ? "—" : d.format(DATE_FMT));
        });
        installmentColumn.setCellValueFactory(cd -> {
            BigDecimal amt = cd.getValue().getInstallmentAmount();
            return new SimpleStringProperty(amt == null ? "—" : amt.toPlainString());
        });
        typeComboBox.setConverter(new StringConverter<AccountType>() {
            public String toString(AccountType t) { return t == null ? "" : t.getName(); }
            public AccountType fromString(String s) { return null; }
        });
        typeComboBox.valueProperty().addListener((obs, o, type) -> {
            boolean matures = type != null
                    && ("DPS".equalsIgnoreCase(type.getName()) || "FDR".equalsIgnoreCase(type.getName()));
            maturityInstallmentBox.setVisible(matures);
            maturityInstallmentBox.setManaged(matures);
            if (!matures) { maturityDatePicker.setValue(null); installmentField.clear(); }
        });

        accountsTable.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null) {
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
            }
        });
    }

    private void loadAccounts() {
        accountsTable.setItems(FXCollections.observableArrayList(
                accountService.getAccountsByUserId(sessionContext.getCurrentUserId())));
    }

    private void loadAccountTypes() {
        List<AccountType> types = accountTypeService.getAccountTypesByUserId(sessionContext.getCurrentUserId());
        typeComboBox.setItems(FXCollections.observableArrayList(types));
        typesTable.setItems(FXCollections.observableArrayList(types));
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
        Account sel = accountsTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
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

    @FXML public void handleDeleteAccount() {
        Account sel = accountsTable.getSelectionModel().getSelectedItem();
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
        accAddBtn.setVisible(true); accAddBtn.setManaged(true);
        accEditButtons.setVisible(false); accEditButtons.setManaged(false);
    }

    private void setupTypesTable() {
        typeIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        typeNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        typesTable.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null) {
                typeNameField.setText(sel.getName());
                typeAddBtn.setVisible(false); typeAddBtn.setManaged(false);
                typeEditButtons.setVisible(true); typeEditButtons.setManaged(true);
            }
        });
    }

    @FXML public void handleAddType() {
        String name = typeNameField.getText().trim();
        if (name.isEmpty()) { showAlert("Validation Error", "Type name cannot be empty"); return; }
        if (!confirm("Add account type '" + name + "'?")) return;
        try { accountTypeService.createAccountType(sessionContext.getCurrentUserId(), name); resetTypeForm(); loadAccountTypes();
        } catch (Exception e) { showAlert("Error", e.getMessage()); }
    }

    @FXML public void handleUpdateType() {
        AccountType sel = typesTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        String name = typeNameField.getText().trim();
        if (name.isEmpty()) { showAlert("Validation Error", "Name cannot be empty"); return; }
        if (!confirm("Update type '" + sel.getName() + "' to '" + name + "'?")) return;
        try { accountTypeService.updateAccountType(sel.getId(), sessionContext.getCurrentUserId(), name); resetTypeForm(); loadAccountTypes();
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
        typesTable.getSelectionModel().clearSelection();
        typeAddBtn.setVisible(true); typeAddBtn.setManaged(true);
        typeEditButtons.setVisible(false); typeEditButtons.setManaged(false);
    }

    private boolean confirm(String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Confirm"); a.setHeaderText(null); a.setContentText(msg);
        Optional<ButtonType> r = a.showAndWait(); return r.isPresent() && r.get() == ButtonType.OK;
    }
    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
}