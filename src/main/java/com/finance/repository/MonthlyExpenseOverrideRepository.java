package com.finance.repository;

import com.finance.entity.MonthlyExpenseOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MonthlyExpenseOverrideRepository extends JpaRepository<MonthlyExpenseOverride, Long> {
    List<MonthlyExpenseOverride> findByUserIdAndAccountTypeNameAndExpenseYear(Long userId, String accountTypeName, Integer expenseYear);

    Optional<MonthlyExpenseOverride> findByUserIdAndAccountTypeNameAndExpenseYearAndExpenseMonth(
            Long userId, String accountTypeName, Integer expenseYear, Integer expenseMonth);

    List<MonthlyExpenseOverride> findByIncomeAmountIsNull();

    @Query("SELECT DISTINCT m.expenseYear FROM MonthlyExpenseOverride m WHERE m.userId = :userId")
    List<Integer> findDistinctYearsByUserId(@Param("userId") Long userId);
}