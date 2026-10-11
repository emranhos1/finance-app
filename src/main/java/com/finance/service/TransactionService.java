package com.finance.service;

import com.finance.entity.Account;
import com.finance.entity.AccountLog;
import com.finance.entity.Loan;
import com.finance.entity.Transaction;
import com.finance.repository.AccountRepository;
import com.finance.repository.LoanRepository;
import com.finance.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final LoanRepository loanRepository;

    public Transaction recordIncomeTransaction(Long userId, LocalDate date, BigDecimal amount,
                                               Long toAccountId, Long categoryId, String note) {
        return recordIncomeTransaction(userId, date, amount, toAccountId, categoryId, null, note);
    }

    public Transaction recordIncomeTransaction(Long userId, LocalDate date, BigDecimal amount,
                                               Long toAccountId, Long categoryId, Long transferTypeId, String note) {
        Account toAccount = accountRepository.findById(toAccountId)
                .orElseThrow(() -> new IllegalArgumentException("To account not found"));
        if (!toAccount.getUserId().equals(userId)) throw new IllegalArgumentException("Account does not belong to user");

        BigDecimal before = toAccount.getBalance();
        toAccount.setBalance(before.add(amount));
        accountRepository.save(toAccount);

        Transaction tx = transactionRepository.save(Transaction.builder()
                .userId(userId).date(date).type(Transaction.TransactionType.INCOME)
                .amount(amount).toAccountId(toAccountId).categoryId(categoryId)
                .transferTypeId(transferTypeId).note(note).build());

        accountService.logBalanceChange(toAccountId, userId, before, toAccount.getBalance(),
                AccountLog.ChangeType.CREDIT, AccountLog.ReferenceType.INCOME, tx.getId(), note);
        return tx;
    }

    public Transaction recordExpenseTransaction(Long userId, LocalDate date, BigDecimal amount,
                                                Long fromAccountId, Long categoryId, String note) {
        return recordExpenseTransaction(userId, date, amount, fromAccountId, categoryId, null, note);
    }

    public Transaction recordExpenseTransaction(Long userId, LocalDate date, BigDecimal amount,
                                                Long fromAccountId, Long categoryId, Long transferTypeId, String note) {
        Account fromAccount = accountRepository.findById(fromAccountId)
                .orElseThrow(() -> new IllegalArgumentException("From account not found"));
        if (!fromAccount.getUserId().equals(userId)) throw new IllegalArgumentException("Account does not belong to user");
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance in '" + fromAccount.getName() + "' (available: \u09f3 "
                    + fromAccount.getBalance().toPlainString() + ")");
        }

        BigDecimal before = fromAccount.getBalance();
        fromAccount.setBalance(before.subtract(amount));
        accountRepository.save(fromAccount);

        Transaction tx = transactionRepository.save(Transaction.builder()
                .userId(userId).date(date).type(Transaction.TransactionType.EXPENSE)
                .amount(amount).fromAccountId(fromAccountId).categoryId(categoryId)
                .transferTypeId(transferTypeId).note(note).build());

        accountService.logBalanceChange(fromAccountId, userId, before, fromAccount.getBalance(),
                AccountLog.ChangeType.DEBIT, AccountLog.ReferenceType.EXPENSE, tx.getId(), note);
        return tx;
    }

    public Transaction recordTransferTransaction(Long userId, LocalDate date, BigDecimal amount,
                                                 Long fromAccountId, Long toAccountId, Long transferTypeId, String note) {
        Account fromAccount = accountRepository.findById(fromAccountId)
                .orElseThrow(() -> new IllegalArgumentException("From account not found"));
        Account toAccount = accountRepository.findById(toAccountId)
                .orElseThrow(() -> new IllegalArgumentException("To account not found"));

        if (!fromAccount.getUserId().equals(userId) || !toAccount.getUserId().equals(userId))
            throw new IllegalArgumentException("Accounts do not belong to user");
        if (fromAccount.getId().equals(toAccount.getId()))
            throw new IllegalArgumentException("Cannot transfer to the same account");
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance in '" + fromAccount.getName() + "' (available: \u09f3 "
                    + fromAccount.getBalance().toPlainString() + ")");
        }

        BigDecimal fromBefore = fromAccount.getBalance();
        BigDecimal toBefore = toAccount.getBalance();

        fromAccount.setBalance(fromBefore.subtract(amount));
        toAccount.setBalance(toBefore.add(amount));
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        Transaction tx = transactionRepository.save(Transaction.builder()
                .userId(userId).date(date).type(Transaction.TransactionType.TRANSFER)
                .amount(amount).fromAccountId(fromAccountId).toAccountId(toAccountId)
                .transferTypeId(transferTypeId).note(note).build());

        accountService.logBalanceChange(fromAccountId, userId, fromBefore, fromAccount.getBalance(),
                AccountLog.ChangeType.DEBIT, AccountLog.ReferenceType.TRANSFER, tx.getId(), note);
        accountService.logBalanceChange(toAccountId, userId, toBefore, toAccount.getBalance(),
                AccountLog.ChangeType.CREDIT, AccountLog.ReferenceType.TRANSFER, tx.getId(), note);
        return tx;
    }

    public Transaction updateTransaction(Long transactionId, Long userId, LocalDate date,
                                         BigDecimal newAmount, Long categoryId, Long transferTypeId, String note) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        if (!tx.getUserId().equals(userId)) throw new IllegalArgumentException("Access denied");
        loanRepository.findByInitialTransactionId(transactionId).ifPresent(loan -> {
            throw new IllegalArgumentException("This transaction was created by a loan (" + loan.getPersonName()
                    + "). Edit or delete it from the Loans tab instead, so the loan record stays in sync.");
        });

        BigDecimal diff = newAmount.subtract(tx.getAmount());

        if (tx.getType() == Transaction.TransactionType.INCOME) {
            Account acc = accountRepository.findById(tx.getToAccountId()).orElseThrow();
            BigDecimal before = acc.getBalance();
            acc.setBalance(before.add(diff));
            accountRepository.save(acc);
            accountService.logBalanceChange(acc.getId(), userId, before, acc.getBalance(),
                    AccountLog.ChangeType.ADJUSTMENT, AccountLog.ReferenceType.INCOME, transactionId, note);
        } else if (tx.getType() == Transaction.TransactionType.EXPENSE) {
            Account acc = accountRepository.findById(tx.getFromAccountId()).orElseThrow();
            BigDecimal before = acc.getBalance();
            BigDecimal after = before.subtract(diff);
            if (after.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Insufficient balance in '" + acc.getName() + "' (available: \u09f3 "
                        + before.toPlainString() + ")");
            }
            acc.setBalance(after);
            accountRepository.save(acc);
            accountService.logBalanceChange(acc.getId(), userId, before, acc.getBalance(),
                    AccountLog.ChangeType.ADJUSTMENT, AccountLog.ReferenceType.EXPENSE, transactionId, note);
        } else if (tx.getType() == Transaction.TransactionType.TRANSFER) {
            Account from = accountRepository.findById(tx.getFromAccountId()).orElseThrow();
            Account to = accountRepository.findById(tx.getToAccountId()).orElseThrow();
            BigDecimal fromBefore = from.getBalance();
            BigDecimal toBefore = to.getBalance();
            BigDecimal fromAfter = fromBefore.subtract(diff);
            if (fromAfter.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Insufficient balance in '" + from.getName() + "' (available: \u09f3 "
                        + fromBefore.toPlainString() + ")");
            }
            from.setBalance(fromAfter);
            to.setBalance(toBefore.add(diff));
            accountRepository.save(from);
            accountRepository.save(to);
            accountService.logBalanceChange(from.getId(), userId, fromBefore, from.getBalance(),
                    AccountLog.ChangeType.ADJUSTMENT, AccountLog.ReferenceType.TRANSFER, transactionId, note);
            accountService.logBalanceChange(to.getId(), userId, toBefore, to.getBalance(),
                    AccountLog.ChangeType.ADJUSTMENT, AccountLog.ReferenceType.TRANSFER, transactionId, note);
        }

        tx.setDate(date);
        tx.setAmount(newAmount);
        tx.setCategoryId(categoryId);
        tx.setTransferTypeId(transferTypeId);
        tx.setNote(note);
        Transaction saved = transactionRepository.save(tx);
        syncLoanStatus(saved.getLoanId());
        return saved;
    }

    public void deleteTransaction(Long transactionId, Long userId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        if (!tx.getUserId().equals(userId)) throw new IllegalArgumentException("Access denied");
        loanRepository.findByInitialTransactionId(transactionId).ifPresent(loan -> {
            throw new IllegalArgumentException("This transaction was created by a loan (" + loan.getPersonName()
                    + "). Delete it from the Loans tab instead, so the loan record is removed too instead of being left behind.");
        });

        if (tx.getType() == Transaction.TransactionType.INCOME && tx.getToAccountId() != null) {
            accountRepository.findById(tx.getToAccountId()).ifPresent(acc -> {
                BigDecimal before = acc.getBalance();
                acc.setBalance(before.subtract(tx.getAmount()));
                accountRepository.save(acc);
                accountService.logBalanceChange(acc.getId(), userId, before, acc.getBalance(),
                        AccountLog.ChangeType.DEBIT, AccountLog.ReferenceType.INCOME, transactionId, "Transaction deleted");
            });
        } else if (tx.getType() == Transaction.TransactionType.EXPENSE && tx.getFromAccountId() != null) {
            accountRepository.findById(tx.getFromAccountId()).ifPresent(acc -> {
                BigDecimal before = acc.getBalance();
                acc.setBalance(before.add(tx.getAmount()));
                accountRepository.save(acc);
                accountService.logBalanceChange(acc.getId(), userId, before, acc.getBalance(),
                        AccountLog.ChangeType.CREDIT, AccountLog.ReferenceType.EXPENSE, transactionId, "Transaction deleted");
            });
        } else if (tx.getType() == Transaction.TransactionType.TRANSFER) {
            if (tx.getFromAccountId() != null) accountRepository.findById(tx.getFromAccountId()).ifPresent(acc -> {
                BigDecimal before = acc.getBalance();
                acc.setBalance(before.add(tx.getAmount()));
                accountRepository.save(acc);
                accountService.logBalanceChange(acc.getId(), userId, before, acc.getBalance(),
                        AccountLog.ChangeType.CREDIT, AccountLog.ReferenceType.TRANSFER, transactionId, "Transaction deleted");
            });
            if (tx.getToAccountId() != null) accountRepository.findById(tx.getToAccountId()).ifPresent(acc -> {
                BigDecimal before = acc.getBalance();
                acc.setBalance(before.subtract(tx.getAmount()));
                accountRepository.save(acc);
                accountService.logBalanceChange(acc.getId(), userId, before, acc.getBalance(),
                        AccountLog.ChangeType.DEBIT, AccountLog.ReferenceType.TRANSFER, transactionId, "Transaction deleted");
            });
        }
        Long linkedLoanId = tx.getLoanId();
        transactionRepository.deleteById(transactionId);
        syncLoanStatus(linkedLoanId);
    }

    /** Keeps a loan's stored OPEN/SETTLED status consistent with its actual remaining balance
     *  whenever one of its repayment transactions is edited or deleted directly from the
     *  Transaction tab - otherwise a loan could stay stuck SETTLED after a repayment that made it
     *  0 is edited down or removed, or stay OPEN after an edit that now fully covers it. */
    private void syncLoanStatus(Long loanId) {
        if (loanId == null) return;
        loanRepository.findById(loanId).ifPresent(loan -> {
            BigDecimal remaining = loan.getPrincipalAmount().subtract(transactionRepository.sumAmountByLoanId(loanId));
            Loan.LoanStatus correctStatus = remaining.compareTo(BigDecimal.ZERO) <= 0
                    ? Loan.LoanStatus.SETTLED : Loan.LoanStatus.OPEN;
            if (loan.getStatus() != correctStatus) {
                loan.setStatus(correctStatus);
                loanRepository.save(loan);
            }
        });
    }

    /** Tags a transaction (typically a loan repayment) as belonging to a loan, so the loan's
     *  remaining balance can be derived from the sum of its tagged repayment transactions. */
    public void linkTransactionToLoan(Long transactionId, Long loanId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        tx.setLoanId(loanId);
        transactionRepository.save(tx);
    }

    public BigDecimal getLoanRepaidAmount(Long loanId) {
        return transactionRepository.sumAmountByLoanId(loanId);
    }

    public List<Transaction> getTransactionsByLoanId(Long loanId) {
        return transactionRepository.findByLoanId(loanId);
    }

    public Transaction getTransactionById(Long transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
    }

    public List<Transaction> getTransactionsByUserId(Long userId) {
        return transactionRepository.findByUserIdOrderByDateDesc(userId);
    }

    public Page<Transaction> getTransactionsPage(Long userId, List<Transaction.TransactionType> types,
                                                  Long categoryId, LocalDate date, Long accountId,
                                                  Transaction.TransactionType filterType, Pageable pageable) {
        return transactionRepository.findTransactionsPage(userId, types, categoryId, date, accountId, filterType, pageable);
    }

    public Page<Transaction> getTransfersPage(Long userId, Long transferTypeId, LocalDate date, Long accountId, Pageable pageable) {
        return transactionRepository.findTransfersPage(userId, Transaction.TransactionType.TRANSFER, transferTypeId, date, accountId, pageable);
    }

    public List<Transaction> getTransactionsByDateRange(Long userId, LocalDate startDate, LocalDate endDate) {
        return transactionRepository.findByUserIdAndDateBetween(userId, startDate, endDate);
    }

    public List<Transaction> getTransactionsByType(Long userId, Transaction.TransactionType type) {
        return transactionRepository.findByUserIdAndType(userId, type);
    }

    public List<Transaction> getTransactionsByCategory(Long userId, Long categoryId) {
        return transactionRepository.findByUserIdAndCategoryId(userId, categoryId);
    }

    public BigDecimal getTotalIncome(Long userId, LocalDate startDate, LocalDate endDate) {
        return transactionRepository.sumIncomeByUserAndDateRange(userId, startDate, endDate);
    }

    public BigDecimal getTotalExpense(Long userId, LocalDate startDate, LocalDate endDate) {
        return transactionRepository.sumExpenseByUserAndDateRange(userId, startDate, endDate);
    }

    public BigDecimal getExpenseByCategory(Long userId, Long categoryId, LocalDate startDate, LocalDate endDate) {
        return transactionRepository.sumExpenseByCategory(userId, categoryId, startDate, endDate);
    }

    public BigDecimal getExpenseByCategoryForAccounts(Long userId, Long categoryId, List<Long> accountIds, LocalDate startDate, LocalDate endDate) {
        if (accountIds == null || accountIds.isEmpty()) return BigDecimal.ZERO;
        return transactionRepository.sumExpenseByCategoryAndFromAccountIds(userId, categoryId, accountIds, startDate, endDate);
    }

    public BigDecimal getIncomeByCategory(Long userId, Long categoryId, LocalDate startDate, LocalDate endDate) {
        return transactionRepository.sumIncomeByCategory(userId, categoryId, startDate, endDate);
    }

    public BigDecimal getExpenseByAccountIds(Long userId, List<Long> accountIds, LocalDate startDate, LocalDate endDate) {
        if (accountIds == null || accountIds.isEmpty()) return BigDecimal.ZERO;
        return transactionRepository.sumExpenseByFromAccountIdsAndDateRange(userId, accountIds, startDate, endDate);
    }

    /** Earliest transaction date this user has ever recorded, or null if they have none. */
    public LocalDate getEarliestTransactionDate(Long userId) {
        return transactionRepository.findEarliestDateByUserId(userId);
    }

    public BigDecimal getIncomeByAccountIds(Long userId, List<Long> accountIds, LocalDate startDate, LocalDate endDate) {
        if (accountIds == null || accountIds.isEmpty()) return BigDecimal.ZERO;
        return transactionRepository.sumIncomeByToAccountIdsAndDateRange(userId, accountIds, startDate, endDate);
    }

    /** Expense for one account type over a date range: EXPENSE transactions paid from accounts
     *  of that type. Transfers between the user's own accounts (Bank, Cash, DPS, FDR, Plot, etc.)
     *  are never counted here - the money is still theirs, just moved, so it isn't real spending. */
    public BigDecimal getEffectiveExpense(Long userId, List<Account> accounts, String typeName, LocalDate start, LocalDate end) {
        List<Long> accountIds = new ArrayList<>();
        for (Account a : accounts) {
            if (typeName.equalsIgnoreCase(a.getAccountType().getName())) accountIds.add(a.getId());
        }
        return getExpenseByAccountIds(userId, accountIds, start, end);
    }

    /** Reports tab: income into the given accounts, optionally limited to one category (null = any). */
    public BigDecimal getIncomeFiltered(Long userId, List<Long> accountIds, Long categoryId, LocalDate start, LocalDate end) {
        if (accountIds == null || accountIds.isEmpty()) return BigDecimal.ZERO;
        return transactionRepository.sumIncomeFiltered(userId, accountIds, categoryId, start, end);
    }

    /** Reports tab: expense from the given accounts, optionally limited to one category (null = any). */
    public BigDecimal getExpenseFiltered(Long userId, List<Long> accountIds, Long categoryId, LocalDate start, LocalDate end) {
        if (accountIds == null || accountIds.isEmpty()) return BigDecimal.ZERO;
        return transactionRepository.sumExpenseFiltered(userId, accountIds, categoryId, start, end);
    }

    /** Transaction id -> its date, for showing an Account Log row under the transaction's own date. */
    public Map<Long, LocalDate> getDatesByIds(Collection<Long> ids) {
        Map<Long, LocalDate> dates = new HashMap<>();
        if (ids == null || ids.isEmpty()) return dates;
        for (Transaction t : transactionRepository.findAllById(ids)) dates.put(t.getId(), t.getDate());
        return dates;
    }

    public BigDecimal getNetIncome(Long userId, LocalDate startDate, LocalDate endDate) {
        return getTotalIncome(userId, startDate, endDate).subtract(getTotalExpense(userId, startDate, endDate));
    }
}