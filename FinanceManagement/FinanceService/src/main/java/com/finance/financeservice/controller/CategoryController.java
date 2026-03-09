package com.finance.financeservice.controller;

import com.finance.financeservice.mongo.document.Category;
import com.finance.financeservice.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<Category>> getAllCategories(
            @RequestParam(value = "userId", required = true) String userId) {
        List<Category> categories = categoryService.getAllCategories(userId);
        return ResponseEntity.ok(categories);
    }

    @PostMapping
    public ResponseEntity<Category> addCategory(
            @RequestParam(value = "userId", required = true) String userId,
            @RequestParam(value = "name", required = true) String name) {
        try {
            Category category = categoryService.addCategory(userId, name);
            return ResponseEntity.ok(category);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteCategory(
            @RequestParam(value = "userId", required = true) String userId,
            @RequestParam(value = "name", required = true) String name) {
        try {
            categoryService.deleteCategory(userId, name);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Category deleted successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/initialize")
    public ResponseEntity<Map<String, String>> initializeDefaultCategories(
            @RequestParam(value = "userId", required = true) String userId) {
        categoryService.initializeDefaultCategories(userId);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Default categories initialized");
        return ResponseEntity.ok(response);
    }
}
