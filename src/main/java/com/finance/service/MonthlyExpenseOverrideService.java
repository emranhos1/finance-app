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

    /** Current year plus every year with saved figures, newest first. */
    public List<Integer> getAvailableYears(Long userId) {
        TreeSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        years.add(LocalDate.now().getYear());
        years.addAll(monthlyExpenseOverrideRepository.findDistinctYearsByUserId(userId));
        return new ArrayList<>(years);
    }

    /** Manual value; always wins over the daily auto-recalculation. */
    public void saveManualOverride(Long userId, String accountTypeName, int year, int month, BigDecimal amount) {
        MonthlyExpenseOverride record = findOrNew(userId, accountTypeName, year, month);
        record.setAmount(amount);
        record.setIsManual(true);
        monthlyExpenseOverrideRepository.save(record);
    }

    /** Syncs the current month with live totals; skips manual rows. Past months stay frozen. */
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

    /** Recalculates one past month and saves it as auto; not for manual months. */
    public BigDecimal recalculateMonth(Long userId, List<Account> accounts, String accountTypeName, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        BigDecimal amount = transactionService.getEffectiveExpense(userId, accounts, accountTypeName, ym.atDay(1), ym.atEndOfMonth());
        autoSaveCurrentMonth(userId, accountTypeName, year, month, amount);
        return amount;
    }

    /** Startup backfill of past months' expenses; only fills months with no saved row. */
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

    /** Month income into Bank + Cash, loans excluded. */
    public BigDecimal calculateIncome(Long userId, List<Account> accounts, YearMonth ym) {
        List<Long> bankAndCashIds = new ArrayList<>();
        for (Account a : accounts) {
            for (String typeName : TRACKED_ACCOUNT_TYPES) {
                if (typeName.equalsIgnoreCase(a.getAccountType().getName())) bankAndCashIds.add(a.getId());
            }
        }
        return transactionService.getIncomeByAccountIds(userId, bankAndCashIds, ym.atDay(1), ym.atEndOfMonth());
    }

    /** Month -> row with income; CASH and BANK rows carry the same income. */
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

    /** Manual income; always wins over auto. */
    public void saveManualIncome(Long userId, int year, int month, BigDecimal amount) {
        writeIncome(userId, year, month, amount, true);
    }

    /** Income version of autoSaveCurrentMonth; skips manual income. */
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

    /** Recalculates one month's income and saves it as auto. */
    public BigDecimal recalculateIncome(Long userId, List<Account> accounts, int year, int month) {
        BigDecimal amount = calculateIncome(userId, accounts, YearMonth.of(year, month));
        autoSaveIncome(userId, year, month, amount);
        return amount;
    }

    /** Startup backfill of income_amount on rows that don't have it yet. */
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

    /** Writes income to both CASH and BANK rows, creating a row with 0 expense if missing. */
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