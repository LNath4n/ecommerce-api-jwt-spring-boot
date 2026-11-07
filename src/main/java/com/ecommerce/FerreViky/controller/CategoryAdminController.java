package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.dtos.category.CategoryDTO.CategoryRequest;
import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.CategoryOptionResponse;
import com.ecommerce.FerreViky.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/admin/categories")
@RestController
@AllArgsConstructor
public class CategoryAdminController {

    private final CategoryService categoryService;

    @PostMapping
    @Operation(summary = "Create category", description = "Creates a new category. The name must be unique, regardless of capitalization.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Category created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid category data"),
            @ApiResponse(responseCode = "409", description = "A category with this name already exists")
    })
    public ResponseEntity<CategoryOptionResponse> createCategory(@Validated @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update category", description = "Changes the category name. The name must remain unique.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid category data"),
            @ApiResponse(responseCode = "404", description = "Category not found"),
            @ApiResponse(responseCode = "409", description = "A category with this name already exists")
    })
    public ResponseEntity<CategoryOptionResponse> updateCategory(
            @Parameter(description = "ID of the category to update", example = "1") @PathVariable Long id,
            @Validated @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.updateCategory(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete category", description = "Deletes a category. If it has associated products, it cannot be deleted; reassign them or remove their category first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Category not found"),
            @ApiResponse(responseCode = "409", description = "The category has associated products")
    })
    public ResponseEntity<String> deleteCategory(
            @Parameter(description = "ID of the category to delete", example = "1") @PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok("Category deleted successfully");
    }
}