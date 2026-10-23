package com.finance.repository;

import com.finance.entity.Account;
import com.finance.entity.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByUserId(Long userId);
    List<Account> findByUserIdAndMaturityDateNotNullAndMaturityDateLessThanEqual(Long userId, LocalDate cutoff);

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM Account a WHERE a.userId = :userId AND a.accountType = :accountType")
    BigDecimal sumBalanceByUserIdAndAccountType(@Param("userId") Long userId, @Param("accountType") AccountType accountType);
}