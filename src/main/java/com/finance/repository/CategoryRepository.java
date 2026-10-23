package com.finance.repository;

import com.finance.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByUserId(Long userId);
    List<Category> findByUserIdAndTypeIn(Long userId, List<Category.CategoryType> types);
    boolean existsByUserIdAndNameAndType(Long userId, String name, Category.CategoryType type);
}