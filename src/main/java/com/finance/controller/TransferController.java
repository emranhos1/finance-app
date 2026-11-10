package com.finance.controller;

import com.finance.context.SessionContext;
import com.finance.entity.Account;
import com.finance.entity.Transaction;
import com.finance.entity.TransferType;
import com.finance.service.AccountService;
import com.finance.service.TransactionService;
import com.finance.service.TransferTypeService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class TransferController {
    private final SessionContext sessionContext;
    private final TransactionService transactionService;
    private final AccountService accountService;
    private final TransferTypeService transferTypeService;

    @FXML private DatePicker datePicker;
    @FXML private ComboBox<Account> fromAccountComboBox;
    @FXML private ComboBox<Account> toAccountComboBox;
    @FXML private Label fromBalanceLabel;
    @FXML private Label toBalanceLabel;
    @FXML private ComboBox<TransferType> transferTypeComboBox;
    @FXML private TextField amountField;
    @FXML private TextArea noteArea;
    @FXML private Button saveTransferBtn;
    @FXML private HBox txEditButtons;

    @FXML private ComboBox<TransferType> transferTypeFilterComboBox;
    @FXML private ComboBox<Account> accountFilterComboBox;
    @FXML private DatePicker dateFilter;

    @FXML private TableView<Transaction> transfersTable;
    @FXML private TableColumn<Transaction, LocalDate> dateColumn;
    @FXML private TableColumn<Transaction, String> typeColumn;
    @FXML private TableColumn<Transaction, String> fromColumn;
    @FXML private TableColumn<Transaction, String> toColumn;
    @FXML private TableColumn<Transaction, String> transferTypeColumn;
    @FXML private TableColumn<Transaction, BigDecimal> amountColumn;
    @FXML private TableColumn<Transaction, String> noteColumn;
    @FXML private Button trPrevPageBtn;
    @FXML private Button trNextPageBtn;
    @FXML private Label trPageLabel;
    @FXML private ComboBox<Integer> trPageSizeComboBox;

    private static final List<Integer> PAGE_SIZE_OPTIONS = Arrays.asList(5, 10, 20, 50, 100);
    private static final TransferType ALL_TRANSFER_TYPES_OPTION = TransferType.builder().id(null).name("All Transfer Types").build();
    private static final Account ALL_ACCOUNTS_OPTION = Account.builder().id(null).name("All Accounts").build();
    private int trCurrentPage = 0;
    private int trTotalPages = 1;

    @FXML private TableView<TransferType> typesTable;
    @FXML private TableColumn<TransferType, Long> typeIdColumn;
    @FXML private TableColumn<TransferType, String> typeNameColumn;
    @FXML private TextField typeNameField;
    @FXML private Button typeAddBtn;
    @FXML private HBox typeEditButtons;

    private Transaction selectedTransfer = null;

    @FXML
    public void initialize() {
        setupAccountComboBoxes();
        setupTable();
        setupTypesTable();
        loadAccounts();
        loadTypes();
        trPageSizeComboBox.setItems(FXCollections.observableArrayList(PAGE_SIZE_OPTIONS));
        trPageSizeComboBox.setValue(20);
        trPageSizeComboBox.setOnAction(e -> loadTransfersPage(0));
        loadTransfersPage(0);
        datePicker.setValue(LocalDate.now());
        resetForm();
        resetTypeForm();
    }

    private void showBalance(Label label, Account account) {
        label.setText(account == null ? "" : "Available balance: ৳ " + account.getBalance().toPlainString());
    }

    private void setupAccountComboBoxes() {
        StringConverter<Account> converter = new StringConverter<Account>() {
            public String toString(Account a) {
                return a == null ? "" : a.getName() + " [" + a.getAccountType().getName() + "]";
            }
            public Account fromString(String s) { return null; }
        };
        fromAccountComboBox.setConverter(converter);
        toAccountComboBox.setConverter(converter);
        fromAccountComboBox.valueProperty().addListener((obs, o, selected) -> showBalance(fromBalanceLabel, selected));
        toAccountComboBox.valueProperty().addListener((obs, o, selected) -> showBalance(toBalanceLabel, selected));

        transferTypeComboBox.setConverter(new StringConverter<TransferType>() {
            public String toString(TransferType t) { return t == null ? "" : t.getName(); }
            public TransferType fromString(String s) { return null; }
        });

        transferTypeFilterComboBox.setConverter(new StringConverter<TransferType>() {
            public String toString(TransferType t) { return t == null ? "" : t.getName(); }
            public TransferType fromString(String s) { return null; }
        });
        transferTypeFilterComboBox.setOnAction(e -> loadTransfersPage(0));

        accountFilterComboBox.setConverter(new StringConverter<Account>() {
            public String toString(Account a) {
                if (a == null) return "";
                return a.getAccountType() == null ? a.getName() : a.getName() + " [" + a.getAccountType().getName() + "]";
            }
            public Account fromString(String s) { return null; }
        });
        accountFilterComboBox.setOnAction(e -> loadTransfersPage(0));

        dateFilter.valueProperty().addListener((obs, o, n) -> loadTransfersPage(0));
    }

    private void setupTable() {
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        amountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        noteColumn.setCellValueFactory(new PropertyValueFactory<>("note"));

        typeColumn.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getType().name()));

        fromColumn.setCellValueFactory(cd -> {
            Long accId = cd.getValue().getFromAccountId();
            if (accId == null) return new SimpleStringProperty("-");
            try { return new SimpleStringProperty(accountService.getAccountById(accId).getName()); }
            catch (Exception e) { return new SimpleStringProperty("-"); }
        });

        toColumn.setCellValueFactory(cd -> {
            Long accId = cd.getValue().getToAccountId();
            if (accId == null) return new SimpleStringProperty("-");
            try { return new SimpleStringProperty(accountService.getAccountById(accId).getName()); }
            catch (Exception e) { return new SimpleStringProperty("-"); }
        });

        transferTypeColumn.setCellValueFactory(cd -> {
            Long typeId = cd.getValue().getTransferTypeId();
            if (typeId == null) return new SimpleStringProperty("-");
            try { return new SimpleStringProperty(transferTypeService.getTransferTypeById(typeId).getName()); }
            catch (Exception e) { return new SimpleStringProperty("-"); }
        });

        transfersTable.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null) populateFormForEdit(sel);
        });
    }

    private void populateFormForEdit(Transaction tx) {
        selectedTransfer = tx;
        datePicker.setValue(tx.getDate());
        amountField.setText(tx.getAmount().toPlainString());
        noteArea.setText(tx.getNote() != null ? tx.getNote() : "");

        if (tx.getFromAccountId() != null) fromAccountComboBox.getItems().stream()
                .filter(a -> a.getId().equals(tx.getFromAccountId())).findFirst().ifPresent(fromAccountComboBox::setValue);
        if (tx.getToAccountId() != null) toAccountComboBox.getItems().stream()
                .filter(a -> a.getId().equals(tx.getToAccountId())).findFirst().ifPresent(toAccountComboBox::setValue);
        if (tx.getTransferTypeId() != null) transferTypeComboBox.getItems().stream()
                .filter(t -> t.getId().equals(tx.getTransferTypeId())).findFirst().ifPresent(transferTypeComboBox::setValue);
        else transferTypeComboBox.setValue(null);

        // update only changes date, amount, transfer type and note, so the accounts are locked while editing
        fromAccountComboBox.setDisable(true);
        toAccountComboBox.setDisable(true);

        saveTransferBtn.setVisible(false);
        saveTransferBtn.setManaged(false);
        txEditButtons.setVisible(true);
        txEditButtons.setManaged(true);
    }

    private void loadAccounts() {
        List<Account> accounts = accountService.getAccountsByUserId(sessionContext.getCurrentUserId());
        fromAccountComboBox.setItems(FXCollections.observableArrayList(accounts));
        toAccountComboBox.setItems(FXCollections.observableArrayList(accounts));

        Account currentFilter = accountFilterComboBox.getValue();
        List<Account> filterOptions = new ArrayList<>();
        filterOptions.add(ALL_ACCOUNTS_OPTION);
        filterOptions.addAll(accounts);
        accountFilterComboBox.setItems(FXCollections.observableArrayList(filterOptions));
        accountFilterComboBox.setValue(currentFilter != null ? currentFilter : ALL_ACCOUNTS_OPTION);
    }

    private void loadTransfersPage(int page) {
        int pageSize = trPageSizeComboBox.getValue() != null ? trPageSizeComboBox.getValue() : 20;
        Pageable pageable = PageRequest.of(page, pageSize);

        TransferType selectedType = transferTypeFilterComboBox.getValue();
        Long transferTypeId = (selectedType != null && selectedType.getId() != null) ? selectedType.getId() : null;
        Account selectedAccount = accountFilterComboBox.getValue();
        Long accountId = (selectedAccount != null && selectedAccount.getId() != null) ? selectedAccount.getId() : null;
        LocalDate selectedDate = dateFilter.getValue();

        Page<Transaction> result = transactionService.getTransfersPage(sessionContext.getCurrentUserId(),
                transferTypeId, selectedDate, accountId, pageable);

        trCurrentPage = result.getNumber();
        trTotalPages = Math.max(result.getTotalPages(), 1);
        transfersTable.setItems(FXCollections.observableArrayList(result.getContent()));
        trPageLabel.setText("Page " + (trCurrentPage + 1) + " of " + trTotalPages + " (" + result.getTotalElements() + " total)");
        trPrevPageBtn.setDisable(trCurrentPage <= 0);
        trNextPageBtn.setDisable(trCurrentPage >= trTotalPages - 1);
    }

    @FXML
    public void handleTrPrevPage() {
        if (trCurrentPage > 0) loadTransfersPage(trCurrentPage - 1);
    }

    @FXML
    public void handleTrNextPage() {
        if (trCurrentPage < trTotalPages - 1) loadTransfersPage(trCurrentPage + 1);
    }

    @FXML
    public void handleSaveTransfer() {
        Account from = fromAccountComboBox.getValue();
        Account to = toAccountComboBox.getValue();
        TransferType transferType = transferTypeComboBox.getValue();
        LocalDate date = com.finance.util.DatePickerUtil.readDate(datePicker);
        String amountStr = amountField.getText().trim();
        String note = noteArea.getText().trim();
        if (from == null || to == null || amountStr.isEmpty() || transferType == null || date == null) {
            showAlert("Validation Error", "From Account, To Account, Transfer Type, Date and Amount are required"); return;
        }
        if (from.getId().equals(to.getId())) {
            showAlert("Validation Error", "Source and destination accounts must be different"); return;
        }
        if (!confirm("Save this transfer?")) return;
        try {
            transactionService.recordTransferTransaction(
                    sessionContext.getCurrentUserId(), date, new BigDecimal(amountStr), from.getId(), to.getId(),
                    transferType.getId(), note);
            resetForm(); loadAccounts(); loadTransfersPage(0);
        } catch (NumberFormatException e) {
            showAlert("Validation Error", "Amount must be a valid number");
        } catch (Exception e) { showAlert("Error", e.getMessage()); }
    }

    @FXML
    public void handleUpdateTransfer() {
        if (selectedTransfer == null) return;
        LocalDate date = com.finance.util.DatePickerUtil.readDate(datePicker);
        String amountStr = amountField.getText().trim();
        String note = noteArea.getText().trim();
        TransferType transferType = transferTypeComboBox.getValue();
        if (amountStr.isEmpty() || transferType == null || date == null) {
            showAlert("Validation Error", "Amount, Transfer Type and Date are required"); return;
        }
        if (!confirm("Update this transfer?")) return;
        try {
            transactionService.updateTransaction(selectedTransfer.getId(), sessionContext.getCurrentUserId(),
                    date, new BigDecimal(amountStr), null, transferType.getId(), note);
            resetForm(); loadAccounts(); loadTransfersPage(0);
        } catch (NumberFormatException e) {
            showAlert("Validation Error", "Amount must be a valid number");
        } catch (Exception e) { showAlert("Error", e.getMessage()); }
    }

    @FXML
    public void handleDeleteTransfer() {
        if (selectedTransfer == null) return;
        if (!confirm("Delete this transfer? The account balances will be reversed.")) return;
        try {
            transactionService.deleteTransaction(selectedTransfer.getId(), sessionContext.getCurrentUserId());
            resetForm(); loadAccounts(); loadTransfersPage(0);
        } catch (Exception e) { showAlert("Error", e.getMessage()); }
    }

    @FXML
    public void handleCancelTransfer() { resetForm(); }

    private void resetForm() {
        selectedTransfer = null;
        fromAccountComboBox.setDisable(false);
        toAccountComboBox.setDisable(false);
        datePicker.setValue(LocalDate.now());
        fromAccountComboBox.setValue(null);
        toAccountComboBox.setValue(null);
        transferTypeComboBox.setValue(null);
        amountField.clear();
        noteArea.clear();
        transfersTable.getSelectionModel().clearSelection();
        saveTransferBtn.setVisible(true);
        saveTransferBtn.setManaged(true);
        txEditButtons.setVisible(false);
        txEditButtons.setManaged(false);
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

    private void loadTypes() {
        List<TransferType> types = transferTypeService.getTransferTypesByUserId(sessionContext.getCurrentUserId());
        transferTypeComboBox.setItems(FXCollections.observableArrayList(types));
        typesTable.setItems(FXCollections.observableArrayList(types));

        TransferType currentFilter = transferTypeFilterComboBox.getValue();
        List<TransferType> filterOptions = new ArrayList<>();
        filterOptions.add(ALL_TRANSFER_TYPES_OPTION);
        filterOptions.addAll(types);
        transferTypeFilterComboBox.setItems(FXCollections.observableArrayList(filterOptions));
        transferTypeFilterComboBox.setValue(currentFilter != null ? currentFilter : ALL_TRANSFER_TYPES_OPTION);
    }

    @FXML
    public void handleClearFilters() {
        transferTypeFilterComboBox.setValue(ALL_TRANSFER_TYPES_OPTION);
        accountFilterComboBox.setValue(ALL_ACCOUNTS_OPTION);
        dateFilter.setValue(null);
        loadTransfersPage(0);
    }

    @FXML public void handleAddType() {
        String name = typeNameField.getText().trim();
        if (name.isEmpty()) { showAlert("Validation Error", "Type name cannot be empty"); return; }
        if (!confirm("Add transfer type '" + name + "'?")) return;
        try { transferTypeService.createTransferType(sessionContext.getCurrentUserId(), name); resetTypeForm(); loadTypes();
        } catch (Exception e) { showAlert("Error", e.getMessage()); }
    }

    @FXML public void handleUpdateType() {
        TransferType sel = typesTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        String name = typeNameField.getText().trim();
        if (name.isEmpty()) { showAlert("Validation Error", "Name cannot be empty"); return; }
        if (!confirm("Update type '" + sel.getName() + "' to '" + name + "'?")) return;
        try { transferTypeService.updateTransferType(sel.getId(), sessionContext.getCurrentUserId(), name); resetTypeForm(); loadTypes();
        } catch (Exception e) { showAlert("Error", e.getMessage()); }
    }

    @FXML public void handleDeleteType() {
        TransferType sel = typesTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (!confirm("Delete type '" + sel.getName() + "'?")) return;
        try { transferTypeService.deleteTransferType(sel.getId(), sessionContext.getCurrentUserId()); resetTypeForm(); loadTypes();
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
