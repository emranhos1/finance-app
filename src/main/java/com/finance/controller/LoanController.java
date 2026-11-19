package com.finance.controller;

import com.finance.context.SessionContext;
import com.finance.entity.Account;
import com.finance.entity.Category;
import com.finance.entity.Loan;
import com.finance.entity.LoanPerson;
import com.finance.entity.TransferType;
import com.finance.service.AccountService;
import com.finance.service.CategoryService;
import com.finance.service.LoanService;
import com.finance.service.LoanService.PersonSummary;
import com.finance.service.TransferTypeService;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class LoanController {
    private final SessionContext sessionContext;
    private final LoanService loanService;
    private final AccountService accountService;
    private final TransferTypeService transferTypeService;
    private final CategoryService categoryService;

    @FXML private VBox addLoanBox;
    @FXML private ComboBox<LoanPerson> personComboBox;
    @FXML private RadioButton lentRadio;
    @FXML private RadioButton borrowedRadio;
    @FXML private ComboBox<Account> accountComboBox;
    @FXML private Label accountBalanceLabel;
    @FXML private ComboBox<TransferType> transferTypeComboBox;
    @FXML private ComboBox<Category> categoryComboBox;
    @FXML private TextField amountField;
    @FXML private DatePicker datePicker;
    @FXML private DatePicker dueDatePicker;
    @FXML private TextArea noteArea;

    @FXML private VBox repaymentBox;
    @FXML private Label selectedLoanLabel;
    @FXML private TextField repayAmountField;
    @FXML private ComboBox<Account> repayAccountComboBox;
    @FXML private DatePicker repayDatePicker;
    @FXML private TextField repayNoteField;

    @FXML private VBox settledSelectionBox;
    @FXML private Label settledLoanLabel;

    @FXML private TableView<PersonSummary> personsSummaryTable;
    @FXML private TableColumn<PersonSummary, String> summaryPersonColumn;
    @FXML private TableColumn<PersonSummary, BigDecimal> summaryLentColumn;
    @FXML private TableColumn<PersonSummary, BigDecimal> summaryBorrowedColumn;
    @FXML private TableColumn<PersonSummary, BigDecimal> summaryNetColumn;
    @FXML private TableColumn<PersonSummary, Number> summaryOpenColumn;

    @FXML private Label historyTitleLabel;
    @FXML private TableView<Loan> loansTable;
    @FXML private TableColumn<Loan, LocalDate> dateColumn;
    @FXML private TableColumn<Loan, String> typeColumn;
    @FXML private TableColumn<Loan, BigDecimal> principalColumn;
    @FXML private TableColumn<Loan, BigDecimal> remainingColumn;
    @FXML private TableColumn<Loan, String> statusColumn;
    @FXML private TableColumn<Loan, LocalDate> dueDateColumn;
    @FXML private TableColumn<Loan, String> noteColumn;

    @FXML private TextField personNameField;
    @FXML private TextArea personNoteArea;
    @FXML private Button addPersonBtn;
    @FXML private VBox personEditButtons;
    @FXML private ComboBox<LoanPerson> mergeTargetComboBox;
    @FXML private TableView<LoanPerson> personsTable;

    // paging of the three tables (rows are held in memory; see util/TablePager). The controls are optional.
    @FXML private Button summaryPrevPageBtn;
    @FXML private Button summaryNextPageBtn;
    @FXML private Label summaryPageLabel;
    @FXML private ComboBox<Integer> summaryPageSizeComboBox;
    @FXML private Button historyPrevPageBtn;
    @FXML private Button historyNextPageBtn;
    @FXML private Label historyPageLabel;
    @FXML private ComboBox<Integer> historyPageSizeComboBox;
    @FXML private Button personsPrevPageBtn;
    @FXML private Button personsNextPageBtn;
    @FXML private Label personsPageLabel;
    @FXML private ComboBox<Integer> personsPageSizeComboBox;
    private com.finance.util.TablePager<PersonSummary> summaryPager;
    private com.finance.util.TablePager<Loan> historyPager;
    private com.finance.util.TablePager<LoanPerson> personsPager;
    /** The person whose history is on screen, so the history page is kept on a reload but reset for another person. */
    private Long historyShownFor;
    @FXML private TableColumn<LoanPerson, String> personListNameColumn;
    @FXML private TableColumn<LoanPerson, String> personListNoteColumn;

    // optional display-only labels (Gold footer lines, Bento tile): a layout may leave any of them out
    @FXML private Label loanNetRemainingLabel;
    @FXML private Label loanPeopleCountLabel;

    /** Remaining of each loan row, kept so the status chip (Open or Partial) does not ask the service twice. */
    private final java.util.Map<Long, BigDecimal> remainingCache = new java.util.HashMap<>();

    private Loan selectedLoan = null;
    private LoanPerson selectedPersonForEdit = null;

    @FXML
    public void initialize() {
        com.finance.util.BanglaFont.apply(historyTitleLabel);   // bold title with the person's name, which can be Bangla
        setupAccountComboBoxes();
        setupPersonComboBox();
        setupTransferTypeComboBox();
        setupCategoryComboBox();
        setupSummaryTable();
        setupHistoryTable();
        setupPersonsTable();
        setupPagers();
        setupSummaries();

        loadAccounts();
        loadTransferTypes();
        loadPersons();
        refreshCategories();
        lentRadio.selectedProperty().addListener((obs, o, sel) -> refreshCategories());
        borrowedRadio.selectedProperty().addListener((obs, o, sel) -> refreshCategories());
        loadPersonSummaries();
        resetLoanForm();
        resetSelection();
        resetPersonSelection();
    }

    private void setupPagers() {
        summaryPager = new com.finance.util.TablePager<>(personsSummaryTable, summaryPageSizeComboBox,
                summaryPageLabel, summaryPrevPageBtn, summaryNextPageBtn);
        historyPager = new com.finance.util.TablePager<>(loansTable, historyPageSizeComboBox,
                historyPageLabel, historyPrevPageBtn, historyNextPageBtn);
        personsPager = new com.finance.util.TablePager<>(personsTable, personsPageSizeComboBox,
                personsPageLabel, personsPrevPageBtn, personsNextPageBtn);
    }

    /** Net remaining of all people and the number of people; worked out from the rows the tables hold, no new query. */
    private void setupSummaries() {
        com.finance.util.UiFormat.watchItems(personsSummaryTable, () -> {
            BigDecimal net = BigDecimal.ZERO;
            if (summaryPager != null) {
                for (PersonSummary ps : summaryPager.getAll()) if (ps.getNetRemaining() != null) net = net.add(ps.getNetRemaining());
            }
            com.finance.util.UiFormat.setText(loanNetRemainingLabel, com.finance.util.UiFormat.money(net));
        });
        com.finance.util.UiFormat.watchItems(personsTable, () ->
                com.finance.util.UiFormat.setText(loanPeopleCountLabel,
                        String.valueOf(personsPager == null ? 0 : personsPager.getAll().size())));
    }

    private BigDecimal remainingOf(Loan loan) {
        if (loan.getId() == null) return loanService.getRemaining(loan);
        return remainingCache.computeIfAbsent(loan.getId(), id -> loanService.getRemaining(loan));
    }

    private void setupAccountComboBoxes() {
        StringConverter<Account> converter = new StringConverter<Account>() {
            public String toString(Account a) {
                return a == null ? "" : a.getName() + " [" + a.getAccountType().getName() + "]";
            }
            public Account fromString(String s) { return null; }
        };
        accountComboBox.setConverter(converter);
        repayAccountComboBox.setConverter(converter);
        accountComboBox.valueProperty().addListener((obs, o, selected) -> accountBalanceLabel.setText(
                selected == null ? "" : "Available balance: ৳ " + selected.getBalance().toPlainString()));
    }

    private void setupPersonComboBox() {
        personComboBox.setConverter(new StringConverter<LoanPerson>() {
            public String toString(LoanPerson p) { return p == null ? "" : p.getName(); }
            public LoanPerson fromString(String s) { return null; }
        });
    }

    private void setupTransferTypeComboBox() {
        transferTypeComboBox.setConverter(new StringConverter<TransferType>() {
            public String toString(TransferType t) { return t == null ? "" : t.getName(); }
            public TransferType fromString(String s) { return null; }
        });
    }

    private void loadTransferTypes() {
        List<TransferType> types = transferTypeService.getTransferTypesByUserId(sessionContext.getCurrentUserId());
        transferTypeComboBox.setItems(FXCollections.observableArrayList(types));
    }

    private void setupCategoryComboBox() {
        categoryComboBox.setConverter(new StringConverter<Category>() {
            public String toString(Category c) { return c == null ? "" : c.getName(); }
            public Category fromString(String s) { return null; }
        });
    }

    private void refreshCategories() {
        Long userId = sessionContext.getCurrentUserId();
        List<Category> categories = lentRadio.isSelected()
                ? categoryService.getExpenseCategories(userId)
                : categoryService.getIncomeCategories(userId);
        categoryComboBox.setItems(FXCollections.observableArrayList(categories));
        categoryComboBox.setValue(null);
    }

    private void setupSummaryTable() {
        summaryPersonColumn.setCellValueFactory(new PropertyValueFactory<>("personName"));
        summaryLentColumn.setCellValueFactory(new PropertyValueFactory<>("totalLent"));
        summaryBorrowedColumn.setCellValueFactory(new PropertyValueFactory<>("totalBorrowed"));
        summaryNetColumn.setCellValueFactory(new PropertyValueFactory<>("netRemaining"));
        summaryOpenColumn.setCellValueFactory(new PropertyValueFactory<>("openCount"));
        com.finance.util.UiFormat.amountColumn(summaryLentColumn, (r, v) -> com.finance.util.UiFormat.Tone.POS);
        com.finance.util.UiFormat.amountColumn(summaryBorrowedColumn, (r, v) -> com.finance.util.UiFormat.Tone.NEG);
        com.finance.util.UiFormat.amountColumn(summaryNetColumn, (r, v) -> com.finance.util.UiFormat.sign(v));
        com.finance.util.UiFormat.countColumn(summaryOpenColumn);

        personsSummaryTable.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null) {
                historyTitleLabel.setText("History - " + sel.getPersonName());
                loadHistoryForPerson(sel.getPersonId());
                resetSelection();
            }
        });
    }

    private void setupHistoryTable() {
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("loanDate"));
        principalColumn.setCellValueFactory(new PropertyValueFactory<>("principalAmount"));
        dueDateColumn.setCellValueFactory(new PropertyValueFactory<>("dueDate"));
        noteColumn.setCellValueFactory(new PropertyValueFactory<>("note"));

        typeColumn.setCellValueFactory(cd -> new SimpleStringProperty(typeLabel(cd.getValue())));
        statusColumn.setCellValueFactory(cd -> {
            Loan loan = cd.getValue();
            // an open loan that is partly repaid is shown as Partial in the themes whose board has that chip
            boolean partial = loan.getStatus() == Loan.LoanStatus.OPEN
                    && loan.getPrincipalAmount() != null
                    && remainingOf(loan).compareTo(loan.getPrincipalAmount()) < 0;
            return new SimpleStringProperty(partial ? "PARTIAL" : loan.getStatus().name());
        });
        remainingColumn.setCellValueFactory(cd -> new SimpleObjectProperty<>(remainingOf(cd.getValue())));
        com.finance.util.UiFormat.chipColumn(typeColumn, v -> v, v -> "I Gave".equals(v) ? "chip-income" : "chip-expense");
        com.finance.util.UiFormat.chipColumn(statusColumn, com.finance.util.UiFormat::loanStatusLabel, com.finance.util.UiFormat::loanStatusChipClass);
        java.util.function.BiFunction<Loan, BigDecimal, com.finance.util.UiFormat.Tone> loanTone = (loan, v) -> loan == null ? com.finance.util.UiFormat.Tone.NEUTRAL
                : loan.getType() == Loan.LoanType.LENT ? com.finance.util.UiFormat.Tone.POS : com.finance.util.UiFormat.Tone.NEG;
        com.finance.util.UiFormat.amountColumn(principalColumn, loanTone);
        com.finance.util.UiFormat.amountColumn(remainingColumn, loanTone);

        loansTable.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null) populateSelection(sel);
        });
    }

    private void setupPersonsTable() {
        personListNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        personListNoteColumn.setCellValueFactory(new PropertyValueFactory<>("note"));

        mergeTargetComboBox.setConverter(new StringConverter<LoanPerson>() {
            public String toString(LoanPerson p) { return p == null ? "" : p.getName(); }
            public LoanPerson fromString(String s) { return null; }
        });

        personsTable.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            if (sel != null) {
                selectedPersonForEdit = sel;
                personNameField.setText(sel.getName());
                personNoteArea.setText(sel.getNote() == null ? "" : sel.getNote());
                addPersonBtn.setVisible(false);
                addPersonBtn.setManaged(false);
                personEditButtons.setVisible(true);
                personEditButtons.setManaged(true);

                List<LoanPerson> others = loanService.getLoanPersonsByUserId(sessionContext.getCurrentUserId());
                others.removeIf(p -> p.getId().equals(sel.getId()));
                mergeTargetComboBox.setItems(FXCollections.observableArrayList(others));
                mergeTargetComboBox.setValue(null);
            }
        });
    }

    private void populateSelection(Loan loan) {
        selectedLoan = loan;
        addLoanBox.setVisible(false);
        addLoanBox.setManaged(false);

        boolean open = loan.getStatus() == Loan.LoanStatus.OPEN;
        repaymentBox.setVisible(open);
        repaymentBox.setManaged(open);
        settledSelectionBox.setVisible(!open);
        settledSelectionBox.setManaged(!open);

        if (open) {
            BigDecimal remaining = loanService.getRemaining(loan);
            selectedLoanLabel.setText(loan.getPersonName() + " - " + typeLabel(loan)
                    + " - Remaining: ৳ " + remaining.toPlainString());
            repayAmountField.clear();
            repayNoteField.clear();
            repayDatePicker.setValue(LocalDate.now());
            repayAccountComboBox.getItems().stream()
                    .filter(a -> a.getId().equals(loan.getAccountId())).findFirst()
                    .ifPresent(repayAccountComboBox::setValue);
        } else {
            settledLoanLabel.setText(loan.getPersonName() + " - " + typeLabel(loan)
                    + " - SETTLED (Principal ৳ " + loan.getPrincipalAmount().toPlainString() + ")");
        }
    }

    private String typeLabel(Loan loan) {
        return loan.getType() == Loan.LoanType.LENT ? "I Gave" : "I Took";
    }

    private void loadAccounts() {
        List<Account> accounts = accountService.getOpenAccountsByUserId(sessionContext.getCurrentUserId());
        accountComboBox.setItems(FXCollections.observableArrayList(accounts));
        repayAccountComboBox.setItems(FXCollections.observableArrayList(accounts));
    }

    private void loadPersons() {
        List<LoanPerson> persons = loanService.getLoanPersonsByUserId(sessionContext.getCurrentUserId());
        personComboBox.setItems(FXCollections.observableArrayList(persons));
        personsPager.setAll(persons);
    }

    private void loadPersonSummaries() {
        List<PersonSummary> summaries = loanService.getPersonSummaries(sessionContext.getCurrentUserId());
        remainingCache.clear();
        summaryPager.setAll(summaries);
        historyPager.setAll(new ArrayList<>());
        historyShownFor = null;
        historyTitleLabel.setText("History");
    }

    private void loadHistoryForPerson(Long loanPersonId) {
        List<Loan> loans = loanService.getLoansByPerson(sessionContext.getCurrentUserId(), loanPersonId);
        remainingCache.clear();
        if (!java.util.Objects.equals(historyShownFor, loanPersonId)) historyPager.reset();
        historyShownFor = loanPersonId;
        historyPager.setAll(loans);
    }

    @FXML
    public void handleSaveLoan() {
        LoanPerson person = personComboBox.getValue();
        Loan.LoanType type = lentRadio.isSelected() ? Loan.LoanType.LENT : Loan.LoanType.BORROWED;
        Account account = accountComboBox.getValue();
        TransferType transferType = transferTypeComboBox.getValue();
        Category category = categoryComboBox.getValue();
        String amountStr = amountField.getText().trim();
        LocalDate date = com.finance.util.DatePickerUtil.readDate(datePicker);
        LocalDate dueDate = com.finance.util.DatePickerUtil.readDate(dueDatePicker);
        String note = noteArea.getText().trim();

        if (person == null || account == null || amountStr.isEmpty() || date == null) {
            showAlert("Validation Error", "Person, Account, Date and Amount are required"); return;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountStr);
        } catch (NumberFormatException e) {
            showAlert("Validation Error", "Amount must be a valid number"); return;
        }

        Long transferTypeId = transferType == null ? null : transferType.getId();
        Long categoryId = category == null ? null : category.getId();

        // same person owes the other direction: this is likely settling that debt, so ask first
        Loan.LoanType oppositeType = (type == Loan.LoanType.LENT) ? Loan.LoanType.BORROWED : Loan.LoanType.LENT;
        List<Loan> openOpposite = new ArrayList<>();
        for (Loan loan : loanService.getLoansByPerson(sessionContext.getCurrentUserId(), person.getId())) {
            if (loan.getStatus() == Loan.LoanStatus.OPEN && loan.getType() == oppositeType) openOpposite.add(loan);
        }

        if (!openOpposite.isEmpty()) {
            BigDecimal owed = BigDecimal.ZERO;
            for (Loan loan : openOpposite) owed = owed.add(loanService.getRemaining(loan));

            String debtDescription = (oppositeType == Loan.LoanType.LENT)
                    ? person.getName() + " already owes you ৳ " + owed.toPlainString() + " from earlier loans."
                    : "You already owe " + person.getName() + " ৳ " + owed.toPlainString() + " from earlier loans.";
            String settleExplanation = (amount.compareTo(owed) > 0)
                    ? "৳ " + owed.toPlainString() + " of this ৳ " + amount.toPlainString()
                        + " would settle that, and the remaining ৳ " + amount.subtract(owed).toPlainString()
                        + " would be recorded as a new loan."
                    : "This whole ৳ " + amount.toPlainString() + " would go toward settling that instead of creating a new loan.";

            ButtonType settleType = new ButtonType("Settle Debt First", ButtonBar.ButtonData.OK_DONE);
            ButtonType newLoanType = new ButtonType("Record as New Loan", ButtonBar.ButtonData.OTHER);
            Dialog<ButtonType> choiceDialog = new com.finance.util.AppDialog<>();
            choiceDialog.setTitle("Existing Balance Found");
            choiceDialog.getDialogPane().getButtonTypes().addAll(settleType, newLoanType, ButtonType.CANCEL);
            Label choiceLabel = new Label(debtDescription + "\n\n" + settleExplanation);
            choiceLabel.setWrapText(true);
            choiceLabel.setMaxWidth(380);
            VBox choiceContent = new VBox(choiceLabel);
            choiceContent.setPadding(new Insets(10));
            choiceDialog.getDialogPane().setContent(choiceContent);
            choiceDialog.getDialogPane().setMinWidth(440);

            Optional<ButtonType> choice = choiceDialog.showAndWait();
            if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) return;

            if (choice.get() == settleType) {
                try {
                    LoanService.BulkSettlementResult settlement = loanService.recordBulkRepayment(
                            sessionContext.getCurrentUserId(), person.getId(), oppositeType, amount, account.getId(),
                            transferTypeId, categoryId, date, note);
                    resetLoanForm();
                    loadAccounts();
                    loadPersonSummaries();
                    loadHistoryForPerson(person.getId());

                    StringBuilder msg = new StringBuilder("Settled " + settlement.getRepaidLoans().size() + " loan(s).");
                    if (settlement.getNewLoanFromExcess() != null) {
                        String owesWho = settlement.getNewLoanFromExcess().getType() == Loan.LoanType.BORROWED
                                ? "you now owe them" : "they now owe you";
                        msg.append("\n\n৳ ").append(settlement.getExcessAmount().toPlainString())
                                .append(" was more than was owed, so it was recorded as a new loan - ")
                                .append(owesWho).append(" ৳ ").append(settlement.getExcessAmount().toPlainString()).append(".");
                    }
                    showAlert("Success", msg.toString());
                } catch (Exception e) {
                    showAlert("Error", e.getMessage());
                }
                return;
            }
        } else {
            if (!confirm("Save this loan entry?")) return;
        }

        try {
            loanService.createLoan(sessionContext.getCurrentUserId(), person.getId(), type,
                    amount, account.getId(), transferTypeId, categoryId, date, dueDate, note);
            resetLoanForm();
            loadAccounts();
            loadPersonSummaries();
        } catch (Exception e) {
            showAlert("Error", e.getMessage());
        }
    }

    @FXML
    public void handleSaveRepayment() {
        if (selectedLoan == null) return;
        String amountStr = repayAmountField.getText().trim();
        Account account = repayAccountComboBox.getValue();
        LocalDate date = com.finance.util.DatePickerUtil.readDate(repayDatePicker);
        String note = repayNoteField.getText().trim();

        if (amountStr.isEmpty() || account == null || date == null) {
            showAlert("Validation Error", "Amount, Account and Date are required"); return;
        }
        if (!confirm("Save this repayment?")) return;
        try {
            Long personId = selectedLoan.getLoanPersonId();
            loanService.recordRepayment(selectedLoan.getId(), sessionContext.getCurrentUserId(),
                    new BigDecimal(amountStr), account.getId(), date, note);
            resetSelection();
            loadAccounts();
            loadPersonSummaries();
            if (personId != null) loadHistoryForPerson(personId);
        } catch (NumberFormatException e) {
            showAlert("Validation Error", "Amount must be a valid number");
        } catch (Exception e) {
            showAlert("Error", e.getMessage());
        }
    }

    @FXML
    public void handleDeleteLoan() {
        if (selectedLoan == null) return;
        if (!confirm("Delete this loan? This will also reverse the original loan transaction "
                + "and any repayments, restoring the account balance(s).")) return;
        try {
            Long personId = selectedLoan.getLoanPersonId();
            loanService.deleteLoan(selectedLoan.getId(), sessionContext.getCurrentUserId());
            resetSelection();
            loadPersonSummaries();
            if (personId != null) loadHistoryForPerson(personId);
        } catch (Exception e) {
            showAlert("Error", e.getMessage());
        }
    }

    @FXML
    public void handleCancelSelection() {
        resetSelection();
    }

    @FXML
    public void handleAddPerson() {
        String name = personNameField.getText().trim();
        String note = personNoteArea.getText().trim();
        if (name.isEmpty()) {
            showAlert("Validation Error", "Name is required"); return;
        }
        if (!confirm("Add loan account '" + name + "'?")) return;
        try {
            loanService.createLoanPerson(sessionContext.getCurrentUserId(), name, note);
            resetPersonForm();
            loadPersons();
        } catch (Exception e) {
            showAlert("Error", e.getMessage());
        }
    }

    @FXML
    public void handleUpdatePerson() {
        if (selectedPersonForEdit == null) return;
        String name = personNameField.getText().trim();
        String note = personNoteArea.getText().trim();
        if (name.isEmpty()) {
            showAlert("Validation Error", "Name is required"); return;
        }
        if (!confirm("Update this loan account? Existing loans for this person will show the new name.")) return;
        try {
            loanService.updateLoanPerson(selectedPersonForEdit.getId(), sessionContext.getCurrentUserId(), name, note);
            resetPersonSelection();
            loadPersons();
            loadPersonSummaries();
        } catch (Exception e) {
            showAlert("Error", e.getMessage());
        }
    }

    @FXML
    public void handleMergePerson() {
        if (selectedPersonForEdit == null) return;
        LoanPerson target = mergeTargetComboBox.getValue();
        if (target == null) {
            showAlert("Validation Error", "Select a person to merge into"); return;
        }
        if (!confirm("Merge \"" + selectedPersonForEdit.getName() + "\" into \"" + target.getName()
                + "\"? All of its loans will move to \"" + target.getName() + "\", and \""
                + selectedPersonForEdit.getName() + "\" will be deleted.")) return;
        try {
            loanService.mergeLoanPersons(selectedPersonForEdit.getId(), target.getId(), sessionContext.getCurrentUserId());
            resetPersonSelection();
            loadPersons();
            loadPersonSummaries();
        } catch (Exception e) {
            showAlert("Error", e.getMessage());
        }
    }

    @FXML
    public void handleDeletePerson() {
        if (selectedPersonForEdit == null) return;
        if (!confirm("Delete this loan account?")) return;
        try {
            loanService.deleteLoanPerson(selectedPersonForEdit.getId(), sessionContext.getCurrentUserId());
            resetPersonSelection();
            loadPersons();
            loadPersonSummaries();
        } catch (Exception e) {
            showAlert("Error", e.getMessage());
        }
    }

    @FXML
    public void handleCancelPersonSelection() {
        resetPersonSelection();
    }

    private void resetLoanForm() {
        personComboBox.setValue(null);
        lentRadio.setSelected(true);
        accountComboBox.setValue(null);
        transferTypeComboBox.setValue(null);
        categoryComboBox.setValue(null);
        amountField.clear();
        datePicker.setValue(LocalDate.now());
        dueDatePicker.setValue(null);
        noteArea.clear();
    }

    private void resetSelection() {
        selectedLoan = null;
        loansTable.getSelectionModel().clearSelection();
        addLoanBox.setVisible(true);
        addLoanBox.setManaged(true);
        repaymentBox.setVisible(false);
        repaymentBox.setManaged(false);
        settledSelectionBox.setVisible(false);
        settledSelectionBox.setManaged(false);
    }

    private void resetPersonForm() {
        personNameField.clear();
        personNoteArea.clear();
    }

    private void resetPersonSelection() {
        selectedPersonForEdit = null;
        personsTable.getSelectionModel().clearSelection();
        resetPersonForm();
        addPersonBtn.setVisible(true);
        addPersonBtn.setManaged(true);
        personEditButtons.setVisible(false);
        personEditButtons.setManaged(false);
    }

    private boolean confirm(String msg) {
        Alert a = new com.finance.util.AppAlert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Confirm"); a.setHeaderText(null);
        setWrappedContent(a, msg);
        Optional<ButtonType> r = a.showAndWait(); return r.isPresent() && r.get() == ButtonType.OK;
    }

    private void showAlert(String title, String msg) {
        Alert a = new com.finance.util.AppAlert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null);
        setWrappedContent(a, msg);
        a.showAndWait();
    }

    /** Wrapped Label so long messages are not clipped. */
    private void setWrappedContent(Alert alert, String msg) {
        Label label = new Label(msg);
        label.setWrapText(true);
        label.setMaxWidth(380);
        VBox content = new VBox(label);
        content.setPadding(new Insets(10));
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setMinWidth(440);
    }
}
