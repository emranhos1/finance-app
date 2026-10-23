package com.finance.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Manual "actual" monthly expense for an account type, overrides the calculated total on the Dashboard. */
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

    /** Typed in by the user, so the daily recalculation must not overwrite it. */
    @Builder.Default
    @Column(name = "is_manual", nullable = false)
    private Boolean isManual = false;

    /** Month total of Bank + Cash income. Same value on the CASH and BANK rows. Null until filled. */
    @Column(name = "income_amount", precision = 15, scale = 2)
    private BigDecimal incomeAmount;

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