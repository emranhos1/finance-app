package com.finance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A user-entered "actual" monthly expense figure for a given account type (e.g. CASH, BANK),
 *  used to override the auto-calculated total on the Dashboard when transaction history is
 *  incomplete for that month (e.g. data carried over from a manual spreadsheet). */
@Entity
@Table(name = "monthly_expense_overrides")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyExpenseOverride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "account_type_name", nullable = false, length = 100)
    private String accountTypeName;

    @Column(name = "expense_year", nullable = false)
    private Integer expenseYear;

    @Column(name = "expense_month", nullable = false)
    private Integer expenseMonth;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /** True if the user typed this figure in manually (via the ✎ edit button) - such a value is
     *  never overwritten by the automatic daily recalculation of the current month's total. */
    @Builder.Default
    @Column(name = "is_manual", nullable = false)
    private Boolean isManual = false;

    /** The month's total Bank + Cash income - the same figure is kept on both the CASH and the BANK
     *  row of a month. Null until filled (by the startup backfill, the auto-save or a manual edit). */
    @Column(name = "income_amount", precision = 15, scale = 2)
    private BigDecimal incomeAmount;

    /** Same meaning as isManual, but for incomeAmount. */
    @Builder.Default
    @Column(name = "is_income_manual", nullable = false)
    private Boolean isIncomeManual = false;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        updatedAt = LocalDateTime.now();
    }
}