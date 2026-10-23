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

    /** Loan whose initial transaction this is. Used to block direct edit/delete of that transaction. */
    Optional<Loan> findByInitialTransactionId(Long initialTransactionId);
}
