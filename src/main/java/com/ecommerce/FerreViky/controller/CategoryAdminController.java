package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.dtos.category.CategoryDTO.CategoryRequest;
import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.CategoryOptionResponse;
import com.ecommerce.FerreViky.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
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
            @ApiResponse(
                    responseCode = "400",
                    description = "Category fields failed validation",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    name = "MissingCategoryName",
                                    summary = "Category name is required",
                                    value = "{\"status\":400,\"message\":\"Validation failed\",\"errors\":{\"name\":\"Name is required\"}}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "A category with this name already exists, ignoring capitalization",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "A category named Gift Cards already exists")
                    )
            )
    })
    public ResponseEntity<CategoryOptionResponse> createCategory(@Validated @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update category", description = "Changes the category name. The name must remain unique.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category updated successfully"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Category fields failed validation",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    name = "MissingCategoryName",
                                    summary = "Category name is required",
                                    value = "{\"status\":400,\"message\":\"Validation failed\",\"errors\":{\"name\":\"Name is required\"}}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Category does not exist",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Category not found with ID: 3")
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Another category already has this name, ignoring capitalization",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "A category named Gift Cards already exists")
                    )
            )
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
            @ApiResponse(
                    responseCode = "404",
                    description = "Category does not exist",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Category not found with ID: 3")
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The category has associated products and cannot be deleted",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(
                                    type = "string",
                                    example = "Category with ID 3 has associated products and cannot be deleted"
                            )
                    )
            )
    })
    public ResponseEntity<String> deleteCategory(
            @Parameter(description = "ID of the category to delete", example = "1") @PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok("Category deleted successfully");
    }
}