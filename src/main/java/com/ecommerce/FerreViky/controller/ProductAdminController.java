package com.ecommerce.FerreViky.controller;

import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.AdminProductResponse;
import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.PublicProductResponse;
import com.ecommerce.FerreViky.dto.dtos.product.ProductDTO.ProductRequest;
import com.ecommerce.FerreViky.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
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
            @ApiResponse(
                    responseCode = "400",
                    description = "Product fields failed validation",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    name = "InvalidProductName",
                                    summary = "Required product name is missing",
                                    value = "{\"status\":400,\"message\":\"Validation failed\",\"errors\":{\"name\":\"Name is required\"}}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "The specified category does not exist",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Category not found with ID: 3")
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "A product with this SKU already exists",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "A product with SKU STEAM-MX-200 already exists")
                    )
            )
    })
    public ResponseEntity<PublicProductResponse> createProduct(@Validated @RequestBody ProductRequest productRequest) {
        return  ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(productRequest));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete product", description = "Deletes a product and its image from the bucket. If it has associated codes or orders, it cannot be deleted; deactivate it by setting active = false.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product deleted successfully"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product does not exist",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Product not found with ID: 2")
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "The product has associated codes or other references and cannot be deleted",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(
                                    type = "string",
                                    example = "Product with ID 2 cannot be deleted because it has dependencies"
                            )
                    )
            )
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
            @ApiResponse(
                    responseCode = "404",
                    description = "Product does not exist",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Product not found with ID: 2")
                    )
            )
    })
    public ResponseEntity<String> deactivateProduct(
            @Parameter(description = "ID of the product to deactivate", example = "1") @PathVariable Long id) {
        productService.deactivateProduct(id);
        return ResponseEntity.ok("Product deactivated successfully");
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update product", description = "Replaces the product data with the request data (PUT semantics). Null fields are saved as null, except 'active', which retains its value if omitted. The image is not modified.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product updated successfully"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Product fields failed validation",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    name = "InvalidProductName",
                                    summary = "Required product name is missing",
                                    value = "{\"status\":400,\"message\":\"Validation failed\",\"errors\":{\"name\":\"Name is required\"}}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product or specified category does not exist",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string"),
                            examples = {
                                    @ExampleObject(
                                            name = "ProductNotFound",
                                            summary = "Product does not exist",
                                            value = "Product not found with ID: 2"
                                    ),
                                    @ExampleObject(
                                            name = "CategoryNotFound",
                                            summary = "Specified category does not exist",
                                            value = "Category not found with ID: 3"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "A product with this SKU already exists",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "A product with SKU STEAM-MX-200 already exists")
                    )
            )
    })
    public ResponseEntity<PublicProductResponse> updateProduct(
            @Validated @RequestBody ProductRequest productRequest,
            @Parameter(description = "ID of the product to update", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(productService.updateProduct(productRequest, id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID", description = "Returns the admin view of a specific product by its ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product does not exist",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Product not found with ID: 2")
                    )
            )
    })
    public ResponseEntity<AdminProductResponse> getProduct(
            @Parameter(description = "ID of the product to retrieve", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(productService.getAdminProductById(id));
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload product image",
            description = "Uploads the product image (multipart field 'file'). Only JPG and PNG are accepted, with a 5 MB maximum. If an image already exists, it is replaced and the previous one is deleted from the bucket.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Image uploaded; returns the product with its imageUrl"),
            @ApiResponse(
                    responseCode = "400",
                    description = "The file is missing, malformed, not a valid image, or has an unsupported format",
                    content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(type = "object"),
                                    examples = {
                                            @ExampleObject(
                                                    name = "MissingFile",
                                                    summary = "The multipart file field is missing",
                                                    value = "{\"status\":400,\"message\":\"Parameter 'file' is required\"}"
                                            ),
                                            @ExampleObject(
                                                    name = "InvalidMultipart",
                                                    summary = "The multipart request is malformed",
                                                    value = "{\"error\":\"Invalid multipart request\"}"
                                            )
                                    }
                            ),
                            @Content(
                                    mediaType = "text/plain",
                                    schema = @Schema(type = "string"),
                                    examples = {
                                            @ExampleObject(
                                                    name = "UnsupportedImageFormat",
                                                    summary = "Only JPG and PNG are supported",
                                                    value = "Unsupported format. Use JPG or PNG"
                                            ),
                                            @ExampleObject(
                                                    name = "InvalidImage",
                                                    summary = "The uploaded file is not a readable image",
                                                    value = "The file is not a valid image"
                                            )
                                    }
                            )
                    }
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product does not exist",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Product not found with ID: 2")
                    )
            ),
            @ApiResponse(
                    responseCode = "413",
                    description = "The image exceeds the maximum allowed size",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    name = "ImageTooLarge",
                                    summary = "Uploaded image exceeds the configured request size limit",
                                    value = "{\"error\":\"The uploaded file exceeds the maximum allowed size\"}"
                            )
                    )
            )
    })
    public ResponseEntity<AdminProductResponse> uploadProductImage(
            @Parameter(description = "Product ID", example = "1") @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(productService.uploadProductImage(id, file));
    }

    @DeleteMapping("/{id}/image")
    @Operation(summary = "Remove product image",
            description = "Deletes the product image from the bucket without deleting the product. If there is no image, nothing happens.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Image deleted; returns the product without imageUrl"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product does not exist",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Product not found with ID: 2")
                    )
            )
    })
    public ResponseEntity<AdminProductResponse> removeProductImage(
            @Parameter(description = "Product ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(productService.removeProductImage(id));
    }
}