package com.finance.repository;

import com.finance.entity.AccountLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AccountLogRepository extends JpaRepository<AccountLog, Long> {
    List<AccountLog> findByAccountIdOrderByCreatedAtDesc(Long accountId);
    List<AccountLog> findByAccountIdOrderByIdAsc(Long accountId);
    List<AccountLog> findByUserIdOrderByCreatedAtDesc(Long userId);
    Page<AccountLog> findByAccountIdInOrderByCreatedAtDescIdDesc(List<Long> accountIds, Pageable pageable);

    /** Account Log filters on the Reports tab. Dates are always passed (callers use wide defaults
     *  when unset); a null referenceType/categoryId means "don't filter on it". Category is read
     *  from the transaction the log row points to (referenceId is the transaction id; null for
     *  MANUAL rows). Loan money - a loan's original lend/borrow transaction and its repayments -
     *  is left out, matching how the rest of the Reports page skips loans. */
    // Date range uses the TRANSACTION's date (what the Transaction tab and the rest of Reports
    // filter on), falling back to when the log row was written only for rows with no transaction
    // (MANUAL adjustments, or a transaction that has since been deleted).
    @Query("SELECT l FROM AccountLog l WHERE l.accountId IN :accountIds " +
           "AND (EXISTS (SELECT dt.id FROM Transaction dt WHERE dt.id = l.referenceId AND dt.date BETWEEN :fromDate AND :toDate) " +
           "     OR (NOT EXISTS (SELECT dt2.id FROM Transaction dt2 WHERE dt2.id = l.referenceId) " +
           "         AND l.createdAt >= :from AND l.createdAt < :to)) " +
           "AND NOT EXISTS (SELECT lt.id FROM Transaction lt WHERE lt.id = l.referenceId " +
           "     AND (lt.loanId IS NOT NULL OR EXISTS (SELECT lo.id FROM Loan lo WHERE lo.initialTransactionId = lt.id))) " +
           "AND (:referenceType IS NULL OR l.referenceType = :referenceType) " +
           "AND (:categoryId IS NULL OR EXISTS (SELECT t.id FROM Transaction t " +
           "     WHERE t.id = l.referenceId AND t.categoryId = :categoryId)) " +
           "ORDER BY l.createdAt DESC, l.id DESC")
    Page<AccountLog> search(@Param("accountIds") List<Long> accountIds,
                            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
                            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                            @Param("referenceType") AccountLog.ReferenceType referenceType,
                            @Param("categoryId") Long categoryId,
                            Pageable pageable);
}
