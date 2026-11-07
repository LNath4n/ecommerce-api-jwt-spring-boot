package com.ecommerce.FerreViky.exceptions.product;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProductExceptionHandler {

    @ExceptionHandler(ProductExceptions.ProductNotFoundException.class)
    public ResponseEntity<String> handleProductNotFound(ProductExceptions.ProductNotFoundException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }

    @ExceptionHandler(ProductExceptions.ProductGroupNotFoundException.class)
    public ResponseEntity<String> handleProductGroupNotFound(ProductExceptions.ProductGroupNotFoundException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }

    @ExceptionHandler(ProductExceptions.CategoryNotFoundException.class)
    public ResponseEntity<String> handleCategoryNotFound(ProductExceptions.CategoryNotFoundException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }

    @ExceptionHandler(ProductExceptions.InvalidImageException.class)
    public ResponseEntity<String> handleInvalidImage(ProductExceptions.InvalidImageException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(ProductExceptions.DuplicateSkuException.class)
    public ResponseEntity<String> handleDuplicateSku(ProductExceptions.DuplicateSkuException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }

    @ExceptionHandler(ProductExceptions.ProductHasDependenciesException.class)
    public ResponseEntity<String> handleProductHasDependencies(ProductExceptions.ProductHasDependenciesException ex) {
        return ResponseEntity.status(409).body(ex.getMessage());
    }
}
