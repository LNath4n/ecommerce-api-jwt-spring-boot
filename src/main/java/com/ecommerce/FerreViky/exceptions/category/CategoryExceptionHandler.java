package com.ecommerce.FerreViky.exceptions.category;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CategoryExceptionHandler {

    @ExceptionHandler(CategoryExceptions.DuplicateCategoryException.class)
    public ResponseEntity<String> handleDuplicateCategory(CategoryExceptions.DuplicateCategoryException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }

    @ExceptionHandler(CategoryExceptions.CategoryHasProductsException.class)
    public ResponseEntity<String> handleCategoryHasProducts(CategoryExceptions.CategoryHasProductsException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }
}