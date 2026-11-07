package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.dtos.producto.ProductoDTO.ProductoAdminResponse;
import com.ecommerce.FerreViky.dto.dtos.producto.ProductoDTO.ProductoPublicoResponse;
import com.ecommerce.FerreViky.dto.dtos.producto.ProductoDTO.ProductoRequest;
import com.ecommerce.FerreViky.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RequestMapping("/admin/products")
@RestController
@AllArgsConstructor
public class ProductAdminController {

    private final ProductService productService;

    @PostMapping
    @Operation(summary = "Create product", description = "Creates a new product. If categoryId is provided, the category must exist; if it is null, the product is created without a category. Stock is not included in the request; it is calculated from the uploaded codes. Upload the image separately using POST /admin/products/{id}/image.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid product data"),
            @ApiResponse(responseCode = "404", description = "Category not found"),
            @ApiResponse(responseCode = "409", description = "A product with this SKU already exists")
    })
    public ResponseEntity<ProductoPublicoResponse> createProduct(@Validated @RequestBody ProductoRequest productoAdminRequest) {
        return  ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(productoAdminRequest));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete product", description = "Deletes a product and its image from the bucket. If it has associated codes or orders, it cannot be deleted; deactivate it by setting activo = false.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Product not found"),
            @ApiResponse(responseCode = "409", description = "The product has associated codes or orders")
    })
    public ResponseEntity<String> deleteProduct(
            @Parameter(description = "ID of the product to delete", example = "1") @PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok("Product deleted successfully");
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate product", description = "Deactivates a product without deleting it: it no longer appears in the public catalog, but its codes, orders, and image are kept. Use this when the product cannot be deleted.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product deactivated successfully"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ResponseEntity<String> deactivateProduct(
            @Parameter(description = "ID of the product to deactivate", example = "1") @PathVariable Long id) {
        productService.deactivateProduct(id);
        return ResponseEntity.ok("Product deactivated successfully");
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update product", description = "Replaces the product data with the request data (PUT semantics). Null fields are saved as null, except 'activo', which retains its value if omitted. The image is not modified.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid product data"),
            @ApiResponse(responseCode = "404", description = "Product or category not found"),
            @ApiResponse(responseCode = "409", description = "A product with this SKU already exists")
    })
    public ResponseEntity<ProductoPublicoResponse> updateProduct(
            @Validated @RequestBody ProductoRequest productoAdminRequest,
            @Parameter(description = "ID of the product to update", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(productService.updateProduct(productoAdminRequest, id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID", description = "Returns the admin view of a specific product by its ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ResponseEntity<ProductoAdminResponse> getProduct(
            @Parameter(description = "ID of the product to retrieve", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(productService.getAdminProductById(id));
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload product image",
            description = "Uploads the product image (multipart field 'file'). Only JPG and PNG are accepted, with a 5 MB maximum. If an image already exists, it is replaced and the previous one is deleted from the bucket.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Image uploaded; returns the product with its imageUrl"),
            @ApiResponse(responseCode = "400", description = "The file is not a valid image or its format is not allowed (JPG and PNG only)"),
            @ApiResponse(responseCode = "404", description = "Product not found"),
            @ApiResponse(responseCode = "413", description = "The image exceeds the maximum allowed size")
    })
    public ResponseEntity<ProductoAdminResponse> uploadProductImage(
            @Parameter(description = "Product ID", example = "1") @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(productService.uploadProductImage(id, file));
    }

    @DeleteMapping("/{id}/image")
    @Operation(summary = "Remove product image",
            description = "Deletes the product image from the bucket without deleting the product. If there is no image, nothing happens.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Image deleted; returns the product without imageUrl"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ResponseEntity<ProductoAdminResponse> removeProductImage(
            @Parameter(description = "Product ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(productService.removeProductImage(id));
    }
}