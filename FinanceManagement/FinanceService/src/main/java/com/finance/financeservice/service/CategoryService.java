package com.finance.financeservice.service;

import com.finance.financeservice.mongo.document.Category;

import java.util.List;

public interface CategoryService {
    List<Category> getAllCategories(String userId);
    
    Category addCategory(String userId, String name);
    
    void deleteCategory(String userId, String categoryName);
    
    void initializeDefaultCategories(String userId);
}
