package com.finance.controller;

import com.finance.context.SessionContext;
import com.finance.entity.Account;
import com.finance.entity.AccountType;
import com.finance.entity.Category;
import com.finance.entity.Loan;
import com.finance.entity.MonthlyExpenseOverride;
import com.finance.entity.Transaction;
import com.finance.service.AccountService;
import com.finance.service.AccountTypeService;
import com.finance.service.CategoryService;
import com.finance.service.LoanService;
import com.finance.service.MonthlyExpenseOverrideService;
import com.finance.service.TransactionService;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class DashboardHomeController {
    private final SessionContext sessionContext;
    private final AccountService accountService;
    private final AccountTypeService accountTypeService;
    private final TransactionService transactionService;
    private final CategoryService categoryService;
    private final MonthlyExpenseOverrideService monthlyExpenseOverrideService;
    private final LoanService loanService;
    private final DashboardController dashboardController;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);
    private static final DateTimeFormatter FULL_MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    // Monthly Income & Expense card: fixed month column, value columns share the rest equally
    private static final double MONTH_COL_WIDTH = 50;
    private static final double VALUE_COL_WIDTH = 120;
    private static final String[] BADGE_PALETTE = {"badge-teal", "badge-gold", "badge-blue", "badge-purple", "badge-rose", "badge-mint"};

    @FXML private HBox loanReminderBanner;
    @FXML private Label loanReminderLabel;

    @FXML private HBox maturityReminderBanner;
    @FXML private Label maturityReminderLabel;

    @FXML private HBox lowBalanceReminderBanner;
    @FXML private Label lowBalanceReminderLabel;

    @FXML private HBox balanceCardsBox;


    @FXML private VBox recentTxBox;

    @FXML private StackPane expenseRingStack;
    @FXML private Label expenseRingLabel;
    @FXML private Label topExpenseLabel;
    @FXML private PieChart dashExpensePieChart;

    @FXML private VBox loansCard;
    @FXML private VBox loansBox;
    @FXML private HBox loansTotalBox;

    @FXML private VBox monthlyIncomeExpenseBox;
    @FXML private ComboBox<Integer> monthlyYearCombo;

    /** Year shown in the Monthly Income & Expense card - picked from monthlyYearCombo. */
    private int selectedYear;
    /** True while the year list is being refilled, so that doesn't count as the user picking a year. */
    private boolean updatingYearCombo;

    @FXML
    public void initialize() {
        selectedYear = LocalDate.now().getYear();
        monthlyYearCombo.setOnAction(e -> {
            if (updatingYearCombo || monthlyYearCombo.getValue() == null) return;
            selectedYear = monthlyYearCombo.getValue();
            refreshDashboard();
        });
        refreshDashboard();
        showLoanReminderToast();
        loanReminderBanner.setCursor(javafx.scene.Cursor.HAND);
        loanReminderBanner.setOnMouseClicked(e -> dashboardController.goToLoansTab());

        showMaturityReminderToast();
        maturityReminderBanner.setCursor(javafx.scene.Cursor.HAND);
        maturityReminderBanner.setOnMouseClicked(e -> dashboardController.goToAccountsTab());

        showLowBalanceToast();
        lowBalanceReminderBanner.setCursor(javafx.scene.Cursor.HAND);
        lowBalanceReminderBanner.setOnMouseClicked(e -> dashboardController.goToAccountsTab());

        loansCard.setCursor(javafx.scene.Cursor.HAND);
        loansCard.setOnMouseClicked(e -> dashboardController.goToLoansTab());
    }

    private void showLoanReminderToast() {
        List<Loan> dueLoans = loanService.getDueSoonOrOverdueLoans(sessionContext.getCurrentUserId(), 3);
        if (dueLoans.isEmpty()) {
            loanReminderBanner.setVisible(false);
            loanReminderBanner.setManaged(false);
            return;
        }

        LocalDate today = LocalDate.now();
        Loan first = dueLoans.get(0);
        long daysDiff = ChronoUnit.DAYS.between(today, first.getDueDate());
        String status = daysDiff < 0 ? "overdue by " + (-daysDiff) + " day(s)"
                : daysDiff == 0 ? "due today"
                : "due in " + daysDiff + " day(s)";
        String message = dueLoans.size() == 1
                ? "Loan with " + first.getPersonName() + " is " + status + " — check the Loans tab."
                : dueLoans.size() + " loans are due soon or overdue — check the Loans tab.";
        loanReminderLabel.setText(message);
        loanReminderBanner.setVisible(true);
        loanReminderBanner.setManaged(true);

        PauseTransition pause = new PauseTransition(Duration.seconds(6));
        pause.setOnFinished(e -> {
            loanReminderBanner.setVisible(false);
            loanReminderBanner.setManaged(false);
        });
        pause.play();
    }

    private void showMaturityReminderToast() {
        List<Account> maturingAccounts = accountService.getUpcomingMaturities(sessionContext.getCurrentUserId(), 3);
        if (maturingAccounts.isEmpty()) {
            maturityReminderBanner.setVisible(false);
            maturityReminderBanner.setManaged(false);
            return;
        }

        LocalDate today = LocalDate.now();
        Account first = maturingAccounts.get(0);
        long daysDiff = ChronoUnit.DAYS.between(today, first.getMaturityDate());
        String status = daysDiff < 0 ? "matured " + (-daysDiff) + " day(s) ago"
                : daysDiff == 0 ? "matures today"
                : "matures in " + daysDiff + " day(s)";
        String message = maturingAccounts.size() == 1
                ? first.getName() + " " + status + " — check the Accounts tab."
                : maturingAccounts.size() + " accounts are maturing soon or overdue — check the Accounts tab.";
        maturityReminderLabel.setText(message);
        maturityReminderBanner.setVisible(true);
        maturityReminderBanner.setManaged(true);

        PauseTransition pause = new PauseTransition(Duration.seconds(6));
        pause.setOnFinished(e -> {
            maturityReminderBanner.setVisible(false);
            maturityReminderBanner.setManaged(false);
        });
        pause.play();
    }

    private void showLowBalanceToast() {
        List<Account> lowAccounts = accountService.getAccountsBelowThreshold(sessionContext.getCurrentUserId());
        if (lowAccounts.isEmpty()) {
            lowBalanceReminderBanner.setVisible(false);
            lowBalanceReminderBanner.setManaged(false);
            return;
        }

        Account first = lowAccounts.get(0);
        String message = lowAccounts.size() == 1
                ? first.getName() + " balance (৳ " + first.getBalance().toPlainString()
                        + ") is below your ৳ " + first.getLowBalanceThreshold().toPlainString() + " alert — check the Accounts tab."
                : lowAccounts.size() + " accounts are below their low-balance alert — check the Accounts tab.";
        lowBalanceReminderLabel.setText(message);
        lowBalanceReminderBanner.setVisible(true);
        lowBalanceReminderBanner.setManaged(true);

        PauseTransition pause = new PauseTransition(Duration.seconds(6));
        pause.setOnFinished(e -> {
            lowBalanceReminderBanner.setVisible(false);
            lowBalanceReminderBanner.setManaged(false);
        });
        pause.play();
    }

    public void refreshDashboard() {
        Long userId = sessionContext.getCurrentUserId();
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        List<Account> accounts = accountService.getAccountsByUserId(userId);

        buildBalanceCards(userId);
        buildLoans(userId);
        buildRecentTransactions(userId, accounts);
        buildExpenseRing(userId, accounts, monthStart, today);
        buildTopExpense(userId, accounts, monthStart, today);
        refreshYearCombo(userId);
        MonthlyFigures income = loadMonthlyIncome(userId, accounts, selectedYear);
        MonthlyFigures cash = loadMonthlyExpense(userId, accounts, "CASH", selectedYear);
        MonthlyFigures bank = loadMonthlyExpense(userId, accounts, "BANK", selectedYear);
        buildMonthlyIncomeExpenseTable(monthlyIncomeExpenseBox, income, cash, bank, userId, accounts, selectedYear);
    }

    /** Refills the year dropdown (current year + every year with saved figures) and keeps
     *  selectedYear selected. */
    private void refreshYearCombo(Long userId) {
        List<Integer> years = monthlyExpenseOverrideService.getAvailableYears(userId);
        if (!years.contains(selectedYear)) selectedYear = LocalDate.now().getYear();
        updatingYearCombo = true;
        try {
            monthlyYearCombo.getItems().setAll(years);
            monthlyYearCombo.setValue(selectedYear);
        } finally {
            updatingYearCombo = false;
        }
    }

    /** Each month's total Bank + Cash income of the given year (index 0 = Jan), the base for the
     *  expense %. Same rules as the expense figures: the current month of the current year is
     *  recalculated from its transactions and auto-saved unless its income was saved manually; every
     *  other month shows the income saved in monthly_expense_overrides (0 if none). */
    private MonthlyFigures loadMonthlyIncome(Long userId, List<Account> accounts, int year) {
        LocalDate today = LocalDate.now();
        int currentMonth = year == today.getYear() ? today.getMonthValue() : -1;
        Map<Integer, MonthlyExpenseOverride> records = monthlyExpenseOverrideService.getIncomeRecordsForYear(userId, year);

        MonthlyFigures income = new MonthlyFigures();
        for (int month = 1; month <= 12; month++) {
            MonthlyExpenseOverride existing = records.get(month);
            boolean isManual = existing != null && Boolean.TRUE.equals(existing.getIsIncomeManual());
            if (month == currentMonth && !isManual) {
                BigDecimal calculated = monthlyExpenseOverrideService.calculateIncome(userId, accounts, YearMonth.of(year, month));
                monthlyExpenseOverrideService.autoSaveIncome(userId, year, month, calculated);
                income.amounts[month - 1] = calculated;
            } else {
                income.amounts[month - 1] = existing != null ? existing.getIncomeAmount() : BigDecimal.ZERO;
            }
            income.manual[month - 1] = isManual;
        }
        return income;
    }

    /** 12 monthly figures of the current year (index 0 = Jan) and whether each was saved manually. */
    private static final class MonthlyFigures {
        final BigDecimal[] amounts = new BigDecimal[12];
        final boolean[] manual = new boolean[12];
    }

    private void buildBalanceCards(Long userId) {
        balanceCardsBox.getChildren().clear();
        List<AccountType> types = accountTypeService.getAccountTypesByUserId(userId);
        for (AccountType at : types) {
            BigDecimal balance = accountService.getBalanceByAccountType(userId, at);
            if (balance.compareTo(BigDecimal.ZERO) == 0) continue;

            Label badge = new Label(initials(at.getName()));
            badge.getStyleClass().addAll("dash-badge", paletteClass(at.getName()));

            Label typeLabel = new Label(at.getName());
            typeLabel.getStyleClass().add("dash-account-type");

            Label amtLabel = new Label(fmt(balance));
            amtLabel.getStyleClass().add("dash-account-amount");

            VBox textBox = new VBox(2, typeLabel, amtLabel);
            HBox card = new HBox(10, badge, textBox);
            card.setAlignment(Pos.CENTER_LEFT);
            card.getStyleClass().add("dash-account-card");
            card.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(card, Priority.ALWAYS);
            balanceCardsBox.getChildren().add(card);
        }
    }

    private void buildRecentTransactions(Long userId, List<Account> accounts) {
        Map<Long, Account> accountsById = new LinkedHashMap<>();
        for (Account a : accounts) accountsById.put(a.getId(), a);
        Map<Long, Category> categoriesById = new LinkedHashMap<>();
        for (Category c : categoryService.getCategoriesByUserId(userId)) categoriesById.put(c.getId(), c);

        List<Transaction> recent = transactionService.getTransactionsByUserId(userId);
        recentTxBox.getChildren().clear();

        if (recent.isEmpty()) {
            Label empty = new Label("No transactions yet");
            empty.getStyleClass().add("dash-section-hint");
            recentTxBox.getChildren().add(empty);
            return;
        }

        int limit = Math.min(3, recent.size());
        for (int i = 0; i < limit; i++) {
            Transaction tx = recent.get(i);
            recentTxBox.getChildren().add(buildTxRow(tx, accountsById, categoriesById));
        }
    }

    private HBox buildTxRow(Transaction tx, Map<Long, Account> accountsById, Map<Long, Category> categoriesById) {
        String glyph;
        String iconClass;
        String amountClass;
        String title;
        String amountText;

        if (tx.getType() == Transaction.TransactionType.INCOME) {
            glyph = "+"; iconClass = "dash-tx-icon-in"; amountClass = "dash-tx-amount-pos";
            Category c = categoriesById.get(tx.getCategoryId());
            title = c != null ? c.getName() : "Income";
            amountText = "+" + fmt(tx.getAmount());
        } else if (tx.getType() == Transaction.TransactionType.EXPENSE) {
            glyph = "−"; iconClass = "dash-tx-icon-out"; amountClass = "dash-tx-amount-neg";
            Category c = categoriesById.get(tx.getCategoryId());
            title = c != null ? c.getName() : "Expense";
            amountText = "-" + fmt(tx.getAmount());
        } else {
            glyph = "\u21C4"; iconClass = "dash-tx-icon-transfer"; amountClass = "dash-tx-amount-neutral";
            title = "Transfer";
            amountText = fmt(tx.getAmount());
        }

        Label icon = new Label(glyph);
        icon.getStyleClass().addAll("dash-tx-icon", iconClass);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dash-tx-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label amountLabel = new Label(amountText);
        amountLabel.getStyleClass().add(amountClass);

        HBox row = new HBox(10, icon, titleLabel, spacer, amountLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("dash-tx-row");
        return row;
    }

    /**
     * "Expense of this month's income" ring - scoped to Bank + Cash only (like the Net worth
     * card), not every account type. mInc is income received into Bank/Cash accounts this month.
     * mExp is EXPENSE transactions paid from Bank/Cash accounts this month (transfers excluded).
     */
    private void buildExpenseRing(Long userId, List<Account> accounts, LocalDate monthStart, LocalDate today) {
        List<Long> bankAndCashIds = new ArrayList<>();
        for (Account a : accounts) {
            String atName = a.getAccountType().getName();
            if ("BANK".equalsIgnoreCase(atName) || "CASH".equalsIgnoreCase(atName)) bankAndCashIds.add(a.getId());
        }
        BigDecimal mInc = transactionService.getIncomeByAccountIds(userId, bankAndCashIds, monthStart, today);
        BigDecimal mExp = transactionService.getEffectiveExpense(userId, accounts, "BANK", monthStart, today)
                .add(transactionService.getEffectiveExpense(userId, accounts, "CASH", monthStart, today));

        BigDecimal pctPrecise;
        if (mInc.compareTo(BigDecimal.ZERO) <= 0) {
            pctPrecise = mExp.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        } else {
            pctPrecise = mExp.divide(mInc, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            if (pctPrecise.compareTo(BigDecimal.valueOf(100)) > 0) pctPrecise = BigDecimal.valueOf(100);
            if (pctPrecise.compareTo(BigDecimal.ZERO) < 0) pctPrecise = BigDecimal.ZERO;
        }
        int pct = pctPrecise.setScale(0, RoundingMode.HALF_UP).intValue();
        expenseRingLabel.setText(pctPrecise.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%");

        // remove any previously drawn ring shapes (keep the centered label)
        expenseRingStack.getChildren().removeIf(n -> n instanceof Circle);

        double radius = 22;
        Circle track = new Circle(radius, radius, radius);
        track.getStyleClass().add("dash-ring-track");

        Circle progress = new Circle(radius, radius, radius);
        progress.getStyleClass().add("dash-ring-progress");
        progress.setStrokeLineCap(StrokeLineCap.ROUND);
        double circumference = 2 * Math.PI * radius;
        double dash = Math.max(0.01, circumference * pct / 100.0);
        progress.getStrokeDashArray().setAll(dash, circumference);
        progress.setRotate(-90);

        expenseRingStack.getChildren().add(0, progress);
        expenseRingStack.getChildren().add(0, track);
    }

    /** Scoped to Bank + Cash accounts only, matching Net worth and the expense ring. */
    private void buildTopExpense(Long userId, List<Account> accounts, LocalDate monthStart, LocalDate today) {
        List<Long> bankAndCashIds = new ArrayList<>();
        for (Account a : accounts) {
            String atName = a.getAccountType().getName();
            if ("BANK".equalsIgnoreCase(atName) || "CASH".equalsIgnoreCase(atName)) bankAndCashIds.add(a.getId());
        }

        List<Category> expenseCategories = categoryService.getExpenseCategories(userId);
        Category topCategory = null;
        BigDecimal topAmount = BigDecimal.ZERO;
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        for (Category c : expenseCategories) {
            BigDecimal amount = transactionService.getExpenseByCategoryForAccounts(userId, c.getId(), bankAndCashIds, monthStart, today);
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                pieData.add(new PieChart.Data(c.getName(), amount.doubleValue()));
            }
            if (amount.compareTo(topAmount) > 0) { topAmount = amount; topCategory = c; }
        }
        dashExpensePieChart.setData(pieData);

        for (PieChart.Data slice : pieData) {
            Tooltip tooltip = new Tooltip(slice.getName() + "\n" + fmt(BigDecimal.valueOf(slice.getPieValue())));
            Tooltip.install(slice.getNode(), tooltip);
        }

        if (topCategory == null) {
            topExpenseLabel.setText("No expense this month");
        } else {
            topExpenseLabel.setText(topCategory.getName() + " — " + fmt(topAmount));
        }
    }

    /** "Given" (people who owe the user, LENT) and "Taken" (people the user owes, BORROWED)
     *  sections, one line per person with a non-zero outstanding amount, largest first. The two
     *  sides are not netted against each other. The whole card is hidden when both are empty. */
    private void buildLoans(Long userId) {
        List<LoanService.PersonSummary> summaries = loanService.getPersonSummaries(userId);
        List<LoanService.PersonSummary> given = new ArrayList<>();
        List<LoanService.PersonSummary> taken = new ArrayList<>();
        for (LoanService.PersonSummary ps : summaries) {
            if (ps.getLentRemaining().compareTo(BigDecimal.ZERO) > 0) given.add(ps);
            if (ps.getBorrowedRemaining().compareTo(BigDecimal.ZERO) > 0) taken.add(ps);
        }
        given.sort((a, b) -> b.getLentRemaining().compareTo(a.getLentRemaining()));
        taken.sort((a, b) -> b.getBorrowedRemaining().compareTo(a.getBorrowedRemaining()));

        loansBox.getChildren().clear();
        loansTotalBox.getChildren().clear();
        loansCard.setVisible(!given.isEmpty() || !taken.isEmpty());
        if (!loansCard.isVisible()) return;

        // totals sit next to the card heading: Given in green, Taken in red
        if (!given.isEmpty()) {
            BigDecimal total = BigDecimal.ZERO;
            for (LoanService.PersonSummary ps : given) total = total.add(ps.getLentRemaining());
            Label givenTotal = new Label(fmt(total));
            givenTotal.getStyleClass().add("dash-tx-amount-pos");
            Tooltip.install(givenTotal, new Tooltip("Total given - still to receive"));
            loansTotalBox.getChildren().add(givenTotal);
        }
        if (!taken.isEmpty()) {
            BigDecimal total = BigDecimal.ZERO;
            for (LoanService.PersonSummary ps : taken) total = total.add(ps.getBorrowedRemaining());
            if (!given.isEmpty()) loansTotalBox.getChildren().add(new Label("/"));
            Label takenTotal = new Label(fmt(total));
            takenTotal.getStyleClass().add("dash-tx-amount-neg");
            Tooltip.install(takenTotal, new Tooltip("Total taken - still to pay back"));
            loansTotalBox.getChildren().add(takenTotal);
        }

        if (!given.isEmpty()) {
            for (LoanService.PersonSummary ps : given) {
                BigDecimal repaid = ps.getTotalLent().subtract(ps.getLentRemaining());
                loansBox.getChildren().add(buildLoanPersonRow(ps.getPersonName(), ps.getLentRemaining(),
                        "dash-tx-amount-pos", "Given " + fmt(ps.getTotalLent()) + " · Repaid " + fmt(repaid)));
            }
        }
        if (!taken.isEmpty()) {
            for (LoanService.PersonSummary ps : taken) {
                BigDecimal repaid = ps.getTotalBorrowed().subtract(ps.getBorrowedRemaining());
                loansBox.getChildren().add(buildLoanPersonRow(ps.getPersonName(), ps.getBorrowedRemaining(),
                        "dash-tx-amount-neg", "Taken " + fmt(ps.getTotalBorrowed()) + " · Repaid " + fmt(repaid)));
            }
        }
    }

    /** One line per person; the given/taken/repaid breakdown is kept in a tooltip to save height. */
    private HBox buildLoanPersonRow(String name, BigDecimal remaining, String amountClass, String detail) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("dash-tx-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label amountLabel = new Label(fmt(remaining));
        amountLabel.getStyleClass().add(amountClass);
        HBox row = new HBox(6, nameLabel, spacer, amountLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("dash-tx-row");
        Tooltip.install(row, new Tooltip(detail));
        return row;
    }

    /**
     * Each month's expense paid from accounts of the given type ("CASH" or "BANK") in the given
     * year (index 0 = Jan), read from the {@code monthly_expense_overrides} table.
     * <p>
     * Behavior:
     * - The CURRENT month (only when the current year is shown) is recalculated live from EXPENSE transactions (paid from an account of
     *   this type) every time the dashboard loads, and that figure is auto-saved into the table -
     *   unless the user has manually corrected this month's figure, in which case the manual
     *   value wins and is left untouched.
     * - Every OTHER month (past or future) is never recalculated - it simply shows whatever is
     *   saved in the table (0 if nothing was ever saved for it). This is what makes a month
     *   "freeze" once it's no longer current: only a manual ✎ edit can change it after that.
     */
    private MonthlyFigures loadMonthlyExpense(Long userId, List<Account> accounts, String typeName, int year) {
        LocalDate today = LocalDate.now();
        int currentMonth = year == today.getYear() ? today.getMonthValue() : -1;
        Map<Integer, MonthlyExpenseOverride> records = monthlyExpenseOverrideService.getRecordsForYear(userId, typeName, year);

        MonthlyFigures expense = new MonthlyFigures();
        for (int month = 1; month <= 12; month++) {
            YearMonth ym = YearMonth.of(year, month);
            MonthlyExpenseOverride existing = records.get(month);
            boolean isManual = existing != null && Boolean.TRUE.equals(existing.getIsManual());
            if (month == currentMonth && !isManual) {
                BigDecimal calculated = transactionService.getEffectiveExpense(userId, accounts, typeName, ym.atDay(1), ym.atEndOfMonth());
                monthlyExpenseOverrideService.autoSaveCurrentMonth(userId, typeName, year, month, calculated);
                expense.amounts[month - 1] = calculated;
            } else {
                expense.amounts[month - 1] = existing != null ? existing.getAmount() : BigDecimal.ZERO;
            }
            expense.manual[month - 1] = isManual;
        }
        return expense;
    }

    /**
     * The Monthly Income & Expense card: one row per month of the current year with the columns
     * Month | Income | Cash Expense | Bank Expense | Cash + Bank Expense | %, then Total and AVG.
     * Income, Cash and Bank each have their own ↻ (recalculate from transactions - auto values only)
     * and ✎ (save manually). Cash + Bank is the sum of the shown Cash and Bank figures, and
     * % = (Cash + Bank) ÷ Income × 100.
     */
    private void buildMonthlyIncomeExpenseTable(VBox container, MonthlyFigures income, MonthlyFigures cash,
                                                MonthlyFigures bank, Long userId, List<Account> accounts, int year) {
        container.getChildren().clear();
        container.getChildren().add(buildMonthlyColumnsRow(
                monthCell("Month", "dash-table-head"),
                cell(styledLabel("Income (Bank + Cash)", "dash-table-head"), true),
                cell(styledLabel("Cash Expense", "dash-table-head"), true),
                cell(styledLabel("Bank Expense", "dash-table-head"), true),
                cell(styledLabel("Cash + Bank Expense", "dash-table-head"), false),
                cell(styledLabel("%", "dash-table-head"), false),
                "dash-monthly-row"));

        BigDecimal[] combined = new BigDecimal[12];
        for (int month = 1; month <= 12; month++) {
            YearMonth ym = YearMonth.of(year, month);
            int i = month - 1;
            BigDecimal monthIncome = income.amounts[i];
            BigDecimal cashAmount = cash.amounts[i];
            BigDecimal bankAmount = bank.amounts[i];
            combined[i] = cashAmount.add(bankAmount);

            container.getChildren().add(buildMonthlyColumnsRow(
                    monthCell(ym.format(MONTH_FMT).toUpperCase(Locale.ENGLISH), "dash-card-label"),
                    editableCell(monthIncome, income.manual[i], "total income",
                            () -> handleRecalculateIncome(ym, monthIncome, userId, accounts),
                            () -> handleEditMonthlyIncome(ym, monthIncome, userId)),
                    editableCell(cashAmount, cash.manual[i], "CASH expense",
                            () -> handleRecalculateMonth(ym, "CASH", cashAmount, userId, accounts),
                            () -> handleEditMonthlyExpense(ym, "CASH", cashAmount, userId)),
                    editableCell(bankAmount, bank.manual[i], "BANK expense",
                            () -> handleRecalculateMonth(ym, "BANK", bankAmount, userId, accounts),
                            () -> handleEditMonthlyExpense(ym, "BANK", bankAmount, userId)),
                    cell(styledLabel(fmt(combined[i]), "dash-card-label"), false),
                    cell(buildPctLabel(combined[i], monthIncome), false),
                    "dash-monthly-row"));
        }

        BigDecimal totalIncome = sum(income.amounts);
        BigDecimal totalCash = sum(cash.amounts);
        BigDecimal totalBank = sum(bank.amounts);
        BigDecimal totalCombined = sum(combined);
        container.getChildren().add(buildMonthlySummaryRow("Total", totalIncome, totalCash, totalBank, totalCombined,
                buildPctLabel(totalCombined, totalIncome), "dash-monthly-row-total"));
        container.getChildren().add(buildMonthlySummaryRow("AVG", avg(totalIncome), avg(totalCash), avg(totalBank),
                avg(totalCombined), null, "dash-monthly-row-avg"));
    }

    /** Total / AVG row. pctLabel is null for AVG (its % would just repeat Total's). */
    private HBox buildMonthlySummaryRow(String label, BigDecimal income, BigDecimal cash, BigDecimal bank,
                                        BigDecimal combined, Label pctLabel, String rowStyleClass) {
        return buildMonthlyColumnsRow(
                monthCell(label, "dash-card-title"),
                cell(styledLabel(fmt(income), "dash-card-title"), true),
                cell(styledLabel(fmt(cash), "dash-card-title"), true),
                cell(styledLabel(fmt(bank), "dash-card-title"), true),
                cell(styledLabel(fmt(combined), "dash-card-title"), false),
                cell(pctLabel != null ? pctLabel : new Label(""), false),
                rowStyleClass);
    }

    /** One line of the card. The five value cells share the same preferred width and all grow
     *  equally, so header, month and Total/AVG rows line up column by column. */
    private HBox buildMonthlyColumnsRow(Label month, HBox income, HBox cash, HBox bank, HBox combined, HBox pct,
                                        String rowStyleClass) {
        HBox row = new HBox(12, month, income, cash, bank, combined, pct);
        row.getStyleClass().add(rowStyleClass);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** An amount with its ↻ and ✎ buttons. ↻ only for auto-calculated figures; a manually saved one
     *  keeps an invisible stand-in so the ✎ column stays aligned. */
    private HBox editableCell(BigDecimal amount, boolean manual, String what, Runnable onRecalculate, Runnable onEdit) {
        Label valueLabel = styledLabel(fmt(amount), manual ? "dash-monthly-value-saved" : "dash-card-label");
        if (manual) {
            Tooltip.install(valueLabel, new Tooltip("Manually saved value — overrides the calculated total"));
        }

        Button recalcBtn = new Button("↻");
        recalcBtn.getStyleClass().add("dash-monthly-edit-btn");
        if (manual) {
            recalcBtn.setVisible(false);
        } else {
            Tooltip.install(recalcBtn, new Tooltip("Recalculate this month's " + what + " from its transactions"));
            recalcBtn.setOnAction(e -> onRecalculate.run());
        }

        Button editBtn = new Button("✎");
        editBtn.getStyleClass().add("dash-monthly-edit-btn");
        Tooltip.install(editBtn, new Tooltip("Manually save this month's actual " + what));
        editBtn.setOnAction(e -> onEdit.run());

        return cell(valueLabel, recalcBtn, editBtn);
    }

    /** A right-aligned value cell; buttonSpace adds invisible ↻ ✎ stand-ins so it lines up with
     *  the editable cells of the same column. */
    private HBox cell(Label label, boolean buttonSpace) {
        return buttonSpace ? cell(label, hiddenButton("↻"), hiddenButton("✎")) : cell(label, null, null);
    }

    private HBox cell(Label label, Button recalcBtn, Button editBtn) {
        label.setMaxWidth(Double.MAX_VALUE);
        label.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(label, Priority.ALWAYS);
        HBox cell = new HBox(4, label);
        if (recalcBtn != null) cell.getChildren().addAll(recalcBtn, editBtn);
        cell.setAlignment(Pos.CENTER_RIGHT);
        cell.setPrefWidth(VALUE_COL_WIDTH);
        cell.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(cell, Priority.ALWAYS);
        return cell;
    }

    private Label monthCell(String text, String styleClass) {
        Label label = styledLabel(text, styleClass);
        label.setMinWidth(Region.USE_PREF_SIZE);
        label.setPrefWidth(MONTH_COL_WIDTH);
        return label;
    }

    private Label styledLabel(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    /** Invisible stand-in that keeps a ↻/✎ button's width so columns stay aligned. */
    private Button hiddenButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("dash-monthly-edit-btn");
        button.setVisible(false);
        return button;
    }

    /** Shows old vs recalculated income and saves only if the user confirms. */
    private void handleRecalculateIncome(YearMonth ym, BigDecimal currentAmount, Long userId, List<Account> accounts) {
        BigDecimal recalculated = monthlyExpenseOverrideService.calculateIncome(userId, accounts, ym);
        if (recalculated.compareTo(currentAmount) == 0) {
            Alert info = new Alert(Alert.AlertType.INFORMATION,
                    ym.format(FULL_MONTH_FMT) + " — income is already up to date (" + fmt(currentAmount) + ").");
            info.setTitle("Recalculate");
            info.setHeaderText(null);
            info.showAndWait();
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                ym.format(FULL_MONTH_FMT) + " — total income\n\n"
                        + "Saved now:      " + fmt(currentAmount) + "\n"
                        + "Recalculated:  " + fmt(recalculated) + "\n\nUpdate to the recalculated figure?");
        confirm.setTitle("Recalculate");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            monthlyExpenseOverrideService.recalculateIncome(userId, accounts, ym.getYear(), ym.getMonthValue());
            refreshDashboard();
        });
    }

    private void handleEditMonthlyIncome(YearMonth ym, BigDecimal currentAmount, Long userId) {
        TextInputDialog dialog = new TextInputDialog(currentAmount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        dialog.setTitle("Save month's actual income");
        dialog.setHeaderText(null);
        dialog.setContentText(ym.format(FULL_MONTH_FMT) + " — total income (৳):");
        dialog.showAndWait().ifPresent(input -> {
            BigDecimal parsed;
            try {
                parsed = new BigDecimal(input.trim());
            } catch (NumberFormatException ex) {
                showValidationError("Please enter a valid amount (e.g. 85000 or 85000.00).");
                return;
            }
            if (parsed.compareTo(BigDecimal.ZERO) < 0) {
                showValidationError("Amount cannot be negative.");
                return;
            }
            monthlyExpenseOverrideService.saveManualIncome(userId, ym.getYear(), ym.getMonthValue(), parsed);
            refreshDashboard();
        });
    }

    /** Shows old vs recalculated figure and saves only if the user confirms. */
    private void handleRecalculateMonth(YearMonth ym, String typeName, BigDecimal currentAmount, Long userId, List<Account> accounts) {
        BigDecimal recalculated = transactionService.getEffectiveExpense(userId, accounts, typeName, ym.atDay(1), ym.atEndOfMonth());
        if (recalculated.compareTo(currentAmount) == 0) {
            Alert info = new Alert(Alert.AlertType.INFORMATION,
                    ym.format(FULL_MONTH_FMT) + " — " + typeName + " is already up to date (" + fmt(currentAmount) + ").");
            info.setTitle("Recalculate");
            info.setHeaderText(null);
            info.showAndWait();
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                ym.format(FULL_MONTH_FMT) + " — " + typeName + " expense\n\n"
                        + "Saved now:      " + fmt(currentAmount) + "\n"
                        + "Recalculated:  " + fmt(recalculated) + "\n\nUpdate to the recalculated figure?");
        confirm.setTitle("Recalculate");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            monthlyExpenseOverrideService.recalculateMonth(userId, accounts, typeName, ym.getYear(), ym.getMonthValue());
            refreshDashboard();
        });
    }

    private void handleEditMonthlyExpense(YearMonth ym, String typeName, BigDecimal currentAmount, Long userId) {
        TextInputDialog dialog = new TextInputDialog(currentAmount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        dialog.setTitle("Save month's actual expense");
        dialog.setHeaderText(null);
        dialog.setContentText(ym.format(FULL_MONTH_FMT) + " — " + typeName + " expense (৳):");
        dialog.showAndWait().ifPresent(input -> {
            BigDecimal parsed;
            try {
                parsed = new BigDecimal(input.trim());
            } catch (NumberFormatException ex) {
                showValidationError("Please enter a valid amount (e.g. 47780 or 47780.00).");
                return;
            }
            if (parsed.compareTo(BigDecimal.ZERO) < 0) {
                showValidationError("Amount cannot be negative.");
                return;
            }
            monthlyExpenseOverrideService.saveManualOverride(userId, typeName, ym.getYear(), ym.getMonthValue(), parsed);
            refreshDashboard();
        });
    }

    private void showValidationError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setTitle("Invalid amount");
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    /** Expense as % of income, e.g. "57.66%" or "119.00%" (not capped at 100). "-" when there was
     *  no income, since a % of zero is meaningless. Shown in red once expense exceeds income. */
    private Label buildPctLabel(BigDecimal expense, BigDecimal income) {
        boolean hasIncome = income != null && income.compareTo(BigDecimal.ZERO) > 0;
        String text = hasIncome
                ? expense.multiply(BigDecimal.valueOf(100)).divide(income, 2, RoundingMode.HALF_UP).toPlainString() + "%"
                : "-";
        Label label = styledLabel(text, "dash-monthly-pct");
        if (hasIncome && expense.compareTo(income) > 0) label.getStyleClass().add("dash-monthly-pct-over");
        return label;
    }

    private BigDecimal sum(BigDecimal[] values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : values) total = total.add(v);
        return total;
    }

    /** 12-month average of a yearly total. */
    private BigDecimal avg(BigDecimal yearTotal) {
        return yearTotal.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    private String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String trimmed = name.trim();
        return trimmed.substring(0, 1).toUpperCase(Locale.ENGLISH);
    }

    private String paletteClass(String name) {
        int idx = Math.abs((name == null ? "" : name).hashCode()) % BADGE_PALETTE.length;
        return BADGE_PALETTE[idx];
    }

    private String fmt(BigDecimal v) { return String.format("৳ %.2f", v); }
}
