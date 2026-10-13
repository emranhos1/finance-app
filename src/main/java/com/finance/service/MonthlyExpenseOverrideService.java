package com.finance.service;

import com.finance.entity.Account;
import com.finance.entity.MonthlyExpenseOverride;
import com.finance.entity.User;
import com.finance.repository.MonthlyExpenseOverrideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MonthlyExpenseOverrideService {
    private static final String[] TRACKED_ACCOUNT_TYPES = {"CASH", "BANK"};

    private final MonthlyExpenseOverrideRepository monthlyExpenseOverrideRepository;
    private final UserService userService;
    private final AccountService accountService;
    private final TransactionService transactionService;

    public Map<Integer, MonthlyExpenseOverride> getRecordsForYear(Long userId, String accountTypeName, int year) {
        Map<Integer, MonthlyExpenseOverride> byMonth = new LinkedHashMap<>();
        for (MonthlyExpenseOverride o : monthlyExpenseOverrideRepository
                .findByUserIdAndAccountTypeNameAndExpenseYear(userId, accountTypeName, year)) {
            byMonth.put(o.getExpenseMonth(), o);
        }
        return byMonth;
    }

    /** Years to offer in the dashboard's year dropdown: the current year plus every year this user
     *  has saved monthly figures for, newest first. */
    public List<Integer> getAvailableYears(Long userId) {
        TreeSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        years.add(LocalDate.now().getYear());
        years.addAll(monthlyExpenseOverrideRepository.findDistinctYearsByUserId(userId));
        return new ArrayList<>(years);
    }

    /** User-entered figure (via the ✎ edit button). Always wins - the daily auto-recalculation
     *  of the current month will never overwrite a value saved through this method. */
    public void saveManualOverride(Long userId, String accountTypeName, int year, int month, BigDecimal amount) {
        MonthlyExpenseOverride record = findOrNew(userId, accountTypeName, year, month);
        record.setAmount(amount);
        record.setIsManual(true);
        monthlyExpenseOverrideRepository.save(record);
    }

    /** Keeps the current month's row in sync with the live transaction total. No-op if the user
     *  has already manually corrected this month's figure. Past months are never touched here -
     *  once a month is no longer the current month, its saved row is left as-is (frozen). */
    public void autoSaveCurrentMonth(Long userId, String accountTypeName, int year, int month, BigDecimal calculatedAmount) {
        MonthlyExpenseOverride existing = monthlyExpenseOverrideRepository
                .findByUserIdAndAccountTypeNameAndExpenseYearAndExpenseMonth(userId, accountTypeName, year, month)
                .orElse(null);
        if (existing != null && Boolean.TRUE.equals(existing.getIsManual())) return;

        MonthlyExpenseOverride record = existing != null ? existing : findOrNew(userId, accountTypeName, year, month);
        record.setAmount(calculatedAmount);
        record.setIsManual(false);
        monthlyExpenseOverrideRepository.save(record);
    }

    /** The ↻ button on the dashboard: recalculates one (usually past, frozen) month from its
     *  transactions with the current rules and saves it as an auto value. Returns the new figure.
     *  Does nothing to a month the user saved manually (the button isn't shown for those). */
    public BigDecimal recalculateMonth(Long userId, List<Account> accounts, String accountTypeName, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        BigDecimal amount = transactionService.getEffectiveExpense(userId, accounts, accountTypeName, ym.atDay(1), ym.atEndOfMonth());
        autoSaveCurrentMonth(userId, accountTypeName, year, month, amount);
        return amount;
    }

    /**
     * One-time, idempotent backfill: for every user and every tracked account type (CASH, BANK),
     * computes each past month's expense total from existing transaction history and saves it as
     * an auto (non-manual) record - but only for months that don't already have a saved row, so
     * it never touches a month the live auto-save or a manual ✎ edit has already produced.
     * Safe to call on every app startup; after the first run it's a no-op.
     */
    public void backfillHistoricalMonths() {
        for (User user : userService.getAllUsers()) {
            LocalDate earliest = transactionService.getEarliestTransactionDate(user.getId());
            if (earliest == null) continue;

            List<Account> accounts = accountService.getAccountsByUserId(user.getId());
            YearMonth start = YearMonth.from(earliest);
            YearMonth end = YearMonth.now();

            for (String typeName : TRACKED_ACCOUNT_TYPES) {
                List<Long> accountIds = new ArrayList<>();
                for (Account a : accounts) {
                    if (typeName.equalsIgnoreCase(a.getAccountType().getName())) accountIds.add(a.getId());
                }
                if (accountIds.isEmpty()) continue;

                for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
                    boolean exists = monthlyExpenseOverrideRepository
                            .findByUserIdAndAccountTypeNameAndExpenseYearAndExpenseMonth(
                                    user.getId(), typeName, ym.getYear(), ym.getMonthValue())
                            .isPresent();
                    if (exists) continue;

                    BigDecimal amount = transactionService.getEffectiveExpense(
                            user.getId(), accounts, typeName, ym.atDay(1), ym.atEndOfMonth());

                    MonthlyExpenseOverride record = MonthlyExpenseOverride.builder()
                            .userId(user.getId())
                            .accountTypeName(typeName)
                            .expenseYear(ym.getYear())
                            .expenseMonth(ym.getMonthValue())
                            .amount(amount)
                            .isManual(false)
                            .build();
                    monthlyExpenseOverrideRepository.save(record);
                }
            }
        }
        log.info("Monthly expense backfill check completed");
    }

    /** A month's total income received into Bank + Cash accounts, from its transactions (loans
     *  excluded) - the same income the dashboard's expense ring uses. */
    public BigDecimal calculateIncome(Long userId, List<Account> accounts, YearMonth ym) {
        List<Long> bankAndCashIds = new ArrayList<>();
        for (Account a : accounts) {
            for (String typeName : TRACKED_ACCOUNT_TYPES) {
                if (typeName.equalsIgnoreCase(a.getAccountType().getName())) bankAndCashIds.add(a.getId());
            }
        }
        return transactionService.getIncomeByAccountIds(userId, bankAndCashIds, ym.atDay(1), ym.atEndOfMonth());
    }

    /** Month -> a row holding that month's income. The CASH and BANK rows of a month carry the same
     *  income, so whichever has it filled is returned. Months with no income saved are absent. */
    public Map<Integer, MonthlyExpenseOverride> getIncomeRecordsForYear(Long userId, int year) {
        Map<Integer, MonthlyExpenseOverride> byMonth = new LinkedHashMap<>();
        for (String typeName : TRACKED_ACCOUNT_TYPES) {
            for (MonthlyExpenseOverride o : monthlyExpenseOverrideRepository
                    .findByUserIdAndAccountTypeNameAndExpenseYear(userId, typeName, year)) {
                if (o.getIncomeAmount() != null) byMonth.putIfAbsent(o.getExpenseMonth(), o);
            }
        }
        return byMonth;
    }

    /** User-entered income (via the income ✎ button). Like saveManualOverride, it always wins over
     *  the auto-calculation. */
    public void saveManualIncome(Long userId, int year, int month, BigDecimal amount) {
        writeIncome(userId, year, month, amount, true);
    }

    /** Income counterpart of autoSaveCurrentMonth: keeps the month's income in sync with its
     *  transactions unless the user has manually saved that month's income. */
    public void autoSaveIncome(Long userId, int year, int month, BigDecimal calculatedAmount) {
        for (String typeName : TRACKED_ACCOUNT_TYPES) {
            boolean manual = monthlyExpenseOverrideRepository
                    .findByUserIdAndAccountTypeNameAndExpenseYearAndExpenseMonth(userId, typeName, year, month)
                    .map(o -> Boolean.TRUE.equals(o.getIsIncomeManual()))
                    .orElse(false);
            if (manual) return;
        }
        writeIncome(userId, year, month, calculatedAmount, false);
    }

    /** The income ↻ button: recalculates one month's income from its transactions and saves it as an
     *  auto value. Returns the new figure. */
    public BigDecimal recalculateIncome(Long userId, List<Account> accounts, int year, int month) {
        BigDecimal amount = calculateIncome(userId, accounts, YearMonth.of(year, month));
        autoSaveIncome(userId, year, month, amount);
        return amount;
    }

    /**
     * Idempotent backfill: fills income_amount on every row that doesn't have it yet, from that
     * month's transactions, as an auto (non-manual) value. Rows that already have an income (auto
     * or manual) are never touched, so after the first run it only fills newly created rows.
     */
    public void backfillIncome() {
        Map<Long, List<Account>> accountsByUser = new LinkedHashMap<>();
        for (MonthlyExpenseOverride row : monthlyExpenseOverrideRepository.findByIncomeAmountIsNull()) {
            List<Account> accounts = accountsByUser.computeIfAbsent(row.getUserId(), accountService::getAccountsByUserId);
            row.setIncomeAmount(calculateIncome(row.getUserId(), accounts,
                    YearMonth.of(row.getExpenseYear(), row.getExpenseMonth())));
            row.setIsIncomeManual(false);
            monthlyExpenseOverrideRepository.save(row);
        }
        log.info("Monthly income backfill check completed");
    }

    /** Writes the same income onto both the CASH and BANK rows of the month, creating a row (with
     *  a 0 expense, which is what a missing row already shows) if that month has none yet. */
    private void writeIncome(Long userId, int year, int month, BigDecimal amount, boolean manual) {
        for (String typeName : TRACKED_ACCOUNT_TYPES) {
            MonthlyExpenseOverride record = findOrNew(userId, typeName, year, month);
            if (record.getAmount() == null) record.setAmount(BigDecimal.ZERO);
            record.setIncomeAmount(amount);
            record.setIsIncomeManual(manual);
            monthlyExpenseOverrideRepository.save(record);
        }
    }

    private MonthlyExpenseOverride findOrNew(Long userId, String accountTypeName, int year, int month) {
        return monthlyExpenseOverrideRepository
                .findByUserIdAndAccountTypeNameAndExpenseYearAndExpenseMonth(userId, accountTypeName, year, month)
                .orElseGet(() -> MonthlyExpenseOverride.builder()
                        .userId(userId)
                        .accountTypeName(accountTypeName)
                        .expenseYear(year)
                        .expenseMonth(month)
                        .build());
    }
}