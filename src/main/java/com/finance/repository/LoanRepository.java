package com.finance.repository;

import com.finance.entity.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {
    List<Loan> findByUserIdAndStatusOrderByLoanDateDesc(Long userId, Loan.LoanStatus status);
    List<Loan> findByUserIdAndLoanPersonIdOrderByLoanDateDesc(Long userId, Long loanPersonId);

    /** Finds the loan (if any) that this transaction is the original lend/borrow transaction for -
     *  used to stop that transaction from being deleted/edited directly from the Transaction tab,
     *  which would otherwise leave the loan row pointing at a transaction that no longer exists. */
    Optional<Loan> findByInitialTransactionId(Long initialTransactionId);
}
