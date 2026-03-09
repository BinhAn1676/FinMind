package com.finance.financeservice.service.impl;

import com.finance.financeservice.mongo.document.Category;
import com.finance.financeservice.mongo.repository.CategoryRepository;
import com.finance.financeservice.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    private static final List<String> DEFAULT_CATEGORIES = Arrays.asList(
            "không xác định",
            "Ăn uống",
            "Mua sắm",
            "Giao thông",
            "Giải trí",
            "Sức khỏe",
            "Giáo dục",
            "Hóa đơn",
            "Lương",
            "Thu nhập khác",
            "Chi tiêu khác",
            "Y tế",
            "Đầu tư",
            "Du lịch",
            "Quà tặng",
            "Gia đình"
    );

    /**
     * Get all categories for a user.
     * Result is cached by userId for fast access from AIService.
     * Cache is automatically evicted when categories are added/deleted.
     */
    @Override
    @Cacheable(value = "userCategories", key = "#userId")
    public List<Category> getAllCategories(String userId) {
        log.debug("Fetching categories from database for user: {}", userId);
        List<Category> categories = categoryRepository.findByUserId(userId);

        // If user has no categories, initialize default ones
        if (categories.isEmpty()) {
            initializeDefaultCategories(userId);
            categories = categoryRepository.findByUserId(userId);
        }

        log.info("Loaded {} categories for user {} (cached)", categories.size(), userId);
        return categories;
    }

    /**
     * Add a new category for a user.
     * Evicts cache so next getAllCategories call fetches updated list.
     */
    @Override
    @CacheEvict(value = "userCategories", key = "#userId")
    public Category addCategory(String userId, String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name cannot be empty");
        }

        String trimmedName = name.trim();

        // Check if category already exists
        if (categoryRepository.existsByUserIdAndName(userId, trimmedName)) {
            throw new IllegalArgumentException("Category already exists");
        }

        Category category = Category.builder()
                .name(trimmedName)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .isDefault(false)
                .build();

        Category saved = categoryRepository.save(category);
        log.info("Added category '{}' for user {} and evicted cache", trimmedName, userId);
        return saved;
    }

    /**
     * Delete a category for a user.
     * Evicts cache so next getAllCategories call fetches updated list.
     */
    @Override
    @CacheEvict(value = "userCategories", key = "#userId")
    public void deleteCategory(String userId, String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name cannot be empty");
        }

        String trimmedName = categoryName.trim();

        // Don't allow deleting default categories
        Category category = categoryRepository.findByUserIdAndName(userId, trimmedName)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        if (category.getIsDefault() != null && category.getIsDefault()) {
            throw new IllegalArgumentException("Cannot delete default category");
        }

        categoryRepository.deleteByUserIdAndName(userId, trimmedName);
        log.info("Deleted category '{}' for user {} and evicted cache", trimmedName, userId);
    }

    /**
     * Initialize default categories for a new user.
     * Evicts cache so next getAllCategories call fetches the new categories.
     */
    @Override
    @CacheEvict(value = "userCategories", key = "#userId")
    public void initializeDefaultCategories(String userId) {
        // Check if user already has categories
        if (!categoryRepository.findByUserId(userId).isEmpty()) {
            return;
        }

        List<Category> defaultCategories = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (String name : DEFAULT_CATEGORIES) {
            // Check if category already exists
            if (!categoryRepository.existsByUserIdAndName(userId, name)) {
                Category category = Category.builder()
                        .name(name)
                        .userId(userId)
                        .createdAt(now)
                        .isDefault(true)
                        .build();
                defaultCategories.add(category);
            }
        }

        if (!defaultCategories.isEmpty()) {
            categoryRepository.saveAll(defaultCategories);
            log.info("Initialized {} default categories for user {} and evicted cache", defaultCategories.size(), userId);
        }
    }
}
