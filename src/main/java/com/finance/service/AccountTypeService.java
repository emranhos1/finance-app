package com.finance.service;

import com.finance.entity.AccountType;
import com.finance.repository.AccountTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountTypeService {
    private final AccountTypeRepository accountTypeRepository;

    public AccountType createAccountType(Long userId, String name) {
        if (name == null || name.trim().isEmpty())
            throw new IllegalArgumentException("Account type name cannot be empty");
        String trimmed = name.trim();
        if (accountTypeRepository.existsByUserIdAndNameIgnoreCase(userId, trimmed))
            throw new IllegalArgumentException("Account type '" + trimmed + "' already exists");
        return accountTypeRepository.save(AccountType.builder().userId(userId).name(trimmed).build());
    }

    public List<AccountType> getAccountTypesByUserId(Long userId) {
        return accountTypeRepository.findByUserId(userId);
    }

    public AccountType updateAccountType(Long typeId, Long userId, String newName) {
        AccountType type = accountTypeRepository.findById(typeId)
                .orElseThrow(() -> new IllegalArgumentException("Account type not found"));
        if (!type.getUserId().equals(userId)) throw new SecurityException("Access denied");

        String trimmed = newName.trim();
        // block only if a different record has the same name
        boolean duplicateExists = accountTypeRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, trimmed, typeId);
        if (duplicateExists)
            throw new IllegalArgumentException("Account type '" + trimmed + "' already exists");

        type.setName(trimmed);
        return accountTypeRepository.save(type);
    }

    public void deleteAccountType(Long typeId, Long userId) {
        AccountType type = accountTypeRepository.findById(typeId)
                .orElseThrow(() -> new IllegalArgumentException("Account type not found"));
        if (!type.getUserId().equals(userId)) throw new SecurityException("Access denied");
        accountTypeRepository.deleteById(typeId);
    }
}