package com.finance.service;

import com.finance.entity.Account;
import com.finance.entity.Loan;
import com.finance.entity.Transaction;
import com.finance.repository.AccountRepository;
import com.finance.repository.LoanRepository;
import com.finance.repository.TransactionRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Balance math tests for TransactionService: create, edit, delete, and overdraft guards. */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AccountService accountService;
    @Mock private LoanRepository loanRepository;

    @InjectMocks
    private TransactionService transactionService;

    private Account bank;
    private Account cash;

    @BeforeEach
    void setUp() {
        bank = Account.builder().id(1L).userId(1L).balance(new BigDecimal("1000.00")).build();
        cash = Account.builder().id(2L).userId(1L).balance(new BigDecimal("500.00")).build();

        lenient().when(accountRepository.findById(1L)).thenReturn(Optional.of(bank));
        lenient().when(accountRepository.findById(2L)).thenReturn(Optional.of(cash));
        lenient().when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void recordTransferTransaction_movesExactAmountBetweenAccounts() {
        Transaction tx = transactionService.recordTransferTransaction(
                1L, LocalDate.now(), new BigDecimal("300.00"), 1L, 2L, null, "test transfer");

        assertThat(bank.getBalance()).isEqualByComparingTo("700.00");
        assertThat(cash.getBalance()).isEqualByComparingTo("800.00");
        assertThat(tx.getAmount()).isEqualByComparingTo("300.00");
    }

    @Test
    void recordTransferTransaction_insufficientBalance_throwsAndLeavesBalancesUntouched() {
        assertThatThrownBy(() -> transactionService.recordTransferTransaction(
                1L, LocalDate.now(), new BigDecimal("5000.00"), 1L, 2L, null, "too much"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(bank.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(cash.getBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    void updateTransaction_increasingTransferAmount_movesTheDifferenceAgain() {
        Transaction original = Transaction.builder()
                .id(10L).userId(1L).type(Transaction.TransactionType.TRANSFER)
                .amount(new BigDecimal("300.00")).fromAccountId(1L).toAccountId(2L).build();
        bank.setBalance(new BigDecimal("700.00"));
        cash.setBalance(new BigDecimal("800.00"));
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(original));

        transactionService.updateTransaction(10L, 1L, LocalDate.now(), new BigDecimal("450.00"), null, null, "increased");

        assertThat(bank.getBalance()).isEqualByComparingTo("550.00");
        assertThat(cash.getBalance()).isEqualByComparingTo("950.00");
    }

    @Test
    void updateTransaction_decreasingTransferAmount_refundsTheDifference() {
        Transaction original = Transaction.builder()
                .id(11L).userId(1L).type(Transaction.TransactionType.TRANSFER)
                .amount(new BigDecimal("300.00")).fromAccountId(1L).toAccountId(2L).build();
        bank.setBalance(new BigDecimal("700.00"));
        cash.setBalance(new BigDecimal("800.00"));
        when(transactionRepository.findById(11L)).thenReturn(Optional.of(original));

        transactionService.updateTransaction(11L, 1L, LocalDate.now(), new BigDecimal("100.00"), null, null, "decreased");

        assertThat(bank.getBalance()).isEqualByComparingTo("900.00");
        assertThat(cash.getBalance()).isEqualByComparingTo("600.00");
    }

    @Test
    void updateTransaction_increasingTransferBeyondAvailableBalance_throwsAndLeavesBalancesUntouched() {
        Transaction original = Transaction.builder()
                .id(13L).userId(1L).type(Transaction.TransactionType.TRANSFER)
                .amount(new BigDecimal("300.00")).fromAccountId(1L).toAccountId(2L).build();
        bank.setBalance(new BigDecimal("700.00"));
        cash.setBalance(new BigDecimal("800.00"));
        when(transactionRepository.findById(13L)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> transactionService.updateTransaction(
                13L, 1L, LocalDate.now(), new BigDecimal("5000.00"), null, null, "too much"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(bank.getBalance()).isEqualByComparingTo("700.00");
        assertThat(cash.getBalance()).isEqualByComparingTo("800.00");
    }

    @Test
    void updateTransaction_expenseIncreaseBeyondAvailableBalance_throwsAndLeavesBalanceUntouched() {
        Transaction original = Transaction.builder()
                .id(12L).userId(1L).type(Transaction.TransactionType.EXPENSE)
                .amount(new BigDecimal("100.00")).fromAccountId(1L).build();
        bank.setBalance(new BigDecimal("150.00"));
        when(transactionRepository.findById(12L)).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> transactionService.updateTransaction(
                12L, 1L, LocalDate.now(), new BigDecimal("10000.00"), null, null, "too much"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(bank.getBalance()).isEqualByComparingTo("150.00");
    }

    @Test
    void deleteTransaction_transfer_reversesBothAccountsExactly() {
        Transaction tx = Transaction.builder()
                .id(20L).userId(1L).type(Transaction.TransactionType.TRANSFER)
                .amount(new BigDecimal("300.00")).fromAccountId(1L).toAccountId(2L).build();
        bank.setBalance(new BigDecimal("700.00"));
        cash.setBalance(new BigDecimal("800.00"));
        when(transactionRepository.findById(20L)).thenReturn(Optional.of(tx));

        transactionService.deleteTransaction(20L, 1L);

        assertThat(bank.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(cash.getBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    void deleteTransaction_expense_refundsTheAccount() {
        Transaction tx = Transaction.builder()
                .id(21L).userId(1L).type(Transaction.TransactionType.EXPENSE)
                .amount(new BigDecimal("250.00")).fromAccountId(1L).build();
        bank.setBalance(new BigDecimal("750.00"));
        when(transactionRepository.findById(21L)).thenReturn(Optional.of(tx));

        transactionService.deleteTransaction(21L, 1L);

        assertThat(bank.getBalance()).isEqualByComparingTo("1000.00");
    }

    // ---- bug 1: zero / negative / missing amount ----

    @Test
    void recordIncome_zeroNegativeOrNullAmount_throwsAndLeavesBalanceUntouched() {
        for (BigDecimal bad : new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("-5.00"), null}) {
            assertThatThrownBy(() -> transactionService.recordIncomeTransaction(
                    1L, LocalDate.now(), bad, 1L, null, "bad"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("greater than zero");
        }
        assertThat(bank.getBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void recordExpense_zeroNegativeOrNullAmount_throwsAndLeavesBalanceUntouched() {
        for (BigDecimal bad : new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("-5.00"), null}) {
            assertThatThrownBy(() -> transactionService.recordExpenseTransaction(
                    1L, LocalDate.now(), bad, 1L, null, "bad"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("greater than zero");
        }
        assertThat(bank.getBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void recordTransfer_zeroNegativeOrNullAmount_throwsAndLeavesBalancesUntouched() {
        for (BigDecimal bad : new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("-5.00"), null}) {
            assertThatThrownBy(() -> transactionService.recordTransferTransaction(
                    1L, LocalDate.now(), bad, 1L, 2L, null, "bad"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("greater than zero");
        }
        assertThat(bank.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(cash.getBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    void updateTransaction_zeroOrNegativeAmount_throws() {
        for (BigDecimal bad : new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("-5.00"), null}) {
            assertThatThrownBy(() -> transactionService.updateTransaction(
                    30L, 1L, LocalDate.now(), bad, null, null, "bad"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("greater than zero");
        }
    }

    // ---- bug 3: edit / delete must not make a balance negative ----

    @Test
    void updateTransaction_incomeLoweredBelowSpentMoney_throwsAndLeavesBalanceUntouched() {
        Transaction income = Transaction.builder()
                .id(40L).userId(1L).type(Transaction.TransactionType.INCOME)
                .amount(new BigDecimal("500.00")).toAccountId(1L).build();
        bank.setBalance(new BigDecimal("200.00"));
        when(transactionRepository.findById(40L)).thenReturn(Optional.of(income));

        assertThatThrownBy(() -> transactionService.updateTransaction(
                40L, 1L, LocalDate.now(), new BigDecimal("100.00"), null, null, "lower"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");

        assertThat(bank.getBalance()).isEqualByComparingTo("200.00");
    }

    @Test
    void deleteTransaction_incomeWhenMoneyAlreadySpent_throwsAndKeepsTheTransaction() {
        Transaction income = Transaction.builder()
                .id(41L).userId(1L).type(Transaction.TransactionType.INCOME)
                .amount(new BigDecimal("500.00")).toAccountId(1L).build();
        bank.setBalance(new BigDecimal("200.00"));
        when(transactionRepository.findById(41L)).thenReturn(Optional.of(income));

        assertThatThrownBy(() -> transactionService.deleteTransaction(41L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");

        assertThat(bank.getBalance()).isEqualByComparingTo("200.00");
        verify(transactionRepository, never()).deleteById(41L);
    }

    @Test
    void deleteTransaction_transferWhenToAccountAlreadySpent_throwsAndKeepsTheTransaction() {
        Transaction transfer = Transaction.builder()
                .id(42L).userId(1L).type(Transaction.TransactionType.TRANSFER)
                .amount(new BigDecimal("300.00")).fromAccountId(1L).toAccountId(2L).build();
        cash.setBalance(new BigDecimal("100.00"));
        when(transactionRepository.findById(42L)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> transactionService.deleteTransaction(42L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");

        verify(transactionRepository, never()).deleteById(42L);
    }

    @Test
    void updateTransaction_transferLoweredBelowToAccountBalance_throwsAndLeavesBalancesUntouched() {
        Transaction transfer = Transaction.builder()
                .id(43L).userId(1L).type(Transaction.TransactionType.TRANSFER)
                .amount(new BigDecimal("300.00")).fromAccountId(1L).toAccountId(2L).build();
        bank.setBalance(new BigDecimal("700.00"));
        cash.setBalance(new BigDecimal("100.00"));
        when(transactionRepository.findById(43L)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> transactionService.updateTransaction(
                43L, 1L, LocalDate.now(), new BigDecimal("100.00"), null, null, "lower"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");

        assertThat(bank.getBalance()).isEqualByComparingTo("700.00");
        assertThat(cash.getBalance()).isEqualByComparingTo("100.00");
    }

    // ---- bug 9: a repayment edit must not push total repaid above the loan amount ----

    @Test
    void updateTransaction_repaymentAbovePrincipal_throwsAndLeavesBalanceUntouched() {
        Loan loan = Loan.builder().id(5L).userId(1L).principalAmount(new BigDecimal("1000.00")).build();
        Transaction repayment = Transaction.builder()
                .id(50L).userId(1L).type(Transaction.TransactionType.INCOME)
                .amount(new BigDecimal("400.00")).toAccountId(1L).loanId(5L).build();
        when(transactionRepository.findById(50L)).thenReturn(Optional.of(repayment));
        when(loanRepository.findById(5L)).thenReturn(Optional.of(loan));
        when(transactionRepository.sumAmountByLoanId(5L)).thenReturn(new BigDecimal("900.00"));

        // 900 - 400 + 600 = 1100 > 1000
        assertThatThrownBy(() -> transactionService.updateTransaction(
                50L, 1L, LocalDate.now(), new BigDecimal("600.00"), null, null, "too much"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceed the loan amount");

        assertThat(bank.getBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void updateTransaction_repaymentUpToPrincipal_isAllowed() {
        Loan loan = Loan.builder().id(5L).userId(1L).principalAmount(new BigDecimal("1000.00")).build();
        Transaction repayment = Transaction.builder()
                .id(51L).userId(1L).type(Transaction.TransactionType.INCOME)
                .amount(new BigDecimal("400.00")).toAccountId(1L).loanId(5L).build();
        when(transactionRepository.findById(51L)).thenReturn(Optional.of(repayment));
        when(loanRepository.findById(5L)).thenReturn(Optional.of(loan));
        when(transactionRepository.sumAmountByLoanId(5L)).thenReturn(new BigDecimal("900.00"));

        // 900 - 400 + 500 = 1000, exactly the principal
        transactionService.updateTransaction(
                51L, 1L, LocalDate.now(), new BigDecimal("500.00"), null, null, "to the limit");

        assertThat(bank.getBalance()).isEqualByComparingTo("1100.00");
    }
}
