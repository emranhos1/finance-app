package com.finance.service;

import com.finance.entity.Account;
import com.finance.entity.AccountLog;
import com.finance.entity.Loan;
import com.finance.entity.Transaction;
import com.finance.repository.AccountLogRepository;
import com.finance.repository.AccountRepository;
import com.finance.repository.LoanRepository;
import com.finance.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Read-only DB checks (balance vs. log chain, dangling loan_id). Never modifies data. */
@Service
@RequiredArgsConstructor
public class DataIntegrityService {
    private final AccountRepository accountRepository;
    private final AccountLogRepository accountLogRepository;
    private final TransactionRepository transactionRepository;
    private final LoanRepository loanRepository;

    public List<String> runFullCheck() {
        List<String> issues = new ArrayList<>();
        issues.addAll(checkAccountBalances());
        issues.addAll(checkDanglingLoanReferences());
        return issues;
    }

    /** Each log's balanceAfter must match the next balanceBefore, and the last must match the account balance.
     *  Accounts with no logs are skipped. */
    public List<String> checkAccountBalances() {
        List<String> issues = new ArrayList<>();
        for (Account account : accountRepository.findAll()) {
            List<AccountLog> logs = accountLogRepository.findByAccountIdOrderByIdAsc(account.getId());
            if (logs.isEmpty()) continue;

            for (int i = 0; i < logs.size() - 1; i++) {
                BigDecimal thisAfter = logs.get(i).getBalanceAfter();
                BigDecimal nextBefore = logs.get(i + 1).getBalanceBefore();
                if (thisAfter.compareTo(nextBefore) != 0) {
                    issues.add("Account \"" + account.getName() + "\": log chain gap between log #"
                            + logs.get(i).getId() + " and #" + logs.get(i + 1).getId()
                            + " (" + thisAfter + " vs " + nextBefore + ")");
                }
            }

            BigDecimal lastLogBalance = logs.get(logs.size() - 1).getBalanceAfter();
            if (lastLogBalance.compareTo(account.getBalance()) != 0) {
                issues.add("Account \"" + account.getName() + "\": current balance (" + account.getBalance()
                        + ") does not match its last log entry (" + lastLogBalance + ")");
            }
        }
        return issues;
    }

    /** Transactions whose loan_id points to a loan that no longer exists. */
    public List<String> checkDanglingLoanReferences() {
        List<String> issues = new ArrayList<>();
        Set<Long> loanIds = new HashSet<>();
        for (Loan loan : loanRepository.findAll()) loanIds.add(loan.getId());

        for (Transaction tx : transactionRepository.findAll()) {
            if (tx.getLoanId() != null && !loanIds.contains(tx.getLoanId())) {
                issues.add("Transaction #" + tx.getId() + " references a loan (id " + tx.getLoanId()
                        + ") that no longer exists");
            }
        }
        return issues;
    }
}
