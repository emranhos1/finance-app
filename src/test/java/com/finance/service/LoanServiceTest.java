package com.finance.service;

import com.finance.entity.Loan;
import com.finance.entity.LoanPerson;
import com.finance.entity.Transaction;
import com.finance.repository.LoanPersonRepository;
import com.finance.repository.LoanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Loan rules: amount checks, ownership, repayment limits and settling. */
@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock private LoanRepository loanRepository;
    @Mock private LoanPersonRepository loanPersonRepository;
    @Mock private TransactionService transactionService;

    @InjectMocks
    private LoanService loanService;

    private Loan lentLoan;

    @BeforeEach
    void setUp() {
        lentLoan = Loan.builder().id(5L).userId(1L).personName("Rahim").loanPersonId(9L)
                .type(Loan.LoanType.LENT).principalAmount(new BigDecimal("1000.00"))
                .status(Loan.LoanStatus.OPEN).build();
        lenient().when(loanRepository.findById(5L)).thenReturn(Optional.of(lentLoan));
        lenient().when(loanRepository.save(any(Loan.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void repaidSoFar(String amount) {
        when(transactionService.getLoanRepaidAmount(5L)).thenReturn(new BigDecimal(amount));
    }

    private void incomeTransactionWillBeCreated() {
        Transaction tx = Transaction.builder().id(100L).userId(1L).build();
        when(transactionService.recordIncomeTransaction(anyLong(), any(), any(), anyLong(), isNull(), anyString()))
                .thenReturn(tx);
    }

    @Test
    void recordRepayment_zeroOrNegativeAmount_throws() {
        for (BigDecimal bad : new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("-1.00"), null}) {
            assertThatThrownBy(() -> loanService.recordRepayment(5L, 1L, bad, 2L, LocalDate.now(), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("greater than zero");
        }
        verify(transactionService, never()).linkTransactionToLoan(anyLong(), anyLong());
    }

    @Test
    void recordRepayment_aboveRemaining_throwsAndRecordsNothing() {
        repaidSoFar("700.00");

        assertThatThrownBy(() -> loanService.recordRepayment(5L, 1L, new BigDecimal("400.00"), 2L, LocalDate.now(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds remaining");

        verify(transactionService, never()).linkTransactionToLoan(anyLong(), anyLong());
    }

    @Test
    void recordRepayment_ofAnotherUsersLoan_isDenied() {
        assertThatThrownBy(() -> loanService.recordRepayment(5L, 2L, new BigDecimal("100.00"), 2L, LocalDate.now(), null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void recordRepayment_onSettledLoan_throws() {
        lentLoan.setStatus(Loan.LoanStatus.SETTLED);

        assertThatThrownBy(() -> loanService.recordRepayment(5L, 1L, new BigDecimal("100.00"), 2L, LocalDate.now(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already settled");
    }

    @Test
    void recordRepayment_partial_keepsLoanOpen() {
        repaidSoFar("0.00");
        incomeTransactionWillBeCreated();

        Loan result = loanService.recordRepayment(5L, 1L, new BigDecimal("400.00"), 2L, LocalDate.now(), null);

        assertThat(result.getStatus()).isEqualTo(Loan.LoanStatus.OPEN);
        verify(transactionService).linkTransactionToLoan(100L, 5L);
    }

    @Test
    void recordRepayment_paysTheRemainingAmount_settlesTheLoan() {
        repaidSoFar("600.00");
        incomeTransactionWillBeCreated();

        Loan result = loanService.recordRepayment(5L, 1L, new BigDecimal("400.00"), 2L, LocalDate.now(), null);

        assertThat(result.getStatus()).isEqualTo(Loan.LoanStatus.SETTLED);
    }

    @Test
    void createLoan_zeroAmount_throwsAndRecordsNoTransaction() {
        LoanPerson person = LoanPerson.builder().id(9L).userId(1L).name("Rahim").build();
        when(loanPersonRepository.findById(9L)).thenReturn(Optional.of(person));

        assertThatThrownBy(() -> loanService.createLoan(1L, 9L, Loan.LoanType.LENT, BigDecimal.ZERO,
                2L, null, null, LocalDate.now(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");

        verify(transactionService, never()).recordExpenseTransaction(
                anyLong(), any(), any(), anyLong(), any(), any(), anyString());
    }

    @Test
    void createLoan_forAnotherUsersPerson_isDenied() {
        LoanPerson person = LoanPerson.builder().id(9L).userId(2L).name("Karim").build();
        when(loanPersonRepository.findById(9L)).thenReturn(Optional.of(person));

        assertThatThrownBy(() -> loanService.createLoan(1L, 9L, Loan.LoanType.LENT, new BigDecimal("100.00"),
                2L, null, null, LocalDate.now(), null, null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void createLoanPerson_duplicateNameIgnoringCase_throws() {
        when(loanPersonRepository.findByUserIdAndNameIgnoreCase(1L, "Rahim"))
                .thenReturn(Optional.of(LoanPerson.builder().id(9L).userId(1L).name("rahim").build()));

        assertThatThrownBy(() -> loanService.createLoanPerson(1L, "  Rahim ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }
}
