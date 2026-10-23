package com.finance.service;

import com.finance.entity.Loan;
import com.finance.entity.LoanPerson;
import com.finance.entity.Transaction;
import com.finance.repository.LoanPersonRepository;
import com.finance.repository.LoanRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class LoanService {
    private final LoanRepository loanRepository;
    private final LoanPersonRepository loanPersonRepository;
    private final TransactionService transactionService;

    public LoanPerson createLoanPerson(Long userId, String name, String note) {
        if (name == null || name.trim().isEmpty())
            throw new IllegalArgumentException("Name cannot be empty");
        String trimmed = name.trim();
        if (loanPersonRepository.findByUserIdAndNameIgnoreCase(userId, trimmed).isPresent())
            throw new IllegalArgumentException("A loan account for \"" + trimmed + "\" already exists");
        return loanPersonRepository.save(LoanPerson.builder()
                .userId(userId).name(trimmed).note(note == null ? null : note.trim()).build());
    }

    public List<LoanPerson> getLoanPersonsByUserId(Long userId) {
        return loanPersonRepository.findByUserIdOrderByNameAsc(userId);
    }

    public void deleteLoanPerson(Long loanPersonId, Long userId) {
        LoanPerson person = loanPersonRepository.findById(loanPersonId)
                .orElseThrow(() -> new IllegalArgumentException("Loan account not found"));
        if (!person.getUserId().equals(userId)) throw new SecurityException("Access denied");
        if (!loanRepository.findByUserIdAndLoanPersonIdOrderByLoanDateDesc(userId, loanPersonId).isEmpty())
            throw new IllegalArgumentException("Cannot delete - this person already has loan entries");
        loanPersonRepository.deleteById(loanPersonId);
    }

    /** Renames a loan account and copies the new name to its loans (personName is denormalized). */
    public LoanPerson updateLoanPerson(Long loanPersonId, Long userId, String newName, String newNote) {
        LoanPerson person = loanPersonRepository.findById(loanPersonId)
                .orElseThrow(() -> new IllegalArgumentException("Loan account not found"));
        if (!person.getUserId().equals(userId)) throw new SecurityException("Access denied");
        if (newName == null || newName.trim().isEmpty())
            throw new IllegalArgumentException("Name cannot be empty");
        String trimmed = newName.trim();

        loanPersonRepository.findByUserIdAndNameIgnoreCase(userId, trimmed)
                .filter(other -> !other.getId().equals(loanPersonId))
                .ifPresent(other -> { throw new IllegalArgumentException("A loan account for \"" + trimmed + "\" already exists"); });

        person.setName(trimmed);
        person.setNote(newNote == null ? null : newNote.trim());
        loanPersonRepository.save(person);

        for (Loan loan : loanRepository.findByUserIdAndLoanPersonIdOrderByLoanDateDesc(userId, loanPersonId)) {
            loan.setPersonName(trimmed);
            loanRepository.save(loan);
        }
        return person;
    }

    /** Moves all loans to targetId and deletes the source account; for accounts duplicated by mistake. */
    public void mergeLoanPersons(Long sourceId, Long targetId, Long userId) {
        if (sourceId.equals(targetId))
            throw new IllegalArgumentException("Cannot merge a loan account into itself");
        LoanPerson source = loanPersonRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source loan account not found"));
        LoanPerson target = loanPersonRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Target loan account not found"));
        if (!source.getUserId().equals(userId) || !target.getUserId().equals(userId))
            throw new SecurityException("Access denied");

        for (Loan loan : loanRepository.findByUserIdAndLoanPersonIdOrderByLoanDateDesc(userId, sourceId)) {
            loan.setLoanPersonId(targetId);
            loan.setPersonName(target.getName());
            loanRepository.save(loan);
        }
        loanPersonRepository.deleteById(sourceId);
    }

    public Loan createLoan(Long userId, Long loanPersonId, Loan.LoanType type, BigDecimal amount,
                            Long accountId, Long transferTypeId, Long categoryId,
                            LocalDate date, LocalDate dueDate, String note) {
        if (loanPersonId == null)
            throw new IllegalArgumentException("Person is required");
        LoanPerson person = loanPersonRepository.findById(loanPersonId)
                .orElseThrow(() -> new IllegalArgumentException("Loan account not found"));
        if (!person.getUserId().equals(userId)) throw new SecurityException("Access denied");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Amount must be greater than zero");

        String autoNote = (type == Loan.LoanType.LENT ? "Loan given to " : "Loan received from ") + person.getName();
        String composedNote = (note == null || note.trim().isEmpty()) ? autoNote : autoNote + " - " + note.trim();

        Transaction tx;
        if (type == Loan.LoanType.LENT) {
            tx = transactionService.recordExpenseTransaction(userId, date, amount, accountId, categoryId, transferTypeId, composedNote);
        } else {
            tx = transactionService.recordIncomeTransaction(userId, date, amount, accountId, categoryId, transferTypeId, composedNote);
        }

        return loanRepository.save(Loan.builder()
                .userId(userId).personName(person.getName()).loanPersonId(loanPersonId).type(type)
                .principalAmount(amount).accountId(accountId).transferTypeId(transferTypeId).categoryId(categoryId)
                .initialTransactionId(tx.getId()).loanDate(date).dueDate(dueDate)
                .note(note).status(Loan.LoanStatus.OPEN).build());
    }

    public Loan recordRepayment(Long loanId, Long userId, BigDecimal amount, Long accountId, LocalDate date, String note) {
        Loan loan = getLoanById(loanId);
        if (!loan.getUserId().equals(userId)) throw new SecurityException("Access denied");
        if (loan.getStatus() == Loan.LoanStatus.SETTLED)
            throw new IllegalArgumentException("This loan is already settled");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Amount must be greater than zero");

        BigDecimal remaining = getRemaining(loan);
        if (amount.compareTo(remaining) > 0)
            throw new IllegalArgumentException("Repayment (৳ " + amount.toPlainString()
                    + ") exceeds remaining balance (৳ " + remaining.toPlainString() + ")");

        String autoNote = (loan.getType() == Loan.LoanType.LENT ? "Loan repayment from " : "Loan repayment to ") + loan.getPersonName();
        String composedNote = (note == null || note.trim().isEmpty()) ? autoNote : autoNote + " - " + note.trim();

        Transaction tx;
        if (loan.getType() == Loan.LoanType.LENT) {
            tx = transactionService.recordIncomeTransaction(userId, date, amount, accountId, null, composedNote);
        } else {
            tx = transactionService.recordExpenseTransaction(userId, date, amount, accountId, null, composedNote);
        }
        transactionService.linkTransactionToLoan(tx.getId(), loanId);

        if (remaining.subtract(amount).compareTo(BigDecimal.ZERO) <= 0) {
            loan.setStatus(Loan.LoanStatus.SETTLED);
            loanRepository.save(loan);
        }
        return loan;
    }

    /** Repays a lump sum across open loans of one type, oldest first.
     *  Any excess becomes a new loan of the opposite type. */
    public BulkSettlementResult recordBulkRepayment(Long userId, Long loanPersonId, Loan.LoanType type,
                                                      BigDecimal totalAmount, Long accountId, Long transferTypeId,
                                                      Long categoryId, LocalDate date, String note) {
        LoanPerson person = loanPersonRepository.findById(loanPersonId)
                .orElseThrow(() -> new IllegalArgumentException("Loan account not found"));
        if (!person.getUserId().equals(userId)) throw new SecurityException("Access denied");
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Amount must be greater than zero");

        List<Loan> openLoans = new ArrayList<>();
        for (Loan loan : getLoansByPerson(userId, loanPersonId)) {
            if (loan.getStatus() == Loan.LoanStatus.OPEN && loan.getType() == type) openLoans.add(loan);
        }
        if (openLoans.isEmpty())
            throw new IllegalArgumentException("No open " + (type == Loan.LoanType.LENT ? "\"I Gave\"" : "\"I Took\"")
                    + " loans for this person");
        Collections.reverse(openLoans); // getLoansByPerson returns newest first; repay oldest first

        BigDecimal totalOwed = BigDecimal.ZERO;
        for (Loan loan : openLoans) totalOwed = totalOwed.add(getRemaining(loan));

        BigDecimal amountLeft = totalAmount.min(totalOwed);
        List<Loan> touched = new ArrayList<>();
        for (Loan loan : openLoans) {
            if (amountLeft.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal portion = getRemaining(loan).min(amountLeft);
            recordRepayment(loan.getId(), userId, portion, accountId, date, note);
            amountLeft = amountLeft.subtract(portion);
            touched.add(loan);
        }

        BigDecimal excess = totalAmount.subtract(totalOwed);
        Loan newLoan = null;
        if (excess.compareTo(BigDecimal.ZERO) > 0) {
            Loan.LoanType oppositeType = (type == Loan.LoanType.LENT) ? Loan.LoanType.BORROWED : Loan.LoanType.LENT;
            String excessNote = (note == null || note.trim().isEmpty())
                    ? "Extra beyond amount owed" : "Extra beyond amount owed - " + note.trim();
            newLoan = createLoan(userId, loanPersonId, oppositeType, excess, accountId, transferTypeId, categoryId,
                    date, null, excessNote);
        }
        return new BulkSettlementResult(touched, excess.compareTo(BigDecimal.ZERO) > 0 ? excess : BigDecimal.ZERO, newLoan);
    }

    @Data
    @AllArgsConstructor
    public static class BulkSettlementResult {
        private List<Loan> repaidLoans;
        private BigDecimal excessAmount;
        private Loan newLoanFromExcess;
    }

    public BigDecimal getRemaining(Loan loan) {
        BigDecimal repaid = transactionService.getLoanRepaidAmount(loan.getId());
        BigDecimal remaining = loan.getPrincipalAmount().subtract(repaid);
        return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
    }

    public Loan getLoanById(Long loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));
    }

    /** The loan this transaction originally created, if any. */
    public Optional<Loan> findLoanByInitialTransactionId(Long transactionId) {
        return loanRepository.findByInitialTransactionId(transactionId);
    }

    public List<Loan> getLoansByUserIdAndStatus(Long userId, Loan.LoanStatus status) {
        return loanRepository.findByUserIdAndStatusOrderByLoanDateDesc(userId, status);
    }

    public List<Loan> getLoansByPerson(Long userId, Long loanPersonId) {
        return loanRepository.findByUserIdAndLoanPersonIdOrderByLoanDateDesc(userId, loanPersonId);
    }

    /** One summary row per person across all their loans. */
    public List<PersonSummary> getPersonSummaries(Long userId) {
        List<PersonSummary> result = new ArrayList<>();
        for (LoanPerson person : getLoanPersonsByUserId(userId)) {
            List<Loan> loans = getLoansByPerson(userId, person.getId());
            BigDecimal totalLent = BigDecimal.ZERO;
            BigDecimal totalBorrowed = BigDecimal.ZERO;
            BigDecimal netRemaining = BigDecimal.ZERO;
            BigDecimal lentRemaining = BigDecimal.ZERO;
            BigDecimal borrowedRemaining = BigDecimal.ZERO;
            int openCount = 0;
            for (Loan loan : loans) {
                BigDecimal remaining = getRemaining(loan);
                if (loan.getType() == Loan.LoanType.LENT) {
                    totalLent = totalLent.add(loan.getPrincipalAmount());
                    netRemaining = netRemaining.add(remaining);
                    lentRemaining = lentRemaining.add(remaining);
                } else {
                    totalBorrowed = totalBorrowed.add(loan.getPrincipalAmount());
                    netRemaining = netRemaining.subtract(remaining);
                    borrowedRemaining = borrowedRemaining.add(remaining);
                }
                if (loan.getStatus() == Loan.LoanStatus.OPEN) openCount++;
            }
            result.add(new PersonSummary(person.getId(), person.getName(), totalLent, totalBorrowed, netRemaining, openCount,
                    lentRemaining, borrowedRemaining));
        }
        return result;
    }

    @Data
    @AllArgsConstructor
    public static class PersonSummary {
        private Long personId;
        private String personName;
        private BigDecimal totalLent;
        private BigDecimal totalBorrowed;
        private BigDecimal netRemaining;
        private int openCount;
        private BigDecimal lentRemaining;
        private BigDecimal borrowedRemaining;
    }

    public List<Loan> getDueSoonOrOverdueLoans(Long userId, int daysAhead) {
        LocalDate threshold = LocalDate.now().plusDays(daysAhead);
        List<Loan> result = new ArrayList<>();
        for (Loan loan : getLoansByUserIdAndStatus(userId, Loan.LoanStatus.OPEN)) {
            if (loan.getDueDate() != null && !loan.getDueDate().isAfter(threshold)) {
                result.add(loan);
            }
        }
        return result;
    }

    /** Deletes the loan row first, then reverses its transactions;
     *  deleteTransaction refuses to delete a transaction a loan still points to. */
    public void deleteLoan(Long loanId, Long userId) {
        Loan loan = getLoanById(loanId);
        if (!loan.getUserId().equals(userId)) throw new SecurityException("Access denied");

        List<Transaction> repayments = transactionService.getTransactionsByLoanId(loanId);
        Long initialTransactionId = loan.getInitialTransactionId();

        loanRepository.deleteById(loanId);

        for (Transaction repayment : repayments) {
            transactionService.deleteTransaction(repayment.getId(), userId);
        }
        if (initialTransactionId != null) {
            transactionService.deleteTransaction(initialTransactionId, userId);
        }
    }
}
